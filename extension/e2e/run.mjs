// End-to-end test: loads the built extension into Chromium and types into a textarea, an input,
// a plain contenteditable, and a real Lexical editor (as on WhatsApp Web).
// Run `npm run build` first. Usage: npm run e2e
import { build } from "esbuild";
import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { chromium } from "playwright";

const extension = resolve("dist/extension");
await build({ entryPoints: ["e2e/page.ts"], bundle: true, outfile: "dist/e2e/page.js", format: "iife", logLevel: "warning" });
const files = {
  "/": { body: readFileSync("e2e/page.html"), contentType: "text/html" },
  "/page.js": { body: readFileSync("dist/e2e/page.js"), contentType: "text/javascript" },
};

const now = Date.now();
const row = (id, extra) => ({ id, updated_at: now, deleted: false, ...extra });
const seed = {
  "table:templates": {
    t1: row("t1", { title: "Salam pembuka", shortcut: "salam", body: "Halo kak, ada yang bisa dibantu?", tags: "" }),
    t2: row("t2", { title: "Ongkir", shortcut: "ongkir", body: "Halo {nama|Kak}, ongkir ke {kota} {total} via {kurir:JNE,J&T}.", tags: "" }),
    t3: row("t3", { title: "Terima kasih", shortcut: "makasih", body: "Terima kasih {@kursor}ya", tags: "" }),
    t4: row("t4", { title: "Dua baris", shortcut: "multi", body: "Baris satu\nBaris dua", tags: "" }),
    t5: row("t5", { title: "Lama", shortcut: "lama", body: "tidak boleh muncul", tags: "", deleted: true }),
    t6: row("t6", { title: "Total", shortcut: "totalan", body: "Ukuran {ukuran|belum ada} untuk {kota}", tags: "" }),
  },
  "table:contacts": {
    c1: row("c1", { name: "Budi Santoso", phones: "+6281234567890", fields: { kota: "Bandung" } }),
    c2: row("c2", { name: "Ani", phones: "+6285600001111", fields: { kota: "Surabaya" } }),
  },
  "table:field_definitions": {
    f1: row("f1", { field_key: "kota", label: "Kota", type: "text", options: "", position: 0 }),
    f2: row("f2", { field_key: "total", label: "Total", type: "currency", options: "", position: 1 }),
    f3: row("f3", { field_key: "ukuran", label: "Ukuran", type: "text", options: "", position: 2 }),
  },
};

const results = [];
async function check(name, fn) {
  try {
    let timer;
    const timeout = new Promise((_, reject) => (timer = setTimeout(() => reject(new Error("timed out after 20s")), 20_000)));
    await Promise.race([fn(), timeout]).finally(() => clearTimeout(timer));
    results.push(["ok", name]);
  } catch (e) {
    results.push(["FAIL", name, e instanceof Error ? e.message : String(e)]);
  }
}
function expectEqual(actual, expected) {
  if (actual !== expected) throw new Error(`expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
}
/** Rich-editor insertion is asynchronous: waits up to 3 s for `read()` to return `expected`. */
async function eventually(read, expected) {
  const deadline = Date.now() + 3000;
  let actual = await read();
  while (actual !== expected && Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, 50));
    actual = await read();
  }
  expectEqual(actual, expected);
}

// Lets context.route() see the service worker's requests, so the manage page's sync never reaches
// the real Supabase project.
process.env.PW_EXPERIMENTAL_SERVICE_WORKER_NETWORK_EVENTS = "1";
const context = await chromium.launchPersistentContext(mkdtempSync(join(tmpdir(), "mk-e2e-")), {
  channel: "chromium",
  headless: true,
  args: [`--disable-extensions-except=${extension}`, `--load-extension=${extension}`],
});
// Poll instead of waitForEvent: the worker can start between checking and subscribing.
// Also wait until its chrome.* APIs are there: right after install (when the welcome tab opens) the
// worker can be listed before chrome.storage is available, and evaluating then fails.
async function extensionWorker() {
  for (const deadline = Date.now() + 30_000; Date.now() < deadline; ) {
    const worker = context.serviceWorkers().find((w) => w.url().startsWith("chrome-extension://"));
    if (worker && (await worker.evaluate(() => !!globalThis.chrome?.storage?.local).catch(() => false))) return worker;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error("the extension's service worker did not start");
}
const worker = await extensionWorker();
await worker.evaluate((data) => chrome.storage.local.set(data), seed);
const supabaseCalls = [];
await context.route(/^https:\/\/[a-z]+\.supabase\.co\//, (route) => {
  supabaseCalls.push(`${route.request().method()} ${new URL(route.request().url()).pathname}`);
  return route.fulfill({ status: 200, body: "[]", contentType: "application/json" });
});
await context.route(/^https:\/\/(e2e\.test|web\.whatsapp\.com)\//, (route) => {
  const file = files[new URL(route.request().url()).pathname] ?? files["/"];
  return route.fulfill({ status: 200, body: file.body, contentType: file.contentType });
});

async function open(url = "https://e2e.test/") {
  const page = await context.newPage();
  await page.goto(url);
  await page.waitForTimeout(300); // content script loads templates from storage
  return page;
}
const formFrame = (page) => page.frameLocator('iframe[src^="chrome-extension://"]');
/**
 * Runs an input action that closes the form. Not awaited: the content script removes the form's
 * iframe while handling the event, and Playwright would wait forever for the removed frame to
 * acknowledge it. Waits for the iframe to go away instead.
 */
async function closingForm(page, action) {
  action().catch(() => undefined);
  await page.locator('iframe[src^="chrome-extension://"]').waitFor({ state: "detached" });
}
/** Types slowly enough for the suggestion list to follow, like a person. */
const typeText = (page, text) => page.keyboard.type(text, { delay: 20 });
/** Whether the suggestion list is on screen, once the content script has caught up. */
async function suggestionsShown(page, expected) {
  const read = () =>
    page.evaluate(() => {
      const host = document.querySelector("magic-keyboard-suggestions");
      return !!host && host.style.display !== "none";
    });
  await eventually(read, expected);
}

const page = await open();

await check("textarea: /sal + Enter inserts the template", async () => {
  await page.click("#ta");
  await typeText(page, "Oke /sal");
  await page.keyboard.press("Enter");
  expectEqual(await page.inputValue("#ta"), "Oke Halo kak, ada yang bisa dibantu?");
});

await check("textarea: {@kursor} places the caret", async () => {
  await page.fill("#ta", "");
  await page.click("#ta");
  await typeText(page, "/makasih");
  await page.keyboard.press("Tab");
  await typeText(page, "banyak ");
  expectEqual(await page.inputValue("#ta"), "Terima kasih banyak ya");
});

await check("textarea: Escape closes suggestions and Enter stays a newline", async () => {
  await page.fill("#ta", "");
  await page.click("#ta");
  await typeText(page, "/sal");
  await page.keyboard.press("Escape");
  await page.keyboard.press("Enter");
  expectEqual(await page.inputValue("#ta"), "/sal\n");
});

await check("text input: slashes in urls and fractions are ignored", async () => {
  await page.click("#input");
  await typeText(page, "lihat https://toko.id/sal");
  await page.keyboard.press("Enter");
  expectEqual(await page.inputValue("#input"), "lihat https://toko.id/sal");
});

await check("password field is ignored", async () => {
  await page.click("#password");
  await typeText(page, "/sal");
  await page.keyboard.press("Enter");
  expectEqual(await page.inputValue("#password"), "/sal");
});

await check("deleted templates are not suggested", async () => {
  await page.fill("#ta", "");
  await page.click("#ta");
  await typeText(page, "/lama");
  await page.keyboard.press("Enter");
  expectEqual(await page.inputValue("#ta"), "/lama\n");
});

await check("form: pick a contact, fill a variable, insert, and save back", async () => {
  await page.fill("#ta", "");
  await page.click("#ta");
  await typeText(page, "/ongkir");
  await page.keyboard.press("Enter");
  const frame = formFrame(page);
  await frame.locator("input[type=search]").fill("ani");
  await frame.locator("input[type=search]").press("Enter");
  await frame.locator("#field-total").fill("15000");
  const preview = await frame.locator(".preview").textContent();
  expectEqual(preview, "Halo Ani, ongkir ke Surabaya Rp15.000 via JNE.");
  await closingForm(page, () => page.keyboard.press("Enter"));
  expectEqual(await page.inputValue("#ta"), "Halo Ani, ongkir ke Surabaya Rp15.000 via JNE.");
  const saved = await worker.evaluate(async () => (await chrome.storage.local.get("table:contacts"))["table:contacts"].c2);
  expectEqual(saved.fields.total, "15000");
  expectEqual(saved.dirty, true);
});

await check("form: Batal keeps the typed trigger", async () => {
  await page.fill("#ta", "");
  await page.click("#ta");
  await typeText(page, "/ongkir");
  await page.keyboard.press("Enter");
  await closingForm(page, () => formFrame(page).getByText("Batal").click());
  expectEqual(await page.inputValue("#ta"), "/ongkir");
});

await check("plain contenteditable: inserts via insertText", async () => {
  await page.click("#plain");
  await typeText(page, "Oke /sal");
  await page.keyboard.press("Enter");
  expectEqual((await page.textContent("#plain")).replace(/ /g, " "), "Oke Halo kak, ada yang bisa dibantu?");
});

const lexicalText = (p) => p.evaluate(() => window.lexicalText());

await check("Lexical: replaces the trigger with a multi-line template", async () => {
  await page.click("#lexical");
  await typeText(page, "Hai /multi");
  await page.keyboard.press("Enter");
  // Lexical turns pasted lines into paragraphs (shown as separate lines, like Shift+Enter in
  // WhatsApp); getTextContent() separates paragraphs with a blank line.
  await eventually(() => lexicalText(page), "Hai Baris satu\n\nBaris dua");
});

await check("Lexical: {@kursor} places the caret", async () => {
  await page.keyboard.press("Enter");
  await typeText(page, "/makasih");
  await page.keyboard.press("Enter");
  await eventually(async () => (await lexicalText(page)).split("\n\n").pop(), "Terima kasih ya");
  await typeText(page, "banyak ");
  expectEqual((await lexicalText(page)).split("\n\n").pop(), "Terima kasih banyak ya");
});

await check("Lexical: the trigger alone shows the list, and deleting it hides the list", async () => {
  await page.keyboard.press("Enter");
  await typeText(page, "/");
  await suggestionsShown(page, true);
  await page.keyboard.press("Backspace");
  await suggestionsShown(page, false);
});

await check("textarea: the trigger alone shows the list, and deleting it hides the list", async () => {
  await page.fill("#ta", "");
  await page.click("#ta");
  await typeText(page, "Oke /");
  await suggestionsShown(page, true);
  await page.keyboard.press("Backspace");
  await suggestionsShown(page, false);
});

await check("Escape keeps the trigger as text for the rest of that word", async () => {
  await page.fill("#ta", "");
  await page.click("#ta");
  await typeText(page, "/");
  await suggestionsShown(page, true);
  await page.keyboard.press("Escape");
  await typeText(page, "sal");
  await suggestionsShown(page, false);
  await typeText(page, " /sal");
  await suggestionsShown(page, true);
  await page.keyboard.press("Enter");
  expectEqual(await page.inputValue("#ta"), "/sal Halo kak, ada yang bisa dibantu?");
});

await page.close();

await check("WhatsApp Web: suggests the chat's contact and inserts into Lexical", async () => {
  const wa = await open("https://web.whatsapp.com/");
  await wa.click("#lexical");
  await typeText(wa, "/ongkir");
  await wa.keyboard.press("Enter");
  const frame = formFrame(wa);
  // The chat's contact is recognised, so the form skips the picker (mockup 6e).
  await frame.locator(".picked").waitFor();
  expectEqual(await frame.locator(".picked b").textContent(), "Budi Santoso");
  expectEqual(await frame.locator(".picked span.small").textContent(), "Dikenali dari chat ini · Bandung");
  await frame.locator("#field-total").fill("20000");
  await closingForm(wa, () => wa.keyboard.press("Enter"));
  await eventually(() => lexicalText(wa), "Halo Budi Santoso, ongkir ke Bandung Rp20.000 via JNE.");
  await wa.close();
});

await check("form: a value from the fallback is marked until the user types another", async () => {
  const wa = await open("https://web.whatsapp.com/");
  await wa.click("#lexical");
  await typeText(wa, "/totalan");
  await wa.keyboard.press("Enter");
  const frame = formFrame(wa);
  await frame.locator(".picked").waitFor();
  // Budi has a city but no size, so only the size comes from the fallback.
  expectEqual(await frame.locator(".fallback-note:not([hidden])").count(), 1);
  expectEqual(await frame.locator("#field-ukuran").inputValue(), "belum ada");
  await frame.locator("#field-ukuran").fill("XL");
  expectEqual(await frame.locator(".fallback-note:not([hidden])").count(), 0);
  await closingForm(wa, () => frame.locator("#field-ukuran").press("Escape"));
  await wa.close();
});

await check("WhatsApp Web: Ganti opens the contact picker", async () => {
  const wa = await open("https://web.whatsapp.com/");
  await wa.click("#lexical");
  await typeText(wa, "/ongkir");
  await wa.keyboard.press("Enter");
  const frame = formFrame(wa);
  await frame.getByText("Ganti").click();
  await frame.locator(".contact.sel .badge").waitFor();
  await closingForm(wa, () => frame.getByText("Batal").click());
  await wa.close();
});

await check("WhatsApp Web: + Kontak baru adds the chat's person with Channel WhatsApp", async () => {
  const wa = await open("https://web.whatsapp.com/");
  // A chat with someone who is not a contact yet.
  await wa.evaluate(() => {
    document.querySelector("#main header span[dir='auto']").textContent = "Rina Hijab";
    document.querySelector("#main [data-id]").setAttribute("data-id", "false_6289900001111@c.us_3EB0BBBB");
  });
  await wa.click("#lexical");
  await typeText(wa, "/ongkir");
  await wa.keyboard.press("Enter");
  const frame = formFrame(wa);
  await frame.getByText("Kontak baru").click();
  expectEqual(await frame.locator("#new-name").inputValue(), "Rina Hijab");
  expectEqual((await frame.locator("#new-phone").inputValue()).replace(/\D/g, "").endsWith("89900001111"), true);
  expectEqual(await frame.locator("#new-channel").inputValue(), "WhatsApp");
  await frame.getByText("Simpan & pakai").click();
  await frame.locator(".picked").waitFor();
  expectEqual(await frame.locator(".picked b").textContent(), "Rina Hijab");
  await frame.locator("#field-kota").fill("Medan");
  await frame.locator("#field-total").fill("25000");
  await closingForm(wa, () => wa.keyboard.press("Enter"));
  await eventually(() => lexicalText(wa), "Halo Rina Hijab, ongkir ke Medan Rp25.000 via JNE.");
  const stored = await worker.evaluate(async () => {
    const { "table:contacts": contacts, "table:field_definitions": fields } = await chrome.storage.local.get(["table:contacts", "table:field_definitions"]);
    return { rina: Object.values(contacts).find((c) => c.name === "Rina Hijab"), channel: fields["builtin-channel"] };
  });
  expectEqual(stored.rina.phones, "+6289900001111");
  expectEqual(stored.rina.fields.channel, "WhatsApp");
  expectEqual(stored.rina.fields.kota, "Medan");
  expectEqual(stored.channel.field_key, "channel");
  await wa.close();
});

await check("a custom trigger (#) works in a textarea and in Lexical, and / then does nothing", async () => {
  await worker.evaluate(() => chrome.storage.local.set({ settings: { trigger: "#", disabledSites: [] } }));
  const p = await open();
  await p.click("#ta");
  await typeText(p, "Oke /sal");
  await p.keyboard.press("Enter");
  expectEqual(await p.inputValue("#ta"), "Oke /sal\n");
  await p.fill("#ta", "");
  await typeText(p, "Oke #sal");
  await p.keyboard.press("Enter");
  expectEqual(await p.inputValue("#ta"), "Oke Halo kak, ada yang bisa dibantu?");
  await p.click("#lexical");
  await typeText(p, "#");
  await suggestionsShown(p, true);
  await typeText(p, "sal");
  await p.keyboard.press("Enter");
  await eventually(() => lexicalText(p), "Halo kak, ada yang bisa dibantu?");
  await p.close();
  await worker.evaluate(() => chrome.storage.local.set({ settings: { trigger: "/", disabledSites: [] } }));
});

await check("a site turned off in the popup gets no suggestions", async () => {
  await worker.evaluate(() => chrome.storage.local.set({ settings: { trigger: "/", disabledSites: ["e2e.test"] } }));
  const off = await open();
  await off.click("#ta");
  await typeText(off, "/sal");
  await off.keyboard.press("Enter");
  expectEqual(await off.inputValue("#ta"), "/sal\n");
  await worker.evaluate(() => chrome.storage.local.set({ settings: { trigger: "/", disabledSites: [] } }));
  await off.close();
});

await check("the toast confirms an insert", async () => {
  const p = await open();
  await p.click("#ta");
  await typeText(p, "/sal");
  await p.keyboard.press("Enter");
  await p.locator("magic-keyboard-toast").waitFor({ state: "attached" });
  await p.close();
});

await check("a web page cannot claim a form", async () => {
  const other = await open();
  const reply = await other.evaluate(
    (id) => new Promise((done) => {
      const frame = document.createElement("iframe");
      frame.src = `chrome-extension://${id}/form.html#guess`;
      frame.onload = () => {
        const channel = new MessageChannel();
        channel.port1.onmessage = (e) => done(e.data);
        frame.contentWindow.postMessage({ type: "magic-keyboard-port" }, "*", [channel.port2]);
      };
      document.body.append(frame);
    }),
    new URL(worker.url()).host,
  );
  expectEqual(reply.type, "cancel");
  await other.close();
});

const extensionId = new URL(worker.url()).host;
const readRows = (table) => worker.evaluate(async (key) => Object.values((await chrome.storage.local.get(key))[key] ?? {}), `table:${table}`);

await check("manage page: add a template, and the content script suggests it right away", async () => {
  await worker.evaluate(() =>
    chrome.storage.local.set({ session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "u", email: "budi@contoh.id" } }),
  );
  const manage = await context.newPage();
  await manage.goto(`chrome-extension://${extensionId}/manage.html`);
  await manage.getByRole("button", { name: "Template baru" }).click();
  await manage.getByLabel("Judul", { exact: true }).fill("Promo");
  await manage.getByLabel("Shortcut", { exact: true }).fill("/promo"); // a typed trigger is dropped
  await manage.getByLabel("Isi pesan", { exact: true }).fill("Diskon 10% hari ini ");
  await manage.getByRole("button", { name: "Kursor", exact: true }).click();
  // Tags below the preview: Enter adds one, a comma too, and text still typed is added on save.
  const tag = manage.getByLabel("Tag baru", { exact: true });
  await tag.fill("promo");
  await tag.press("Enter");
  await tag.pressSequentially("diskon,");
  await tag.fill("Promo"); // duplicate, ignored
  await tag.press("Enter");
  await tag.fill("lebaran");
  expectEqual((await manage.locator(".tag-input .tag").allTextContents()).join(","), "promo,diskon");
  if (process.env.SHOT_DIR) await manage.screenshot({ path: `${process.env.SHOT_DIR}/manage-tags.png` });
  await manage.getByRole("button", { name: "Simpan", exact: true }).click();
  await manage.getByText("Template disimpan.").waitFor();
  await manage.locator(".card-item", { hasText: "/promo" }).waitFor();
  const promo = (await readRows("templates")).find((t) => t.shortcut === "promo");
  expectEqual(promo.body, "Diskon 10% hari ini {@kursor}");
  expectEqual(promo.tags, "promo,diskon,lebaran");
  // Saved changes are uploaded right away (to the fake Supabase above).
  await eventually(async () => supabaseCalls.includes("POST /rest/v1/templates"), true);

  const p = await open();
  await p.click("#ta");
  await typeText(p, "/promo");
  await p.keyboard.press("Enter");
  expectEqual(await p.inputValue("#ta"), "Diskon 10% hari ini ");
  await p.close();
  await manage.close();
});

await check("manage page: a taken shortcut is refused", async () => {
  const manage = await context.newPage();
  await manage.goto(`chrome-extension://${extensionId}/manage.html`);
  await manage.getByRole("button", { name: "Template baru" }).click();
  await manage.getByLabel("Judul", { exact: true }).fill("Salam lagi");
  await manage.getByLabel("Shortcut", { exact: true }).fill("salam");
  await manage.getByLabel("Isi pesan", { exact: true }).fill("Halo");
  await manage.getByRole("button", { name: "Simpan", exact: true }).click();
  await manage.getByText("Shortcut /salam sudah dipakai template lain").waitFor();
  await manage.close();
});

await check("manage page: a contact variable asks for a fallback", async () => {
  const manage = await context.newPage();
  await manage.goto(`chrome-extension://${extensionId}/manage.html`);
  await manage.getByRole("button", { name: "Template baru" }).click();
  await manage.getByRole("button", { name: "Kota", exact: true }).click();
  await manage.getByLabel("Kalau kosong, pakai (opsional)", { exact: true }).fill("kotamu");
  await manage.getByRole("button", { name: "Sisipkan", exact: true }).click();
  await manage.getByRole("button", { name: "Nama", exact: true }).click();
  await manage.getByRole("button", { name: "Sisipkan", exact: true }).click();
  expectEqual(await manage.locator('textarea[name="body"]').inputValue(), "{kota|kotamu}{nama}");
  expectEqual(await manage.locator(".bubble .var").count(), 2);
  expectEqual(await manage.locator(".chip-editor .var-chip").count(), 2);
  await manage.close();
});

await check("manage page: variables in the message are chips; clicking one sets its fallback or removes it", async () => {
  const manage = await context.newPage();
  await manage.goto(`chrome-extension://${extensionId}/manage.html`);
  await manage.getByRole("button", { name: "Template baru" }).click();
  const editor = manage.getByRole("textbox", { name: "Isi pesan" });
  await editor.click();
  // Typed braces turn into a chip as soon as the variable is complete, and typing goes on after it.
  await manage.keyboard.type("Halo {nama|Kak}, ongkir ke {kota} ya");
  await manage.keyboard.press("Enter");
  await manage.keyboard.type("Makasih");
  const body = manage.locator('textarea[name="body"]');
  expectEqual(await body.inputValue(), "Halo {nama|Kak}, ongkir ke {kota} ya\nMakasih");
  expectEqual((await manage.locator(".chip-editor .var-chip").allTextContents()).join("/"), "NamaKak/Kota");

  await manage.locator(".chip-editor .var-chip", { hasText: "Kota" }).click();
  await manage.getByLabel("Kalau kosong, pakai (opsional)", { exact: true }).fill("Bandung");
  await manage.getByLabel("Kalau kosong, pakai (opsional)", { exact: true }).press("Enter");
  expectEqual(await body.inputValue(), "Halo {nama|Kak}, ongkir ke {kota|Bandung} ya\nMakasih");

  await manage.locator(".chip-editor .var-chip", { hasText: "Nama" }).click();
  if (process.env.SHOT_DIR) await manage.screenshot({ path: `${process.env.SHOT_DIR}/manage-chips.png` });
  await manage.getByRole("dialog", { name: "Atur Nama" }).getByRole("button", { name: "Hapus" }).click();
  expectEqual(await body.inputValue(), "Halo , ongkir ke {kota|Bandung} ya\nMakasih");
  expectEqual(await manage.locator(".bubble .var").count(), 1);
  await manage.close();
});

await check("manage page: AI makes a template from a description, then rewrites it; nothing changes before Pakai", async () => {
  const aiBodies = [];
  const quota = { limit: 5, remaining: 4, period: "total" };
  const fulfill = (route, status, body) => route.fulfill({ status, body: JSON.stringify(body), contentType: "application/json" });
  const aiRoute = /^https:\/\/[a-z]+\.supabase\.co\/functions\/v1\/ai-template$/;
  const quotaRoute = /^https:\/\/[a-z]+\.supabase\.co\/rest\/v1\/rpc\/ai_quota$/;
  await context.route(quotaRoute, (route) => fulfill(route, 200, quota));
  const reports = [];
  const reportRoute = /^https:\/\/[a-z]+\.supabase\.co\/rest\/v1\/ai_reports$/;
  await context.route(reportRoute, (route) => (reports.push(route.request().postDataJSON()), route.fulfill({ status: 201, body: "" })));
  await context.route(aiRoute, (route) => {
    const body = route.request().postDataJSON();
    aiBodies.push(body);
    if (body.mode === "create") return fulfill(route, 200, { template: { title: "Info ongkir", shortcut: "info_ongkir", body: "Halo {nama|Kak}, ongkir ke {kota} Rp{ongkir}." }, quota });
    if (body.style === "casual") return fulfill(route, 429, { error: "quota_exceeded" });
    return fulfill(route, 200, { template: { title: "Info ongkir", shortcut: "ongkir", body: "Halo {nama|Kak}, ongkir ke {kota}: Rp{ongkir}. Terima kasih 🙏" }, quota });
  });
  try {
    const manage = await context.newPage();
    await manage.goto(`chrome-extension://${extensionId}/manage.html`);
    await manage.getByRole("button", { name: "Template baru" }).click();
    const body = manage.locator('textarea[name="body"]');

    await manage.getByRole("button", { name: "Buat dengan AI" }).click();
    await manage.getByText("Sisa 4 dari 5 kali gratis").waitFor();
    await manage.getByLabel("Mau template untuk apa?").fill("balas pembeli yang tanya ongkir");
    await manage.locator(".ai-box").getByRole("button", { name: "Buat", exact: true }).click();
    await manage.locator(".ai-result", { hasText: "/info_ongkir" }).waitFor();
    expectEqual(await body.inputValue(), "");
    expectEqual(aiBodies[0].mode, "create");
    expectEqual(aiBodies[0].description, "balas pembeli yang tanya ongkir");
    expectEqual(aiBodies[0].fields.includes("kota"), true);
    await manage.locator(".ai-box").getByRole("button", { name: "Pakai" }).click();
    expectEqual(await body.inputValue(), "Halo {nama|Kak}, ongkir ke {kota} Rp{ongkir}.");
    expectEqual(await manage.getByLabel("Judul").inputValue(), "Info ongkir");
    expectEqual(await manage.getByLabel("Shortcut").inputValue(), "info-ongkir");
    expectEqual(await manage.locator(".ai-box").isHidden(), true);

    // With text, the same button rewrites it and shows the original next to the result.
    await manage.getByRole("button", { name: "Rapikan dengan AI" }).click();
    await manage.getByRole("radio", { name: "Lebih santai" }).click();
    await manage.locator(".ai-box").getByRole("button", { name: "Rapikan", exact: true }).click();
    await manage.locator(".ai-box").getByText("Jatah AI sudah habis.").waitFor();
    await manage.getByRole("radio", { name: "Lebih sopan" }).click();
    await manage.locator(".ai-box").getByRole("button", { name: "Rapikan", exact: true }).click();
    await manage.locator(".ai-box").getByText("Sebelum").waitFor();
    if (process.env.SHOT_DIR) await manage.screenshot({ path: `${process.env.SHOT_DIR}/manage-ai.png` });
    expectEqual(aiBodies.at(-1).body, "Halo {nama|Kak}, ongkir ke {kota} Rp{ongkir}.");
    expectEqual(aiBodies.at(-1).style, "polite");
    // Google Play's AI content policy: a result can be reported from the same box.
    await manage.locator(".ai-box").getByRole("button", { name: "Laporkan hasil ini" }).click();
    await manage.locator(".ai-box").getByRole("button", { name: "Salah atau menyesatkan" }).click();
    await manage.locator(".ai-box").getByText("laporanmu terkirim").waitFor();
    expectEqual(reports.at(-1).reason, "wrong");
    expectEqual(reports.at(-1).input, "Halo {nama|Kak}, ongkir ke {kota} Rp{ongkir}.");
    expectEqual(reports.at(-1).result, "Halo {nama|Kak}, ongkir ke {kota}: Rp{ongkir}. Terima kasih 🙏");
    await manage.locator(".ai-box").getByRole("button", { name: "Pakai" }).click();
    expectEqual(await body.inputValue(), "Halo {nama|Kak}, ongkir ke {kota}: Rp{ongkir}. Terima kasih 🙏");
    expectEqual(await manage.getByLabel("Shortcut").inputValue(), "info-ongkir");
    await manage.close();
  } finally {
    await context.unroute(aiRoute);
    await context.unroute(quotaRoute);
    await context.unroute(reportRoute);
  }
});

await check("manage page: add a choice field with a fallback, then delete a template", async () => {
  const manage = await context.newPage();
  await manage.goto(`chrome-extension://${extensionId}/manage.html#fields`);
  await manage.getByRole("button", { name: "Field baru" }).click();
  await manage.getByLabel("Nama field", { exact: true }).fill("Kurir Pilihan");
  await manage.getByText("{kurir_pilihan}").waitFor();
  // The icon follows the name until one is picked: Kurir → truck.
  expectEqual(await manage.locator(".icon-head .field-badge").getAttribute("data-icon"), "truck");
  await manage.getByRole("radio", { name: /Pilihan/ }).click();
  for (const choice of ["JNE", "J&T", "JNE"]) {
    await manage.getByLabel("Pilihan baru", { exact: true }).fill(choice);
    await manage.getByLabel("Pilihan baru", { exact: true }).press("Enter");
  }
  await manage.getByLabel("Kalau kosong, isi dengan (opsional)").selectOption("J&T");
  await manage.getByRole("button", { name: "Simpan", exact: true }).click();
  await manage.getByText("Field disimpan.").waitFor();
  const kurir = (await readRows("field_definitions")).find((f) => f.field_key === "kurir_pilihan");
  expectEqual(kurir.options, "JNE\nJ&T");
  expectEqual(kurir.fallback, "J&T");
  expectEqual(kurir.position, 3);
  expectEqual(kurir.icon, "");

  await manage.getByRole("tab", { name: /Template/ }).click();
  await manage.locator(".card-item", { hasText: "/promo" }).click();
  manage.once("dialog", (d) => d.accept());
  await manage.getByRole("button", { name: "Hapus", exact: true }).click();
  await manage.getByText("Template dihapus.").waitFor();
  expectEqual((await readRows("templates")).find((t) => t.shortcut === "promo").deleted, true);
  await manage.close();
});

await check("manage page: field icons are automatic, can be picked from the gallery, and long variables fit", async () => {
  const manage = await context.newPage();
  await manage.setViewportSize({ width: 900, height: 900 });
  await manage.goto(`chrome-extension://${extensionId}/manage.html#fields`);
  await manage.getByRole("button", { name: "Field baru" }).click();
  await manage.getByLabel("Nama field", { exact: true }).fill("Nama panggilan testing dhlh untuk pelanggan setia");
  expectEqual(await manage.locator(".icon-head .field-badge").getAttribute("data-icon"), "user");
  await manage.getByRole("button", { name: "Ganti ikon" }).click();
  await manage.getByRole("radio", { name: "gift" }).click();
  expectEqual(await manage.locator(".icon-head .field-badge").getAttribute("data-icon"), "gift");
  await manage.getByRole("button", { name: "Simpan", exact: true }).click();
  await manage.getByText("Field disimpan.").waitFor();
  const saved = (await readRows("field_definitions")).find((f) => f.label.startsWith("Nama panggilan testing"));
  expectEqual(saved.icon, "gift");
  const card = manage.locator(".card-item", { hasText: "Nama panggilan testing" });
  expectEqual(await card.locator(".field-badge").getAttribute("data-icon"), "gift");
  // The name keeps the width; the long variable is cut short on its own line.
  const [name, variable] = await Promise.all([card.locator("b").boundingBox(), card.locator(".var").boundingBox()]);
  expectEqual(name.width > 150, true);
  expectEqual(variable.y > name.y, true);
  if (process.env.SHOT_DIR) await manage.screenshot({ path: `${process.env.SHOT_DIR}/manage-fields.png`, fullPage: true });
  // Back to automatic.
  await card.click();
  await manage.getByRole("button", { name: "Ganti ikon" }).click();
  await manage.getByRole("radio", { name: "Otomatis" }).click();
  await manage.getByRole("button", { name: "Simpan", exact: true }).click();
  await manage.getByText("Field disimpan.").waitFor();
  expectEqual((await readRows("field_definitions")).find((f) => f.id === saved.id).icon, "");
  await manage.close();
});

await check("manage page: Channel is built in, its choices editable and it cannot be deleted", async () => {
  const manage = await context.newPage();
  await manage.goto(`chrome-extension://${extensionId}/manage.html#fields`);
  const bawaan = manage.locator(".cards").first();
  await bawaan.getByText("{channel}").waitFor();
  expectEqual((await manage.locator(".cards").nth(1).textContent()).includes("{channel}"), false);
  await bawaan.locator(".card-item", { hasText: "Channel" }).click();
  expectEqual(await manage.getByRole("button", { name: "Hapus", exact: true }).count(), 0);
  expectEqual(await manage.getByRole("radiogroup", { name: "Tipe data" }).count(), 0);
  await manage.getByLabel("Pilihan baru", { exact: true }).fill("Lazada");
  await manage.getByLabel("Pilihan baru", { exact: true }).press("Enter");
  if (process.env.SHOT_DIR) await manage.screenshot({ path: `${process.env.SHOT_DIR}/manage-channel.png` });
  await manage.getByRole("button", { name: "Simpan", exact: true }).click();
  await manage.getByText("Field disimpan.").waitFor();
  const channel = (await readRows("field_definitions")).find((f) => f.id === "builtin-channel");
  expectEqual(channel.field_key, "channel");
  expectEqual(channel.options.split("\n").at(-1), "Lazada");
  await manage.close();
});

await check("help page: questions open in place, and a message is sent to support_messages", async () => {
  const help = await context.newPage();
  await help.goto(`chrome-extension://${extensionId}/help.html`);
  await help.getByText("Apakah ScriptGo membaca yang saya ketik?").click();
  await help.getByText("Ketikanmu tidak dikirim ke mana pun", { exact: false }).waitFor();
  await help.getByRole("radio", { name: "Saran" }).click();
  const before = supabaseCalls.length;
  await help.getByLabel("Pesanmu").fill("Tolong tambah fitur jadwal kirim");
  await help.getByRole("button", { name: "Kirim" }).click();
  await help.getByText("Terkirim.", { exact: false }).waitFor();
  expectEqual(supabaseCalls.slice(before).includes("POST /rest/v1/support_messages"), true);
  await help.close();
});

await check("popup: full name and the Ajak teman link", async () => {
  await worker.evaluate(() =>
    chrome.storage.local.set({
      session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "u", email: "budi@contoh.id" },
      profile: { fullName: "Budi Santoso", referralCode: "budi2345", referralJoined: 2 },
    }),
  );
  const popup = await context.newPage();
  await popup.setViewportSize({ width: 380, height: 900 });
  await popup.goto(`chrome-extension://${extensionId}/popup.html`);
  await popup.locator(".name").getByText("Budi Santoso").waitFor();
  expectEqual(await popup.getByLabel("Link ajak teman").inputValue(), "https://scriptgo.id/?ref=budi2345");
  expectEqual((await popup.locator(".refer").textContent()).includes("2 teman sudah daftar"), true);
  if (process.env.SHOT_DIR) await popup.screenshot({ path: `${process.env.SHOT_DIR}/popup-referral.png`, fullPage: true });
  await popup.close();
  await worker.evaluate(() => chrome.storage.local.remove(["session", "profile"]));
});

await check("account page: full name, password check, and the theme", async () => {
  await worker.evaluate(() =>
    chrome.storage.local.set({
      session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "u", email: "budi@contoh.id" },
      profile: { fullName: "Budi Santoso", plan: { kind: "trial", trialEndsAt: Date.now() + 3 * 86_400_000 - 60_000 } },
    }),
  );
  const page = await context.newPage();
  await page.goto(`chrome-extension://${extensionId}/account.html`);
  await page.getByText("Paket: Trial Pro").waitFor();
  await page.getByText(/^Berakhir 3 hari lagi \(/).waitFor();
  expectEqual(await page.getByRole("link", { name: "Langganan Pro" }).getAttribute("href"), "https://scriptgo.id/bayar");
  expectEqual(await page.getByLabel("Nama lengkap").inputValue(), "Budi Santoso");
  expectEqual(await page.getByLabel("Email", { exact: true }).inputValue(), "budi@contoh.id");
  await page.getByLabel("Kata sandi baru").fill("rahasia123");
  await page.getByLabel("Ulangi kata sandi").fill("rahasia124");
  await page.getByRole("button", { name: "Simpan kata sandi" }).click();
  await page.getByText("Kedua kata sandi belum sama.").waitFor();
  await page.getByRole("radio", { name: "Gelap" }).click();
  await eventually(() => page.evaluate(() => document.documentElement.dataset.theme), "dark");
  if (process.env.SHOT_DIR) await page.screenshot({ path: `${process.env.SHOT_DIR}/account-dark.png`, fullPage: true });
  await page.getByRole("radio", { name: "Ikuti sistem" }).click();
  await eventually(() => page.evaluate(() => document.documentElement.dataset.theme ?? "system"), "system");
  await page.close();
  await worker.evaluate(() => chrome.storage.local.remove(["session", "profile", "theme"]));
});

await check("Free plan: no suggestions on web pages, and the popup says the extension is for Pro", async () => {
  await worker.evaluate(() =>
    chrome.storage.local.set({
      session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "u", email: "budi@contoh.id" },
      profile: { fullName: "Budi Santoso", plan: { kind: "trial", trialEndsAt: Date.now() - 60_000 } },
    }),
  );
  const p = await open();
  await p.click("#ta");
  await typeText(p, "/sal");
  await p.keyboard.press("Enter");
  expectEqual(await p.inputValue("#ta"), "/sal\n");
  await p.close();

  const popup = await context.newPage();
  await popup.setViewportSize({ width: 380, height: 700 });
  await popup.goto(`chrome-extension://${extensionId}/popup.html`);
  await popup.getByText("Paket Gratis").waitFor();
  expectEqual(await popup.getByRole("link", { name: "Langganan Pro" }).getAttribute("href"), "https://scriptgo.id/bayar");
  if (process.env.SHOT_DIR) await popup.screenshot({ path: `${process.env.SHOT_DIR}/popup-free.png` });
  await popup.close();
  await worker.evaluate(() => chrome.storage.local.remove(["session", "profile"]));
});

await check("popup: signing in can switch to a password and back, keeping the email", async () => {
  await worker.evaluate(() => chrome.storage.local.remove("session"));
  const popup = await context.newPage();
  await popup.goto(`chrome-extension://${extensionId}/popup.html`);
  await popup.getByLabel("Email").fill("budi@contoh.id");
  await popup.getByRole("button", { name: "Masuk dengan kata sandi" }).click();
  expectEqual(await popup.getByLabel("Email").inputValue(), "budi@contoh.id");
  const submit = popup.getByRole("button", { name: "Masuk", exact: true });
  expectEqual(await submit.isDisabled(), true);
  await popup.getByLabel("Kata sandi").fill("rahasia123");
  expectEqual(await submit.isDisabled(), false);
  await popup.getByRole("button", { name: "Pakai kode email saja" }).click();
  expectEqual(await popup.getByLabel("Kata sandi").count(), 0);
  await popup.getByRole("button", { name: "Kirim kode" }).waitFor();
  await popup.close();
});

await context.close();
for (const [status, name, error] of results) console.log(`${status.padEnd(4)} ${name}${error ? `\n     ${error}` : ""}`);
const failed = results.filter(([s]) => s === "FAIL").length;
console.log(`\n${results.length - failed}/${results.length} passed`);
process.exit(failed ? 1 : 0);
