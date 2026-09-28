import { describe, expect, it } from "vitest";
import { base64Url, challenge, newVerifier, parseRedirect } from "../src/engine/pkce";

describe("pkce", () => {
  it("matches the RFC 7636 example", async () => {
    expect(await challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).toBe("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
  });

  it("encodes base64url without padding", () => {
    expect(base64Url(new Uint8Array([251, 255, 191]))).toBe("-_-_");
    expect(base64Url(new Uint8Array([1]))).toBe("AQ");
  });

  it("makes 64-character verifiers from the allowed alphabet", () => {
    const v = newVerifier();
    expect(v).toHaveLength(64);
    expect(v).toMatch(/^[A-Za-z0-9\-._~]+$/);
  });

  it("reads the code from the query or the fragment", () => {
    expect(parseRedirect("https://abc.chromiumapp.org/?code=xyz")).toEqual({ kind: "code", code: "xyz" });
    expect(parseRedirect("chrome-extension://abc/auth.html#code=q1")).toEqual({ kind: "code", code: "q1" });
    expect(parseRedirect("https://abc.chromiumapp.org/#error=access_denied&error_description=Email+link+is+invalid")).toEqual({
      kind: "error",
      message: "Email link is invalid",
    });
    expect(parseRedirect("not a url")).toEqual({ kind: "unknown" });
  });
});
