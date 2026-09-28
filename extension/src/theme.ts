// Tema (Akun → Tema): light, dark, or follow the system, for every extension page. Stored in
// chrome.storage.local like the other settings; ui.css reads data-theme on <html>.

export type Theme = "system" | "light" | "dark";
export const KEY_THEME = "theme";
export const THEMES: ReadonlyArray<[Theme, string]> = [
  ["system", "Ikuti sistem"],
  ["light", "Terang"],
  ["dark", "Gelap"],
];

export const themeOf = (value: unknown): Theme => (value === "light" || value === "dark" ? value : "system");

function apply(theme: Theme): void {
  if (theme === "system") delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = theme;
}

/** Applies the saved theme now and whenever it changes (e.g. from another open page). */
export function followTheme(): void {
  void chrome.storage.local.get(KEY_THEME).then((stored) => apply(themeOf(stored[KEY_THEME])));
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === "local" && KEY_THEME in changes) apply(themeOf(changes[KEY_THEME].newValue));
  });
}
