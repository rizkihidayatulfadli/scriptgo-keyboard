// payments: pays for ScriptGo Pro through Duitku POP (PRD §9, payment only on the web).
//   POST JSON {product} with the user's access token → creates an order (price from SQL), asks
//     Duitku for an invoice, returns {orderId, paymentUrl}; scriptgo.id/bayar sends the buyer there.
//   POST form from Duitku (callback) → checks the signature, then asks Duitku for the transaction
//     status (the callback alone is not trusted) and marks the order paid, which extends Pro.
// Deployed with verify_jwt = false because Duitku's callback has no JWT; the create path checks the
// token itself. Secrets (Supabase → Edge Functions → Secrets): DUITKU_MERCHANT_CODE,
// DUITKU_API_KEY, DUITKU_MODE ("sandbox" until live, then "production").
import { crypto } from "jsr:@std/crypto@1";
import { encodeHex } from "jsr:@std/encoding@1/hex";
import { DUITKU, fromDuitku, invoiceBody, invoiceHeaders, isProduct, statusBody, verifyCallback, type Hash } from "./logic.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const MERCHANT = Deno.env.get("DUITKU_MERCHANT_CODE") ?? "";
const API_KEY = Deno.env.get("DUITKU_API_KEY") ?? "";
const SANDBOX = (Deno.env.get("DUITKU_MODE") ?? "sandbox") !== "production";
const BASE = SANDBOX ? DUITKU.sandbox.base : DUITKU.production.base;
const CALLBACK_URL = `${SUPABASE_URL}/functions/v1/payments`;
const RETURN_URL = "https://scriptgo.id/bayar";

const CORS = {
  "Access-Control-Allow-Origin": "https://scriptgo.id",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

const hash: Hash = (algorithm, text) =>
  encodeHex(crypto.subtle.digestSync(algorithm === "md5" ? "MD5" : "SHA-256", new TextEncoder().encode(text)));

async function userId(authorization: string | null): Promise<string | null> {
  if (!authorization?.startsWith("Bearer ")) return null;
  const res = await fetch(`${SUPABASE_URL}/auth/v1/user`, { headers: { apikey: ANON_KEY, Authorization: authorization } });
  if (!res.ok) return null;
  const user = await res.json();
  return typeof user?.id === "string" ? user.id : null;
}

/** Calls a payments RPC as the service role; returns its JSON, or throws with the error message. */
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

async function create(req: Request): Promise<Response> {
  const user = await userId(req.headers.get("Authorization"));
  if (!user) return reply(401, { error: "not_signed_in" });
  const product = (await req.json().catch(() => null))?.product;
  if (!isProduct(product)) return reply(400, { error: "unknown_product" });

  let order: { id: string; amount: number; label: string; email: string; name: string };
  try {
    order = (await rpc("payments_create_order", { target: user, product })) as typeof order;
  } catch (e) {
    const message = (e as Error).message;
    return reply(message.includes("too_many_orders") ? 429 : 500, { error: message.includes("too_many_orders") ? "too_many_orders" : "server_error" });
  }

  const res = await fetch(`${BASE}/api/merchant/createInvoice`, {
    method: "POST",
    headers: invoiceHeaders(MERCHANT, API_KEY, Date.now(), hash),
    body: JSON.stringify(invoiceBody(order, CALLBACK_URL, `${RETURN_URL}?order=${order.id}`)),
    signal: AbortSignal.timeout(20_000),
  }).catch(() => null);
  const invoice = res?.ok ? await res.json().catch(() => null) : null;
  if (!invoice?.paymentUrl) {
    console.error("createInvoice failed", res?.status, invoice?.statusMessage ?? "");
    await rpc("payments_mark_failed", { order_id: order.id }).catch(() => undefined);
    return reply(502, { error: "gateway_failed" });
  }
  await rpc("payments_attach", { order_id: order.id, ref: String(invoice.reference ?? ""), url: String(invoice.paymentUrl) });
  return reply(200, { orderId: order.id, paymentUrl: invoice.paymentUrl });
}

async function callback(req: Request): Promise<Response> {
  const form = new URLSearchParams(await req.text());
  const notice = verifyCallback(form, MERCHANT, API_KEY, hash);
  if (!notice) return new Response("Bad signature", { status: 400 });
  // A second layer only: the status check below is what decides.
  if (!fromDuitku(req.headers.get("x-forwarded-for"), SANDBOX)) console.warn("callback from an unexpected IP", req.headers.get("x-forwarded-for"));

  const res = await fetch(`${BASE}/api/merchant/transactionStatus`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(statusBody(MERCHANT, API_KEY, notice.orderId, hash)),
    signal: AbortSignal.timeout(20_000),
  }).catch(() => null);
  const status = res?.ok ? await res.json().catch(() => null) : null;
  if (!status) return new Response("Status check failed", { status: 502 });

  if (status.statusCode === "00") {
    await rpc("payments_mark_paid", { order_id: notice.orderId, paid_amount: Number(status.amount), ref: String(status.reference ?? notice.reference) });
  } else if (status.statusCode === "02") {
    await rpc("payments_mark_failed", { order_id: notice.orderId });
  }
  return new Response("OK");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS });
  if (req.method !== "POST") return reply(405, { error: "method_not_allowed" });
  if (!MERCHANT || !API_KEY) return reply(503, { error: "payments_not_configured" });
  try {
    const type = req.headers.get("content-type") ?? "";
    return type.includes("application/x-www-form-urlencoded") ? await callback(req) : await create(req);
  } catch (e) {
    console.error(e);
    return reply(500, { error: "server_error" });
  }
});
