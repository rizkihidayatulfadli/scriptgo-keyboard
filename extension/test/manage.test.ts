import { describe, expect, it } from "vitest";
import { parse } from "../src/engine/template";
import {
  buildVariableSnippet,
  fieldError,
  fieldKeyFromLabel,
  MAX_TAGS,
  normalizeShortcut,
  normalizeTags,
  parseOptions,
  parseTags,
  storedOptions,
  templateError,
  variableLabel,
} from "../src/engine/manage";

describe("fieldKeyFromLabel (same cases as FieldKeysTest.kt)", () => {
  it("derives keys from labels", () => {
    expect(fieldKeyFromLabel("Kota Asal")).toBe("kota_asal");
    expect(fieldKeyFromLabel("  Diskon (%) ")).toBe("diskon");
    expect(fieldKeyFromLabel("Café")).toBe("cafe");
    expect(fieldKeyFromLabel("!!!")).toBe("field");
  });

  it("avoids reserved and existing keys", () => {
    expect(fieldKeyFromLabel("Nama")).toBe("nama_2");
    expect(fieldKeyFromLabel("Kota", ["kota", "kota_2"])).toBe("kota_3");
  });
});

describe("normalizeShortcut", () => {
  it("trims, lowercases, and drops a typed trigger", () => {
    expect(normalizeShortcut(" Ongkir ", "/")).toBe("ongkir");
    expect(normalizeShortcut("#salam", "#")).toBe("salam");
    expect(normalizeShortcut("/salam", "#")).toBe("/salam");
  });
});

describe("templateError", () => {
  const others = [{ id: "t1", shortcut: "salam" }];
  const ok = { id: null, title: "Ongkir", shortcut: "ongkir", body: "Ongkir {total}" };

  it("accepts a valid template", () => {
    expect(templateError(ok, others, "/")).toBeNull();
  });

  it("requires a title and a body", () => {
    expect(templateError({ ...ok, title: " " }, others, "/")).toBe("Judul wajib diisi");
    expect(templateError({ ...ok, body: "" }, others, "/")).toBe("Isi template wajib diisi");
  });

  it("checks the shortcut's characters", () => {
    expect(templateError({ ...ok, shortcut: "on kir" }, others, "/")).toBe("Shortcut hanya boleh huruf kecil, angka, - dan _");
    expect(templateError({ ...ok, shortcut: "" }, others, "/")).toBe("Shortcut hanya boleh huruf kecil, angka, - dan _");
  });

  it("rejects a shortcut used by another template, but not by itself", () => {
    expect(templateError({ ...ok, shortcut: "salam" }, others, "#")).toBe("Shortcut #salam sudah dipakai template lain");
    expect(templateError({ ...ok, id: "t1", shortcut: "salam" }, others, "/")).toBeNull();
  });
});

describe("fields", () => {
  it("requires a name, and options for a choice field", () => {
    expect(fieldError({ id: null, label: "", type: "text", options: [] })).toBe("Nama field wajib diisi");
    expect(fieldError({ id: null, label: "Kurir", type: "choice", options: [] })).toBe("Isi minimal satu pilihan");
    expect(fieldError({ id: null, label: "Kurir", type: "choice", options: ["JNE"] })).toBeNull();
  });

  it("parses one option per line", () => {
    expect(parseOptions(" JNE \n\nJ&T\nJNE\n")).toEqual(["JNE", "J&T"]);
  });

  it("stores options by type", () => {
    expect(storedOptions("choice", "JNE\nJ&T", "USD")).toEqual(["JNE", "J&T"]);
    expect(storedOptions("currency", "JNE", "USD")).toEqual(["USD"]);
    expect(storedOptions("currency", "", "IDR")).toEqual([]);
    expect(storedOptions("text", "JNE", "USD")).toEqual([]);
  });
});

describe("buildVariableSnippet (same cases as VariableSnippetTest.kt)", () => {
  it("builds plain, fallback, and options", () => {
    expect(buildVariableSnippet("nama")).toBe("{nama}");
    expect(buildVariableSnippet("nama", "")).toBe("{nama}");
    expect(buildVariableSnippet("nama", "Kak")).toBe("{nama|Kak}");
    expect(buildVariableSnippet("kurir", "Kak", [" JNE ", "", "J&T", "JNE"])).toBe("{kurir:JNE,J&T}");
  });

  it("escapes special characters so the parser reads them back", () => {
    const fallback = "a {b} c\\d | e: f";
    expect(parse(buildVariableSnippet("x", fallback)).variables).toEqual([{ key: "x", default: fallback }]);
    const options = ["Rp10.000, cepat", "{kilat}", "a|b:c"];
    expect(parse(buildVariableSnippet("ongkir", null, options)).variables).toEqual([{ key: "ongkir", options }]);
  });
});

describe("variableLabel", () => {
  it("names fields, built-ins, and unknown keys", () => {
    expect(variableLabel("kota|Bandung", { kota: "Kota Asal" })).toBe("Kota Asal");
    expect(variableLabel("nama|Kak")).toBe("Nama");
    expect(variableLabel("@tanggal")).toBe("Tanggal");
    expect(variableLabel("nama_toko")).toBe("Nama toko");
  });
});

describe("tags", () => {
  it("trims, collapses spaces, and drops empty and duplicate tags (ignoring case)", () => {
    expect(normalizeTags(["  promo ", "Promo", "", "ongkir   luar kota", "PROMO"])).toEqual(["promo", "ongkir luar kota"]);
  });

  it("splits on commas, the separator the app stores", () => {
    expect(normalizeTags(["a,b", " c , "])).toEqual(["a", "b", "c"]);
    expect(parseTags("promo,ongkir")).toEqual(["promo", "ongkir"]);
    expect(parseTags("")).toEqual([]);
  });

  it("keeps each tag short and at most MAX_TAGS", () => {
    expect(normalizeTags(["x".repeat(50)])).toEqual(["x".repeat(30)]);
    expect(normalizeTags(Array.from({ length: 15 }, (_, i) => `t${i}`))).toHaveLength(MAX_TAGS);
  });
});
