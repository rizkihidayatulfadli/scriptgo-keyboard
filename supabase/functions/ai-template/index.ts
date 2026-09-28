// ai-template: makes or rewrites a template with a model on OpenRouter, for signed-in users within
// their quota (public.ai_consume; Pro 20 a day, free 5 in total). Only the user's description or
// template text and their field keys are sent to the model: never contacts or chat content.
// Secrets (Supabase → Edge Functions → Secrets): OPENROUTER_API_KEY, optional AI_FALLBACK_MODEL
// (a cheap paid model tried when the free router fails). Design:
// docs/superpowers/specs/2026-09-28-ai-templates-design.md.
import { buildMessages, parseRequest, parseResult } from "./logic.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const OPENROUTER_KEY = Deno.env.get("OPENROUTER_API_KEY") ?? "";
const FALLBACK_MODEL = Deno.env.get("AI_FALLBACK_MODEL") ?? "";
const MODEL_TIMEOUT_MS = 25_000;

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

/** The user id behind [authorization] ("Bearer <access token>"), or null. */
async function userId(authorization: string | null): Promise<string | null> {
  if (!authorization?.startsWith("Bearer ")) return null;
  const res = await fetch(`${SUPABASE_URL}/auth/v1/user`, { headers: { apikey: ANON_KEY, Authorization: authorization } });
  if (!res.ok) return null;
  const user = await res.json();
  return typeof user?.id === "string" ? user.id : null;
}

/** Calls a quota RPC as the service role; returns the quota JSON, or throws with the error message. */
async function rpc(name: "ai_consume" | "ai_refund", target: string): Promise<unknown> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: { apikey: SERVICE_KEY, Authorization: `Bearer ${SERVICE_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify({ target }),
  });
  const body = await res.json().catch(() => null);
  if (!res.ok) throw new Error(body?.message ?? `rpc ${res.status}`);
  return body;
}

/** The model's text reply, or null on any failure (network, timeout, provider error). */
async function complete(messages: ReturnType<typeof buildMessages>): Promise<string | null> {
  const models = ["openrouter/free", ...(FALLBACK_MODEL ? [FALLBACK_MODEL] : [])];
  try {
    const res = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      signal: AbortSignal.timeout(MODEL_TIMEOUT_MS),
      headers: {
        Authorization: `Bearer ${OPENROUTER_KEY}`,
        "Content-Type": "application/json",
        "HTTP-Referer": "https://scriptgo.id",
        "X-Title": "ScriptGo",
      },
      body: JSON.stringify({ models, messages, temperature: 0.4, max_tokens: 800 }),
    });
    if (!res.ok) return null;
    const data = await res.json();
    const text = data?.choices?.[0]?.message?.content;
    return typeof text === "string" ? text : null;
  } catch {
    return null;
  }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS });
  if (req.method !== "POST") return reply(405, { error: "method_not_allowed" });
  if (!OPENROUTER_KEY) return reply(503, { error: "ai_not_configured" });

  const user = await userId(req.headers.get("Authorization"));
  if (!user) return reply(401, { error: "not_signed_in" });

  const request = parseRequest(await req.json().catch(() => null));
  if (!request) return reply(400, { error: "invalid_input" });

  let quota: unknown;
  try {
    quota = await rpc("ai_consume", user);
  } catch (e) {
    const message = (e as Error).message;
    return message.includes("quota_exceeded") ? reply(429, { error: "quota_exceeded" }) : reply(500, { error: "server_error" });
  }

  // Up to two tries: free models sometimes answer with something that is not a usable template.
  const messages = buildMessages(request);
  for (let attempt = 0; attempt < 2; attempt++) {
    const text = await complete(messages);
    const template = text ? parseResult(text, request) : null;
    if (template) return reply(200, { template, quota });
  }
  quota = await rpc("ai_refund", user).catch(() => quota);
  return reply(502, { error: "ai_failed", quota });
});
