// Template engine, a port of engine/ (Kotlin). docs/template-syntax.md is the source of truth, and
// both implementations must pass docs/template-fixtures.json and docs/field-format-fixtures.json.

/** A variable the form may ask for. `key` is lowercase; at most one of default and options is set. */
export interface Variable {
  key: string;
  default?: string;
  options?: string[];
}

/** The contact a template is rendered for. phones are E.164; fields hold text from formatFieldValue. */
export interface Contact {
  name: string;
  phones: string[];
  fields: Record<string, string>;
}

/** `cursor` is a UTF-16 index into `text`, or null when the template has no `{@kursor}`. */
export interface RenderResult {
  text: string;
  cursor: number | null;
}

/** Local wall-clock time without a zone; injected so rendering is testable. */
export interface LocalDateTime {
  year: number;
  /** 1–12 */
  month: number;
  day: number;
  hour: number;
  minute: number;
}

type BuiltinKind = "tanggal" | "tanggal_pendek" | "hari" | "bulan" | "tahun" | "jam" | "kursor";
const BUILTINS: readonly BuiltinKind[] = ["tanggal", "tanggal_pendek", "hari", "bulan", "tahun", "jam", "kursor"];

type Token =
  | { kind: "text"; value: string }
  | { kind: "var"; variable: Variable }
  | { kind: "builtin"; builtin: BuiltinKind };

export class ParsedTemplate {
  /** Variables in order of first appearance, without duplicates or builtins. */
  readonly variables: Variable[];

  constructor(readonly tokens: readonly Token[]) {
    const seen = new Set<string>();
    this.variables = [];
    for (const t of tokens) {
      if (t.kind === "var" && !seen.has(t.variable.key)) {
        seen.add(t.variable.key);
        this.variables.push(t.variable);
      }
    }
  }
}

const KEY = /^[A-Za-z0-9_]+$/;
/** Characters that `\` escapes outside a variable. */
const TEXT_ESCAPABLE = "{}\\";
/** Characters that `\` escapes inside a default or an option. */
const VALUE_ESCAPABLE = "{}\\,|";

/** Parses template text. Never throws: malformed syntax is kept as plain text. */
export function parse(template: string): ParsedTemplate {
  const tokens: Token[] = [];
  let text = "";
  let i = 0;
  while (i < template.length) {
    const c = template[i];
    if (c === "\\" && i + 1 < template.length && TEXT_ESCAPABLE.includes(template[i + 1])) {
      text += template[i + 1];
      i += 2;
      continue;
    }
    if (c === "{") {
      const close = findClosingBrace(template, i + 1);
      if (close === -1) {
        // Unclosed brace: everything from here on is plain text.
        text += template.slice(i);
        break;
      }
      const token = parseBody(template.slice(i + 1, close));
      if (token === null) {
        text += template.slice(i, close + 1);
      } else {
        if (text) {
          tokens.push({ kind: "text", value: text });
          text = "";
        }
        tokens.push(token);
      }
      i = close + 1;
      continue;
    }
    text += c;
    i++;
  }
  if (text) tokens.push({ kind: "text", value: text });
  return new ParsedTemplate(tokens);
}

/**
 * A piece of template source for the chip editor: plain text (as written, escapes kept) or one
 * variable the engine recognizes, with its source. Joining every `raw` gives back the template.
 */
export type SourceSegment =
  | { kind: "text"; raw: string }
  | { kind: "var"; raw: string; key: string; builtin: boolean; default?: string; options?: string[] };

/** Splits template source into text and variables the way [parse] reads it. Never throws. */
export function sourceSegments(template: string): SourceSegment[] {
  const out: SourceSegment[] = [];
  let text = "";
  const flush = () => {
    if (text) out.push({ kind: "text", raw: text });
    text = "";
  };
  let i = 0;
  while (i < template.length) {
    const c = template[i];
    if (c === "\\" && i + 1 < template.length && TEXT_ESCAPABLE.includes(template[i + 1])) {
      text += template.slice(i, i + 2);
      i += 2;
      continue;
    }
    if (c === "{") {
      const close = findClosingBrace(template, i + 1);
      if (close === -1) {
        text += template.slice(i);
        break;
      }
      const raw = template.slice(i, close + 1);
      const token = parseBody(template.slice(i + 1, close));
      if (token === null || token.kind === "text") {
        text += raw;
      } else {
        flush();
        out.push(
          token.kind === "builtin"
            ? { kind: "var", raw, key: `@${token.builtin}`, builtin: true }
            : { kind: "var", raw, builtin: false, ...token.variable },
        );
      }
      i = close + 1;
      continue;
    }
    text += c;
    i++;
  }
  flush();
  return out;
}

/** Index of the first unescaped `}` at or after `from`, or -1. */
function findClosingBrace(s: string, from: number): number {
  let j = from;
  while (j < s.length) {
    if (s[j] === "\\") j += 2;
    else if (s[j] === "}") return j;
    else j++;
  }
  return -1;
}

function parseBody(body: string): Token | null {
  if (body.startsWith("@")) {
    const key = body.slice(1);
    return (BUILTINS as readonly string[]).includes(key) ? { kind: "builtin", builtin: key as BuiltinKind } : null;
  }
  const sep = indexOfUnescaped(body, "|:");
  const key = sep === -1 ? body : body.slice(0, sep);
  if (!KEY.test(key)) return null;
  const normalizedKey = key.toLowerCase();
  if (sep === -1) return { kind: "var", variable: { key: normalizedKey } };

  const rest = body.slice(sep + 1);
  if (body[sep] === "|") return { kind: "var", variable: { key: normalizedKey, default: unescape(rest) } };
  const options = splitUnescaped(rest, ",").map((o) => unescape(o).trim());
  return { kind: "var", variable: { key: normalizedKey, options } };
}

function indexOfUnescaped(s: string, chars: string): number {
  let j = 0;
  while (j < s.length) {
    if (s[j] === "\\") j += 2;
    else if (chars.includes(s[j])) return j;
    else j++;
  }
  return -1;
}

function splitUnescaped(s: string, delimiter: string): string[] {
  const parts: string[] = [];
  let start = 0;
  let j = 0;
  while (j < s.length) {
    if (s[j] === "\\") j += 2;
    else if (s[j] === delimiter) {
      parts.push(s.slice(start, j));
      start = ++j;
    } else j++;
  }
  parts.push(s.slice(start));
  return parts;
}

function unescape(s: string): string {
  let out = "";
  let j = 0;
  while (j < s.length) {
    if (s[j] === "\\" && j + 1 < s.length && VALUE_ESCAPABLE.includes(s[j + 1])) {
      out += s[j + 1];
      j += 2;
    } else {
      out += s[j];
      j++;
    }
  }
  return out;
}

/**
 * Renders a parsed template. Each variable takes the first available of: inputs, the contact's
 * non-empty value, its default, its first option, its field's non-empty fallback in fieldFallbacks,
 * or an empty string. With markMissing, a variable with none of these renders as `[key]`. Never throws.
 */
export function render(
  parsed: ParsedTemplate,
  contact: Contact | null,
  inputs: Record<string, string>,
  now: LocalDateTime,
  markMissing = false,
  fieldFallbacks: Record<string, string> = {},
): RenderResult {
  let out = "";
  let cursor: number | null = null;
  const firstOccurrence = new Map(parsed.variables.map((v) => [v.key, v]));
  for (const token of parsed.tokens) {
    if (token.kind === "text") out += token.value;
    else if (token.kind === "var") {
      const variable = firstOccurrence.get(token.variable.key)!;
      out += resolve(variable, contact, inputs, fieldFallbacks) ?? (markMissing ? `[${variable.key}]` : "");
    } else if (token.builtin === "kursor") {
      if (cursor === null) cursor = out.length;
    } else out += formatBuiltin(token.builtin, now);
  }
  return { text: out, cursor };
}

/** The value the form is prefilled with, before the user edits it. */
export function prefillValue(variable: Variable, contact: Contact | null, fieldFallbacks: Record<string, string> = {}): string {
  return resolve(variable, contact, {}, fieldFallbacks) ?? "";
}

function resolve(
  variable: Variable,
  contact: Contact | null,
  inputs: Record<string, string>,
  fieldFallbacks: Record<string, string>,
): string | null {
  const own = (map: Record<string, string>) => Object.prototype.hasOwnProperty.call(map, variable.key);
  if (own(inputs)) return inputs[variable.key];
  const fromContact = contactValue(variable.key, contact);
  if (fromContact) return fromContact;
  if (variable.default !== undefined) return variable.default;
  if (variable.options?.length) return variable.options[0];
  return own(fieldFallbacks) && fieldFallbacks[variable.key] ? fieldFallbacks[variable.key] : null;
}

function contactValue(key: string, contact: Contact | null): string | null {
  if (!contact) return null;
  if (key === "nama") return contact.name;
  if (key === "nomor") return contact.phones.length ? formatFieldValue("phone", contact.phones[0]) : null;
  return Object.prototype.hasOwnProperty.call(contact.fields, key) ? contact.fields[key] : null;
}

const pad = (n: number, width: number) => String(n).padStart(width, "0");

function formatBuiltin(kind: Exclude<BuiltinKind, "kursor">, now: LocalDateTime): string {
  switch (kind) {
    case "tanggal":
      return longDate(now.year, now.month, now.day);
    case "tanggal_pendek":
      return `${pad(now.day, 2)}/${pad(now.month, 2)}/${pad(now.year, 4)}`;
    case "hari":
      return dayName(now.year, now.month, now.day);
    case "bulan":
      return MONTHS[now.month - 1];
    case "tahun":
      return String(now.year);
    case "jam":
      return `${pad(now.hour, 2)}.${pad(now.minute, 2)}`;
  }
}

// --- Field formats ---------------------------------------------------------------------------

export type FieldType =
  | "text"
  | "longtext"
  | "number"
  | "currency"
  | "date"
  | "choice"
  | "phone"
  | "email"
  | "url"
  | "boolean"
  | "multichoice";

export const FIELD_TYPES: readonly FieldType[] = [
  "text", "longtext", "number", "currency", "date", "choice", "phone", "email", "url", "boolean", "multichoice",
];

/**
 * Currencies for "currency" fields. A field stores its code as its only option; no option or an
 * unknown code means IDR. The symbol is written before the amount; cents allows up to two decimals.
 */
export const CURRENCIES = [
  { code: "IDR", symbol: "Rp", cents: false },
  { code: "USD", symbol: "US$", cents: true },
  { code: "SGD", symbol: "S$", cents: true },
  { code: "MYR", symbol: "RM", cents: true },
  { code: "EUR", symbol: "€", cents: true },
  { code: "AUD", symbol: "A$", cents: true },
  { code: "JPY", symbol: "¥", cents: false },
  { code: "CNY", symbol: "CN¥", cents: true },
  { code: "SAR", symbol: "SAR ", cents: true },
] as const;

export type FieldCurrency = (typeof CURRENCIES)[number];

export function currencyFromCode(code: string | null | undefined): FieldCurrency {
  return CURRENCIES.find((c) => c.code === code) ?? CURRENCIES[0];
}

const NUMBER = /^(-?)(\d+)(?:\.(\d+))?$/;
const INTEGER = /^(-?)(\d+)$/;
const AMOUNT_WITH_CENTS = /^(-?)(\d+)(?:\.(\d{1,2}))?$/;
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Turns a stored field value into template text. Empty input gives empty output, and a value that
 * is invalid for its type is returned unchanged. A list (multichoice) is joined with ", ".
 * `currency` (a CURRENCIES code) is only used by "currency". Never throws.
 */
export function formatFieldValue(type: FieldType, raw: string | string[], currency?: string | null): string {
  if (Array.isArray(raw)) return raw.join(", ");
  if (raw === "") return "";
  switch (type) {
    case "number": {
      const m = NUMBER.exec(raw);
      return m ? m[1] + groupThousands(m[2]) + (m[3] ? `,${m[3]}` : "") : raw;
    }
    case "currency": {
      const c = currencyFromCode(currency);
      const m = (c.cents ? AMOUNT_WITH_CENTS : INTEGER).exec(raw);
      return m ? m[1] + c.symbol + groupThousands(m[2]) + (m[3] ? "," + m[3].padEnd(2, "0") : "") : raw;
    }
    case "date": {
      const date = parseIsoDate(raw);
      return date ? longDate(date.year, date.month, date.day) : raw;
    }
    case "phone":
      return raw.startsWith("+62") ? "0" + raw.slice(3) : raw;
    case "boolean":
      return raw === "true" ? "Ya" : raw === "false" ? "Tidak" : raw;
    default:
      return raw;
  }
}

function groupThousands(digits: string): string {
  let out = "";
  for (let i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 === 0) out += ".";
    out += digits[i];
  }
  return out;
}

/** A strict ISO calendar date (like java.time.LocalDate.parse), or null. */
export function parseIsoDate(raw: string): { year: number; month: number; day: number } | null {
  const m = ISO_DATE.exec(raw);
  if (!m) return null;
  const [year, month, day] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const d = new Date(Date.UTC(year, month - 1, day));
  if (d.getUTCFullYear() !== year || d.getUTCMonth() !== month - 1 || d.getUTCDate() !== day) return null;
  return { year, month, day };
}

const MONTHS = [
  "Januari", "Februari", "Maret", "April", "Mei", "Juni",
  "Juli", "Agustus", "September", "Oktober", "November", "Desember",
];
const DAYS = ["Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"];

function longDate(year: number, month: number, day: number): string {
  return `${day} ${MONTHS[month - 1]} ${year}`;
}

function dayName(year: number, month: number, day: number): string {
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCFullYear(year); // Date.UTC maps years 0–99 to 1900–1999
  return DAYS[date.getUTCDay()];
}

/** The browser's current local time. Call it at the edge, never inside the engine. */
export function localNow(date: Date = new Date()): LocalDateTime {
  return {
    year: date.getFullYear(),
    month: date.getMonth() + 1,
    day: date.getDate(),
    hour: date.getHours(),
    minute: date.getMinutes(),
  };
}
