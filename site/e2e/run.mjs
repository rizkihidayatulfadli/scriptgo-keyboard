// End-to-end test of the built site (dist/) in Chromium: landing page demo and sections,
// /daftar, /konfirmasi, and /bayar with Supabase mocked, SEO files. Run `npm run build` first.
// Usage: npm run e2e
import { createServer } from "node:http";
import { existsSync, readFileSync } from "node:fs";
import { extname, join, normalize } from "node:path";
import { chromium } from "playwright";

const root = "dist";
const types = { ".html": "text/html", ".css": "text/css", ".js": "text/javascript", ".svg": "image/svg+xml", ".png": "image/png", ".woff2": "font/woff2", ".xml": "application/xml", ".txt": "text/plain" };
// Serves dist/ like the static host: "/daftar" → daftar.html, "/" → index.html.
const server = createServer((req, res) => {
  let path = normalize(decodeURIComponent(new URL(req.url, "http://x").pathname)).replace(/^(\.\.[/\\])+/, "");
  if (path.endsWith("/")) path += "index.html";
  let file = join(root, path);
  if (!existsSync(file) && existsSync(`${file}.html`)) file = `${file}.html`;
  if (!existsSync(file)) { res.writeHead(404).end("not found"); return; }
  res.writeHead(200, { "Content-Type": types[extname(file)] ?? "application/octet-stream" }).end(readFileSync(file));
});
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const base = `http://127.0.0.1:${server.address().port}`;
const browser = await chromium.launch();

const results = [];
async function check(name, fn) {
  let timer;
  try {
    const timeout = new Promise((_, reject) => (timer = setTimeout(() => reject(new Error("timed out after 20s")), 20_000)));
    await Promise.race([fn(), timeout]);
    results.push(["ok", name]);
  } catch (e) {
    results.push(["FAIL", name, e instanceof Error ? e.message : String(e)]);
  } finally {
    clearTimeout(timer);
  }
}
const expect = (cond, message) => { if (!cond) throw new Error(message); };
const expectEqual = (actual, expected) => expect(actual === expected, `expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);

/**
 * A fresh page. [rpc] answers Supabase RPC calls: (name, body) => ({ status, json }) or "abort".
 * Returns the page, the RPC calls made, and console errors.
 */
async function open(path, { rpc, routes = {}, init, reducedMotion = "no-preference", width = 1280, clock = false } = {}) {
  const context = await browser.newContext({ reducedMotion, viewport: { width, height: 900 } });
  if (init) await context.addInitScript(init);
  const page = await context.newPage();
  const calls = [], errors = [];
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  page.on("pageerror", (e) => errors.push(e.message));
  await page.route("**/rest/v1/rpc/*", async (route) => {
    const name = route.request().url().split("/").pop();
    const body = route.request().postDataJSON();
    calls.push({ name, body });
    const answer = rpc ? await rpc(name, body) : name === "ab_track" ? { status: 204, json: null } : { status: 500, json: { message: "no handler" } };
    if (answer === "abort") return route.abort();
    await route.fulfill({ status: answer.status, contentType: "application/json", body: JSON.stringify(answer.json) });
  });
  // Other Supabase endpoints (auth, functions, tables): {glob: (request) => ({ status, json })}.
  for (const [glob, answer] of Object.entries(routes)) {
    await page.route(glob, async (route) => {
      const request = route.request();
      calls.push({ name: new URL(request.url()).pathname + new URL(request.url()).search, body: request.postDataJSON?.() ?? null, auth: request.headers().authorization ?? "" });
      const { status, json } = await answer(request);
      await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(json) });
    });
  }
  if (clock) await page.clock.install();
  await page.goto(base + path);
  return { page, calls, errors, close: () => context.close() };
}
const noHorizontalScroll = async (page) => {
  const [scroll, inner] = await page.evaluate(() => [document.documentElement.scrollWidth, innerWidth]);
  expect(scroll <= inner, `page scrolls sideways: ${scroll}px > ${inner}px`);
};

// Landing page ---------------------------------------------------------------------------------

await check("index loads without console errors", async () => {
  const { page, errors, close } = await open("/");
  await page.waitForTimeout(500);
  expectEqual(errors.join("\n"), "");
  await close();
});

await check("A/B: a variant is picked, shown, kept, and counted once per visitor", async () => {
  const context = await browser.newContext();
  const page = await context.newPage();
  const calls = [];
  await page.route("**/rest/v1/rpc/*", (route) => {
    calls.push({ name: route.request().url().split("/").pop(), body: route.request().postDataJSON() });
    return route.fulfill({ status: 204, body: "" });
  });
  await page.goto(`${base}/`);
  const variant = await page.locator("#hero-title").getAttribute("data-variant");
  expect(variant === "a" || variant === "b", `variant ${variant}`);
  const title = await page.locator("#hero-title").textContent();
  expect(title.includes(variant === "a" ? "hitungan detik" : "/ongkir"), `title ${title} does not match variant ${variant}`);
  await page.waitForTimeout(300);
  const views = calls.filter((c) => c.name === "ab_track");
  expectEqual(views.length, 1);
  const { p_experiment, p_variant, p_visitor, p_event } = views[0].body;
  expectEqual(`${p_experiment} ${p_variant} ${p_event}`, `hero-1 ${variant} view`);
  expect(/^[0-9a-f-]{36}$/.test(p_visitor), `visitor ${p_visitor}`);
  await page.reload();
  expectEqual(await page.locator("#hero-title").getAttribute("data-variant"), variant);
  await page.waitForTimeout(300);
  expectEqual(calls.filter((c) => c.name === "ab_track").at(-1).body.p_visitor, p_visitor);
  await page.locator("a[data-cta]").first().click();
  await page.waitForURL(/daftar/);
  const cta = calls.filter((c) => c.name === "ab_track" && c.body.p_event === "cta");
  expectEqual(cta.length, 1);
  expectEqual(cta[0].body.p_variant, variant);
  await page.fill("#email", "rina@gmail.com");
  await page.click("#submit");
  await page.locator("#sent").waitFor();
  expectEqual(calls.find((c) => c.name === "join_waitlist").body.p_ab, variant);
  await context.close();
});

await check("A/B: ?v=b previews version B without counting", async () => {
  const { page, calls, close } = await open("/?v=b");
  expectEqual(await page.locator("#hero-title").getAttribute("data-variant"), "b");
  expect((await page.locator("#hero-title").textContent()).includes("Ketik /ongkir,"), "B title missing");
  expect((await page.locator("#hero-sub").textContent()).includes("terisi sendiri"), "B subtitle missing");
  await page.waitForTimeout(300);
  expectEqual(calls.length, 0);
  await close();
});

await check("A/B: both variants split roughly evenly", async () => {
  const seen = { a: 0, b: 0 };
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.route("**/rest/v1/rpc/*", (route) => route.fulfill({ status: 204, body: "" }));
  for (let i = 0; i < 40; i++) {
    await page.goto(`${base}/`);
    seen[await page.locator("#hero-title").getAttribute("data-variant")]++;
    await page.evaluate(() => localStorage.clear());
  }
  expect(seen.a >= 8 && seen.b >= 8, `split ${JSON.stringify(seen)}`);
  await context.close();
});

await check("demo chip /rekening sends the rekening reply", async () => {
  const { page, close } = await open("/");
  await page.click('.try [data-t="rekening"]');
  await page.waitForFunction(() => {
    const r = document.getElementById("demo-reply");
    return r.classList.contains("show") && r.textContent.includes("rekening");
  }, null, { timeout: 8000 });
  expectEqual(await page.locator('.try [data-t="rekening"]').getAttribute("aria-pressed"), "true");
  await close();
});

await check("Pro card links to /bayar, Gratis card to sign-up", async () => {
  const { page, close } = await open("/", { reducedMotion: "reduce" });
  expectEqual(await page.locator(".plan.featured #subscribe").getAttribute("href"), "bayar");
  expectEqual(await page.locator(".plan.featured #subscribe").textContent(), "Langganan Pro");
  expect((await page.locator(".plan:not(.featured) a[data-cta]").count()) === 1, "Gratis card has no sign-up button");
  await close();
});

await check("sign-up links carry ref and utm", async () => {
  const { page, close } = await open("/?ref=abcdefgh&utm_source=ig");
  const hrefs = await page.locator("a[data-cta]").evaluateAll((as) => as.map((a) => a.getAttribute("href")));
  expect(hrefs.length >= 3, `only ${hrefs.length} sign-up links`);
  for (const href of hrefs) expectEqual(href, "daftar?ref=abcdefgh&utm_source=ig");
  await close();
});

await check("reduced motion: reply shown at once, nothing animating", async () => {
  const { page, close } = await open("/", { reducedMotion: "reduce" });
  await page.waitForTimeout(300);
  expect(await page.locator("#demo-reply").evaluate((el) => el.classList.contains("show") && getComputedStyle(el).opacity === "1"), "reply not visible");
  const running = await page.evaluate(() => document.getAnimations().filter((a) => a.playState === "running").length);
  expectEqual(running, 0);
  expect(await page.locator("[data-reveal]").first().evaluate((el) => getComputedStyle(el).opacity === "1"), "reveal content hidden");
  await page.click('.try [data-t="salam"]');
  expect((await page.locator("#demo-reply").textContent()).includes("Terima kasih"), "chip did not swap the reply");
  await close();
});

await check("sections in order, prices, hidden testimonials, FAQ toggles", async () => {
  const { page, close } = await open("/");
  const headings = await page.locator("main h2").evaluateAll((hs) => hs.filter((h) => !h.closest("[hidden]")).map((h) => h.id));
  expectEqual(headings.join(","), "h-problem,h-saver,h-cara,h-fitur,h-maker,h-harga,h-faq,h-closing");
  const pricing = await page.locator("#harga").textContent();
  for (const text of ["Rp49.000", "Rp24.500", "Rp39.000", "Hemat 20%"]) expect(pricing.includes(text), `pricing misses ${text}`);
  expect(!(await page.locator("#testimonials").isVisible()), "testimonials visible");
  const items = page.locator(".faq details");
  expect(await items.nth(0).evaluate((d) => d.open), "first FAQ answer is not open");
  await items.nth(1).locator("summary").click();
  expect(await items.nth(1).evaluate((d) => d.open), "FAQ did not open");
  expect(!(await items.nth(0).evaluate((d) => d.open)), "opening one FAQ answer did not close the other");
  await close();
});

await check("scrolling reveals sections and answers the chat pile", async () => {
  const { page, close } = await open("/");
  await page.locator("#pile").scrollIntoViewIfNeeded();
  await page.waitForFunction(() => document.querySelectorAll("#pile li.done").length === 5, null, { timeout: 6000 });
  await page.locator("#h-closing").scrollIntoViewIfNeeded();
  await page.waitForFunction(() => document.querySelector(".closing-card").classList.contains("in"), null, { timeout: 3000 });
  await close();
});

await check("Tonton 30 detik loads the video only when clicked", async () => {
  const { page, close } = await open("/");
  expectEqual(await page.locator("video").count(), 0);
  await page.locator("#watch").scrollIntoViewIfNeeded();
  await page.click("#watch");
  const src = await page.locator(".watch video").getAttribute("src");
  expectEqual(src, "video/scriptgo-30s.mp4");
  expect(existsSync("dist/video/scriptgo-30s.mp4") && existsSync("dist/video/scriptgo-30s-poster.jpg"), "video files missing from dist");
  await close();
});

await check("calculator updates with the slider", async () => {
  const { page, close } = await open("/", { reducedMotion: "reduce" });
  await page.locator("#chats").fill("120");
  expectEqual(await page.locator("#chats-out").textContent(), "120");
  expectEqual(await page.locator("#saved").textContent(), "± 1,2 jam");
  await close();
});

for (const path of ["/", "/daftar", "/konfirmasi", "/bayar"]) {
  await check(`${path} fits a 390px phone`, async () => {
    const { page, close } = await open(path, { width: 390 });
    await page.waitForTimeout(300);
    await noHorizontalScroll(page);
    await close();
  });
}

// /daftar ----------------------------------------------------------------------------------------

await check("daftar: invalid email is caught before sending", async () => {
  const { page, calls, close } = await open("/daftar");
  expect(!(await page.locator("#sent").isVisible()), "Cek email shown before sending");
  await page.fill("#email", "rina@gmail");
  await page.click("#submit");
  expectEqual(await page.locator("#msg").textContent(), "Email belum benar.");
  expectEqual(calls.length, 0);
  await close();
});

await check("daftar: sends normalized data and shows Cek email, resend after 60 s", async () => {
  const { page, calls, close } = await open("/daftar?ref=abcdefgh&utm_source=ig&utm_campaign=beta", {
    rpc: () => ({ status: 204, json: null }),
    clock: true,
  });
  await page.fill("#email", " Rina@Gmail.com ");
  await page.fill("#wa", "0812-3456-7890");
  await page.click('[data-device="iphone"]');
  await page.click('[data-device="laptop"]');
  await page.click('[data-device="laptop"]'); // off again
  await page.click("#submit");
  await page.locator("#sent").waitFor();
  expectEqual(calls.length, 1);
  expectEqual(calls[0].name, "join_waitlist");
  expectEqual(JSON.stringify(calls[0].body), JSON.stringify({ p_email: "rina@gmail.com", p_whatsapp: "+6281234567890", p_device: "android,iphone", p_referred_by: "abcdefgh", p_source: "ig/beta", p_ab: "" }));
  expectEqual(await page.locator("#sent-email").textContent(), "rina@gmail.com");
  expect(!(await page.locator("#signup").isVisible()), "form still visible");
  expect(await page.locator("#resend").isDisabled(), "resend enabled right away");
  await page.clock.runFor(61_000);
  expect(!(await page.locator("#resend").isDisabled()), "resend still disabled after 60 s");
  await page.click("#resend");
  await page.waitForFunction(() => document.getElementById("sent-msg").textContent === "Link baru sudah dikirim.");
  expectEqual(calls.length, 2);
  await close();
});

await check("daftar: empty WhatsApp is fine, wrong one is caught", async () => {
  const { page, calls, close } = await open("/daftar", { rpc: () => ({ status: 204, json: null }) });
  await page.fill("#email", "rina@gmail.com");
  await page.fill("#wa", "12345");
  await page.click("#submit");
  expectEqual(await page.locator("#msg").textContent(), "Nomor WhatsApp belum benar. Contoh: 0812 3456 7890.");
  await page.fill("#wa", "");
  await page.click("#submit");
  await page.locator("#sent").waitFor();
  expectEqual(calls[0].body.p_whatsapp, "");
  expectEqual(calls[0].body.p_device, "android");
  await close();
});

await check("daftar: devices are multiple choice, and at least one is needed", async () => {
  const { page, calls, close } = await open("/daftar", { rpc: () => ({ status: 204, json: null }) });
  await page.fill("#email", "rina@gmail.com");
  await page.click('[data-device="android"]'); // the default, now off
  await page.click("#submit");
  expectEqual(await page.locator("#msg").textContent(), "Pilih minimal satu perangkat.");
  expectEqual(calls.length, 0);
  for (const d of ["laptop", "iphone", "android"]) await page.click(`[data-device="${d}"]`);
  await page.click("#submit");
  await page.locator("#sent").waitFor();
  expectEqual(calls[0].body.p_device, "android,iphone,laptop");
  await close();
});

await check("daftar: rate_limited and network errors", async () => {
  let answer = { status: 400, json: { message: "rate_limited" } };
  const { page, close } = await open("/daftar", { rpc: () => answer });
  await page.fill("#email", "rina@gmail.com");
  await page.click("#submit");
  await page.waitForFunction(() => document.getElementById("msg").textContent === "Terlalu sering. Coba lagi beberapa menit lagi.");
  answer = "abort";
  await page.click("#submit");
  await page.waitForFunction(() => document.getElementById("msg").textContent === "Gagal mengirim. Cek koneksi internet, lalu coba lagi.");
  await close();
});

// /konfirmasi ------------------------------------------------------------------------------------

const token = "ab".repeat(32);
await check("konfirmasi: success shows the referral link and hides the token", async () => {
  const { page, calls, close } = await open(`/konfirmasi?t=${token}`, { rpc: () => ({ status: 200, json: "abcdefgh" }) });
  await page.locator("#ok").waitFor();
  expectEqual(JSON.stringify(calls[0]), JSON.stringify({ name: "confirm_waitlist", body: { p_token: token } }));
  expectEqual(await page.locator("#ref-link").inputValue(), "https://scriptgo.id/?ref=abcdefgh");
  const wa = await page.locator("#ref-wa").getAttribute("href");
  expect(wa.startsWith("https://wa.me/?text=") && decodeURIComponent(wa).includes("https://scriptgo.id/?ref=abcdefgh"), `bad share link ${wa}`);
  expect(!page.url().includes(token), "token still in the URL");
  expect(!(await page.locator("#checking").isVisible()) && !(await page.locator("#bad").isVisible()), "other states still visible");
  await close();
});

await check("konfirmasi: invalid token and missing token", async () => {
  const bad = await open(`/konfirmasi?t=${token}`, { rpc: () => ({ status: 400, json: { message: "invalid_token" } }) });
  await bad.page.locator("#bad").waitFor();
  expectEqual(await bad.page.locator("#bad h1").textContent(), "Link ini sudah tidak berlaku");
  expectEqual(await bad.page.locator("#bad a").getAttribute("href"), "daftar");
  await bad.close();
  const missing = await open("/konfirmasi");
  await missing.page.locator("#bad").waitFor();
  expectEqual(missing.calls.length, 0);
  expectEqual(await missing.page.locator('meta[name="robots"]').getAttribute("content"), "noindex");
  await missing.close();
});

// /bayar -----------------------------------------------------------------------------------------

const quote = {
  founder: true,
  products: [
    { product: "pro_1", months: 1, label: "ScriptGo Pro 1 bulan", normal: 49000, amount: 24500 },
    { product: "pro_3", months: 3, label: "ScriptGo Pro 3 bulan", normal: 125000, amount: 62500 },
    { product: "pro_12", months: 12, label: "ScriptGo Pro 12 bulan", normal: 439000, amount: 219500 },
  ],
};
const signedIn = () => sessionStorage.setItem("scriptgo-pay-session", JSON.stringify({ token: "tok", email: "rina@gmail.com", expires: Date.now() + 3_600_000 }));
const payRoutes = (payments) => ({
  "**/auth/v1/otp": () => ({ status: 200, json: {} }),
  "**/auth/v1/verify": () => ({ status: 200, json: { access_token: "tok", expires_in: 3600, user: { email: "rina@gmail.com" } } }),
  "**/functions/v1/payments": payments,
});

await check("bayar: email code sign-in, founder prices, 12 months chosen, pays with the token", async () => {
  const { page, calls, errors, close } = await open("/bayar", {
    // The fake Duitku page is the landing page, which counts an A/B visit (ab_track) once it loads.
    rpc: (name) => (name === "payment_quote" ? { status: 200, json: quote } : name === "ab_track" ? { status: 204, json: null } : { status: 500, json: {} }),
    routes: payRoutes(() => ({ status: 200, json: { orderId: "SG-ABCDEF1234", paymentUrl: `${base}/?duitku` } })),
  });
  await page.fill("#email", "Rina@Gmail.com ");
  await page.click("#send-code");
  await page.locator("#code-step").waitFor();
  expectEqual(await page.locator("#code-email").textContent(), "rina@gmail.com");
  expectEqual(JSON.stringify(calls.find((c) => c.name === "/auth/v1/otp").body), JSON.stringify({ email: "rina@gmail.com", create_user: true }));
  await page.fill("#code", "123456");
  await page.click("#verify");
  await page.locator(".plan-option").first().waitFor();
  expectEqual(await page.locator("#plans-account").textContent(), "rina@gmail.com");
  expectEqual(await page.locator(".plan-option").count(), 3);
  expectEqual(await page.locator('.plan-option[aria-checked="true"]').getAttribute("data-product"), "pro_12");
  expect((await page.locator("#plans-note").textContent()).includes("Harga pendiri"), "founder note missing");
  expectEqual(await page.locator('.plan-option[data-product="pro_12"] .plan-normal').textContent(), "Rp439.000");
  expectEqual(await page.locator('.plan-option[data-product="pro_12"] .plan-badge').textContent(), "Paling hemat");
  expectEqual(await page.locator(".plan-badge").count(), 1);
  expectEqual(calls.find((c) => c.name === "payment_quote") !== undefined, true);
  await page.click('.plan-option[data-product="pro_3"]');
  await page.click("#pay");
  await page.waitForURL(/\?duitku$/);
  const pay = calls.find((c) => c.name === "/functions/v1/payments");
  expectEqual(JSON.stringify(pay.body), JSON.stringify({ product: "pro_3" }));
  expectEqual(pay.auth, "Bearer tok");
  expectEqual(errors.join("\n"), "");
  await close();
});

await check("bayar: wrong code and payments not configured yet", async () => {
  const { page, close } = await open("/bayar", {
    rpc: () => ({ status: 200, json: quote }),
    routes: { ...payRoutes(() => ({ status: 503, json: { error: "payments_not_configured" } })), "**/auth/v1/verify": () => ({ status: 403, json: { error_code: "otp_expired" } }) },
  });
  await page.fill("#email", "rina@gmail.com");
  await page.click("#send-code");
  await page.fill("#code", "12345");
  await page.click("#verify");
  expectEqual(await page.locator("#code-msg").textContent(), "Kodenya 6 angka.");
  await page.fill("#code", "123456");
  await page.click("#verify");
  await page.locator("#code-msg.error").waitFor();
  expect((await page.locator("#code-msg").textContent()).startsWith("Kode salah"), "wrong code not explained");
  await close();
  const later = await open("/bayar", { init: signedIn, rpc: () => ({ status: 200, json: quote }), routes: payRoutes(() => ({ status: 503, json: { error: "payments_not_configured" } })) });
  await later.page.locator(".plan-option").first().waitFor();
  await later.page.click("#pay");
  await later.page.locator("#plans-msg.error").waitFor();
  expectEqual(await later.page.locator("#plans-msg").textContent(), "Pembayaran belum dibuka. Coba lagi nanti.");
  expect(await later.page.locator("#pay").isEnabled(), "pay button stays disabled");
  await later.close();
});

await check("bayar: back from Duitku shows paid, failed, and signed-out orders", async () => {
  const paid = await open("/bayar?order=SG-ABCDEF1234", { init: signedIn, routes: { "**/rest/v1/orders*": () => ({ status: 200, json: [{ status: "paid", months: 12 }] }) } });
  await paid.page.locator("#order-title", { hasText: "Pro sudah aktif" }).waitFor();
  expectEqual(await paid.page.locator("#order-icon").getAttribute("data-state"), "ok");
  expect(paid.calls[0].name.includes("id=eq.SG-ABCDEF1234") && paid.calls[0].auth === "Bearer tok", "order read without the token");
  expect(!(await paid.page.locator("#order-again").isVisible()), "retry shown for a paid order");
  await paid.close();
  const failed = await open("/bayar?order=SG-ABCDEF1234", { init: signedIn, routes: { "**/rest/v1/orders*": () => ({ status: 200, json: [{ status: "failed", months: 1 }] }) } });
  await failed.page.locator("#order-title", { hasText: "Pembayaran belum berhasil" }).waitFor();
  expectEqual(await failed.page.locator("#order-icon").getAttribute("data-state"), "fail");
  expect(await failed.page.locator("#order-again").isVisible(), "retry missing for a failed order");
  await failed.close();
  const guest = await open("/bayar?order=SG-ABCDEF1234");
  await guest.page.locator("#order-title", { hasText: "Terima kasih!" }).waitFor();
  expectEqual(await guest.page.locator("#order-icon").getAttribute("data-state"), "wait");
  expectEqual(guest.calls.length, 0);
  expectEqual(await guest.page.locator('meta[name="robots"]').getAttribute("content"), "noindex");
  await guest.close();
});

// SEO ------------------------------------------------------------------------------------------

await check("sitemap, robots, share tags, and structured data", async () => {
  const sitemap = readFileSync("dist/sitemap.xml", "utf8");
  for (const u of ["/</loc>", "/daftar</loc>", "/privacy.html</loc>", "/hapus-akun.html</loc>"]) expect(sitemap.includes(u), `sitemap misses ${u}`);
  expect(!sitemap.includes("konfirmasi") && !sitemap.includes("bayar"), "sitemap lists a noindex page");
  expect(readFileSync("dist/robots.txt", "utf8").includes("Sitemap: https://scriptgo.id/sitemap.xml"), "robots.txt misses the sitemap");
  expect(existsSync("dist/og.png"), "dist/og.png missing");
  for (const page of ["index.html", "daftar.html", "konfirmasi.html", "bayar.html", "privacy.html"]) {
    const html = readFileSync(`dist/${page}`, "utf8");
    const refs = [...html.matchAll(/(?:href|src)="([a-z]+\.(?:css|js|svg|png)[^"]*)"/g)].map((m) => m[1]);
    expect(refs.length > 0 && refs.every((r) => /\?v=[0-9a-f]{8}$/.test(r)), `${page} has unversioned assets: ${refs.join(", ")}`);
  }
  const { page, close } = await open("/");
  const meta = (sel) => page.locator(sel).getAttribute("content");
  expect(/^https:\/\/scriptgo\.id\/og\.png\?v=[0-9a-f]{8}$/.test(await meta('meta[property="og:image"]')), "og:image is not a versioned scriptgo.id/og.png");
  expect((await meta('meta[property="og:title"]')).length > 10, "og:title empty");
  expectEqual(await meta('meta[name="twitter:card"]'), "summary_large_image");
  const types = await page.locator('script[type="application/ld+json"]').evaluateAll((ss) => ss.map((s) => JSON.parse(s.textContent)["@type"]));
  expectEqual(types.join(","), "SoftwareApplication,FAQPage");
  const faq = await page.evaluate(() => JSON.parse(document.querySelectorAll('script[type="application/ld+json"]')[1].textContent).mainEntity.length);
  expectEqual(faq, await page.locator(".faq details").count());
  await close();
});

await browser.close();
server.close();
for (const [status, name, detail] of results) console.log(`${status} ${name}${detail ? `: ${detail}` : ""}`);
const failed = results.filter(([s]) => s !== "ok").length;
console.log(`${results.length - failed}/${results.length} passed`);
process.exit(failed ? 1 : 0);
