import { describe, expect, it } from "vitest";
import { aiErrorText, aiRequestBody, parseAiTemplate, parseQuota, quotaEmptyText, quotaText } from "../src/ai";

describe("AI help", () => {
  it("sends the description or the text with field keys only", () => {
    expect(aiRequestBody({ mode: "create", description: "  balas tanya ongkir " }, ["kota"])).toEqual({ mode: "create", description: "balas tanya ongkir", fields: ["kota"] });
    expect(aiRequestBody({ mode: "rewrite", style: "shorter", title: " Ongkir ", body: "Halo {nama|Kak}" }, [])).toEqual({
      mode: "rewrite",
      style: "shorter",
      title: "Ongkir",
      body: "Halo {nama|Kak}",
      fields: [],
    });
  });

  it("reads quotas as the app does", () => {
    expect(parseQuota({ limit: 20, remaining: 7, period: "day" })).toEqual({ limit: 20, remaining: 7, daily: true });
    expect(parseQuota({ limit: 5, remaining: -1, period: "total" })).toEqual({ limit: 5, remaining: 0, daily: false });
    expect(parseQuota({ error: "x" })).toBeNull();
    expect(parseQuota(null)).toBeNull();
    expect(quotaText({ limit: 5, remaining: 3, daily: false })).toBe("Sisa 3 dari 5 kali gratis");
    expect(quotaText({ limit: 20, remaining: 20, daily: true })).toBe("Sisa 20 dari 20 hari ini");
    expect(quotaEmptyText({ limit: 5, remaining: 0, daily: false })).toContain("Paket Pro");
  });

  it("takes only a complete template from the reply", () => {
    const template = { title: "Info ongkir", shortcut: "ongkir", body: "Ongkir ke {kota} ..." };
    expect(parseAiTemplate({ template, quota: {} })).toEqual(template);
    expect(parseAiTemplate({ template: { ...template, body: " " } })).toBeNull();
    expect(parseAiTemplate({ template: { title: "x" } })).toBeNull();
    expect(parseAiTemplate({ error: "ai_failed" })).toBeNull();
  });

  it("explains failures without codes", () => {
    expect(aiErrorText(429)).toBe("Jatah AI sudah habis.");
    expect(aiErrorText(503)).toContain("belum aktif");
    expect(aiErrorText(502)).toContain("jatahmu tidak berkurang");
    expect(aiErrorText(500)).toContain("jatahmu tidak berkurang");
  });
});

describe("AI report", () => {
  it("sends what was asked and what the AI answered", async () => {
    const { aiReportRow } = await import("../src/ai");
    expect(aiReportRow({ mode: "create", description: "tanya ongkir" }, "Halo {nama}", "offensive")).toEqual({
      mode: "create", style: "", input: "tanya ongkir", result: "Halo {nama}", reason: "offensive", note: "", source: "extension",
    });
    expect(aiReportRow({ mode: "rewrite", style: "casual", title: "t", body: "halo" }, "hai", "wrong")).toMatchObject({ mode: "rewrite", style: "casual", input: "halo" });
  });
});
