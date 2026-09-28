// Rules for editing templates and custom fields from the extension, the same as the Android app's
// ManagerRepository.saveTemplate / saveField and engine/.../contacts/FieldKeys.kt.

import { CURRENCIES, type FieldType } from "./template";

/** What a shortcut may contain, after the trigger character: `ongkir`, `salam-2`. */
export const SHORTCUT = /^[a-z0-9_-]+$/;

/** Field types offered for editing, as in the app (P0 in docs/PRD.md). */
export const EDITABLE_FIELD_TYPES: readonly FieldType[] = ["text", "longtext", "number", "currency", "date", "choice"];

export const FIELD_TYPE_LABELS: Record<FieldType, string> = {
  text: "Teks",
  longtext: "Teks panjang",
  number: "Angka",
  currency: "Mata uang",
  date: "Tanggal",
  choice: "Pilihan",
  phone: "Nomor HP",
  email: "Email",
  url: "URL",
  boolean: "Ya/Tidak",
  multichoice: "Pilihan ganda",
};

/** Keys the template engine already maps to contact properties (see docs/template-syntax.md). */
export const RESERVED_FIELD_KEYS: readonly string[] = ["nama", "nomor"];

/**
 * What the user typed as a shortcut, as stored: trimmed, lower case, without a leading trigger
 * character (people often type `/ongkir` when asked for the shortcut).
 */
export function normalizeShortcut(typed: string, trigger: string): string {
  const s = typed.trim().toLowerCase();
  return s.startsWith(trigger) ? s.slice(trigger.length) : s;
}

export interface TemplateInput {
  /** Null for a new template. */
  id: string | null;
  title: string;
  shortcut: string;
  body: string;
  /** Tags as the user entered them; normalized by [normalizeTags] when saved. Omitted = keep. */
  tags?: string[];
}

/** At most this many tags per template, each at most [TAG_MAX_LENGTH] characters. */
export const MAX_TAGS = 10;
export const TAG_MAX_LENGTH = 30;

/**
 * Tags as stored: split on commas (the storage separator, shared with the app), trimmed, inner
 * spaces collapsed, empty ones dropped, duplicates removed ignoring case (first spelling kept),
 * each cut to [TAG_MAX_LENGTH], at most [MAX_TAGS].
 */
export function normalizeTags(entered: readonly string[]): string[] {
  const seen = new Set<string>();
  const tags: string[] = [];
  for (const tag of entered.flatMap((t) => t.split(","))) {
    const clean = tag.trim().replace(/\s+/g, " ").slice(0, TAG_MAX_LENGTH).trim();
    if (!clean || seen.has(clean.toLowerCase())) continue;
    seen.add(clean.toLowerCase());
    tags.push(clean);
    if (tags.length === MAX_TAGS) break;
  }
  return tags;
}

/** The stored tag text (`"promo,ongkir"`) as a list. */
export const parseTags = (stored: string): string[] => normalizeTags([stored]);

/**
 * Why [input] cannot be saved, or null when it can. [others] are the live templates; a shortcut
 * may not be used by another one.
 */
export function templateError(
  input: TemplateInput,
  others: readonly { id: string; shortcut: string }[],
  trigger: string,
): string | null {
  if (!input.title.trim()) return "Judul wajib diisi";
  if (!SHORTCUT.test(input.shortcut)) return "Shortcut hanya boleh huruf kecil, angka, - dan _";
  if (others.some((t) => t.id !== input.id && t.shortcut === input.shortcut)) {
    return `Shortcut ${trigger}${input.shortcut} sudah dipakai template lain`;
  }
  if (!input.body.trim()) return "Isi template wajib diisi";
  return null;
}

export interface FieldInput {
  /** Null for a new field. */
  id: string | null;
  label: string;
  type: FieldType;
  /** Choices for "choice"; for "currency", the currency code (empty means IDR). */
  options: string[];
  /** Raw value used when a contact has none; empty or missing for none. For "choice", one of the options. */
  fallback?: string;
  /** Icon the user picked (a fieldIcons name); empty for automatic, missing to keep the stored one. */
  icon?: string;
}

export function fieldError(input: FieldInput): string | null {
  if (!input.label.trim()) return "Nama field wajib diisi";
  if (input.type === "choice" && input.options.length === 0) return "Isi minimal satu pilihan";
  return null;
}

/** Choice options as stored: one per line, trimmed, without blanks or repeats. */
export function parseOptions(text: string): string[] {
  return [...new Set(text.split("\n").map((o) => o.trim()).filter((o) => o !== ""))];
}

/**
 * The stored options for a field of [type]: choices for "choice", the currency code for a
 * currency other than IDR (IDR stays an empty list, as the app stores it), nothing otherwise.
 */
export function storedOptions(type: FieldType, choices: string, currency: string): string[] {
  if (type === "choice") return parseOptions(choices);
  if (type === "currency") return currency && currency !== CURRENCIES[0].code ? [currency] : [];
  return [];
}

/** Derives a field's template key from its label, e.g. "Kota Asal" → `kota_asal`, unique among [existing]. */
export function fieldKeyFromLabel(label: string, existing: Iterable<string> = []): string {
  const taken = new Set([...existing, ...RESERVED_FIELD_KEYS]);
  const base =
    label
      .normalize("NFD")
      .replace(/\p{M}+/gu, "")
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "_")
      .replace(/^_+|_+$/g, "") || "field";
  if (!taken.has(base)) return base;
  let n = 2;
  while (taken.has(`${base}_${n}`)) n++;
  return `${base}_${n}`;
}

/** Variables offered as one-tap inserts in the template editor, with a short description. */
export function variableSnippets(fieldKeys: readonly { key: string; label: string }[]): Array<{ text: string; label: string }> {
  return [
    { text: "{nama}", label: "Nama kontak" },
    { text: "{nomor}", label: "Nomor HP kontak" },
    ...fieldKeys.map((f) => ({ text: `{${f.key}}`, label: f.label })),
    { text: "{@tanggal}", label: "Tanggal hari ini" },
    { text: "{@hari}", label: "Nama hari" },
    { text: "{@jam}", label: "Jam sekarang" },
    { text: "{@kursor}", label: "Posisi kursor setelah disisipkan" },
  ];
}

/**
 * The `{...}` text the editor inserts for a variable (port of VariableSnippet.kt): `{nama}`,
 * `{nama|Kak}` with a fallback, or `{kurir:JNE,J&T}` with options. Special characters are escaped
 * (docs/template-syntax.md), so the parser reads back exactly what the user typed.
 */
export function buildVariableSnippet(key: string, fallback: string | null = null, options: readonly string[] = []): string {
  const clean = [...new Set(options.map((o) => o.trim()).filter((o) => o !== ""))];
  if (clean.length) return `{${key}:${clean.map((o) => escapeSnippet(o, ",")).join(",")}}`;
  if (fallback) return `{${key}|${escapeSnippet(fallback, "")}}`;
  return `{${key}}`;
}

function escapeSnippet(value: string, extra: string): string {
  let out = "";
  for (const ch of value) out += (ch === "\\" || ch === "{" || ch === "}" || extra.includes(ch) ? "\\" : "") + ch;
  return out;
}

/** Built-in variables every contact has. */
export const BUILT_IN_VARIABLES: ReadonlyArray<{ key: string; label: string; description: string }> = [
  { key: "nama", label: "Nama", description: "Nama di kontak" },
  { key: "nomor", label: "Nomor HP", description: "Nomor HP di kontak" },
];

const VARIABLE_LABELS: Record<string, string> = {
  nama: "Nama",
  nomor: "Nomor HP",
  "@tanggal": "Tanggal",
  "@tanggal_pendek": "Tanggal",
  "@hari": "Hari",
  "@bulan": "Bulan",
  "@tahun": "Tahun",
  "@jam": "Jam",
  "@kursor": "Kursor",
};

/** A readable name for a variable key: the field's label, a built-in name, or the key in words. */
export function variableLabel(key: string, fieldLabels: Record<string, string> = {}): string {
  const base = key.split("|")[0].split(":")[0].trim();
  if (Object.prototype.hasOwnProperty.call(fieldLabels, base)) return fieldLabels[base];
  if (VARIABLE_LABELS[base]) return VARIABLE_LABELS[base];
  const words = base.replace(/^@/, "").replace(/[_-]/g, " ").trim();
  return words ? words[0].toUpperCase() + words.slice(1) : key;
}
