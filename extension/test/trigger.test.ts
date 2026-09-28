import { describe, expect, it } from "vitest";
import { findTrigger, formatDuration, MAX_QUERY_LENGTH, rankTemplates, secondsSaved } from "../src/engine/trigger";

describe("findTrigger", () => {
  it("finds a trigger at the start or after whitespace", () => {
    expect(findTrigger("/ong")).toEqual({ query: "ong", length: 4 });
    expect(findTrigger("Halo kak\n/ongkir-jkt")).toEqual({ query: "ongkir-jkt", length: 11 });
    expect(findTrigger("Siap /")).toEqual({ query: "", length: 1 });
    // contenteditable editors often hold a non-breaking space
    expect(findTrigger("Siap /sal")).toEqual({ query: "sal", length: 4 });
  });

  it("ignores slashes inside words, urls, and fractions", () => {
    for (const text of ["dan/atau", "https://toko.id", "1/2", "/ong kir", "halo"]) expect(findTrigger(text)).toBeNull();
  });

  it("supports a custom trigger character", () => {
    expect(findTrigger("ok ;salam", ";")).toEqual({ query: "salam", length: 6 });
    expect(findTrigger("ok /salam", ";")).toBeNull();
  });

  it("rejects overly long queries", () => {
    expect(findTrigger("/" + "a".repeat(MAX_QUERY_LENGTH + 1))).toBeNull();
  });
});

describe("rankTemplates", () => {
  const ongkir = { id: "1", shortcut: "ongkir", title: "Ongkir Jabodetabek", usageCount: 5 };
  const ongkirLuar = { id: "2", shortcut: "ongkir-luar", title: "Ongkir Luar Jawa", usageCount: 9 };
  const salam = { id: "3", shortcut: "salam", title: "Salam pembuka", usageCount: 20 };
  const rekening = { id: "4", shortcut: "rek", title: "Nomor rekening BCA", usageCount: 0 };
  const all = [ongkir, ongkirLuar, salam, rekening];

  it("lists by usage for an empty query", () => expect(rankTemplates("", all)).toEqual([salam, ongkirLuar, ongkir, rekening]));

  it("puts the exact shortcut first, then prefixes by usage", () => {
    expect(rankTemplates("ongkir", all)).toEqual([ongkir, ongkirLuar]);
    expect(rankTemplates("ong", all)).toEqual([ongkirLuar, ongkir]);
  });

  it("matches title words, substrings, and letters in order", () => {
    expect(rankTemplates("bca", all)).toEqual([rekening]);
    expect(rankTemplates("jawa", all)).toEqual([ongkirLuar]);
    expect(rankTemplates("okl", all)).toEqual([ongkirLuar]);
  });

  it("respects the limit", () => expect(rankTemplates("", all, 2)).toHaveLength(2));
});

describe("time saved", () => {
  it("estimates seconds from characters", () => {
    expect(secondsSaved(0)).toBe(0);
    expect(secondsSaved(120)).toBe(40);
    expect(secondsSaved(120, 2)).toBe(60);
  });

  it("formats durations in Indonesian", () => {
    expect(formatDuration(45)).toBe("45 detik");
    expect(formatDuration(12 * 60 + 30)).toBe("12 menit");
    expect(formatDuration(3 * 3600)).toBe("3 jam");
    expect(formatDuration(3 * 3600 + 20 * 60)).toBe("3 jam 20 menit");
  });
});
