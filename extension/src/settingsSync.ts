// Settings that follow the account (table user_settings, shared with the app's SettingsSyncer):
// the trigger character (`manager.trigger_char`) and the theme (`app.app_theme`). Values are stored
// typed like the app's SharedPreferences, `{"s": "#"}`. Everything else in the row belongs to the
// app and is kept as is. No network here; background.ts reads and writes the row.

import { type Theme } from "./theme";

export interface SyncedSettings {
  trigger: string;
  theme: Theme;
}

type Json = Record<string, unknown>;

/** A trigger the keyboard and the extension both accept: one symbol, not a letter or digit. */
export const validTrigger = (value: unknown): value is string =>
  typeof value === "string" && [...value].length === 1 && !/[\p{L}\p{Nd}_\s-]/u.test(value);

const stringAt = (data: Json, group: string, key: string): unknown => {
  const g = data[group];
  const v = g && typeof g === "object" ? (g as Json)[key] : undefined;
  return v && typeof v === "object" ? (v as Json).s : undefined;
};

/** The trigger and theme in the server row, when present and valid. */
export function readRemote(data: unknown): Partial<SyncedSettings> {
  const d = (data && typeof data === "object" ? data : {}) as Json;
  const out: Partial<SyncedSettings> = {};
  const trigger = stringAt(d, "manager", "trigger_char");
  if (validTrigger(trigger)) out.trigger = trigger;
  const theme = stringAt(d, "app", "app_theme");
  if (theme === "light" || theme === "dark" || theme === "system") out.theme = theme;
  return out;
}

/** The server row's data with this browser's trigger and theme written in, other keys untouched. */
export function writeRemote(data: unknown, settings: SyncedSettings): Json {
  const d = structuredClone((data && typeof data === "object" ? data : {}) as Json);
  const group = (name: string): Json => {
    const g = d[name];
    if (!g || typeof g !== "object") d[name] = {};
    return d[name] as Json;
  };
  group("manager").trigger_char = { s: settings.trigger };
  group("app").app_theme = { s: settings.theme };
  return d;
}

/**
 * What a sync does, like the app: local edits since the last sync win and are uploaded; otherwise
 * a newer server row is applied. Times are epoch ms (`remoteAt` is the row's updated_at, or null
 * when there is no row yet).
 */
export function decide(editedAt: number, syncedAt: number, remoteAt: number | null): "push" | "pull" | "none" {
  if (editedAt > syncedAt) return "push";
  if (remoteAt !== null && remoteAt > syncedAt) return "pull";
  return "none";
}
