// The account's plan from my_plan() (port of engine/.../plan/AccountPlan.kt, PRD §9).
// beta: the closed beta, all Pro features. trial: 14 days of Pro after release. pro: paid (or
// granted). free: after the trial, with the free limits.

export type PlanKind = "beta" | "trial" | "pro" | "free";

export interface AccountPlan {
  kind: PlanKind;
  /** Epoch ms when the trial ends (trial only). */
  trialEndsAt?: number;
  /** Epoch ms when Pro ends (pro only); absent = no end date. */
  proUntil?: number;
}

const DAY = 86_400_000;

const time = (value: unknown): number | undefined => {
  if (typeof value !== "string" || !value) return undefined;
  const ms = Date.parse(value);
  return Number.isNaN(ms) ? undefined : ms;
};

/** The plan in my_plan()'s answer, or null for an unknown one (callers keep what they had). */
export function parsePlan(json: unknown): AccountPlan | null {
  const p = (json && typeof json === "object" ? json : {}) as Record<string, unknown>;
  switch (p.plan) {
    case "beta":
    case "free":
      return { kind: p.plan };
    case "trial": {
      const trialEndsAt = time(p.trial_ends_at);
      return trialEndsAt === undefined ? null : { kind: "trial", trialEndsAt };
    }
    case "pro": {
      const proUntil = time(p.pro_until);
      return proUntil === undefined ? { kind: "pro" } : { kind: "pro", proUntil };
    }
    default:
      return null;
  }
}

/** The plan as of [now]: a trial or Pro whose end has passed is free (like AccountPlan.at). */
export function planAt(plan: AccountPlan, now: number): AccountPlan {
  const end = plan.kind === "trial" ? plan.trialEndsAt : plan.kind === "pro" ? plan.proUntil : undefined;
  return end !== undefined && end <= now ? { kind: "free" } : plan;
}

/** Whether Pro features (sync, extension, no limits) are open. */
export const hasProFeatures = (plan: AccountPlan) => plan.kind !== "free";

/** Days left until the trial or Pro ends, rounded up, 0 once passed; null without an end. */
export function daysLeft(plan: AccountPlan, now: number): number | null {
  const end = plan.kind === "trial" ? plan.trialEndsAt : plan.kind === "pro" ? plan.proUntil : undefined;
  if (end === undefined) return null;
  return end <= now ? 0 : Math.ceil((end - now) / DAY);
}

const dateText = (ms: number) =>
  new Date(ms).toLocaleDateString("id-ID", { day: "numeric", month: "short", year: "numeric", timeZone: "Asia/Jakarta" });

/** The plan's name and a line about it, as in the app's Akun → Paket. */
export function planText(plan: AccountPlan, now: number): { title: string; subtitle: string } {
  switch (plan.kind) {
    case "beta":
      return { title: "Beta", subtitle: "Semua fitur Pro gratis selama masa uji." };
    case "trial": {
      const days = daysLeft(plan, now) ?? 0;
      return { title: "Trial Pro", subtitle: days > 0 ? `Berakhir ${days} hari lagi (${dateText(plan.trialEndsAt!)}).` : "Berakhir hari ini." };
    }
    case "pro":
      return { title: "Pro", subtitle: plan.proUntil === undefined ? "Aktif." : `Aktif sampai ${dateText(plan.proUntil)}.` };
    case "free":
      return { title: "Gratis", subtitle: "Maksimal 20 template dan 30 pemakaian. Sync dan Chrome extension khusus Pro." };
  }
}

/** Where Pro is bought (scriptgo.id/bayar, Duitku). Chrome allows the link; the Android app never shows it. */
export const SUBSCRIBE_URL = "https://scriptgo.id/bayar";

/**
 * The Akun → Paket button: subscribe on a trial or the free plan, extend a Pro that ends; none in
 * the beta or for Pro without an end date.
 */
export function subscribeLabel(plan: AccountPlan, now: number): string | null {
  const current = planAt(plan, now);
  if (current.kind === "trial" || current.kind === "free") return "Langganan Pro";
  if (current.kind === "pro" && current.proUntil !== undefined) return "Perpanjang Pro";
  return null;
}
