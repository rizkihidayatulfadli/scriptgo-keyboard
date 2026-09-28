// Background service worker: account sign-in and sync with Supabase (PRD 6.5, 6.6).
// The only code in the extension that talks to the network, and only to Supabase.
// Content scripts never send typed text anywhere.

import { AI_REPORT_REASONS, aiErrorText, aiReportRow, aiRequestBody, parseAiTemplate, parseQuota, type AiInput } from "./ai";
import { CHANNEL_DEFAULT_OPTIONS, CHANNEL_FIELD_ID, CHANNEL_KEY, nextUpdatedAt, shouldApplyRemote } from "./engine/contacts";
import { fieldError, fieldKeyFromLabel, normalizeTags, templateError, type FieldInput, type TemplateInput } from "./engine/manage";
import { challenge, newVerifier, parseRedirect } from "./engine/pkce";
import { parsePlan } from "./engine/plan";
import { decide, readRemote, writeRemote } from "./settingsSync";
import { KEY_THEME, themeOf } from "./theme";
import {
  cursorKey,
  DEFAULT_SETTINGS,
  EXTENSION_PRO_ONLY,
  extensionAllowed,
  fullNameOf,
  KEY_CHARS,
  KEY_LAST_SYNC,
  KEY_PKCE,
  KEY_PROFILE,
  KEY_SESSION,
  KEY_SETTINGS,
  KEY_SETTINGS_EDITED_AT,
  KEY_SETTINGS_SYNCED_AT,
  KEY_USAGE,
  readTable,
  referralOf,
  TABLES,
  tableKey,
  type FormRequest,
  type Message,
  type Profile,
  type Reply,
  type Session,
  type Settings,
  type TableName,
} from "./store";

/** Project `magic-keyboard` (Singapore). The publishable key is safe to ship; RLS protects the data. */
const SUPABASE_URL = "https://hsanqncbfurjhnshdpnc.supabase.co";
const PUBLISHABLE_KEY = "sb_publishable_8YEhzjHcC9OOFN0n7B1vUg_E7540P_A";
const BATCH = 500;
const SYNC_ALARM = "sync";

class SyncError extends Error {
  constructor(
    message: string,
    /** HTTP status, 0 when the server was not reached. */
    readonly status = 0,
    /** Supabase error_code, e.g. refresh_token_not_found. */
    readonly code = "",
  ) {
    super(message);
  }
}

/** Shown when the stored session cannot be refreshed any more; the popup then asks to sign in. */
const SESSION_ENDED = "Sesi kamu sudah berakhir. Masuk lagi untuk melanjutkan sinkron.";

let storageChain: Promise<unknown> = Promise.resolve();

/** Runs read-modify-write steps on stored tables one at a time, so sync and edits never overwrite each other. */
function locked<T>(fn: () => Promise<T>): Promise<T> {
  const result = storageChain.then(fn);
  storageChain = result.catch(() => undefined);
  return result;
}

// --- Auth ------------------------------------------------------------------------------------

async function getSession(): Promise<Session | null> {
  return ((await chrome.storage.local.get(KEY_SESSION))[KEY_SESSION] as Session | undefined) ?? null;
}

async function storeSession(json: Record<string, unknown>): Promise<Session> {
  const user = (json.user ?? {}) as { id?: string; email?: string };
  const previous = await getSession();
  const session: Session = {
    accessToken: String(json.access_token),
    refreshToken: String(json.refresh_token),
    expiresAt: Date.now() + Number(json.expires_in ?? 3600) * 1000,
    userId: user.id ?? previous?.userId ?? "",
    email: user.email ?? previous?.email ?? "",
  };
  await chrome.storage.local.set({ [KEY_SESSION]: session });
  return session;
}

/** A new PKCE verifier (kept until the code comes back) and its challenge. */
async function startPkce(): Promise<string> {
  const verifier = newVerifier();
  await chrome.storage.local.set({ [KEY_PKCE]: verifier });
  return challenge(verifier);
}

/**
 * Sends one email with a 6-digit code and a sign-in link. The link comes back to auth.html in this
 * extension (PKCE), so it works when the email is opened in this browser.
 */
async function sendCode(email: string): Promise<void> {
  const codeChallenge = await startPkce();
  const redirect = encodeURIComponent(chrome.runtime.getURL("auth.html"));
  await request(
    "POST",
    `/auth/v1/otp?redirect_to=${redirect}`,
    { email: email.trim(), create_user: true, code_challenge: codeChallenge, code_challenge_method: "s256" },
    false,
  );
}

async function verifyCode(email: string, code: string): Promise<void> {
  const json = await request("POST", "/auth/v1/verify", { type: "email", email: email.trim(), token: code.trim() }, false);
  await signIn(json as Record<string, unknown>);
}

async function passwordSignIn(email: string, password: string): Promise<void> {
  const json = await request("POST", "/auth/v1/token?grant_type=password", { email: email.trim(), password }, false);
  await signIn(json as Record<string, unknown>);
}

/** Exchanges the code from a sign-in redirect (email link or Google) for a session. */
async function completeRedirect(url: string): Promise<void> {
  const result = parseRedirect(url);
  if (result.kind === "error") throw new SyncError(result.message);
  if (result.kind !== "code") throw new SyncError("Link masuk tidak lengkap. Minta kode baru.");
  const verifier = (await chrome.storage.local.get(KEY_PKCE))[KEY_PKCE] as string | undefined;
  if (!verifier) throw new SyncError("Link ini dibuat di perangkat lain. Masukkan kode 6 digit dari email.");
  const json = await request("POST", "/auth/v1/token?grant_type=pkce", { auth_code: result.code, code_verifier: verifier }, false);
  await chrome.storage.local.remove(KEY_PKCE);
  await signIn(json as Record<string, unknown>);
}

/** Google sign-in in a browser window; Supabase redirects back to this extension's chromiumapp.org URL. */
async function googleSignIn(): Promise<void> {
  const codeChallenge = await startPkce();
  const redirect = chrome.identity.getRedirectURL();
  const url =
    `${SUPABASE_URL}/auth/v1/authorize?provider=google&redirect_to=${encodeURIComponent(redirect)}` +
    `&code_challenge=${codeChallenge}&code_challenge_method=s256`;
  let returned: string | undefined;
  try {
    returned = await chrome.identity.launchWebAuthFlow({ url, interactive: true });
  } catch {
    throw new SyncError("Masuk dengan Google dibatalkan.");
  }
  if (!returned) throw new SyncError("Masuk dengan Google dibatalkan.");
  await completeRedirect(returned);
}

async function signIn(json: Record<string, unknown>): Promise<void> {
  await clearLocalData(); // start clean so another account's data never mixes in
  await storeSession(json);
  await loadProfile().catch(() => undefined);
}

/** The account's full name, "Ajak teman" link, and plan, for the popup and Akun. */
async function loadProfile(): Promise<void> {
  const [user, referral, plan] = await Promise.all([
    request("GET", "/auth/v1/user", null, true),
    request("POST", "/rest/v1/rpc/my_referral", {}, true).catch(() => null),
    request("POST", "/rest/v1/rpc/my_plan", {}, true).catch(() => null),
  ]);
  const stored = (await chrome.storage.local.get(KEY_PROFILE))[KEY_PROFILE] as Profile | undefined;
  // A plan that failed to load keeps the last known one.
  const known = parsePlan(plan) ?? stored?.plan;
  const profile: Profile = { fullName: fullNameOf(user), ...referralOf(referral), ...(known ? { plan: known } : {}) };
  await chrome.storage.local.set({ [KEY_PROFILE]: profile });
}

/** Akun → Data pribadi: saves the full name on the account (the app shows the same one). */
async function saveFullName(fullName: string): Promise<void> {
  const name = fullName.trim().replace(/\s+/g, " ").slice(0, 120);
  if (!name) throw new SyncError("Nama lengkap masih kosong.");
  const user = await request("PUT", "/auth/v1/user", { data: { full_name: name } }, true);
  const stored = (await chrome.storage.local.get(KEY_PROFILE))[KEY_PROFILE] as Profile | undefined;
  await chrome.storage.local.set({ [KEY_PROFILE]: { ...stored, fullName: fullNameOf(user) || name } });
}

/** Akun → Kata sandi: sets or changes the password (then usable in "Masuk dengan kata sandi"). */
async function setPassword(password: string): Promise<void> {
  if (password.length < 8) throw new SyncError("Kata sandi minimal 8 karakter.");
  try {
    await request("PUT", "/auth/v1/user", { password }, true);
  } catch (e) {
    if (!(e instanceof SyncError)) throw e;
    if (e.code === "weak_password") throw new SyncError("Kata sandi terlalu lemah. Pakai minimal 8 karakter, campur huruf dan angka.");
    if (e.code === "same_password") throw new SyncError("Kata sandi baru sama dengan yang lama.");
    if (e.code === "reauthentication_needed") throw new SyncError("Demi keamanan, keluar lalu masuk lagi dengan kode email, kemudian ubah kata sandi.");
    throw e;
  }
}

/**
 * Akun → Hapus akun: deletes the account and everything synced to it (delete_account, as in the
 * app), then clears this browser. Templates and contacts on other devices stay on those devices.
 */
async function deleteAccount(): Promise<void> {
  await request("POST", "/rest/v1/rpc/delete_account", {}, true);
  await chrome.storage.local.remove(KEY_SESSION);
  await clearLocalData();
}

/** Sends a Pusat bantuan message; signed out it goes anonymously, with the reply address given. */
async function sendSupport(kind: string, message: string, replyEmail: string): Promise<void> {
  if (!["help", "idea", "bug"].includes(kind)) throw new SyncError("Jenis pesan tidak dikenal.");
  const text = message.trim().slice(0, 4000);
  if (!text) throw new SyncError("Pesan masih kosong.");
  const signedIn = !!(await getSession());
  const row = {
    kind,
    message: text,
    reply_email: signedIn ? "" : replyEmail.trim().slice(0, 254),
    source: "extension",
    app_version: chrome.runtime.getManifest().version,
    device: navigator.userAgent.replace(/^Mozilla\/5\.0 /, "").slice(0, 120),
    language: navigator.language.slice(0, 10),
  };
  try {
    await request("POST", "/rest/v1/support_messages", row, signedIn, { Prefer: "return=minimal" });
  } catch (e) {
    if (e instanceof SyncError && /Too many|rate_limited/.test(e.message)) {
      throw new SyncError("Pesan terlalu banyak dalam waktu singkat. Coba lagi beberapa menit lagi.");
    }
    throw e;
  }
}

async function signOut(): Promise<void> {
  if (await getSession()) await request("POST", "/auth/v1/logout", null, true).catch(() => undefined);
  await chrome.storage.local.remove(KEY_SESSION);
  await clearLocalData();
}

function clearLocalData(): Promise<void> {
  return locked(() =>
    chrome.storage.local.remove([
      ...TABLES.map(tableKey),
      ...TABLES.map(cursorKey),
      KEY_LAST_SYNC,
      KEY_USAGE,
      KEY_PROFILE,
      // A new sign-in takes the account's trigger and theme, like a fresh install of the app.
      KEY_SETTINGS_EDITED_AT,
      KEY_SETTINGS_SYNCED_AT,
    ]),
  );
}

let refreshing: Promise<string> | null = null;

/**
 * A valid access token, refreshed shortly before it expires. Only one refresh runs at a time:
 * Supabase rotates the refresh token, so two parallel refreshes would spend it twice and end the
 * session. A refresh token the server no longer knows ends the session here (sign in again)
 * instead of failing every sync with a raw error.
 */
async function accessToken(): Promise<string> {
  const session = await getSession();
  if (!session) throw new SyncError("Belum masuk");
  if (Date.now() < session.expiresAt - 60_000) return session.accessToken;
  refreshing ??= (async () => {
    try {
      const json = await request("POST", "/auth/v1/token?grant_type=refresh_token", { refresh_token: session.refreshToken }, false);
      return (await storeSession(json as Record<string, unknown>)).accessToken;
    } catch (e) {
      if (e instanceof SyncError && (e.status === 400 || e.status === 401)) {
        await chrome.storage.local.remove([KEY_SESSION, KEY_PROFILE]);
        throw new SyncError(SESSION_ENDED, e.status, e.code);
      }
      throw e;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

// --- Sync ------------------------------------------------------------------------------------

let running: Promise<number> | null = null;

/** Uploads local edits, then downloads changes. Returns how many local records were replaced. */
function syncNow(): Promise<number> {
  running ??= (async () => {
    try {
      // Sync is for Pro (the server refuses it too, can_sync()); the plan comes with the profile.
      const profile = (await chrome.storage.local.get(KEY_PROFILE))[KEY_PROFILE] as Profile | undefined;
      if (!extensionAllowed(profile, Date.now())) throw new SyncError(EXTENSION_PRO_ONLY);
      for (const table of TABLES) await push(table);
      let pulled = 0;
      for (const table of TABLES) pulled += await pull(table);
      await syncSettings();
      await chrome.storage.local.set({ [KEY_LAST_SYNC]: Date.now() });
      return pulled;
    } finally {
      running = null;
    }
  })();
  return running;
}

/**
 * Trigger character and theme follow the account through user_settings, the row the app syncs its
 * settings in (SettingsSyncer). Edits made here since the last sync are written into the row; a
 * newer row (e.g. the trigger changed on the phone) is applied here.
 */
async function syncSettings(): Promise<void> {
  const stored = await chrome.storage.local.get([KEY_SETTINGS, KEY_THEME, KEY_SETTINGS_EDITED_AT, KEY_SETTINGS_SYNCED_AT]);
  const rows = (await request("GET", "/rest/v1/user_settings?select=data,updated_at", null, true)) as Array<{ data?: unknown; updated_at?: number }>;
  const row = rows[0] ?? null;
  const remoteAt = row ? Number(row.updated_at ?? 0) : null;
  const settings = { ...DEFAULT_SETTINGS, ...(stored[KEY_SETTINGS] as Partial<Settings> | undefined) };
  const theme = themeOf(stored[KEY_THEME]);
  const syncedAt = Number(stored[KEY_SETTINGS_SYNCED_AT] ?? 0);
  switch (decide(Number(stored[KEY_SETTINGS_EDITED_AT] ?? 0), syncedAt, remoteAt)) {
    case "push": {
      const at = Math.max(Date.now(), (remoteAt ?? 0) + 1);
      const data = writeRemote(row?.data, { trigger: settings.trigger, theme });
      await request("POST", "/rest/v1/user_settings", { data, updated_at: at }, true, { Prefer: "resolution=merge-duplicates,return=minimal" });
      await chrome.storage.local.set({ [KEY_SETTINGS_SYNCED_AT]: at });
      break;
    }
    case "pull": {
      const remote = readRemote(row?.data);
      await chrome.storage.local.set({
        [KEY_SETTINGS]: { ...settings, trigger: remote.trigger ?? settings.trigger },
        [KEY_THEME]: remote.theme ?? theme,
        [KEY_SETTINGS_SYNCED_AT]: remoteAt,
      });
      break;
    }
    case "none":
      break;
  }
}

async function push(table: TableName): Promise<void> {
  const rows = Object.values(await readTable(table)).filter((r) => r.dirty);
  for (let i = 0; i < rows.length; i += BATCH) {
    const batch = rows.slice(i, i + BATCH).map(({ dirty: _dirty, ...row }) => row);
    await request("POST", `/rest/v1/${table}`, batch, true, { Prefer: "resolution=merge-duplicates,return=minimal" });
    // Mark clean unless edited again meanwhile.
    await locked(async () => {
      const current = await readTable(table);
      for (const row of batch) {
        const local = current[row.id];
        if (local && local.updated_at === row.updated_at) local.dirty = false;
      }
      await chrome.storage.local.set({ [tableKey(table)]: current });
    });
  }
}

async function pull(table: TableName): Promise<number> {
  let applied = 0;
  for (;;) {
    const cursor = (await chrome.storage.local.get(cursorKey(table)))[cursorKey(table)] as string | undefined;
    const filter = cursor ? `&synced_at=gt.${encodeURIComponent(cursor)}` : "";
    const rows = (await request(
      "GET",
      `/rest/v1/${table}?select=*&order=synced_at.asc&limit=${BATCH}${filter}`,
      null,
      true,
    )) as Array<Record<string, unknown>>;
    if (rows.length === 0) return applied;

    applied += await locked(async () => {
      const local = await readTable(table);
      let count = 0;
      for (const { user_id: _user, synced_at: _synced, ...row } of rows) {
        const id = String(row.id);
        const existing = local[id];
        const meta = existing ? { updatedAt: existing.updated_at, dirty: !!existing.dirty } : null;
        if (!shouldApplyRemote(meta, Number(row.updated_at))) continue;
        (local as Record<string, unknown>)[id] = { ...row, updated_at: Number(row.updated_at), dirty: false };
        count++;
      }
      await chrome.storage.local.set({
        [tableKey(table)]: local,
        [cursorKey(table)]: String(rows[rows.length - 1].synced_at),
      });
      return count;
    });
    if (rows.length < BATCH) return applied;
  }
}

/** Saves form values back to a contact (the "Simpan ke kontak" checkbox) and starts uploading them. */
async function saveContactFields(contactId: string, fields: Record<string, string>): Promise<void> {
  await locked(async () => {
    const contacts = await readTable("contacts");
    const contact = contacts[contactId];
    if (!contact || contact.deleted) throw new SyncError("Kontak tidak ditemukan");
    contact.fields = { ...contact.fields, ...fields };
    contact.updated_at = nextUpdatedAt(contact.updated_at, Date.now());
    contact.dirty = true;
    await chrome.storage.local.set({ [tableKey("contacts")]: contacts });
  });
  // Upload in the background; the form is waiting. A failed upload is retried on the next sync.
  if (await getSession()) void syncNow().catch(() => undefined);
}

/**
 * Creates a contact from the form's "+ Kontak baru" and, when missing, the built-in Channel field
 * (same id and content as the app creates, so the two merge on sync). A number that already
 * belongs to a contact returns that contact instead of a duplicate.
 */
async function createContact(name: string, phone: string, fields: Record<string, string>): Promise<string> {
  const id = await locked(async () => {
    const trimmed = name.trim().slice(0, 120);
    if (!trimmed && !phone) throw new SyncError("Isi nama atau nomor");
    if (phone && !/^\+[1-9]\d{6,14}$/.test(phone)) throw new SyncError("Nomor belum benar");
    const contacts = await readTable("contacts");
    const existing = phone ? Object.values(contacts).find((c) => !c.deleted && c.phones.split("\n").includes(phone)) : undefined;
    if (existing) return existing.id;
    const id = crypto.randomUUID();
    const clean = Object.fromEntries(Object.entries(fields).filter(([k, v]) => /^[a-z0-9_]{1,40}$/.test(k) && v.trim()).map(([k, v]) => [k, v.trim().slice(0, 500)]));
    contacts[id] = { id, name: trimmed || phone, phones: phone, fields: clean, updated_at: Date.now(), deleted: false, dirty: true };
    const defs = await readTable("field_definitions");
    const hasChannel = Object.values(defs).some((f) => f.field_key === CHANNEL_KEY && !f.deleted) || defs[CHANNEL_FIELD_ID];
    if (!hasChannel) {
      defs[CHANNEL_FIELD_ID] = {
        id: CHANNEL_FIELD_ID, field_key: CHANNEL_KEY, label: "Channel", type: "choice",
        options: CHANNEL_DEFAULT_OPTIONS.join("\n"), fallback: "", position: -1, updated_at: 1, deleted: false, dirty: true,
      };
    }
    await chrome.storage.local.set({ [tableKey("contacts")]: contacts, [tableKey("field_definitions")]: defs });
    return id;
  });
  await uploadSoon();
  return id;
}

/** Starts uploading local edits; a failed upload is retried on the next sync. */
async function uploadSoon(): Promise<void> {
  if (await getSession()) void syncNow().catch(() => undefined);
}

/** Saves a template from the manage page. Returns the error to show, or null. */
async function saveTemplate(input: TemplateInput): Promise<string | null> {
  const trigger = ((await chrome.storage.local.get(KEY_SETTINGS))[KEY_SETTINGS] as { trigger?: string } | undefined)?.trigger ?? "/";
  const error = await locked(async () => {
    const templates = await readTable("templates");
    const live = Object.values(templates).filter((t) => !t.deleted);
    const problem = templateError(input, live, trigger);
    if (problem) return problem;
    const existing = input.id ? templates[input.id] : undefined;
    const id = existing?.id ?? crypto.randomUUID();
    templates[id] = {
      id,
      title: input.title.trim(),
      shortcut: input.shortcut,
      body: input.body,
      tags: input.tags ? normalizeTags(input.tags).join(",") : (existing?.tags ?? ""),
      updated_at: nextUpdatedAt(existing?.updated_at ?? null, Date.now()),
      deleted: false,
      dirty: true,
    };
    await chrome.storage.local.set({ [tableKey("templates")]: templates });
    return null;
  });
  if (!error) await uploadSoon();
  return error;
}

/** Saves a custom field from the manage page. A new field gets a key from its label, fixed after. */
async function saveField(input: FieldInput): Promise<string | null> {
  const error = await locked(async () => {
    const fields = await readTable("field_definitions");
    const problem = fieldError(input);
    if (problem) return problem;
    const live = Object.values(fields).filter((f) => !f.deleted);
    const existing = input.id ? fields[input.id] : undefined;
    // The built-in Channel field keeps the app's fixed id, key and type, so both copies merge on sync.
    const channel = input.id === CHANNEL_FIELD_ID || existing?.field_key === CHANNEL_KEY;
    const id = existing?.id ?? (channel ? CHANNEL_FIELD_ID : crypto.randomUUID());
    fields[id] = {
      id,
      field_key: existing?.field_key ?? (channel ? CHANNEL_KEY : fieldKeyFromLabel(input.label, live.map((f) => f.field_key))),
      label: channel ? (existing?.label ?? "Channel") : input.label.trim(),
      type: channel ? "choice" : input.type,
      options: input.options.join("\n"),
      fallback: (input.fallback ?? "").trim().slice(0, 200),
      icon: (input.icon ?? existing?.icon ?? "").slice(0, 40),
      position: existing?.position ?? (channel ? -1 : live.reduce((max, f) => Math.max(max, f.position + 1), 0)),
      updated_at: nextUpdatedAt(existing?.updated_at ?? null, Date.now()),
      deleted: false,
      dirty: true,
    };
    await chrome.storage.local.set({ [tableKey("field_definitions")]: fields });
    return null;
  });
  if (!error) await uploadSoon();
  return error;
}

/**
 * Deletes a template or field as a tombstone, like the app. A deleted field's values stay on
 * contacts, so adding the same field again brings them back.
 */
async function deleteRow(table: "templates" | "field_definitions", id: string): Promise<void> {
  await locked(async () => {
    const rows = await readTable(table);
    const row = rows[id];
    if (!row) return;
    row.deleted = true;
    row.dirty = true;
    row.updated_at = nextUpdatedAt(row.updated_at, Date.now());
    await chrome.storage.local.set({ [tableKey(table)]: rows });
  });
  await uploadSoon();
}

function recordUse(templateId: string, chars: number): Promise<void> {
  return locked(() => addUse(templateId, chars));
}

async function addUse(templateId: string, chars: number): Promise<void> {
  const stored = await chrome.storage.local.get([KEY_USAGE, KEY_CHARS]);
  const usage = (stored[KEY_USAGE] as Record<string, number> | undefined) ?? {};
  usage[templateId] = (usage[templateId] ?? 0) + 1;
  const total = Number(stored[KEY_CHARS] ?? 0) + Math.max(0, chars);
  await chrome.storage.local.set({ [KEY_USAGE]: usage, [KEY_CHARS]: total });
}

// --- AI help (Edge Function ai-template, same as the app) -------------------------------------

async function aiTemplate(input: AiInput): Promise<Reply> {
  // Field keys only, so the model reuses {kota}; values and contacts never leave the browser.
  const fields = Object.values(await readTable("field_definitions"))
    .filter((f) => !f.deleted)
    .map((f) => f.field_key);
  try {
    // One call only: the function already retries the model, and a second call would take another use.
    const json = await request("POST", "/functions/v1/ai-template", aiRequestBody(input, fields), true);
    const template = parseAiTemplate(json);
    const quota = parseQuota((json as { quota?: unknown } | null)?.quota);
    return template ? { ok: true, template, quota } : { ok: false, error: aiErrorText(502), quota };
  } catch (e) {
    if (!(e instanceof SyncError) || e.message === SESSION_ENDED) throw e;
    const quota = await aiQuota().catch(() => null);
    // Status 0: not signed in or no connection; the message already says which.
    return { ok: false, error: e.status === 0 ? e.message : aiErrorText(e.status), quota };
  }
}

async function aiQuota() {
  return parseQuota(await request("POST", "/rest/v1/rpc/ai_quota", {}, true));
}

// --- HTTP ------------------------------------------------------------------------------------

async function request(
  method: string,
  path: string,
  body: unknown,
  auth: boolean,
  extraHeaders: Record<string, string> = {},
): Promise<unknown> {
  const headers: Record<string, string> = {
    apikey: PUBLISHABLE_KEY,
    "Content-Type": "application/json",
    Accept: "application/json",
    ...extraHeaders,
  };
  if (auth) headers.Authorization = `Bearer ${await accessToken()}`;
  let response: Response;
  try {
    response = await fetch(SUPABASE_URL + path, {
      method,
      headers,
      body: body === null ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new SyncError("Tidak bisa terhubung ke server. Periksa koneksi internet.");
  }
  const text = await response.text();
  if (!response.ok) throw new SyncError(errorMessage(response.status, text), response.status, errorCode(text));
  return text ? JSON.parse(text) : null;
}

function errorCode(body: string): string {
  try {
    return String((JSON.parse(body) as { error_code?: unknown }).error_code ?? "");
  } catch {
    return "";
  }
}

function errorMessage(status: number, body: string): string {
  let json: Record<string, unknown> = {};
  try {
    json = JSON.parse(body);
  } catch {
    // not JSON
  }
  const code = String(json.error_code ?? "");
  if (code === "otp_expired") return "Kode salah atau sudah kedaluwarsa. Minta kode baru.";
  // Only the password grant answers invalid_credentials.
  if (code === "invalid_credentials") return "Email atau kata sandi salah. Lupa? Masuk dengan kode email.";
  if (code.startsWith("flow_state") || code === "bad_code_verifier") return "Link masuk sudah kedaluwarsa atau sudah dipakai. Minta kode baru.";
  if (code === "provider_disabled" || code === "validation_failed") return "Masuk dengan Google belum diaktifkan. Pakai email dulu.";
  if (code === "over_email_send_rate_limit" || status === 429) return "Terlalu banyak permintaan. Coba lagi beberapa menit lagi.";
  if (status === 401 || status === 403) return "Sesi berakhir. Silakan masuk lagi.";
  return `Gagal (${status}): ${String(json.msg || json.message || body.slice(0, 200))}`;
}

// --- Wiring ----------------------------------------------------------------------------------

const formKey = (nonce: string) => `form:${nonce}`;

async function handle(message: Message, sender: chrome.runtime.MessageSender): Promise<Reply> {
  // Only the extension's own pages may sign in, sync, or claim a form; content scripts run inside
  // web pages and only announce forms and usage.
  const fromExtensionPage = sender.url?.startsWith(chrome.runtime.getURL("")) ?? false;
  const contentScriptAllowed = message.type === "openForm" || message.type === "recordUse";
  if (!fromExtensionPage && !contentScriptAllowed) return { ok: false, error: "Tidak diizinkan" };

  switch (message.type) {
    case "sendCode":
      await sendCode(message.email);
      return { ok: true };
    case "verifyCode": {
      await verifyCode(message.email, message.code);
      const pulled = await syncNow();
      return { ok: true, message: `Berhasil masuk. ${pulled} data diunduh.` };
    }
    case "passwordSignIn": {
      await passwordSignIn(message.email, message.password);
      const pulled = await syncNow();
      return { ok: true, message: `Berhasil masuk. ${pulled} data diunduh.` };
    }
    case "googleSignIn": {
      await googleSignIn();
      const pulled = await syncNow();
      return { ok: true, message: `Berhasil masuk. ${pulled} data diunduh.` };
    }
    case "completeLink": {
      await completeRedirect(message.url);
      const pulled = await syncNow();
      return { ok: true, message: `Berhasil masuk. ${pulled} data diunduh.` };
    }
    case "sendSupport":
      await sendSupport(message.kind, message.message, message.replyEmail);
      return { ok: true, message: "Terkirim." };
    case "signOut":
      await signOut();
      return { ok: true, message: "Kamu sudah keluar. Data di browser ini dihapus." };
    case "saveFullName":
      await saveFullName(message.fullName);
      return { ok: true, message: "Nama disimpan." };
    case "setPassword":
      await setPassword(message.password);
      return { ok: true, message: "Kata sandi disimpan. Sekarang kamu juga bisa masuk dengan email dan kata sandi." };
    case "deleteAccount":
      await deleteAccount();
      return { ok: true, message: "Akun dan semua data sinkronnya sudah dihapus." };
    case "syncNow": {
      // A profile that fails to load does not stop the sync, but an ended session does.
      await loadProfile().catch((e) => {
        if (e instanceof SyncError && e.message === SESSION_ENDED) throw e;
      });
      const pulled = await syncNow();
      return { ok: true, message: pulled > 0 ? `Sinkron selesai, ${pulled} data diperbarui.` : "Sinkron selesai." };
    }
    case "createContact":
      return { ok: true, contactId: await createContact(message.name, message.phone, message.fields) };
    case "saveContactFields":
      await saveContactFields(message.contactId, message.fields);
      return { ok: true };
    case "recordUse":
      await recordUse(message.templateId, message.chars);
      return { ok: true };
    case "saveTemplate": {
      const error = await saveTemplate(message.template);
      return error ? { ok: false, error } : { ok: true, message: "Template disimpan." };
    }
    case "deleteTemplate":
      await deleteRow("templates", message.id);
      return { ok: true, message: "Template dihapus." };
    case "saveField": {
      const error = await saveField(message.field);
      return error ? { ok: false, error } : { ok: true, message: "Field disimpan." };
    }
    case "deleteField": {
      const row = (await readTable("field_definitions"))[message.id];
      if (message.id === CHANNEL_FIELD_ID || row?.field_key === CHANNEL_KEY) return { ok: false, error: "Field Channel bawaan tidak bisa dihapus." };
      await deleteRow("field_definitions", message.id);
      return { ok: true, message: "Field dihapus." };
    }
    case "aiQuota":
      return { ok: true, quota: await aiQuota() };
    case "aiTemplate":
      return aiTemplate(message.input);
    case "aiReport":
      if (!AI_REPORT_REASONS.some(([reason]) => reason === message.reason)) return { ok: false, error: "Alasan tidak dikenal." };
      await request("POST", "/rest/v1/ai_reports", aiReportRow(message.input, message.result, message.reason), true, { Prefer: "return=minimal" });
      return { ok: true, message: "Terima kasih, laporanmu terkirim. Kami pakai untuk memperbaiki AI." };
    case "openForm":
      await chrome.storage.session.set({ [formKey(message.nonce)]: message.request });
      return { ok: true };
    case "claimForm": {
      const key = formKey(message.nonce);
      const request = (await chrome.storage.session.get(key))[key] as FormRequest | undefined;
      await chrome.storage.session.remove(key);
      return request ? { ok: true, request } : { ok: false, error: "Form tidak valid" };
    }
  }
}

chrome.runtime.onMessage.addListener((message: Message, sender, sendResponse) => {
  if (sender.id !== chrome.runtime.id) return false;
  handle(message, sender).then(sendResponse, (e: unknown) =>
    sendResponse({ ok: false, error: e instanceof Error ? e.message : "Terjadi kesalahan" } satisfies Reply),
  );
  return true; // reply asynchronously
});

async function syncIfSignedIn(): Promise<void> {
  if (!(await getSession())) return;
  // Also after an update, so the popup picks up the full name (older versions stored the business name).
  await loadProfile().catch(() => undefined);
  await syncNow().catch(() => undefined);
}

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === SYNC_ALARM) void syncIfSignedIn();
});
chrome.runtime.onInstalled.addListener((details) => {
  void chrome.alarms.create(SYNC_ALARM, { periodInMinutes: 15 });
  void syncIfSignedIn();
  // First install: the welcome tab walks through signing in, pinning, and a first try (mockup 6a).
  if (details?.reason === "install") void chrome.tabs?.create({ url: chrome.runtime.getURL("welcome.html") });
  // Chrome does not put content scripts into tabs that were already open. Without this, those tabs
  // need a reload before suggestions work, and after an update they keep the old copy (old trigger).
  void injectIntoOpenTabs();
});

/** Adds the current content script to every open web page (the old copy, if any, goes quiet). */
async function injectIntoOpenTabs(): Promise<void> {
  const tabs = await chrome.tabs?.query({ url: ["http://*/*", "https://*/*"] }).catch(() => []);
  for (const tab of tabs ?? []) {
    if (tab.id === undefined || tab.discarded) continue;
    await chrome.scripting?.executeScript({ target: { tabId: tab.id, allFrames: true }, files: ["content.js"] }).catch(() => undefined);
  }
}
chrome.runtime.onStartup.addListener(() => void syncIfSignedIn());
