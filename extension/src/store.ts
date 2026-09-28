// Local data in chrome.storage.local, mirroring the Supabase tables (supabase/migrations).
// The background worker is the only writer of synced tables; content scripts read them.

import { rankTemplates, type TemplateEntry } from "./engine/trigger";
import type { FieldInput, TemplateInput } from "./engine/manage";
import { FIELD_TYPES, type FieldType } from "./engine/template";
import type { AiInput, AiQuota, AiReportReason, AiTemplate } from "./ai";
import { hasProFeatures, planAt, type AccountPlan } from "./engine/plan";

export const TABLES = ["templates", "contacts", "field_definitions"] as const;
export type TableName = (typeof TABLES)[number];

interface SyncColumns {
  id: string;
  updated_at: number;
  deleted: boolean;
  /** Local only: edited here and not uploaded yet. */
  dirty?: boolean;
}

export interface TemplateRow extends SyncColumns {
  title: string;
  shortcut: string;
  body: string;
  tags: string;
}

export interface ContactRow extends SyncColumns {
  name: string;
  /** E.164 numbers separated by newlines, as on Android. */
  phones: string;
  fields: Record<string, string>;
}

export interface FieldRow extends SyncColumns {
  field_key: string;
  label: string;
  type: string;
  /** Choice options separated by newlines. */
  options: string;
  /** Raw value used when a contact has none; empty for none. Missing on rows from older clients. */
  fallback?: string;
  /** Icon the user picked (a fieldIcons name); empty or missing means automatic. */
  icon?: string;
  position: number;
}

export interface RowTypes {
  templates: TemplateRow;
  contacts: ContactRow;
  field_definitions: FieldRow;
}

export type Table<T extends TableName> = Record<string, RowTypes[T]>;

export interface Session {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  userId: string;
  email: string;
}

export interface Settings {
  trigger: string;
  /** Hostnames where the extension stays quiet ("Aktif di situs ini" off). */
  disabledSites: string[];
}

export const DEFAULT_SETTINGS: Settings = { trigger: "/", disabledSites: [] };

/** Shown in the popup: the account's full name (Akun → Data pribadi, user_metadata.full_name) and its
 * "Ajak teman" code with how many friends signed up with it (my_referral). */
export interface Profile {
  fullName: string;
  referralCode?: string;
  referralJoined?: number;
  /** The account's plan from my_plan() (beta, trial, pro, free). */
  plan?: AccountPlan;
}

/**
 * Whether the extension may work for this account at [now]: sync and suggestions are for Pro
 * (PRD §9; beta and trial count). An unknown plan (not loaded yet) is not locked.
 */
export const extensionAllowed = (profile: Profile | undefined, now: number) => !profile?.plan || hasProFeatures(planAt(profile.plan, now));

/** Shown instead of syncing, and in the popup, when the plan is Free. */
export const EXTENSION_PRO_ONLY = "Chrome extension dan sync khusus paket Pro. Template dan kontak tetap aman di app HP.";

/** The link friends open to sign up for the beta; same as on scriptgo.id/konfirmasi. */
export const referralLink = (code: string) => `https://scriptgo.id/?ref=${code}`;

/** The WhatsApp message that shares [link]. */
export const referralShareText = (link: string) =>
  `Aku pakai ScriptGo buat balas chat pelanggan pakai template, jadi jauh lebih cepat. Ikut beta gratis: ${link}`;

/** The referral part of my_referral()'s answer, or nothing when it is malformed. */
export function referralOf(json: unknown): Pick<Profile, "referralCode" | "referralJoined"> {
  const r = json as { code?: unknown; joined?: unknown } | null;
  if (typeof r?.code !== "string" || !/^[a-z0-9]{6,12}$/.test(r.code)) return {};
  return { referralCode: r.code, referralJoined: Math.max(0, Number(r.joined) || 0) };
}

/** The full name in a Supabase user object: full_name, else the name Google gave. Like the app. */
export function fullNameOf(user: unknown): string {
  const meta = (user as { user_metadata?: Record<string, unknown> } | null)?.user_metadata ?? {};
  const pick = (v: unknown) => (typeof v === "string" ? v.trim() : "");
  return pick(meta.full_name) || pick(meta.name);
}

/** The popup header's name: the full name, else the part of the email before "@". */
export function displayName(profile: Profile | undefined, email: string): string {
  return profile?.fullName?.trim() || email.split("@")[0] || email;
}

export const tableKey = (table: TableName) => `table:${table}`;
export const cursorKey = (table: TableName) => `cursor:${table}`;
export const KEY_SESSION = "session";
export const KEY_LAST_SYNC = "lastSyncAt";
export const KEY_SETTINGS = "settings";
/** When the trigger or theme was last changed in this browser, and when they last matched the account (epoch ms). */
export const KEY_SETTINGS_EDITED_AT = "settingsEditedAt";
export const KEY_SETTINGS_SYNCED_AT = "settingsSyncedAt";
/** Template id → times inserted from this browser, for ranking. */
export const KEY_USAGE = "usage";
/** Characters inserted from this browser (counts only, never text). */
export const KEY_CHARS = "charsInserted";
/** PKCE verifier for the pending email link or Google sign-in. */
export const KEY_PKCE = "pkceVerifier";
export const KEY_PROFILE = "profile";

export async function readTable<T extends TableName>(table: T): Promise<Table<T>> {
  const key = tableKey(table);
  return ((await chrome.storage.local.get(key))[key] as Table<T> | undefined) ?? {};
}

export async function readSettings(): Promise<Settings> {
  const stored = (await chrome.storage.local.get(KEY_SETTINGS))[KEY_SETTINGS] as Partial<Settings> | undefined;
  return { ...DEFAULT_SETTINGS, ...stored };
}

// --- What the content script works with ---------------------------------------------------

export interface Template extends TemplateEntry {
  body: string;
}

export interface Contact {
  id: string;
  name: string;
  phones: string[];
  fields: Record<string, string>;
}

export interface FieldDefinition {
  key: string;
  label: string;
  type: FieldType;
  options: string[];
  /** Raw value used when a contact has none and the template has no fallback; empty for none. */
  fallback: string;
}

export interface LocalData {
  templates: Template[];
  contacts: Contact[];
  fields: FieldDefinition[];
  settings: Settings;
}

const lines = (s: string) => s.split("\n").filter((l) => l !== "");

export async function loadData(): Promise<LocalData> {
  const keys = [...TABLES.map(tableKey), KEY_SETTINGS, KEY_USAGE];
  const stored = await chrome.storage.local.get(keys);
  const usage = (stored[KEY_USAGE] as Record<string, number> | undefined) ?? {};
  const live = <T extends SyncColumns>(table: Record<string, T> | undefined) =>
    Object.values(table ?? {}).filter((r) => !r.deleted);

  return {
    templates: live(stored[tableKey("templates")] as Table<"templates">).map((t) => ({
      id: t.id,
      shortcut: t.shortcut,
      title: t.title,
      body: t.body,
      usageCount: usage[t.id] ?? 0,
    })),
    contacts: live(stored[tableKey("contacts")] as Table<"contacts">)
      .map((c) => ({ id: c.id, name: c.name, phones: lines(c.phones), fields: c.fields ?? {} }))
      .sort((a, b) => a.name.localeCompare(b.name, "id")),
    fields: live(stored[tableKey("field_definitions")] as Table<"field_definitions">)
      .sort((a, b) => a.position - b.position)
      .map((f) => ({
        key: f.field_key,
        label: f.label,
        type: (FIELD_TYPES as readonly string[]).includes(f.type) ? (f.type as FieldType) : "text",
        options: lines(f.options),
        fallback: f.fallback ?? "",
      })),
    settings: { ...DEFAULT_SETTINGS, ...(stored[KEY_SETTINGS] as Partial<Settings> | undefined) },
  };
}

export function suggestTemplates(data: LocalData, query: string): Template[] {
  return rankTemplates(query, data.templates, 6);
}

// --- Messages from content script and popup to the background worker ------------------------

export type Message =
  | { type: "sendCode"; email: string }
  | { type: "verifyCode"; email: string; code: string }
  /** Email and password, for accounts that set a password in the app (Akun → Kata sandi). */
  | { type: "passwordSignIn"; email: string; password: string }
  /** Google sign-in in a browser window (chrome.identity). */
  | { type: "googleSignIn" }
  /** The sign-in link from the email opened auth.html with this code. */
  | { type: "completeLink"; url: string }
  | { type: "signOut" }
  /** Pusat bantuan (help.html): a help request, idea, or bug report for support_messages. */
  | { type: "sendSupport"; kind: string; message: string; replyEmail: string }
  | { type: "syncNow" }
  /** Akun (account.html): the name on the account, a new password, or deleting the account. */
  | { type: "saveFullName"; fullName: string }
  | { type: "setPassword"; password: string }
  | { type: "deleteAccount" }
  | { type: "saveContactFields"; contactId: string; fields: Record<string, string> }
  /**
   * "+ Kontak baru" in the form: a contact with its name, number (E.164 or empty), and field values.
   * Replies with the contact's id; a number that already belongs to a contact returns that contact.
   */
  | { type: "createContact"; name: string; phone: string; fields: Record<string, string> }
  /** Template and field edits from the manage page (manage.html). */
  | { type: "saveTemplate"; template: TemplateInput }
  | { type: "deleteTemplate"; id: string }
  | { type: "saveField"; field: FieldInput }
  | { type: "deleteField"; id: string }
  | { type: "recordUse"; templateId: string; chars: number }
  /** AI help in the template editor: the uses left, or a template made or rewritten (ai.ts). */
  | { type: "aiQuota" }
  | { type: "aiTemplate"; input: AiInput }
  /** "Laporkan hasil ini" under an AI result (Google Play's AI content policy). */
  | { type: "aiReport"; input: AiInput; result: string; reason: AiReportReason }
  /** The content script announces a form it is about to open in an iframe. */
  | { type: "openForm"; nonce: string; request: FormRequest }
  /** The form iframe claims its request; each nonce works once. */
  | { type: "claimForm"; nonce: string };

/** What the variable form needs from the page that opened it. */
export interface FormRequest {
  templateId: string;
  /** The open WhatsApp chat, used to suggest a contact. */
  hint: { title: string | null; phone: string | null } | null;
}

export type Reply =
  | { ok: true; message?: string; request?: FormRequest; contactId?: string; template?: AiTemplate; quota?: AiQuota | null }
  | { ok: false; error: string; quota?: AiQuota | null };

export function send(message: Message): Promise<Reply> {
  return chrome.runtime.sendMessage(message);
}
