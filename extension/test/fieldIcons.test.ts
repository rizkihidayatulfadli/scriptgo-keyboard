// docs/field-icon-fixtures.json must pass here exactly as in engine/ (FieldIconsTest.kt).
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { FIELD_ICON_KEYWORDS, FIELD_ICON_TYPE_DEFAULTS } from "../src/engine/fieldIconCatalog";
import { FIELD_ICON_NAMES, normalizeIconText, resolveFieldIcon } from "../src/engine/fieldIcons";
import { FIELD_ICON_PATHS } from "../src/fieldIconPaths";

interface Case { label: string; key: string; type: string; stored: string; icon: string }
const { cases } = JSON.parse(readFileSync(new URL("../../docs/field-icon-fixtures.json", import.meta.url), "utf8")) as { cases: Case[] };

describe("field icon fixtures", () => {
  for (const c of cases) {
    it(`${c.label} (${c.type}, stored '${c.stored}')`, () => {
      expect(resolveFieldIcon(c.stored, c.label, c.key, c.type)).toBe(c.icon);
    });
  }
});

describe("field icon catalog", () => {
  it("has a drawing for every icon and every pickable icon in the gallery", () => {
    const all = new Set(FIELD_ICON_NAMES);
    expect(all.size).toBe(FIELD_ICON_NAMES.length);
    for (const [icon] of FIELD_ICON_KEYWORDS) expect(all.has(icon)).toBe(true);
    for (const icon of Object.values(FIELD_ICON_TYPE_DEFAULTS)) expect(all.has(icon)).toBe(true);
    for (const icon of FIELD_ICON_NAMES) expect(FIELD_ICON_PATHS[icon]?.length).toBeGreaterThan(0);
  });

  it("normalizes accents and separators like the engine", () => {
    expect(normalizeIconText("Tgl_Lahir (anak)")).toBe("tgl lahir anak");
    expect(normalizeIconText("  Área  ")).toBe("area");
  });
});
