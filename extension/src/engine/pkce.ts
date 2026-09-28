// PKCE (RFC 7636) for Google sign-in and the email sign-in link, and parsing of the redirect that
// carries the code back. Port of engine/src/main/kotlin/com/magickeyboard/auth/Pkce.kt.

const VERIFIER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

/** A 64-character random code verifier. */
export function newVerifier(random: (n: number) => Uint8Array = (n) => crypto.getRandomValues(new Uint8Array(n))): string {
  return Array.from(random(64), (b) => VERIFIER_CHARS[b % VERIFIER_CHARS.length]).join("");
}

export function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** S256 code challenge for [verifier]. */
export async function challenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

export type AuthRedirect = { kind: "code"; code: string } | { kind: "error"; message: string } | { kind: "unknown" };

/** Reads `code` or `error_description` from the query or fragment of a sign-in redirect URL. */
export function parseRedirect(url: string): AuthRedirect {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return { kind: "unknown" };
  }
  const params = new URLSearchParams(parsed.search);
  new URLSearchParams(parsed.hash.replace(/^#/, "")).forEach((value, key) => {
    if (!params.has(key)) params.set(key, value);
  });
  const code = params.get("code");
  if (code) return { kind: "code", code };
  const error = params.get("error_description") ?? params.get("error");
  if (error) return { kind: "error", message: error.replace(/\+/g, " ") };
  return { kind: "unknown" };
}
