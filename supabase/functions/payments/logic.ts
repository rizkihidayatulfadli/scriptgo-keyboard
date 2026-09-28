// Pure logic of the payments Edge Function (Duitku POP): signatures, the invoice request, and the
// checks on Duitku's callback. No Deno or network APIs, so `node --test` can run logic.test.ts.
// Duitku reference: the official PHP library (github.com/duitkupg/duitku-php, Duitku/Pop.php).

export type Hash = (algorithm: "md5" | "sha256", text: string) => string;

export const DUITKU = {
  sandbox: { base: "https://api-sandbox.duitku.com" },
  production: { base: "https://api-prod.duitku.com" },
};

/**
 * Duitku's outgoing IPs (callbacks). Checked as a second layer after the signature; the list comes
 * from Duitku's docs as the user copied it on 28 September 2026.
 */
export const DUITKU_IPS = {
  production: ["182.23.85.8", "182.23.85.9", "182.23.85.10", "182.23.85.13", "182.23.85.14", "103.177.101.184", "103.177.101.185", "103.177.101.186", "103.177.101.189", "103.177.101.190"],
  sandbox: ["182.23.85.11", "182.23.85.12", "103.177.101.187", "103.177.101.188"],
};

/** What can be bought on scriptgo.id/bayar (PRD §9; Tim comes with shared templates later). */
export const PRODUCTS = ["pro_1", "pro_3", "pro_12"] as const;
export type Product = (typeof PRODUCTS)[number];
export const isProduct = (value: unknown): value is Product => typeof value === "string" && (PRODUCTS as readonly string[]).includes(value);

/** Headers of createInvoice: sha256(merchantCode + timestamp + apiKey), timestamp in ms. */
export function invoiceHeaders(merchantCode: string, apiKey: string, timestamp: number, hash: Hash): Record<string, string> {
  return {
    "Content-Type": "application/json",
    "x-duitku-signature": hash("sha256", merchantCode + timestamp + apiKey),
    "x-duitku-timestamp": String(timestamp),
    "x-duitku-merchantcode": merchantCode,
  };
}

export interface Order {
  id: string;
  amount: number;
  label: string;
  email: string;
  name: string;
}

/** The createInvoice body for [order]; Duitku shows its own page with QRIS, VA, and e-wallets. */
export function invoiceBody(order: Order, callbackUrl: string, returnUrl: string) {
  return {
    paymentAmount: order.amount,
    merchantOrderId: order.id,
    productDetails: order.label,
    email: order.email,
    customerVaName: (order.name || "ScriptGo").slice(0, 20),
    itemDetails: [{ name: order.label, price: order.amount, quantity: 1 }],
    customerDetail: { firstName: order.name || order.email.split("@")[0], email: order.email },
    callbackUrl,
    returnUrl,
    expiryPeriod: 60 * 24,
  };
}

/** transactionStatus body: signature md5(merchantCode + merchantOrderId + apiKey). */
export function statusBody(merchantCode: string, apiKey: string, orderId: string, hash: Hash) {
  return { merchantCode, merchantOrderId: orderId, signature: hash("md5", merchantCode + orderId + apiKey) };
}

export interface Callback {
  merchantCode: string;
  amount: number;
  orderId: string;
  resultCode: string;
  reference: string;
}

/**
 * Duitku's callback (x-www-form-urlencoded), or null when a field is missing or the signature
 * md5(merchantCode + amount + merchantOrderId + apiKey) does not match.
 */
export function verifyCallback(form: URLSearchParams, merchantCode: string, apiKey: string, hash: Hash): Callback | null {
  const get = (key: string) => form.get(key) ?? "";
  const amount = get("amount");
  const orderId = get("merchantOrderId");
  const signature = get("signature").toLowerCase();
  if (get("merchantCode") !== merchantCode || !/^\d+$/.test(amount) || !orderId || !signature) return null;
  if (hash("md5", merchantCode + amount + orderId + apiKey) !== signature) return null;
  return { merchantCode, amount: Number(amount), orderId, resultCode: get("resultCode"), reference: get("reference") };
}

/** Whether [ip] (the first address of x-forwarded-for) is one of Duitku's for this mode. */
export function fromDuitku(forwardedFor: string | null, sandbox: boolean): boolean {
  const ip = (forwardedFor ?? "").split(",")[0].trim();
  return (sandbox ? DUITKU_IPS.sandbox : DUITKU_IPS.production).includes(ip);
}
