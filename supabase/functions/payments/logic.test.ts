// Run: node --test supabase/functions/payments/logic.test.ts (Node 22.18+ strips the types).
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { fromDuitku, invoiceBody, invoiceHeaders, isProduct, statusBody, verifyCallback } from "./logic.ts";

const hash = (algorithm: "md5" | "sha256", text: string) => createHash(algorithm).update(text).digest("hex");

test("invoice headers sign merchant code, timestamp, and key with sha256", () => {
  const h = invoiceHeaders("D0001", "KEY", 1759000000000, hash);
  assert.equal(h["x-duitku-signature"], createHash("sha256").update("D00011759000000000KEY").digest("hex"));
  assert.equal(h["x-duitku-timestamp"], "1759000000000");
  assert.equal(h["x-duitku-merchantcode"], "D0001");
});

test("invoice body carries the order, and a short VA name", () => {
  const body = invoiceBody({ id: "SG-1", amount: 24500, label: "ScriptGo Pro 1 bulan", email: "rina@contoh.id", name: "Rina Hijab Collection Bandung" }, "https://cb", "https://ret");
  assert.equal(body.paymentAmount, 24500);
  assert.equal(body.merchantOrderId, "SG-1");
  assert.equal(body.customerVaName, "Rina Hijab Collectio");
  assert.deepEqual(body.itemDetails, [{ name: "ScriptGo Pro 1 bulan", price: 24500, quantity: 1 }]);
  assert.equal(invoiceBody({ id: "x", amount: 1, label: "l", email: "budi@x.id", name: "" }, "c", "r").customerDetail.firstName, "budi");
});

test("status check signs with md5", () => {
  assert.equal(statusBody("D0001", "KEY", "SG-1", hash).signature, createHash("md5").update("D0001SG-1KEY").digest("hex"));
});

test("a callback is accepted only with the right merchant and signature", () => {
  const form = (sig: string, extra: Record<string, string> = {}) =>
    new URLSearchParams({ merchantCode: "D0001", amount: "24500", merchantOrderId: "SG-1", resultCode: "00", reference: "DS123", signature: sig, ...extra });
  const good = createHash("md5").update("D000124500SG-1KEY").digest("hex");
  assert.deepEqual(verifyCallback(form(good), "D0001", "KEY", hash), { merchantCode: "D0001", amount: 24500, orderId: "SG-1", resultCode: "00", reference: "DS123" });
  assert.deepEqual(verifyCallback(form(good.toUpperCase()), "D0001", "KEY", hash)?.orderId, "SG-1");
  assert.equal(verifyCallback(form("0".repeat(32)), "D0001", "KEY", hash), null);
  assert.equal(verifyCallback(form(good, { merchantCode: "D9999" }), "D0001", "KEY", hash), null);
  assert.equal(verifyCallback(form(good, { amount: "24500.5" }), "D0001", "KEY", hash), null);
});

test("callback IPs are Duitku's, per mode", () => {
  assert.equal(fromDuitku("182.23.85.11, 10.0.0.1", true), true);
  assert.equal(fromDuitku("182.23.85.11", false), false);
  assert.equal(fromDuitku("182.23.85.14", false), true);
  assert.equal(fromDuitku(null, false), false);
});

test("only known products", () => {
  assert.equal(isProduct("pro_12"), true);
  assert.equal(isProduct("team_1"), false);
});
