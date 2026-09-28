// The icon of a contact field (port of engine/…/contacts/FieldIcons.kt; both are checked against
// docs/field-icon-fixtures.json). A field stores an icon only when the user picked one; otherwise
// pickFieldIcon chooses from its label and key.

import { FIELD_ICON_KEYWORDS, FIELD_ICON_NAMES, FIELD_ICON_TYPE_DEFAULTS } from "./fieldIconCatalog";

const KNOWN = new Set(FIELD_ICON_NAMES);

/** Lowercase ASCII words separated by single spaces ("Tgl_Lahir (anak)" → "tgl lahir anak"). */
export function normalizeIconText(text: string): string {
  return text
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

/** The first catalog icon with a keyword among the words of label or key, else the type's default. */
export function pickFieldIcon(label: string, key: string, type: string): string {
  const words = ` ${normalizeIconText(label)} ${normalizeIconText(key)} `;
  for (const [icon, keywords] of FIELD_ICON_KEYWORDS) {
    if (keywords.some((k) => words.includes(` ${k} `))) return icon;
  }
  return FIELD_ICON_TYPE_DEFAULTS[type] ?? FIELD_ICON_TYPE_DEFAULTS.text;
}

/** The icon to show: the stored one when it is known, otherwise pickFieldIcon. */
export function resolveFieldIcon(stored: string | undefined, label: string, key: string, type: string): string {
  return stored && KNOWN.has(stored) ? stored : pickFieldIcon(label, key, type);
}

export { FIELD_ICON_NAMES };
