// Pure logic of the play-billing Edge Function (docs/play-billing.md): reading Google's answer about
// a subscription, reading Real-time Developer Notifications, and signing the service-account token.
// No Deno or network APIs (only WebCrypto, passed in), so `node --test` can run logic.test.ts.
// Google reference: purchases.subscriptionsv2 (SubscriptionPurchaseV2) and the RTDN reference.

/** Subscription products sold in the app (Play Console → Monetize → Subscriptions). */
export const PLAY_PRODUCTS = ["scriptgo_pro"] as const;
export const isPlayProduct = (value: unknown): boolean => typeof value === "string" && (PLAY_PRODUCTS as readonly string[]).includes(value);

export const ANDROID_PUBLISHER = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";
export const SCOPE = "https://www.googleapis.com/auth/androidpublisher";

const STATES = ["pending", "active", "paused", "in_grace_period", "on_hold", "canceled", "expired", "pending_purchase_canceled"];

/** What play_upsert_subscription stores (without the account). */
export interface Subscription {
  productId: string;
  basePlanId: string;
  offerId: string;
  state: string;
  expiresAt: string | null;
  autoRenewing: boolean;
  acknowledged: boolean;
  testPurchase: boolean;
  latestOrderId: string;
  /** Token this purchase replaces (upgrade, downgrade, or resubscribe), or null. */
  linkedPurchaseToken: string | null;
  /** obfuscatedExternalAccountId the app set when buying (sha256 hex of the user id), or "". */
  accountHash: string;
}

const text = (value: unknown): string => (typeof value === "string" ? value : "");

/**
 * Reads a SubscriptionPurchaseV2. The line item with the latest expiry decides product, plan and
 * expiry (a subscription has one line item unless it is a bundle). Throws on a body that is not one.
 */
export function parseSubscription(body: unknown): Subscription {
  const b = body as Record<string, any> | null;
  const items = Array.isArray(b?.lineItems) ? b!.lineItems : [];
  if (!b || typeof b.subscriptionState !== "string" || items.length === 0) throw new Error("not_a_subscription");
  const item = items.reduce((best: any, it: any) => (text(it?.expiryTime) > text(best?.expiryTime) ? it : best), items[0]);
  const state = b.subscriptionState.replace(/^SUBSCRIPTION_STATE_/, "").toLowerCase();
  return {
    productId: text(item?.productId),
    basePlanId: text(item?.offerDetails?.basePlanId),
    offerId: text(item?.offerDetails?.offerId),
    state: STATES.includes(state) ? state : "unspecified",
    expiresAt: text(item?.expiryTime) || null,
    autoRenewing: item?.autoRenewingPlan?.autoRenewEnabled === true,
    acknowledged: b.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    testPurchase: b.testPurchase != null,
    latestOrderId: text(item?.latestSuccessfulOrderId) || text(b.latestOrderId),
    linkedPurchaseToken: text(b.linkedPurchaseToken) || null,
    accountHash: text(b.externalAccountIdentifiers?.obfuscatedExternalAccountId).toLowerCase(),
  };
}

/** Whether Google still owes the buyer access, so the purchase must be acknowledged (within 3 days). */
export const needsAcknowledge = (s: Subscription): boolean => !s.acknowledged && (s.state === "active" || s.state === "in_grace_period");

export type Notification =
  | { kind: "test" }
  | { kind: "subscription"; packageName: string; purchaseToken: string; type: number }
  | { kind: "voided"; packageName: string; purchaseToken: string }
  | { kind: "other" };

/**
 * Reads a Pub/Sub push body ({message: {data: base64 JSON}}) carrying a Real-time Developer
 * Notification. Throws when the body is not a Pub/Sub message.
 */
export function parseNotification(body: unknown, decodeBase64: (data: string) => string): Notification {
  const data = (body as any)?.message?.data;
  if (typeof data !== "string") throw new Error("not_pubsub");
  const n = JSON.parse(decodeBase64(data));
  const packageName = text(n?.packageName);
  if (n?.testNotification) return { kind: "test" };
  const sub = n?.subscriptionNotification;
  if (sub && text(sub.purchaseToken)) return { kind: "subscription", packageName, purchaseToken: sub.purchaseToken, type: Number(sub.notificationType) || 0 };
  // productType 1 = subscription; a refunded subscription is re-read like any other change.
  const voided = n?.voidedPurchaseNotification;
  if (voided && text(voided.purchaseToken) && Number(voided.productType) === 1) return { kind: "voided", packageName, purchaseToken: voided.purchaseToken };
  return { kind: "other" };
}

const base64url = (bytes: Uint8Array): string => {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};
const utf8 = (s: string) => new TextEncoder().encode(s);

/** DER bytes of a PKCS#8 PEM private key (the private_key of a service-account JSON). */
export function pemToDer(pem: string): Uint8Array<ArrayBuffer> {
  const b64 = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const bin = atob(b64);
  const out = new Uint8Array(new ArrayBuffer(bin.length));
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export interface ServiceAccount {
  client_email: string;
  private_key: string;
  token_uri?: string;
}

/** Reads the service-account JSON secret; null when it is missing or not one. */
export function parseServiceAccount(json: string | undefined): ServiceAccount | null {
  if (!json) return null;
  try {
    const sa = JSON.parse(json);
    return typeof sa?.client_email === "string" && typeof sa?.private_key === "string" ? sa : null;
  } catch {
    return null;
  }
}

/**
 * The signed JWT a service account trades for an access token (OAuth 2.0 JWT bearer grant),
 * valid for an hour from [nowSeconds]. [subtle] is WebCrypto (Deno and Node both have it).
 */
export async function serviceAccountJwt(sa: ServiceAccount, nowSeconds: number, subtle: SubtleCrypto): Promise<string> {
  const header = base64url(utf8(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = base64url(utf8(JSON.stringify({
    iss: sa.client_email,
    scope: SCOPE,
    aud: sa.token_uri ?? "https://oauth2.googleapis.com/token",
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  })));
  const key = await subtle.importKey("pkcs8", pemToDer(sa.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = new Uint8Array(await subtle.sign("RSASSA-PKCS1-v1_5", key, utf8(`${header}.${claims}`)));
  return `${header}.${claims}.${base64url(signature)}`;
}

/** Compares two secrets in time that does not depend on where they differ. */
export function sameSecret(a: string, b: string): boolean {
  if (!a || !b) return false;
  let diff = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++) diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return diff === 0;
}

/** The URL that reads (GET) a subscription purchase. */
export const subscriptionUrl = (packageName: string, token: string) =>
  `${ANDROID_PUBLISHER}/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(token)}`;

/** The URL that acknowledges (POST) a subscription purchase. */
export const acknowledgeUrl = (packageName: string, productId: string, token: string) =>
  `${ANDROID_PUBLISHER}/${encodeURIComponent(packageName)}/purchases/subscriptions/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}:acknowledge`;
