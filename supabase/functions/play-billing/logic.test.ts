// Run: node --test supabase/functions/play-billing/logic.test.ts (Node 22.18+ strips the types).
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash, generateKeyPairSync, createVerify } from "node:crypto";
import {
  acknowledgeUrl, isPlayProduct, needsAcknowledge, parseNotification, parseServiceAccount, parseSubscription,
  sameSecret, serviceAccountJwt, subscriptionUrl,
} from "./logic.ts";

const USER = "6f1c2c3e-1111-4a4a-9b9b-222233334444";
const HASH = createHash("sha256").update(USER).digest("hex");

// Shape from the purchases.subscriptionsv2 reference.
const ACTIVE = {
  kind: "androidpublisher#subscriptionPurchaseV2",
  regionCode: "ID",
  startTime: "2026-09-29T02:00:00.000Z",
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  latestOrderId: "GPA.1111-2222-3333-44444",
  acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
  externalAccountIdentifiers: { obfuscatedExternalAccountId: HASH.toUpperCase() },
  testPurchase: {},
  lineItems: [{
    productId: "scriptgo_pro",
    expiryTime: "2026-10-29T02:00:00.000Z",
    autoRenewingPlan: { autoRenewEnabled: true },
    offerDetails: { basePlanId: "monthly", offerId: "founder", offerTags: [] },
    latestSuccessfulOrderId: "GPA.1111-2222-3333-44444",
  }],
};

test("an active purchase is read into what the database stores", () => {
  const s = parseSubscription(ACTIVE);
  assert.deepEqual(s, {
    productId: "scriptgo_pro", basePlanId: "monthly", offerId: "founder", state: "active",
    expiresAt: "2026-10-29T02:00:00.000Z", autoRenewing: true, acknowledged: false, testPurchase: true,
    latestOrderId: "GPA.1111-2222-3333-44444", linkedPurchaseToken: null, accountHash: HASH,
  });
  assert.equal(needsAcknowledge(s), true);
});

test("states lose their prefix; unknown ones become unspecified", () => {
  const read = (state: string) => parseSubscription({ ...ACTIVE, subscriptionState: state }).state;
  assert.equal(read("SUBSCRIPTION_STATE_IN_GRACE_PERIOD"), "in_grace_period");
  assert.equal(read("SUBSCRIPTION_STATE_CANCELED"), "canceled");
  assert.equal(read("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED"), "pending_purchase_canceled");
  assert.equal(read("SUBSCRIPTION_STATE_SOMETHING_NEW"), "unspecified");
});

test("an acknowledged, canceled, real purchase with a linked token", () => {
  const s = parseSubscription({
    ...ACTIVE, subscriptionState: "SUBSCRIPTION_STATE_CANCELED", acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    testPurchase: undefined, linkedPurchaseToken: "old-token-123",
    lineItems: [{ ...ACTIVE.lineItems[0], autoRenewingPlan: { autoRenewEnabled: false } }],
  });
  assert.equal(s.acknowledged, true);
  assert.equal(s.testPurchase, false);
  assert.equal(s.autoRenewing, false);
  assert.equal(s.linkedPurchaseToken, "old-token-123");
  assert.equal(needsAcknowledge(s), false);
});

test("the line item with the latest expiry wins; missing fields are empty", () => {
  const s = parseSubscription({
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    lineItems: [{ productId: "a", expiryTime: "2026-10-01T00:00:00Z" }, { productId: "b", expiryTime: "2026-12-01T00:00:00Z" }],
  });
  assert.equal(s.productId, "b");
  assert.equal(s.basePlanId, "");
  assert.equal(s.accountHash, "");
  assert.equal(s.latestOrderId, "");
});

test("a body that is not a subscription is refused", () => {
  assert.throws(() => parseSubscription({ error: { code: 404 } }), /not_a_subscription/);
  assert.throws(() => parseSubscription({ subscriptionState: "SUBSCRIPTION_STATE_ACTIVE", lineItems: [] }), /not_a_subscription/);
  assert.throws(() => parseSubscription(null), /not_a_subscription/);
});

const push = (payload: unknown) => ({ message: { data: Buffer.from(JSON.stringify(payload)).toString("base64"), messageId: "1" }, subscription: "projects/p/subscriptions/s" });
const decode = (data: string) => Buffer.from(data, "base64").toString("utf8");

test("RTDN: subscription, test, voided and other notifications", () => {
  assert.deepEqual(
    parseNotification(push({ version: "1.0", packageName: "id.scriptgo.keyboard", eventTimeMillis: "1", subscriptionNotification: { version: "1.0", notificationType: 4, purchaseToken: "tok-1234567890", subscriptionId: "scriptgo_pro" } }), decode),
    { kind: "subscription", packageName: "id.scriptgo.keyboard", purchaseToken: "tok-1234567890", type: 4 },
  );
  assert.deepEqual(parseNotification(push({ version: "1.0", packageName: "id.scriptgo.keyboard", testNotification: { version: "1.0" } }), decode), { kind: "test" });
  assert.deepEqual(
    parseNotification(push({ packageName: "id.scriptgo.keyboard", voidedPurchaseNotification: { purchaseToken: "tok-voided-1", orderId: "GPA.1", productType: 1, refundType: 1 } }), decode),
    { kind: "voided", packageName: "id.scriptgo.keyboard", purchaseToken: "tok-voided-1" },
  );
  assert.deepEqual(parseNotification(push({ packageName: "id.scriptgo.keyboard", oneTimeProductNotification: { purchaseToken: "x" } }), decode), { kind: "other" });
  assert.throws(() => parseNotification({ hello: 1 }, decode), /not_pubsub/);
});

test("the service-account JWT is RS256-signed with the right claims", async () => {
  const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const pem = privateKey.export({ type: "pkcs8", format: "pem" }).toString();
  const sa = parseServiceAccount(JSON.stringify({ type: "service_account", client_email: "play@scriptgo.iam.gserviceaccount.com", private_key: pem, token_uri: "https://oauth2.googleapis.com/token" }));
  assert.ok(sa);
  const jwt = await serviceAccountJwt(sa!, 1_790_000_000, globalThis.crypto.subtle);
  const [header, claims, signature] = jwt.split(".");
  assert.deepEqual(JSON.parse(Buffer.from(header, "base64url").toString()), { alg: "RS256", typ: "JWT" });
  assert.deepEqual(JSON.parse(Buffer.from(claims, "base64url").toString()), {
    iss: "play@scriptgo.iam.gserviceaccount.com", scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token", iat: 1_790_000_000, exp: 1_790_003_600,
  });
  assert.equal(createVerify("RSA-SHA256").update(`${header}.${claims}`).verify(publicKey, Buffer.from(signature, "base64url")), true);
});

test("a missing or broken service-account secret reads as not configured", () => {
  assert.equal(parseServiceAccount(undefined), null);
  assert.equal(parseServiceAccount("{not json"), null);
  assert.equal(parseServiceAccount(JSON.stringify({ client_email: "x" })), null);
});

test("secrets compare exactly, and empty never matches", () => {
  assert.equal(sameSecret("abc123", "abc123"), true);
  assert.equal(sameSecret("abc123", "abc124"), false);
  assert.equal(sameSecret("abc", "abc123"), false);
  assert.equal(sameSecret("", ""), false);
});

test("Google URLs and products", () => {
  assert.equal(subscriptionUrl("id.scriptgo.keyboard", "a/b"), "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/id.scriptgo.keyboard/purchases/subscriptionsv2/tokens/a%2Fb");
  assert.equal(acknowledgeUrl("id.scriptgo.keyboard", "scriptgo_pro", "t"), "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/id.scriptgo.keyboard/purchases/subscriptions/scriptgo_pro/tokens/t:acknowledge");
  assert.equal(isPlayProduct("scriptgo_pro"), true);
  assert.equal(isPlayProduct("pro_1"), false);
});
