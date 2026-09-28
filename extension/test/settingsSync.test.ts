// Trigger and theme in user_settings, the row the app's SettingsSyncer also reads and writes.
import { describe, expect, it } from "vitest";
import { decide, readRemote, validTrigger, writeRemote } from "../src/settingsSync";

describe("readRemote", () => {
  it("reads the app's typed values", () => {
    expect(readRemote({ manager: { trigger_char: { s: "#" }, ask_contact: { b: true } }, app: { app_theme: { s: "dark" } } })).toEqual({
      trigger: "#",
      theme: "dark",
    });
  });

  it("ignores missing, malformed, or invalid values", () => {
    expect(readRemote(null)).toEqual({});
    expect(readRemote({ manager: { trigger_char: { s: "ab" } }, app: { app_theme: { s: "blue" } } })).toEqual({});
    expect(readRemote({ manager: { trigger_char: "#" } })).toEqual({});
    expect(readRemote({ manager: { trigger_char: { s: "a" } } })).toEqual({});
  });
});

describe("writeRemote", () => {
  it("writes the trigger and theme and keeps the app's other settings", () => {
    const row = { keyboard: { vibrate: { b: false } }, manager: { trigger_char: { s: "/" }, space_hold_ms: { i: 600 } }, app: { app_language: { s: "id" } } };
    expect(writeRemote(row, { trigger: "#", theme: "light" })).toEqual({
      keyboard: { vibrate: { b: false } },
      manager: { trigger_char: { s: "#" }, space_hold_ms: { i: 600 } },
      app: { app_language: { s: "id" }, app_theme: { s: "light" } },
    });
    expect(row.manager.trigger_char.s).toBe("/"); // the input is not changed
  });

  it("starts a row when there is none", () => {
    expect(writeRemote(undefined, { trigger: ";", theme: "system" })).toEqual({ manager: { trigger_char: { s: ";" } }, app: { app_theme: { s: "system" } } });
  });
});

describe("decide", () => {
  it("uploads edits made since the last sync, like the app", () => {
    expect(decide(200, 100, 150)).toBe("push");
    expect(decide(200, 100, null)).toBe("push");
  });
  it("applies a newer row when nothing changed here", () => {
    expect(decide(0, 100, 150)).toBe("pull");
    expect(decide(0, 0, 150)).toBe("pull");
  });
  it("does nothing when both sides match", () => {
    expect(decide(100, 150, 150)).toBe("none");
    expect(decide(0, 0, null)).toBe("none");
  });
});

describe("validTrigger", () => {
  it("accepts one symbol only", () => {
    for (const t of ["/", "#", ";", "\\", "!", "@"]) expect(validTrigger(t)).toBe(true);
    for (const t of ["", "ab", "a", "1", "_", "-", " ", 5]) expect(validTrigger(t)).toBe(false);
  });
});
