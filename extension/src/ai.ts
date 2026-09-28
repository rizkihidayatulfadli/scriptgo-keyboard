// AI help in the template editor (Kelola): "Buat dengan AI" from a description, "Rapikan dengan AI"
// in a chosen style. The background worker calls the ai-template Edge Function (same as the app,
// docs/superpowers/specs/2026-09-28-ai-templates-design.md); this module holds the wording and the
// checks on what comes back. No DOM and no network here.

export type AiStyle = "polite" | "shorter" | "casual" | "spelling";

/** Rewrite styles, as sent to the function, with the app's labels. */
export const AI_STYLES: readonly (readonly [AiStyle, string])[] = [
  ["polite", "Lebih sopan"],
  ["shorter", "Lebih singkat"],
  ["casual", "Lebih santai"],
  ["spelling", "Perbaiki ejaan"],
];

export const AI_DESCRIPTION_MAX = 500;

export type AiInput = { mode: "create"; description: string } | { mode: "rewrite"; style: AiStyle; title: string; body: string };

export interface AiTemplate {
  title: string;
  shortcut: string;
  body: string;
}

/** Uses left: per day for Pro, in total for free accounts. */
export interface AiQuota {
  limit: number;
  remaining: number;
  daily: boolean;
}

/** The request body for the function; [fields] are the user's field keys (never their values). */
export function aiRequestBody(input: AiInput, fields: string[]): Record<string, unknown> {
  return input.mode === "create"
    ? { mode: "create", description: input.description.trim(), fields }
    : { mode: "rewrite", style: input.style, title: input.title.trim(), body: input.body, fields };
}

/** A quota from ai_quota() or the function's reply, or null when it is not one. */
export function parseQuota(raw: unknown): AiQuota | null {
  if (!raw || typeof raw !== "object") return null;
  const q = raw as Record<string, unknown>;
  if (typeof q.limit !== "number" || typeof q.remaining !== "number") return null;
  return { limit: q.limit, remaining: Math.max(0, q.remaining), daily: q.period === "day" };
}

/** The template in the function's reply, or null when it is not a usable one. */
export function parseAiTemplate(raw: unknown): AiTemplate | null {
  const t = raw && typeof raw === "object" ? (raw as Record<string, unknown>).template : null;
  if (!t || typeof t !== "object") return null;
  const { title, shortcut, body } = t as Record<string, unknown>;
  if (typeof title !== "string" || typeof shortcut !== "string" || typeof body !== "string" || !body.trim()) return null;
  return { title, shortcut, body };
}

export function quotaText(q: AiQuota): string {
  return q.daily ? `Sisa ${q.remaining} dari ${q.limit} hari ini` : `Sisa ${q.remaining} dari ${q.limit} kali gratis`;
}

export function quotaEmptyText(q: AiQuota): string {
  return q.daily ? "Jatah AI hari ini sudah habis. Coba lagi besok." : "Jatah AI gratis sudah habis. Paket Pro dapat 20 kali per hari.";
}

/** A friendly message for a failed call, from the HTTP status (0 = no connection). */
export function aiErrorText(status: number): string {
  if (status === 0) return "Tidak bisa terhubung ke server. Periksa koneksi internet.";
  if (status === 429) return "Jatah AI sudah habis.";
  if (status === 401 || status === 403) return "Sesi berakhir. Silakan masuk lagi.";
  if (status === 503) return "Fitur AI belum aktif. Coba lagi nanti.";
  if (status === 400) return "Deskripsi atau isi template belum bisa dipakai. Coba tulis ulang.";
  return "AI belum berhasil membuat template. Coba lagi; jatahmu tidak berkurang.";
}

export type AiReportReason = "offensive" | "wrong" | "other";

/** Why an AI result is reported (ai_reports.reason), with the app's labels. */
export const AI_REPORT_REASONS: readonly (readonly [AiReportReason, string])[] = [
  ["offensive", "Menyinggung atau tidak pantas"],
  ["wrong", "Salah atau menyesatkan"],
  ["other", "Lainnya"],
];

/** The ai_reports row for a reported result: what was asked, and what the AI answered. */
export function aiReportRow(input: AiInput, result: string, reason: AiReportReason): Record<string, string> {
  return {
    mode: input.mode,
    style: input.mode === "rewrite" ? input.style : "",
    input: (input.mode === "create" ? input.description : input.body).slice(0, 4000),
    result: result.slice(0, 4000),
    reason,
    note: "",
    source: "extension",
  };
}
