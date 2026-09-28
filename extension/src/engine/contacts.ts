// Port of engine/.../contacts (PhoneNumbers, TemplateForm, SyncMerge), plus WhatsApp Web contact matching.

import { getCountryCallingCode, parsePhoneNumberFromString, type CountryCode } from "libphonenumber-js";
import {
  formatFieldValue,
  prefillValue,
  render,
  type FieldType,
  type LocalDateTime,
  type ParsedTemplate,
  type RenderResult,
  type Variable,
} from "./template";
import type { ChatHint } from "./whatsapp";

export type { ChatHint } from "./whatsapp";

const has = (o: object, key: string) => Object.prototype.hasOwnProperty.call(o, key);

export const DEFAULT_REGION: CountryCode = "ID";

/**
 * Returns `raw` as E.164 (`+6281234567890`), or null when it is not a possible phone number.
 * Accepts local (`0812-3456-7890`), international (`+62 812…`) and bare country code (`62812…`) forms.
 */
export function normalizePhone(raw: string, region: CountryCode = DEFAULT_REGION): string | null {
  const cleaned = raw.trim();
  if ((cleaned.match(/\d/g) ?? []).length < 6) return null;
  try {
    const number = parsePhoneNumberFromString(withPlusIfCountryCode(cleaned, region), region);
    return number && number.isPossible() ? number.number : null;
  } catch {
    return null;
  }
}

/** Indonesian exports often write `62812…` without the plus sign; treat that as international. */
function withPlusIfCountryCode(raw: string, region: CountryCode): string {
  if (raw.startsWith("+") || raw.startsWith("0")) return raw;
  const digits = raw.replace(/\D/g, "");
  return digits.startsWith(getCountryCallingCode(region)) ? `+${digits}` : raw;
}

/** A contact as the form sees it; `fields` hold raw stored values keyed by field key. */
export interface ContactData {
  name: string;
  phones: string[];
  fields: Record<string, string>;
}

/**
 * Initial raw form values: the contact's value, else the default, else the first option, else the
 * field's fallback (`fieldFallbacks`, raw values keyed by field key), else empty.
 */
export function prefillForm(
  variables: readonly Variable[],
  contact: ContactData | null,
  fieldTypes: Record<string, FieldType>,
  fieldFallbacks: Record<string, string> = {},
): Record<string, string> {
  const values: Record<string, string> = {};
  for (const variable of variables) {
    const key = variable.key;
    let fromContact: string | null = null;
    if (contact) {
      if (key === "nama") fromContact = contact.name;
      else if (key === "nomor") fromContact = contact.phones.length ? formatFieldValue("phone", contact.phones[0]) : null;
      else if (has(fieldTypes, key) && has(contact.fields, key)) fromContact = contact.fields[key];
    }
    values[key] = fromContact || prefillValue(variable, null, fieldFallbacks);
  }
  return values;
}

/**
 * Keys whose prefilled value is a fallback, the template's (`{kota|Bandung}`) or the field's,
 * because the contact has nothing for them, so the form can say so. Only variables a contact can
 * fill count: `nama`, `nomor`, and custom fields. Port of TemplateForm.fallbackKeys.
 */
export function fallbackKeys(
  variables: readonly Variable[],
  contact: ContactData | null,
  fieldTypes: Record<string, FieldType>,
  fieldFallbacks: Record<string, string> = {},
): Set<string> {
  const keys = new Set<string>();
  if (!contact) return keys;
  for (const variable of variables) {
    const key = variable.key;
    let fromContact: string | undefined;
    if (key === "nama") fromContact = contact.name;
    else if (key === "nomor") fromContact = contact.phones[0];
    else if (has(fieldTypes, key)) fromContact = has(contact.fields, key) ? contact.fields[key] : undefined;
    else continue;
    const fromField = variable.default === undefined && variable.options === undefined && has(fieldFallbacks, key) && fieldFallbacks[key] !== "";
    if (!fromContact && (variable.default || fromField)) keys.add(key);
  }
  return keys;
}

/**
 * Renders with the form's raw values, formatting custom-field values by their type.
 * `currencies` maps a currency field's key to its currency code; missing means IDR.
 */
export function renderForm(
  parsed: ParsedTemplate,
  values: Record<string, string>,
  fieldTypes: Record<string, FieldType>,
  now: LocalDateTime,
  currencies: Record<string, string> = {},
): RenderResult {
  const inputs: Record<string, string> = {};
  for (const [key, raw] of Object.entries(values)) {
    inputs[key] = has(fieldTypes, key) ? formatFieldValue(fieldTypes[key], raw, has(currencies, key) ? currencies[key] : null) : raw;
  }
  return render(parsed, null, inputs, now);
}

/**
 * Custom-field values to save back to the contact: non-empty form values for field keys that
 * differ from what the contact has. `nama` and `nomor` are never written back, and neither is a
 * field's fallback left as is for a contact without a value.
 */
export function changedFields(
  values: Record<string, string>,
  contact: ContactData,
  fieldTypes: Record<string, FieldType>,
  fieldFallbacks: Record<string, string> = {},
): Record<string, string> {
  const changes: Record<string, string> = {};
  for (const [key, raw] of Object.entries(values)) {
    const stored = has(contact.fields, key) ? contact.fields[key] : undefined;
    const keptFallback = !stored && has(fieldFallbacks, key) && raw === fieldFallbacks[key];
    if (has(fieldTypes, key) && raw !== "" && stored !== raw && !keptFallback) changes[key] = raw;
  }
  return changes;
}

// --- Sync ------------------------------------------------------------------------------------

/** Sync state of one record: when it was last edited (client epoch ms) and whether it awaits upload. */
export interface SyncMeta {
  updatedAt: number;
  dirty: boolean;
}

/** Last-write-wins: whether a pulled remote version should replace the local one. */
export function shouldApplyRemote(local: SyncMeta | null, remoteUpdatedAt: number): boolean {
  if (!local) return true;
  if (remoteUpdatedAt > local.updatedAt) return true;
  if (remoteUpdatedAt === local.updatedAt) return !local.dirty;
  return false;
}

/** Timestamp for a local edit: now, but always after the previous one so the edit wins. */
export function nextUpdatedAt(previous: number | null, now: number): number {
  return previous !== null && now <= previous ? previous + 1 : now;
}

// --- WhatsApp Web ----------------------------------------------------------------------------

export interface Matchable {
  name: string;
  phones: string[];
}

const normalizeName = (s: string) => s.normalize("NFKC").replace(/\s+/g, " ").trim().toLowerCase();

/**
 * The contact the open chat most likely belongs to: same phone number first (including a title
 * that is itself a number, as for unsaved WhatsApp contacts), then the same name. Null if none or
 * if the name matches more than one contact. The user can always pick someone else.
 */
export function matchChatContact<T extends Matchable>(contacts: readonly T[], hint: ChatHint): T | null {
  const phones = [hint.phone, hint.title ? normalizePhone(hint.title) : null].filter((p): p is string => !!p);
  for (const phone of phones) {
    const found = contacts.find((c) => c.phones.includes(phone));
    if (found) return found;
  }
  if (!hint.title) return null;
  const title = normalizeName(hint.title);
  if (!title) return null;
  const byName = contacts.filter((c) => normalizeName(c.name) === title);
  return byName.length === 1 ? byName[0] : null;
}

// --- Channel field and new contacts from the form ---------------------------------------------
// Port of engine/.../contacts/Channels.kt: the built-in "Channel" choice field (same id and
// defaults on every device, so it merges into one on sync) and the channel of the site in use.

export const CHANNEL_KEY = "channel";
export const CHANNEL_FIELD_ID = "builtin-channel";
/** Stand-in "package" for WhatsApp Web, as on Android. */
export const WHATSAPP_WEB = "web.whatsapp.com";
export const CHANNEL_DEFAULT_OPTIONS = ["WhatsApp", "WA Business", "Instagram", "TikTok", "Shopee", "Tokopedia", "Facebook", "Telegram", "Lainnya"];

const CHANNEL_BY_SOURCE: Record<string, string> = {
  "com.whatsapp": "WhatsApp",
  [WHATSAPP_WEB]: "WhatsApp",
  "com.whatsapp.w4b": "WA Business",
  "com.instagram.android": "Instagram",
  "com.instagram.barcelona": "Instagram",
  "com.zhiliaoapp.musically": "TikTok",
  "com.ss.android.ugc.trill": "TikTok",
  "com.shopee.id": "Shopee",
  "com.shopee.sellercenter.id": "Shopee",
  "com.tokopedia.tkpd": "Tokopedia",
  "com.tokopedia.sellerapp": "Tokopedia",
  "com.facebook.orca": "Facebook",
  "com.facebook.katana": "Facebook",
  "com.facebook.lite": "Facebook",
  "org.telegram.messenger": "Telegram",
};

/**
 * The option of `options` for the app or site `source`, written as the user wrote it; null when
 * it is not a known chat app or the user removed that option.
 */
export function channelOptionFor(source: string | null | undefined, options: readonly string[]): string | null {
  const wanted = has(CHANNEL_BY_SOURCE, source ?? "") ? CHANNEL_BY_SOURCE[source!] : null;
  if (!wanted) return null;
  return options.find((o) => o.trim().toLowerCase() === wanted.toLowerCase()) ?? null;
}

/**
 * Name and number to start a new contact with: the open chat's title (unless it is just a number)
 * or what was typed in the search box, and the chat's number or a number typed in the search box.
 */
export function newContactPrefill(hint: ChatHint | null, search: string): { name: string; phone: string } {
  const title = hint?.title?.trim() ?? "";
  const titlePhone = title ? normalizePhone(title) : null;
  const typed = search.trim();
  const typedPhone = /^[+\d][\d\s()-]*$/.test(typed) ? normalizePhone(typed) : null;
  const name = title && !titlePhone ? title : typed && !typedPhone ? typed : "";
  return { name: name.slice(0, 120), phone: hint?.phone ?? titlePhone ?? typedPhone ?? "" };
}
