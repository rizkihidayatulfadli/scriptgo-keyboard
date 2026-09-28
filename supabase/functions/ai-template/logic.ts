// Pure logic of the ai-template Edge Function: request checks, the prompt, and checks on what the
// model returns. No Deno or network APIs here, so `node --test` can run logic.test.ts.
// Template syntax: docs/template-syntax.md.

export type Style = "polite" | "shorter" | "casual" | "spelling";

export type AiRequest =
  | { mode: "create"; description: string; fields: string[] }
  | { mode: "rewrite"; style: Style; title: string; body: string; fields: string[] };

export type AiTemplate = { title: string; shortcut: string; body: string };

export type Message = { role: "system" | "user"; content: string };

const STYLES: Record<Style, string> = {
  polite: "lebih sopan dan hangat, tetap singkat",
  shorter: "lebih singkat dan padat, buang kata yang tidak perlu",
  casual: "lebih santai dan akrab, seperti seller yang ramah",
  spelling: "perbaiki ejaan dan tanda baca saja, jangan ubah gaya atau isinya",
};

const KEY = /^[a-z0-9_]{1,40}$/;
const MAX_DESCRIPTION = 500;
const MAX_BODY = 4000;
const MAX_RESULT_BODY = 2000;
const MAX_TITLE = 60;

const text = (value: unknown, max: number): string | null => {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 && trimmed.length <= max ? trimmed : null;
};

/** The request body as sent by the app or extension, or null when it is not usable. */
export function parseRequest(raw: unknown): AiRequest | null {
  if (!raw || typeof raw !== "object") return null;
  const r = raw as Record<string, unknown>;
  // Field keys only (never values): lets the model reuse {kota} rather than inventing {kota_tujuan}.
  const fields = Array.isArray(r.fields)
    ? r.fields.filter((f): f is string => typeof f === "string" && KEY.test(f)).slice(0, 30)
    : [];
  if (r.mode === "create") {
    const description = text(r.description, MAX_DESCRIPTION);
    return description ? { mode: "create", description, fields } : null;
  }
  if (r.mode === "rewrite") {
    const style = r.style as Style;
    const title = text(r.title, MAX_TITLE) ?? "";
    const body = text(r.body, MAX_BODY);
    return style in STYLES && body ? { mode: "rewrite", style, title, body, fields } : null;
  }
  return null;
}

const SYSTEM = `Kamu membantu seller, CS, dan admin di Indonesia membuat template balasan chat untuk aplikasi ScriptGo.
Tulis dalam Bahasa Indonesia yang wajar untuk chat WhatsApp: ramah, jelas, tidak kaku, tanpa tanda kutip di awal dan akhir.

Sintaks template:
- {nama} = nama pelanggan, {nomor} = nomor HP pelanggan.
- Variabel lain ditulis {key} dengan key huruf kecil, angka, dan garis bawah, misalnya {kota}, {ongkir}, {total}.
- Nilai default: {nama|Kak}. Pilihan: {kurir:JNE,J&T,SiCepat} (opsi pertama jadi default).
- Bawaan yang terisi sendiri: {@tanggal}, {@hari}, {@jam}. {@kursor} menandai posisi kursor.
- Kurung kurawal biasa ditulis \\{ dan \\}.
- Pakai variabel hanya untuk data yang berubah per pelanggan atau per pesanan. Jangan mengarang nomor rekening, harga, atau alamat; jadikan variabel.

Balas HANYA dengan satu objek JSON tanpa teks lain:
{"title": "judul singkat, maksimal 40 karakter", "shortcut": "kata pendek huruf kecil tanpa spasi", "body": "isi template"}`;

/** Chat messages for OpenRouter. The user turn holds only what the user wrote (and field keys). */
export function buildMessages(request: AiRequest): Message[] {
  const fields = request.fields.length
    ? `\nField kontak yang sudah ada (pakai jika cocok): ${request.fields.map((f) => `{${f}}`).join(", ")}`
    : "";
  const user =
    request.mode === "create"
      ? `Buat template dari deskripsi ini:\n${request.description}${fields}`
      : `Tulis ulang template ini agar ${STYLES[request.style]}.
Semua variabel {...} yang ada harus tetap ada dan ditulis persis sama. Judul boleh tetap.
Judul: ${request.title || "(tanpa judul)"}
Isi:
${request.body}${fields}`;
  return [
    { role: "system", content: SYSTEM },
    { role: "user", content: user },
  ];
}

/** Variable keys in [body] (lowercased; built-ins keep their @), in order, without duplicates. */
export function variablesOf(body: string): string[] {
  const keys: string[] = [];
  const pattern = /\\[{}\\]|\{(@?[A-Za-z0-9_]+)(?:[|:][^}]*)?\}/g;
  for (const match of body.matchAll(pattern)) {
    const key = match[1]?.toLowerCase();
    if (key && !keys.includes(key)) keys.push(key);
  }
  return keys;
}

const toShortcut = (value: string): string =>
  value
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, "_")
    .replace(/^[_-]+|[_-]+$/g, "")
    .slice(0, 32);

/**
 * The template in the model's reply, or null when it cannot be used: not JSON, empty or overlong
 * parts, or (for a rewrite) a variable of the original went missing.
 */
export function parseResult(reply: string, request: AiRequest): AiTemplate | null {
  const start = reply.indexOf("{");
  const end = reply.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  let data: Record<string, unknown>;
  try {
    data = JSON.parse(reply.slice(start, end + 1));
  } catch {
    return null;
  }
  const title = text(data.title, MAX_TITLE);
  const body = text(data.body, MAX_RESULT_BODY);
  const shortcut = typeof data.shortcut === "string" ? toShortcut(data.shortcut) : "";
  if (!title || !body || !shortcut) return null;
  if (request.mode === "rewrite") {
    const kept = variablesOf(body);
    if (variablesOf(request.body).some((key) => !kept.includes(key))) return null;
  }
  return { title, shortcut, body };
}
