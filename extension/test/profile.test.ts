// The name in the popup's profile card.
import { describe, expect, it } from "vitest";
import { displayName, fullNameOf, referralLink, referralOf, referralShareText } from "../src/store";

describe("fullNameOf", () => {
  it("prefers full_name, then Google's name", () => {
    expect(fullNameOf({ user_metadata: { full_name: " Rina Hijab ", name: "rina" } })).toBe("Rina Hijab");
    expect(fullNameOf({ user_metadata: { full_name: "", name: "Budi Santoso" } })).toBe("Budi Santoso");
  });

  it("is empty without a name", () => {
    expect(fullNameOf({ user_metadata: {} })).toBe("");
    expect(fullNameOf({})).toBe("");
    expect(fullNameOf(null)).toBe("");
    expect(fullNameOf({ user_metadata: { full_name: 42 } })).toBe("");
  });
});

describe("displayName", () => {
  it("shows the full name, else the email before @", () => {
    expect(displayName({ fullName: "Rizki Fadli" }, "rizki@contoh.id")).toBe("Rizki Fadli");
    expect(displayName({ fullName: "  " }, "rizki@contoh.id")).toBe("rizki");
    expect(displayName(undefined, "rizki@contoh.id")).toBe("rizki");
  });

  it("ignores a profile stored by an older version (business name only)", () => {
    expect(displayName({ businessName: "Boosthenics" } as never, "rizki@contoh.id")).toBe("rizki");
  });
});

describe("referral", () => {
  it("reads my_referral's code and friend count", () => {
    expect(referralOf({ code: "abc23xyz", joined: 2 })).toEqual({ referralCode: "abc23xyz", referralJoined: 2 });
    expect(referralOf({ code: "abc23xyz" })).toEqual({ referralCode: "abc23xyz", referralJoined: 0 });
  });

  it("ignores a missing or malformed answer", () => {
    expect(referralOf(null)).toEqual({});
    expect(referralOf({ code: "../evil" })).toEqual({});
    expect(referralOf({ code: 42 })).toEqual({});
  });

  it("links to the landing page like /konfirmasi, and shares that link", () => {
    expect(referralLink("abc23xyz")).toBe("https://scriptgo.id/?ref=abc23xyz");
    expect(referralShareText("https://scriptgo.id/?ref=abc23xyz")).toContain("https://scriptgo.id/?ref=abc23xyz");
  });
});
