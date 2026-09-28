// End-to-end test of the admin dashboard (admin/) in Chromium with Supabase mocked. Uses the
// Playwright installed for the site: `cd site && npm ci`, then from the repo root:
//   node admin/e2e/run.mjs
import { createRequire } from "node:module";
import { createServer } from "node:http";
import { existsSync, readFileSync } from "node:fs";
import { extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(new URL("../../site/package.json", import.meta.url));
const { chromium } = require("playwright");
const root = fileURLToPath(new URL("..", import.meta.url));
const types = { ".html": "text/html", ".css": "text/css", ".js": "text/javascript", ".svg": "image/svg+xml", ".woff2": "font/woff2" };
const server = createServer((req, res) => {
  let path = normalize(decodeURIComponent(new URL(req.url, "http://x").pathname)).replace(/^(\.\.[/\\])+/, "");
  if (path.endsWith("/")) path += "index.html";
  const file = join(root, path);
  if (!existsSync(file)) return res.writeHead(404).end();
  res.writeHead(200, { "Content-Type": types[extname(file)] ?? "application/octet-stream" }).end(readFileSync(file));
});
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const base = `http://127.0.0.1:${server.address().port}`;
const browser = await chromium.launch();

const results = [];
async function check(name, fn) {
  try {
    await Promise.race([fn(), new Promise((_, reject) => setTimeout(() => reject(new Error("timed out")), 20_000))]);
    results.push(["ok", name]);
  } catch (e) {
    results.push(["FAIL", name, e instanceof Error ? e.message : String(e)]);
  }
}
const expect = (cond, message) => { if (!cond) throw new Error(message); };
const expectEqual = (a, b) => expect(a === b, `expected ${JSON.stringify(b)}, got ${JSON.stringify(a)}`);

const DATA = {
  admin_check: true,
  admin_overview: { users: 4, users_7d: 4, waitlist: { pending: 1, invited: 2 }, waitlist_verified: 3, pro_active: 1, revenue_today: 24500, revenue_month: 24500,
    orders_pending: 2, ai_today: 3, reports_new: 1, support_new: 3, limits_from: null, limits_active: false },
  admin_orders: { orders: [{ id: "SG-1BCF131DDB", email: "rina@gmail.com", product: "pro_1", months: 1, amount: 24500, founder: true, status: "paid", reference: "D123", created_at: "2026-09-28T11:33:28Z", paid_at: "2026-09-28T11:36:01Z" }],
    daily: [{ day: "2026-09-27", revenue: 0, orders: 0 }, { day: "2026-09-28", revenue: 24500, orders: 1 }] },
  admin_waitlist: [
    { id: "w1", email: "budi@contoh.id", name: "Budi", whatsapp: "+6281234567890", business_type: "fashion", role: "owner", device: "android", referrals: 2, source: "ig", beta_status: "pending", email_verified_at: "2026-09-27T10:00:00Z", created_at: "2026-09-27T09:00:00Z" },
    { id: "w2", email: "ani@contoh.id", name: "Ani <b>x</b>", whatsapp: "", business_type: "food", role: "owner", device: "android", referrals: 0, source: "", beta_status: "approved", email_verified_at: "2026-09-27T10:00:00Z", created_at: "2026-09-26T09:00:00Z" },
  ],
  admin_users: [{ id: "u1", email: "rina@gmail.com", created_at: "2026-09-27T08:00:00Z", last_sign_in_at: "2026-09-28T11:00:00Z", plan: { plan: "pro", pro_until: "2026-10-28T11:36:01Z" }, note: "duitku SG-1BCF131DDB", business_name: "Toko Rina", city: "Bandung", templates: 12, contacts: 40, admin: false }],
  admin_inbox: { reports: [{ id: "r1", email: "rina@gmail.com", mode: "create", style: "", input: "template ongkir", result: "Halo Kak…", reason: "wrong", note: "", source: "android", status: "new", created_at: "2026-09-28T10:00:00Z" }],
    support: [{ id: "s1", email: "budi@contoh.id", kind: "idea", message: "Tolong tambah jadwal kirim", reply_email: "", source: "extension", app_version: "1.0", status: "new", created_at: "2026-09-28T09:00:00Z" }], ai_daily: [{ day: "2026-09-28", calls: 3, users: 1 }] },
  admin_ab_report: [{ variant: "a", visitors: 10, clicks: 3, signups: 2, confirmed: 1, click_rate: 0.3, confirm_rate: 0.1, chance_b_wins_confirmed: null }, { variant: "b", visitors: 12, clicks: 5, signups: 3, confirmed: 2, click_rate: 0.41, confirm_rate: 0.16, chance_b_wins_confirmed: 0.7 }],
};

async function open({ admin = true, signedIn = true } = {}) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  if (signedIn) await context.addInitScript(() => sessionStorage.setItem("scriptgo-admin-session", JSON.stringify({ token: "tok", email: "rizki@contoh.id", expires: Date.now() + 3_600_000 })));
  const page = await context.newPage();
  const calls = [], errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("dialog", (d) => (d.type() === "prompt" ? d.accept("hadiah beta") : d.accept()));
  await page.route("**/rest/v1/rpc/*", async (route) => {
    const name = route.request().url().split("/").pop();
    const body = route.request().postDataJSON();
    calls.push({ name, body, auth: route.request().headers().authorization });
    if (!admin && name !== "admin_check") return route.fulfill({ status: 403, contentType: "application/json", body: JSON.stringify({ message: "not_admin" }) });
    const json = name === "admin_check" ? admin : name in DATA ? DATA[name] : null;
    await route.fulfill({ status: json === null ? 204 : 200, contentType: "application/json", body: json === null ? "" : JSON.stringify(json) });
  });
  await page.route("**/auth/v1/otp", (r) => { calls.push({ name: "otp", body: r.request().postDataJSON() }); return r.fulfill({ status: 200, contentType: "application/json", body: "{}" }); });
  await page.route("**/auth/v1/verify", (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ access_token: "tok", expires_in: 3600, user: { email: "rizki@contoh.id" } }) }));
  await page.goto(base + "/");
  return { page, calls, errors, close: () => context.close() };
}

await check("sign-in never creates accounts, and a non-admin account is turned away", async () => {
  const { page, calls, close } = await open({ admin: false, signedIn: false });
  await page.fill("#email", "orang@contoh.id");
  await page.click("#send-code");
  await page.locator("#code-form").waitFor();
  expectEqual(calls.find((c) => c.name === "otp").body.create_user, false);
  await page.fill("#code", "123456");
  await page.click("#verify");
  await page.getByText("Akun ini bukan admin.").waitFor();
  expect(await page.locator("#app").isHidden(), "dashboard shown to a non-admin");
  await close();
});

await check("overview shows the numbers and a 30-day revenue chart", async () => {
  const { page, errors, calls, close } = await open();
  await page.getByText("Pendapatan hari ini").waitFor();
  expect((await page.locator(".stat").first().textContent()).includes("Rp24.500"), "revenue not shown");
  expectEqual(await page.locator(".chart .bar").count(), 2);
  const heights = await page.locator(".chart .bar").evaluateAll((bars) => bars.map((b) => b.getBoundingClientRect().height));
  expect(heights[1] > 100 && heights[0] < 5, `bars not scaled: ${heights}`);
  expect(calls.every((c) => c.auth === "Bearer tok"), "RPC without the token");
  expectEqual(errors.join("\n"), "");
  await close();
});

await check("beta: approve a pending sign-up; invite asks first; names are shown as text", async () => {
  const { page, calls, close } = await open();
  await page.getByRole("tab", { name: "Beta & waitlist" }).click();
  await page.getByText("budi@contoh.id").waitFor();
  expectEqual(await page.locator("td b", { hasText: "Ani" }).textContent(), "Ani <b>x</b>");
  await page.locator("tr", { hasText: "budi@contoh.id" }).getByRole("button", { name: "Setujui" }).click();
  await page.getByText("budi@contoh.id: Setujui.").waitFor();
  expectEqual(JSON.stringify(calls.find((c) => c.name === "admin_set_beta_status").body), JSON.stringify({ waitlist_id: "w1", new_status: "approved" }));
  await page.locator("tr", { hasText: "ani@contoh.id" }).getByRole("button", { name: "Undang" }).click();
  await page.getByText("ani@contoh.id: Undang.").waitFor();
  await close();
});

await check("users: give Pro 3 months with a reason, and revoke", async () => {
  const { page, calls, close } = await open();
  await page.getByRole("tab", { name: "Pengguna & paket" }).click();
  await page.getByText("Toko Rina · Bandung").waitFor();
  await page.getByRole("button", { name: "+3 bln" }).click();
  await page.getByText("Pro 3 bulan untuk rina@gmail.com.").waitFor();
  expectEqual(JSON.stringify(calls.find((c) => c.name === "admin_grant_pro").body), JSON.stringify({ target: "u1", months: 3, reason: "hadiah beta" }));
  await page.getByRole("button", { name: "Cabut" }).click();
  await page.getByText("Pro rina@gmail.com dicabut.").waitFor();
  await close();
});

await check("payments, inbox and A/B tabs load; limits switch asks before saving", async () => {
  const { page, calls, errors, close } = await open();
  await page.getByRole("tab", { name: "Pembayaran" }).click();
  await page.getByText("SG-1BCF131DDB").waitFor();
  await page.getByRole("tab", { name: "Laporan & A/B" }).click();
  await page.getByText("Tolong tambah jadwal kirim").waitFor();
  await page.locator("tr", { hasText: "Tolong tambah" }).getByRole("button", { name: "Selesai" }).click();
  await page.getByText("Ditandai selesai.").waitFor();
  expectEqual(JSON.stringify(calls.find((c) => c.name === "admin_set_inbox_status").body), JSON.stringify({ kind: "support", item: "s1", new_status: "done" }));
  expectEqual(await page.locator("td b", { hasText: "B" }).count() >= 1, true);
  await page.getByRole("tab", { name: "Ringkasan" }).click();
  await page.getByLabel("Tanggal mulai batas gratis").fill("2026-11-01");
  await page.getByRole("button", { name: "Simpan tanggal" }).click();
  await page.getByText("Saklar disimpan.").waitFor();
  expectEqual(calls.find((c) => c.name === "admin_set_limits_from").body.starts, "2026-10-31T17:00:00.000Z");
  // The address bar's #tab (and back/forward) switches tabs.
  await page.evaluate(() => (location.hash = "#orders"));
  await page.getByText("SG-1BCF131DDB").waitFor();
  expectEqual(await page.getByRole("tab", { name: "Pembayaran" }).getAttribute("aria-selected"), "true");
  await page.goBack();
  await page.getByText("Pendapatan hari ini").waitFor();
  if (process.env.SHOT_DIR) await page.screenshot({ path: `${process.env.SHOT_DIR}/admin-overview.png`, fullPage: true });
  expectEqual(errors.join("\n"), "");
  await close();
});

await check("the page is noindex and only talks to Supabase (CSP)", async () => {
  const html = readFileSync(join(root, "index.html"), "utf8");
  expect(html.includes('content="noindex, nofollow"'), "no noindex");
  expect(html.includes("connect-src https://hsanqncbfurjhnshdpnc.supabase.co;"), "CSP connect-src missing");
  expect(!/<script[^>]+src="https?:/.test(html), "external script");
});

await browser.close();
server.close();
for (const [s, n, d] of results) console.log(`${s} ${n}${d ? `: ${d}` : ""}`);
const failed = results.filter(([s]) => s !== "ok").length;
console.log(`${results.length - failed}/${results.length} passed`);
process.exit(failed ? 1 : 0);
