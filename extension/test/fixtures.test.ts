// The shared fixtures in docs/ must pass here exactly as they pass in engine/ (Kotlin).
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { formatFieldValue, parse, render, type Contact, type FieldType, type LocalDateTime } from "../src/engine/template";

interface TemplateFixture {
  name: string;
  template: string;
  contact?: Contact;
  inputs?: Record<string, string>;
  now?: string;
  markMissing?: boolean;
  fieldFallbacks?: Record<string, string>;
  expected: { variables: unknown[]; text: string; cursor: number | null };
}

interface FieldFixture {
  name: string;
  type: FieldType;
  currency?: string;
  raw: string | string[];
  expected: string;
}

const load = <T>(file: string): T[] => JSON.parse(readFileSync(new URL(`../../docs/${file}`, import.meta.url), "utf8"));

function parseNow(iso?: string): LocalDateTime {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso ?? "2026-01-01T00:00:00")!;
  return { year: +m[1], month: +m[2], day: +m[3], hour: +m[4], minute: +m[5] };
}

describe("template-fixtures.json", () => {
  for (const f of load<TemplateFixture>("template-fixtures.json")) {
    it(f.name, () => {
      const parsed = parse(f.template);
      expect(parsed.variables).toEqual(f.expected.variables);
      const result = render(parsed, f.contact ?? null, f.inputs ?? {}, parseNow(f.now), f.markMissing ?? false, f.fieldFallbacks ?? {});
      expect(result).toEqual({ text: f.expected.text, cursor: f.expected.cursor });
    });
  }
});

describe("field-format-fixtures.json", () => {
  for (const f of load<FieldFixture>("field-format-fixtures.json")) {
    it(f.name, () => expect(formatFieldValue(f.type, f.raw, f.currency)).toBe(f.expected));
  }
});
