// The background worker against an in-memory chrome.storage and a fake Supabase.
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Message, Reply } from "../src/store";

const EXTENSION = "chrome-extension://abc/";

function memoryArea() {
  let items: Record<string, unknown> = {};
  return {
    items: () => items,
    get: async (keys: string | string[]) =>
      Object.fromEntries([keys].flat().filter((k) => k in items).map((k) => [k, structuredClone(items[k])])),
    set: async (values: Record<string, unknown>) => {
      items = { ...items, ...structuredClone(values) };
    },
    remove: async (keys: string | string[]) => {
      for (const k of [keys].flat()) delete items[k];
    },
  };
}

interface Call {
  method: string;
  path: string;
  headers: Record<string, string>;
  body: unknown;
}

let local: ReturnType<typeof memoryArea>;
let installed: (details: { reason: string }) => void = () => undefined;
let injected: number[] = [];
let calls: Call[];
let routes: Array<(call: Call) => unknown>;
let handler: (message: Message, sender: { id: string; url?: string }, reply: (r: Reply) => void) => boolean;

function respond(path: RegExp, body: unknown, method = "GET", status = 200) {
  routes.push((call) => (call.method === method && path.test(call.path) ? { status, body } : undefined));
}

async function sendMessage(message: Message, url = `${EXTENSION}popup.html`): Promise<Reply> {
  return new Promise((resolve) => handler(message, { id: "abc", url }, resolve));
}

beforeEach(async () => {
  vi.resetModules();
  local = memoryArea();
  const session = memoryArea();
  calls = [];
  routes = [];
  injected = [];
  vi.stubGlobal("chrome", {
    storage: { local, session },
    runtime: {
      id: "abc",
      getURL: (path: string) => EXTENSION + path,
      onMessage: { addListener: (fn: typeof handler) => (handler = fn) },
      onInstalled: { addListener: (fn: typeof installed) => (installed = fn) },
      onStartup: { addListener: () => undefined },
    },
    alarms: { create: async () => undefined, onAlarm: { addListener: () => undefined } },
    tabs: {
      query: async () => [
        { id: 1, url: "https://web.whatsapp.com/" },
        { id: 2, url: "https://mail.google.com/", discarded: true },
        { id: 3, url: "https://x.com/home" },
      ],
      create: async () => undefined,
    },
    scripting: { executeScript: async (options: { target: { tabId: number } }) => void injected.push(options.target.tabId) },
  });
  vi.stubGlobal("fetch", async (url: string, init: { method: string; headers: Record<string, string>; body?: string }) => {
    const call = { method: init.method, path: url.replace(/^https:\/\/[^/]+/, ""), headers: init.headers, body: init.body ? JSON.parse(init.body) : null };
    calls.push(call);
    for (const route of routes) {
      const hit = route(call) as { status: number; body: unknown } | undefined;
      if (hit) return new Response(hit.body === null ? "" : JSON.stringify(hit.body), { status: hit.status });
    }
    return new Response("[]", { status: 200 });
  });
  await import("../src/background");
});

const sessionJson = (token = "access-1", userId = "user-1") => ({
  access_token: token,
  refresh_token: "refresh-1",
  expires_in: 3600,
  user: { id: userId, email: "budi@contoh.id" },
});

const serverTemplate = (updatedAt: number, syncedAt: string) => ({
  id: "t1",
  user_id: "user-1",
  title: "Salam",
  shortcut: "salam",
  body: "Halo kak",
  tags: "",
  updated_at: updatedAt,
  deleted: false,
  synced_at: syncedAt,
});

describe("background sync", () => {
  it("takes the trigger and theme set on the phone from user_settings", async () => {
    await local.set({ session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "user-1", email: "x" } });
    respond(/^\/rest\/v1\/user_settings\?/, [{ data: { manager: { trigger_char: { s: "#" } }, app: { app_theme: { s: "dark" } } }, updated_at: 5000 }]);

    expect((await sendMessage({ type: "syncNow" })).ok).toBe(true);

    expect(local.items().settings).toEqual({ trigger: "#", disabledSites: [] });
    expect(local.items().theme).toBe("dark");
    expect(local.items().settingsSyncedAt).toBe(5000);
    expect(calls.some((c) => c.method === "POST" && c.path === "/rest/v1/user_settings")).toBe(false);
  });

  it("sends a trigger changed here to user_settings, keeping the app's other settings", async () => {
    await local.set({
      session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "user-1", email: "x" },
      settings: { trigger: ";", disabledSites: ["x.com"] },
      settingsEditedAt: 9000,
      settingsSyncedAt: 5000,
    });
    respond(/^\/rest\/v1\/user_settings\?/, [{ data: { manager: { trigger_char: { s: "/" }, ask_contact: { b: true } } }, updated_at: 5000 }]);

    expect((await sendMessage({ type: "syncNow" })).ok).toBe(true);

    const post = calls.find((c) => c.method === "POST" && c.path === "/rest/v1/user_settings")!;
    expect(post.headers.Prefer).toBe("resolution=merge-duplicates,return=minimal");
    expect(post.body).toMatchObject({ data: { manager: { trigger_char: { s: ";" }, ask_contact: { b: true } }, app: { app_theme: { s: "system" } } } });
    expect((post.body as { updated_at: number }).updated_at).toBeGreaterThan(5000);
    expect(local.items().settings).toEqual({ trigger: ";", disabledSites: ["x.com"] });
  });

  it("puts the new content script into open web pages after an update", async () => {
    installed({ reason: "update" });
    await vi.waitFor(() => expect(injected).toEqual([1, 3]));
  });

  it("refreshes an expired token once even when requests run in parallel", async () => {
    await local.set({ session: { accessToken: "old", refreshToken: "r1", expiresAt: Date.now() - 1000, userId: "user-1", email: "budi@contoh.id" } });
    respond(/^\/auth\/v1\/token\?grant_type=refresh_token$/, { access_token: "new", refresh_token: "r2", expires_in: 3600 }, "POST");
    respond(/^\/auth\/v1\/user$/, { id: "user-1", user_metadata: { full_name: "Budi" } });
    respond(/^\/rest\/v1\/rpc\/my_referral$/, { code: "budi2345", joined: 0 }, "POST");

    const reply = await sendMessage({ type: "syncNow" });

    expect(reply.ok).toBe(true);
    expect(calls.filter((c) => c.path.startsWith("/auth/v1/token")).length).toBe(1);
    expect(calls.filter((c) => c.path.startsWith("/rest/") || c.path === "/auth/v1/user").every((c) => c.headers.Authorization === "Bearer new")).toBe(true);
    expect(local.items().profile).toEqual({ fullName: "Budi", referralCode: "budi2345", referralJoined: 0 });
  });

  it("ends the session when the refresh token is no longer valid, with a clear message", async () => {
    await local.set({ session: { accessToken: "old", refreshToken: "gone", expiresAt: Date.now() - 1000, userId: "user-1", email: "budi@contoh.id" } });
    respond(/^\/auth\/v1\/token\?grant_type=refresh_token$/, { error_code: "refresh_token_not_found", msg: "Invalid Refresh Token: Refresh Token Not Found" }, "POST", 400);

    const reply = await sendMessage({ type: "syncNow" });

    expect(reply).toEqual({ ok: false, error: "Sesi kamu sudah berakhir. Masuk lagi untuk melanjutkan sinkron." });
    expect(local.items().session).toBeUndefined();
  });

  it("keeps the account's full name for the popup, not the business name", async () => {
    respond(/^\/auth\/v1\/verify$/, sessionJson(), "POST");
    respond(/^\/auth\/v1\/user$/, { id: "user-1", email: "budi@contoh.id", user_metadata: { full_name: " Budi Santoso ", name: "budi g" } });
    respond(/^\/rest\/v1\/business_profiles/, [{ business_name: "Toko Budi" }]);

    await sendMessage({ type: "verifyCode", email: "budi@contoh.id", code: "123456" });

    expect(local.items().profile).toEqual({ fullName: "Budi Santoso" });
  });

  it("keeps the account's Ajak teman code, and still signs in when it cannot be loaded", async () => {
    respond(/^\/auth\/v1\/verify$/, sessionJson(), "POST");
    respond(/^\/auth\/v1\/user$/, { id: "user-1", user_metadata: { full_name: "Budi" } });
    respond(/^\/rest\/v1\/rpc\/my_referral$/, { code: "budi2345", joined: 3 }, "POST");
    await sendMessage({ type: "verifyCode", email: "budi@contoh.id", code: "123456" });
    expect(local.items().profile).toEqual({ fullName: "Budi", referralCode: "budi2345", referralJoined: 3 });

    await sendMessage({ type: "signOut" });
    routes = [];
    respond(/^\/auth\/v1\/verify$/, sessionJson(), "POST");
    respond(/^\/auth\/v1\/user$/, { id: "user-1", user_metadata: { full_name: "Budi" } });
    respond(/^\/rest\/v1\/rpc\/my_referral$/, { message: "boom" }, "POST", 500);
    const reply = await sendMessage({ type: "verifyCode", email: "budi@contoh.id", code: "123456" });
    expect(reply.ok).toBe(true);
    expect(local.items().profile).toEqual({ fullName: "Budi" });
  });

  it("signs in with the emailed code and pulls data", async () => {
    respond(/^\/auth\/v1\/verify$/, sessionJson(), "POST");
    respond(/^\/rest\/v1\/templates\?/, [serverTemplate(100, "2026-09-26T10:00:00Z")]);

    const reply = await sendMessage({ type: "verifyCode", email: " budi@contoh.id ", code: "123456" });

    expect(reply).toEqual({ ok: true, message: "Berhasil masuk. 1 data diunduh." });
    expect(calls[0].body).toEqual({ type: "email", email: "budi@contoh.id", token: "123456" });
    const items = local.items();
    expect(items["table:templates"]).toEqual({
      t1: { id: "t1", title: "Salam", shortcut: "salam", body: "Halo kak", tags: "", updated_at: 100, deleted: false, dirty: false },
    });
    expect(items["cursor:templates"]).toBe("2026-09-26T10:00:00Z");
    const pull = calls.find((c) => c.path.startsWith("/rest/v1/templates"))!;
    expect(pull.headers.Authorization).toBe("Bearer access-1");
    expect(pull.path).toBe("/rest/v1/templates?select=*&order=synced_at.asc&limit=500");
  });

  it("signs in with email and password", async () => {
    respond(/^\/auth\/v1\/token\?grant_type=password$/, sessionJson(), "POST");
    const reply = await sendMessage({ type: "passwordSignIn", email: " budi@contoh.id ", password: "rahasia123" });
    expect(reply).toEqual({ ok: true, message: "Berhasil masuk. 0 data diunduh." });
    expect(calls[0].body).toEqual({ email: "budi@contoh.id", password: "rahasia123" });
    expect(local.items().session).toMatchObject({ accessToken: "access-1" });
  });

  it("says when the password is wrong", async () => {
    respond(/^\/auth\/v1\/token\?grant_type=password$/, { error_code: "invalid_credentials", msg: "Invalid login credentials" }, "POST", 400);
    expect(await sendMessage({ type: "passwordSignIn", email: "a@b.id", password: "salah" })).toEqual({
      ok: false,
      error: "Email atau kata sandi salah. Lupa? Masuk dengan kode email.",
    });
  });

  it("pulls only rows after the cursor", async () => {
    await local.set({ session: { ...sessionJson(), accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "user-1", email: "x" } });
    await local.set({ "cursor:contacts": "2026-09-26T10:00:00.5+00:00" });
    await sendMessage({ type: "syncNow" });
    expect(calls.map((c) => c.path)).toContain(
      "/rest/v1/contacts?select=*&order=synced_at.asc&limit=500&synced_at=gt.2026-09-26T10%3A00%3A00.5%2B00%3A00",
    );
  });

  it("uploads contact edits from the form and keeps them over older server versions", async () => {
    // Signed out while editing, so the save does not start its own upload before the test's sync.
    await local.set({
      "table:contacts": {
        c1: { id: "c1", name: "Budi", phones: "+6281234567890", fields: { kota: "Bandung" }, updated_at: 100, deleted: false, dirty: false },
      },
    });
    await sendMessage({ type: "saveContactFields", contactId: "c1", fields: { total: "150000" } });
    const edited = (local.items()["table:contacts"] as Record<string, { updated_at: number; dirty: boolean }>).c1;
    expect(edited.dirty).toBe(true);
    expect(edited.updated_at).toBeGreaterThan(100);

    await local.set({ session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "user-1", email: "x" } });
    // An older copy on the server must not replace the local edit.
    respond(/^\/rest\/v1\/contacts\?/, [
      { id: "c1", user_id: "user-1", name: "Budi", phones: "", fields: {}, updated_at: 50, deleted: false, synced_at: "2026-09-26T11:00:00Z" },
    ]);
    calls = [];
    await sendMessage({ type: "syncNow" });

    const push = calls.find((c) => c.method === "POST" && c.path === "/rest/v1/contacts")!;
    expect(push.headers.Prefer).toBe("resolution=merge-duplicates,return=minimal");
    expect(push.body).toEqual([
      { id: "c1", name: "Budi", phones: "+6281234567890", fields: { kota: "Bandung", total: "150000" }, updated_at: edited.updated_at, deleted: false },
    ]);
    const after = (local.items()["table:contacts"] as Record<string, { fields: object; dirty: boolean }>).c1;
    expect(after.fields).toEqual({ kota: "Bandung", total: "150000" });
    expect(after.dirty).toBe(false);
  });

  it("creates a contact from the form, with the built-in Channel field", async () => {
    const reply = await sendMessage({ type: "createContact", name: " Rina Hijab ", phone: "+6289900001111", fields: { channel: "WhatsApp", "Bad Key": "x" } }, `${EXTENSION}form.html#n`);
    expect(reply.ok).toBe(true);
    const id = (reply as { contactId: string }).contactId;
    const contact = (local.items()["table:contacts"] as Record<string, { name: string; phones: string; fields: object; dirty: boolean }>)[id];
    expect(contact).toMatchObject({ name: "Rina Hijab", phones: "+6289900001111", fields: { channel: "WhatsApp" }, dirty: true });
    const channel = (local.items()["table:field_definitions"] as Record<string, { field_key: string; type: string; options: string; updated_at: number }>)["builtin-channel"];
    expect(channel).toMatchObject({ field_key: "channel", type: "choice", updated_at: 1 });
    expect(channel.options.split("\n")[0]).toBe("WhatsApp");

    // The same number again gives the same contact, not a duplicate.
    const again = await sendMessage({ type: "createContact", name: "Rina", phone: "+6289900001111", fields: {} }, `${EXTENSION}form.html#n`);
    expect((again as { contactId: string }).contactId).toBe(id);
    expect(Object.keys(local.items()["table:contacts"] as object)).toHaveLength(1);
  });

  it("keeps the user's own Channel field and rejects an empty or bad contact", async () => {
    await local.set({
      "table:field_definitions": {
        "builtin-channel": { id: "builtin-channel", field_key: "channel", label: "Asal", type: "choice", options: "WA\nIG", position: -1, updated_at: 500, deleted: false, dirty: false },
      },
    });
    await sendMessage({ type: "createContact", name: "Ani", phone: "", fields: { channel: "WA" } }, `${EXTENSION}form.html#n`);
    const channel = (local.items()["table:field_definitions"] as Record<string, { label: string; updated_at: number }>)["builtin-channel"];
    expect(channel).toMatchObject({ label: "Asal", updated_at: 500 });
    expect(await sendMessage({ type: "createContact", name: " ", phone: "", fields: {} }, `${EXTENSION}form.html#n`)).toEqual({ ok: false, error: "Isi nama atau nomor" });
    expect(await sendMessage({ type: "createContact", name: "X", phone: "0812", fields: {} }, `${EXTENSION}form.html#n`)).toEqual({ ok: false, error: "Nomor belum benar" });
  });

  it("refreshes an expired access token", async () => {
    await local.set({ session: { accessToken: "old", refreshToken: "r", expiresAt: Date.now() - 1, userId: "user-1", email: "x" } });
    respond(/^\/auth\/v1\/token\?grant_type=refresh_token$/, sessionJson("access-2"), "POST");
    await sendMessage({ type: "syncNow" });
    expect(calls[0].body).toEqual({ refresh_token: "r" });
    expect(calls.filter((c) => c.path.startsWith("/rest/")).every((c) => c.headers.Authorization === "Bearer access-2")).toBe(true);
  });

  it("signing out removes the account's data from the browser", async () => {
    await local.set({
      session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "user-1", email: "x" },
      "table:templates": { t1: {} },
      "cursor:templates": "c",
      settings: { trigger: ";" },
    });
    await sendMessage({ type: "signOut" });
    expect(Object.keys(local.items())).toEqual(["settings"]);
  });

  it("reports readable errors", async () => {
    respond(/^\/auth\/v1\/verify$/, { error_code: "otp_expired", msg: "Token has expired" }, "POST", 403);
    expect(await sendMessage({ type: "verifyCode", email: "a@b.id", code: "000000" })).toEqual({
      ok: false,
      error: "Kode salah atau sudah kedaluwarsa. Minta kode baru.",
    });
  });

  it("only lets extension pages sign in, sync, or claim forms", async () => {
    const page = "https://web.whatsapp.com/";
    for (const message of [
      { type: "syncNow" },
      { type: "signOut" },
      { type: "claimForm", nonce: "n" },
      { type: "saveContactFields", contactId: "c1", fields: {} },
      { type: "createContact", name: "X", phone: "", fields: {} },
      { type: "saveTemplate", template: { id: null, title: "X", shortcut: "x", body: "x" } },
      { type: "deleteTemplate", id: "t1" },
      { type: "saveField", field: { id: null, label: "X", type: "text", options: [] } },
      { type: "deleteField", id: "f1" },
    ] as Message[]) {
      expect(await sendMessage(message, page)).toEqual({ ok: false, error: "Tidak diizinkan" });
    }
    expect(calls).toEqual([]);
  });

  it("hands a form request to exactly one claim", async () => {
    const request = { templateId: "t1", hint: null };
    expect(await sendMessage({ type: "openForm", nonce: "n1", request }, "https://web.whatsapp.com/")).toEqual({ ok: true });
    expect(await sendMessage({ type: "claimForm", nonce: "n1" }, `${EXTENSION}form.html#n1`)).toEqual({ ok: true, request });
    expect(await sendMessage({ type: "claimForm", nonce: "n1" }, `${EXTENSION}form.html#n1`)).toEqual({ ok: false, error: "Form tidak valid" });
  });

});

type Rows = Record<string, Record<string, unknown>>;
const MANAGE = `${EXTENSION}manage.html`;

describe("editing from the manage page", () => {
  it("adds a template, rejects a taken shortcut, and uploads it", async () => {
    await local.set({ settings: { trigger: "#", disabledSites: [] }, "table:templates": { t1: { id: "t1", title: "Salam", shortcut: "salam", body: "Halo", tags: "", updated_at: 100, deleted: false } } });
    expect(await sendMessage({ type: "saveTemplate", template: { id: null, title: "Lain", shortcut: "salam", body: "x" } }, MANAGE)).toEqual({
      ok: false,
      error: "Shortcut #salam sudah dipakai template lain",
    });

    const reply = await sendMessage({ type: "saveTemplate", template: { id: null, title: " Ongkir ", shortcut: "ongkir", body: "Ongkir {total}" } }, MANAGE);
    expect(reply).toEqual({ ok: true, message: "Template disimpan." });
    const added = Object.values(local.items()["table:templates"] as Rows).find((t) => t.shortcut === "ongkir")!;
    expect(added).toMatchObject({ title: "Ongkir", body: "Ongkir {total}", tags: "", deleted: false, dirty: true });

    await local.set({ session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "user-1", email: "x" } });
    await sendMessage({ type: "syncNow" });
    const push = calls.find((c) => c.method === "POST" && c.path === "/rest/v1/templates")!;
    expect(push.body).toEqual([{ id: added.id, title: "Ongkir", shortcut: "ongkir", body: "Ongkir {total}", tags: "", updated_at: added.updated_at, deleted: false }]);
  });

  it("saves the full name, sets a password, and deletes the account from the Akun page", async () => {
    await local.set({ session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "user-1", email: "budi@contoh.id" }, profile: { fullName: "budi", referralCode: "budi2345" } });
    respond(/^\/auth\/v1\/user$/, { id: "user-1", user_metadata: { full_name: "Budi Santoso" } }, "PUT");
    expect(await sendMessage({ type: "saveFullName", fullName: "  Budi   Santoso " }, MANAGE)).toEqual({ ok: true, message: "Nama disimpan." });
    expect(calls.at(-1)).toMatchObject({ method: "PUT", path: "/auth/v1/user", body: { data: { full_name: "Budi Santoso" } } });
    expect(local.items().profile).toEqual({ fullName: "Budi Santoso", referralCode: "budi2345" });
    expect(await sendMessage({ type: "saveFullName", fullName: "  " }, MANAGE)).toEqual({ ok: false, error: "Nama lengkap masih kosong." });

    expect(await sendMessage({ type: "setPassword", password: "pendek" }, MANAGE)).toEqual({ ok: false, error: "Kata sandi minimal 8 karakter." });
    routes = [];
    respond(/^\/auth\/v1\/user$/, { error_code: "same_password", msg: "same" }, "PUT", 422);
    expect(await sendMessage({ type: "setPassword", password: "rahasia123" }, MANAGE)).toEqual({ ok: false, error: "Kata sandi baru sama dengan yang lama." });

    routes = [];
    respond(/^\/rest\/v1\/rpc\/delete_account$/, null, "POST");
    const reply = await sendMessage({ type: "deleteAccount" }, MANAGE);
    expect(reply.ok).toBe(true);
    expect(calls.at(-1)).toMatchObject({ method: "POST", path: "/rest/v1/rpc/delete_account" });
    expect(local.items().session).toBeUndefined();
    expect(local.items().profile).toBeUndefined();
  });

  it("edits the built-in Channel field under its fixed id and never deletes it", async () => {
    // Not synced yet: saving creates it with the app's id, key and type.
    await sendMessage({ type: "saveField", field: { id: "builtin-channel", label: "Diubah", type: "text", options: ["WhatsApp", "Shopee"], fallback: "" } }, MANAGE);
    const defs = local.items()["table:field_definitions"] as Rows;
    expect(defs["builtin-channel"]).toMatchObject({ field_key: "channel", label: "Channel", type: "choice", options: "WhatsApp\nShopee", position: -1, dirty: true });
    expect(Object.keys(defs)).toEqual(["builtin-channel"]);

    expect(await sendMessage({ type: "deleteField", id: "builtin-channel" }, MANAGE)).toEqual({ ok: false, error: "Field Channel bawaan tidak bisa dihapus." });
    expect((local.items()["table:field_definitions"] as Rows)["builtin-channel"].deleted).toBe(false);
  });

  it("saves the tags entered in the editor, normalized as the app stores them", async () => {
    await local.set({ "table:templates": { t1: { id: "t1", title: "Salam", shortcut: "salam", body: "Halo", tags: "promo", updated_at: 1, deleted: false } } });
    await sendMessage({ type: "saveTemplate", template: { id: "t1", title: "Salam", shortcut: "salam", body: "Halo", tags: [" Pengiriman ", "promo", "PROMO", ""] } }, MANAGE);
    expect((local.items()["table:templates"] as Rows).t1.tags).toBe("Pengiriman,promo");
    await sendMessage({ type: "saveTemplate", template: { id: "t1", title: "Salam", shortcut: "salam", body: "Halo", tags: [] } }, MANAGE);
    expect((local.items()["table:templates"] as Rows).t1.tags).toBe("");
  });

  it("edits a template in place, keeping its tags and a later updated_at", async () => {
    await local.set({ "table:templates": { t1: { id: "t1", title: "Salam", shortcut: "salam", body: "Halo", tags: "promo", updated_at: Date.now() + 60_000, deleted: false } } });
    await sendMessage({ type: "saveTemplate", template: { id: "t1", title: "Salam", shortcut: "salam", body: "Halo kak" } }, MANAGE);
    const t1 = (local.items()["table:templates"] as Rows).t1;
    expect(t1).toMatchObject({ body: "Halo kak", tags: "promo", dirty: true });
    expect(t1.updated_at as number).toBeGreaterThan(Date.now());
  });

  it("adds fields with keys from their labels, at the end, and keeps the key when renamed", async () => {
    await local.set({ "table:field_definitions": { f1: { id: "f1", field_key: "kota", label: "Kota", type: "text", options: "", position: 0, updated_at: 1, deleted: false } } });
    await sendMessage({ type: "saveField", field: { id: null, label: "Kota", type: "choice", options: ["Bandung", "Surabaya"] } }, MANAGE);
    await sendMessage({ type: "saveField", field: { id: null, label: "Total", type: "currency", options: ["USD"] } }, MANAGE);
    const fields = Object.values(local.items()["table:field_definitions"] as Rows);
    expect(fields.find((f) => f.field_key === "kota_2")).toMatchObject({ label: "Kota", type: "choice", options: "Bandung\nSurabaya", position: 1, dirty: true });
    expect(fields.find((f) => f.field_key === "total")).toMatchObject({ options: "USD", position: 2 });

    await sendMessage({ type: "saveField", field: { id: "f1", label: "Kota Asal", type: "text", options: [], fallback: " Bandung " } }, MANAGE);
    expect((local.items()["table:field_definitions"] as Rows).f1).toMatchObject({ field_key: "kota", label: "Kota Asal", position: 0, fallback: "Bandung" });
    expect(await sendMessage({ type: "saveField", field: { id: null, label: "Kurir", type: "choice", options: [] } }, MANAGE)).toEqual({
      ok: false,
      error: "Isi minimal satu pilihan",
    });
  });

  it("deletes as a tombstone", async () => {
    await local.set({ "table:templates": { t1: { id: "t1", title: "Salam", shortcut: "salam", body: "Halo", tags: "", updated_at: 100, deleted: false } } });
    expect(await sendMessage({ type: "deleteTemplate", id: "t1" }, MANAGE)).toEqual({ ok: true, message: "Template dihapus." });
    expect((local.items()["table:templates"] as Rows).t1).toMatchObject({ deleted: true, dirty: true });
  });
});

describe("AI help", () => {
  const signedIn = () =>
    local.set({
      session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "user-1", email: "x" },
      "table:field_definitions": {
        f1: { id: "f1", field_key: "kota", label: "Kota", deleted: false },
        f2: { id: "f2", field_key: "lama", label: "Lama", deleted: true },
      },
    });

  it("asks the ai-template function with field keys and returns the template and quota", async () => {
    await signedIn();
    respond(/^\/functions\/v1\/ai-template$/, { template: { title: "Ongkir", shortcut: "ongkir", body: "Ke {kota}" }, quota: { limit: 5, remaining: 4, period: "total" } }, "POST");

    const reply = await sendMessage({ type: "aiTemplate", input: { mode: "create", description: "tanya ongkir" } }, `${EXTENSION}manage.html`);

    expect(reply).toEqual({ ok: true, template: { title: "Ongkir", shortcut: "ongkir", body: "Ke {kota}" }, quota: { limit: 5, remaining: 4, daily: false } });
    const call = calls.find((c) => c.path === "/functions/v1/ai-template")!;
    expect(call.body).toEqual({ mode: "create", description: "tanya ongkir", fields: ["kota"] });
    expect(call.headers.Authorization).toBe("Bearer a");
    expect(calls.filter((c) => c.path === "/functions/v1/ai-template").length).toBe(1);
  });

  it("says the quota is used up, with the quota for the page", async () => {
    await signedIn();
    respond(/^\/functions\/v1\/ai-template$/, { error: "quota_exceeded" }, "POST", 429);
    respond(/^\/rest\/v1\/rpc\/ai_quota$/, { limit: 5, remaining: 0, period: "total" }, "POST");

    const reply = await sendMessage({ type: "aiTemplate", input: { mode: "rewrite", style: "polite", title: "", body: "halo" } }, `${EXTENSION}manage.html`);

    expect(reply).toEqual({ ok: false, error: "Jatah AI sudah habis.", quota: { limit: 5, remaining: 0, daily: false } });
  });

  it("is not available to web pages", async () => {
    await signedIn();
    const reply = await sendMessage({ type: "aiQuota" }, "https://web.whatsapp.com/");
    expect(reply.ok).toBe(false);
    expect(calls.length).toBe(0);
  });
});

describe("account plan", () => {
  it("keeps the plan from my_plan with the profile, and the last one when it fails", async () => {
    await local.set({ session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "user-1", email: "x" } });
    respond(/^\/auth\/v1\/user$/, { id: "user-1", user_metadata: { full_name: "Budi" } });
    respond(/^\/rest\/v1\/rpc\/my_plan$/, { plan: "trial", trial_ends_at: "2026-10-12T08:00:00+00:00" }, "POST");

    expect((await sendMessage({ type: "syncNow" })).ok).toBe(true);
    expect(local.items().profile).toMatchObject({ fullName: "Budi", plan: { kind: "trial", trialEndsAt: Date.parse("2026-10-12T08:00:00Z") } });

    routes.unshift((call) => (call.path === "/rest/v1/rpc/my_plan" ? { status: 500, body: { message: "down" } } : undefined));
    expect((await sendMessage({ type: "syncNow" })).ok).toBe(true);
    expect(local.items().profile).toMatchObject({ plan: { kind: "trial" } });
  });
});

describe("Free plan", () => {
  it("does not sync, with a message saying the extension is for Pro", async () => {
    await local.set({ session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "user-1", email: "x" } });
    respond(/^\/auth\/v1\/user$/, { id: "user-1", user_metadata: { full_name: "Budi" } });
    respond(/^\/rest\/v1\/rpc\/my_plan$/, { plan: "free" }, "POST");

    const reply = await sendMessage({ type: "syncNow" });

    expect(reply).toEqual({ ok: false, error: "Chrome extension dan sync khusus paket Pro. Template dan kontak tetap aman di app HP." });
    expect(calls.some((c) => c.path.startsWith("/rest/v1/templates"))).toBe(false);
    expect(local.items().profile).toMatchObject({ plan: { kind: "free" } });
  });
});

describe("AI report", () => {
  it("stores a report in ai_reports, and refuses an unknown reason", async () => {
    await local.set({ session: { accessToken: "a", refreshToken: "r", expiresAt: Date.now() + 3_600_000, userId: "user-1", email: "x" } });
    respond(/^\/rest\/v1\/ai_reports$/, null, "POST", 201);
    const input = { mode: "create" as const, description: "tanya ongkir" };

    expect((await sendMessage({ type: "aiReport", input, result: "Halo", reason: "offensive" }, `${EXTENSION}manage.html`)).ok).toBe(true);
    const call = calls.find((c) => c.path === "/rest/v1/ai_reports")!;
    expect(call.body).toMatchObject({ mode: "create", input: "tanya ongkir", result: "Halo", reason: "offensive", source: "extension" });
    expect(call.headers.Prefer).toBe("return=minimal");

    const bad = await sendMessage({ type: "aiReport", input, result: "Halo", reason: "spam" as never }, `${EXTENSION}manage.html`);
    expect(bad.ok).toBe(false);
  });
});
