// play-billing: ScriptGo Pro bought through Google Play Billing in the Android app (docs/play-billing.md).
//   POST JSON {purchaseToken} with the user's access token (the app, right after buying) → asks
//     Google for the subscription, checks it was bought for this account (obfuscatedExternalAccountId
//     = sha256 of the user id), stores it, acknowledges it, and returns {plan} (my_plan's JSON).
//   POST from Pub/Sub to ?token=PLAY_RTDN_TOKEN (Real-time Developer Notifications: renewals,
//     cancellations, grace period, refunds) → reads the subscription from Google again and stores it.
// Google is always asked; nothing the app or the notification says about the state is trusted.
// Deployed with verify_jwt = false because Pub/Sub has no Supabase JWT; the app path checks the
// token itself. Secrets (Supabase → Edge Functions → Secrets): PLAY_SERVICE_ACCOUNT_JSON,
// PLAY_PACKAGE_NAME (id.scriptgo.keyboard), PLAY_RTDN_TOKEN. Without them it answers 503.
import {
  acknowledgeUrl, isPlayProduct, needsAcknowledge, parseNotification, parseServiceAccount, parseSubscription,
  sameSecret, serviceAccountJwt, subscriptionUrl, type Subscription,
} from "./logic.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const ACCOUNT = parseServiceAccount(Deno.env.get("PLAY_SERVICE_ACCOUNT_JSON"));
const PACKAGE = Deno.env.get("PLAY_PACKAGE_NAME") ?? "id.scriptgo.keyboard";
const RTDN_TOKEN = Deno.env.get("PLAY_RTDN_TOKEN") ?? "";

const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

async function sha256Hex(text: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
  return Array.from(digest, (b) => b.toString(16).padStart(2, "0")).join("");
}

const decodeBase64 = (data: string) => new TextDecoder().decode(Uint8Array.from(atob(data), (c) => c.charCodeAt(0)));

async function userId(authorization: string | null): Promise<string | null> {
  if (!authorization?.startsWith("Bearer ")) return null;
  const res = await fetch(`${SUPABASE_URL}/auth/v1/user`, { headers: { apikey: ANON_KEY, Authorization: authorization } });
  if (!res.ok) return null;
  const user = await res.json();
  return typeof user?.id === "string" ? user.id : null;
}

/** Calls a play_* RPC as the service role; returns its JSON, or throws with the error message. */
async function rpc(name: string, args: Record<string, unknown>): Promise<unknown> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: { apikey: SERVICE_KEY, Authorization: `Bearer ${SERVICE_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify(args),
  });
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(body?.message ?? `rpc ${res.status}`);
  return body;
}

let access: { token: string; until: number } | null = null;

/** An access token for the Play Developer API, reused until a minute before it expires. */
async function googleToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (access && access.until > now + 60) return access.token;
  const assertion = await serviceAccountJwt(ACCOUNT!, now, crypto.subtle);
  const res = await fetch(ACCOUNT!.token_uri ?? "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  const body = await res.json().catch(() => null);
  if (!res.ok || typeof body?.access_token !== "string") throw new Error(`google_auth ${res.status}`);
  access = { token: body.access_token, until: now + (Number(body.expires_in) || 3600) };
  return access.token;
}

/** Google's view of [token]; null when Google does not know it (wrong app or made up). */
async function fetchSubscription(token: string): Promise<Subscription | null> {
  const res = await fetch(subscriptionUrl(PACKAGE, token), { headers: { Authorization: `Bearer ${await googleToken()}` } });
  if (res.status === 400 || res.status === 404 || res.status === 410) return null;
  if (!res.ok) throw new Error(`google ${res.status}`);
  return parseSubscription(await res.json());
}

/** Stores [s] for [owner], acknowledges it with Google when needed, and returns the owner's plan. */
async function store(token: string, owner: string, s: Subscription): Promise<unknown> {
  const plan = await rpc("play_upsert_subscription", {
    purchase_token: token, target: owner, product_id: s.productId, base_plan_id: s.basePlanId, offer_id: s.offerId,
    state: s.state, expires_at: s.expiresAt, auto_renewing: s.autoRenewing, acknowledged: s.acknowledged,
    test_purchase: s.testPurchase, latest_order_id: s.latestOrderId, replaces: s.linkedPurchaseToken,
  });
  if (needsAcknowledge(s)) {
    const res = await fetch(acknowledgeUrl(PACKAGE, s.productId, token), {
      method: "POST",
      headers: { Authorization: `Bearer ${await googleToken()}`, "Content-Type": "application/json" },
      body: "{}",
    });
    // Unacknowledged purchases are refunded after 3 days; the next notification or app call retries.
    if (res.ok) await rpc("play_mark_acknowledged", { purchase_token: token });
    else console.error("acknowledge failed", res.status);
  }
  return plan;
}

async function fromApp(req: Request): Promise<Response> {
  const user = await userId(req.headers.get("Authorization"));
  if (!user) return reply(401, { error: "not_signed_in" });
  const token = (await req.json().catch(() => null))?.purchaseToken;
  if (typeof token !== "string" || token.length < 10 || token.length > 4096) return reply(400, { error: "bad_token" });
  const s = await fetchSubscription(token);
  if (!s) return reply(400, { error: "unknown_purchase" });
  if (!isPlayProduct(s.productId)) return reply(400, { error: "unknown_product" });
  if (s.accountHash !== (await sha256Hex(user))) return reply(403, { error: "other_account" });
  try {
    return reply(200, { plan: await store(token, user, s) });
  } catch (e) {
    if (e instanceof Error && e.message === "token_owned_by_other_account") return reply(409, { error: "other_account" });
    throw e;
  }
}

async function fromGoogle(req: Request): Promise<Response> {
  const n = parseNotification(await req.json(), decodeBase64);
  // Answering 200 tells Pub/Sub not to retry; errors below (500) make it try again later.
  if (n.kind === "test" || n.kind === "other") return reply(200, { ok: true, ignored: n.kind });
  if (n.packageName !== PACKAGE) return reply(200, { ok: true, ignored: "package" });
  const s = await fetchSubscription(n.purchaseToken);
  if (!s || !isPlayProduct(s.productId)) return reply(200, { ok: true, ignored: "unknown_purchase" });
  const owner = ((await rpc("play_owner_of", { purchase_token: n.purchaseToken })) as string | null)
    ?? (s.accountHash ? ((await rpc("play_account_for", { account_hash: s.accountHash })) as string | null) : null)
    ?? (s.linkedPurchaseToken ? ((await rpc("play_owner_of", { purchase_token: s.linkedPurchaseToken })) as string | null) : null);
  if (!owner) return reply(200, { ok: true, ignored: "no_account" });
  await store(n.purchaseToken, owner, s);
  return reply(200, { ok: true });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return reply(405, { error: "method" });
  if (!ACCOUNT) return reply(503, { error: "play_not_configured" });
  const rtdn = new URL(req.url).searchParams.get("token");
  try {
    if (rtdn !== null) {
      if (!sameSecret(rtdn, RTDN_TOKEN)) return reply(401, { error: "bad_rtdn_token" });
      return await fromGoogle(req);
    }
    return await fromApp(req);
  } catch (e) {
    console.error(e);
    return reply(500, { error: "server" });
  }
});
