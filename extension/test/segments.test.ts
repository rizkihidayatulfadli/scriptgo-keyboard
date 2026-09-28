// sourceSegments: the chip editor's view of template source. Joining the raw pieces must give the
// template back unchanged, and variables must be exactly the ones parse() sees.
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { parse, sourceSegments } from "../src/engine/template";

const fixtures = JSON.parse(readFileSync(new URL("../../docs/template-fixtures.json", import.meta.url), "utf8")) as { name: string; template: string }[];

describe("sourceSegments", () => {
  it("splits text and variables, keeping their source", () => {
    expect(sourceSegments("Halo {nama|Kak}, kurir {kurir:JNE,J&T} {@kursor}")).toEqual([
      { kind: "text", raw: "Halo " },
      { kind: "var", raw: "{nama|Kak}", key: "nama", default: "Kak", builtin: false },
      { kind: "text", raw: ", kurir " },
      { kind: "var", raw: "{kurir:JNE,J&T}", key: "kurir", options: ["JNE", "J&T"], builtin: false },
      { kind: "text", raw: " " },
      { kind: "var", raw: "{@kursor}", key: "@kursor", builtin: true },
    ]);
  });

  it("keeps escapes, invalid keys, unknown builtins and unclosed braces as text", () => {
    expect(sourceSegments("\\{nama\\} {ada spasi} {@cuaca} {kota")).toEqual([{ kind: "text", raw: "\\{nama\\} {ada spasi} {@cuaca} {kota" }]);
    expect(sourceSegments("")).toEqual([]);
  });

  it("normalizes the key but keeps the source as written", () => {
    expect(sourceSegments("{Kota}")).toEqual([{ kind: "var", raw: "{Kota}", key: "kota", builtin: false }]);
  });

  for (const f of fixtures) {
    it(`round-trips and matches parse(): ${f.name}`, () => {
      const segments = sourceSegments(f.template);
      expect(segments.map((s) => s.raw).join("")).toBe(f.template);
      const vars = segments.flatMap((s) => (s.kind === "var" && !s.builtin ? [s.key] : []));
      expect([...new Set(vars)]).toEqual(parse(f.template).variables.map((v) => v.key));
    });
  }
});
