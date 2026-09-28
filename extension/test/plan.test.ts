import { describe, expect, it } from "vitest";
import { daysLeft, hasProFeatures, parsePlan, planText } from "../src/engine/plan";

const now = Date.parse("2026-10-01T08:00:00Z");

describe("account plan", () => {
  it("reads each plan from my_plan (same cases as AccountPlanTest.kt)", () => {
    expect(parsePlan({ plan: "beta" })).toEqual({ kind: "beta" });
    expect(parsePlan({ plan: "trial", trial_ends_at: "2026-10-12T08:40:38.261541+00:00" })).toEqual({ kind: "trial", trialEndsAt: Date.parse("2026-10-12T08:40:38.261Z") });
    expect(parsePlan({ plan: "pro", pro_until: "2026-10-03T08:00:00+07:00" })).toEqual({ kind: "pro", proUntil: Date.parse("2026-10-03T01:00:00Z") });
    expect(parsePlan({ plan: "pro", pro_until: null })).toEqual({ kind: "pro" });
    expect(parsePlan({ plan: "free", trial_ends_at: "2026-10-12T08:00:00Z" })).toEqual({ kind: "free" });
  });

  it("ignores an unknown plan or a trial without an end", () => {
    expect(parsePlan({ plan: "team" })).toBeNull();
    expect(parsePlan(null)).toBeNull();
    expect(parsePlan({ plan: "trial" })).toBeNull();
    expect(parsePlan({ plan: "trial", trial_ends_at: "besok" })).toBeNull();
  });

  it("counts days left, rounded up, stopping at zero", () => {
    const trial = (end: string) => ({ kind: "trial" as const, trialEndsAt: Date.parse(end) });
    expect(daysLeft(trial("2026-10-15T08:00:00Z"), now)).toBe(14);
    expect(daysLeft(trial("2026-10-03T09:00:00Z"), now)).toBe(3);
    expect(daysLeft(trial("2026-10-01T09:00:00Z"), now)).toBe(1);
    expect(daysLeft(trial("2026-10-01T07:00:00Z"), now)).toBe(0);
    expect(daysLeft({ kind: "pro" }, now)).toBeNull();
    expect(daysLeft({ kind: "beta" }, now)).toBeNull();
  });

  it("only free lacks Pro features", () => {
    expect(hasProFeatures({ kind: "beta" })).toBe(true);
    expect(hasProFeatures({ kind: "trial", trialEndsAt: now })).toBe(true);
    expect(hasProFeatures({ kind: "pro" })).toBe(true);
    expect(hasProFeatures({ kind: "free" })).toBe(false);
  });

  it("describes the plan without asking to buy", () => {
    expect(planText({ kind: "beta" }, now)).toEqual({ title: "Beta", subtitle: "Semua fitur Pro gratis selama masa uji." });
    expect(planText({ kind: "trial", trialEndsAt: Date.parse("2026-10-04T08:00:00Z") }, now).subtitle).toMatch(/^Berakhir 3 hari lagi \(4 Okt 2026\)\.$/);
    expect(planText({ kind: "trial", trialEndsAt: now - 1 }, now).subtitle).toBe("Berakhir hari ini.");
    expect(planText({ kind: "pro", proUntil: Date.parse("2026-12-31T20:00:00Z") }, now).subtitle).toBe("Aktif sampai 1 Jan 2027.");
    expect(planText({ kind: "pro" }, now).subtitle).toBe("Aktif.");
  });
});

describe("plan as of now", () => {
  it("treats an ended trial or Pro as free, like AccountPlan.at", async () => {
    const { planAt } = await import("../src/engine/plan");
    const { extensionAllowed } = await import("../src/store");
    expect(planAt({ kind: "trial", trialEndsAt: now + 1 }, now)).toEqual({ kind: "trial", trialEndsAt: now + 1 });
    expect(planAt({ kind: "trial", trialEndsAt: now }, now)).toEqual({ kind: "free" });
    expect(planAt({ kind: "pro", proUntil: now - 1 }, now)).toEqual({ kind: "free" });
    expect(planAt({ kind: "pro" }, now)).toEqual({ kind: "pro" });
    expect(extensionAllowed(undefined, now)).toBe(true);
    expect(extensionAllowed({ fullName: "" }, now)).toBe(true);
    expect(extensionAllowed({ fullName: "", plan: { kind: "beta" } }, now)).toBe(true);
    expect(extensionAllowed({ fullName: "", plan: { kind: "free" } }, now)).toBe(false);
    expect(extensionAllowed({ fullName: "", plan: { kind: "trial", trialEndsAt: now - 1 } }, now)).toBe(false);
  });
});

describe("subscribe button", () => {
  it("offers Langganan on a trial or free, Perpanjang on Pro that ends, nothing otherwise", async () => {
    const { subscribeLabel, SUBSCRIBE_URL } = await import("../src/engine/plan");
    expect(SUBSCRIBE_URL).toBe("https://scriptgo.id/bayar");
    expect(subscribeLabel({ kind: "beta" }, now)).toBeNull();
    expect(subscribeLabel({ kind: "trial", trialEndsAt: now + 1 }, now)).toBe("Langganan Pro");
    expect(subscribeLabel({ kind: "free" }, now)).toBe("Langganan Pro");
    expect(subscribeLabel({ kind: "pro", proUntil: now + 1 }, now)).toBe("Perpanjang Pro");
    expect(subscribeLabel({ kind: "pro", proUntil: now - 1 }, now)).toBe("Langganan Pro");
    expect(subscribeLabel({ kind: "pro" }, now)).toBeNull();
  });
});
