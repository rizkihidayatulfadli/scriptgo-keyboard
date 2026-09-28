// Welcome tab, opened once after install (mockup 6a): sign in, pin the extension, try it.

import { icon } from "./icons";
import { KEY_SESSION, type Session } from "./store";
import { brand, h, mountSignIn } from "./ui";

const app = document.getElementById("app")!;

function step(n: number, done: boolean, title: string, text: Node | string, ...extra: HTMLElement[]): HTMLElement {
  return h(
    "section",
    { class: done ? "step done" : "step" },
    h("span", { class: "num", "aria-hidden": "true" }, done ? icon("check", 18) : String(n)),
    h("h2", {}, title),
    h("p", { class: "small" }, text),
    ...extra,
  );
}

async function draw(): Promise<void> {
  const session = (await chrome.storage.local.get(KEY_SESSION))[KEY_SESSION] as Session | undefined;
  const signIn = h("div", { class: "stack" });
  const steps = h(
    "div",
    { class: "steps" },
    step(
      1,
      !!session,
      "Masuk",
      session ? `Masuk sebagai ${session.email}.` : "Pakai akun yang sama dengan app di HP, supaya template dan kontak ikut.",
      ...(session ? [] : [signIn]),
    ),
    step(
      2,
      false,
      "Sematkan extension",
      "Klik ikon puzzle di pojok kanan atas Chrome, lalu ikon pin di samping ScriptGo.",
      h("div", { class: "pinhint" }, icon("puzzle", 18), h("span", {}, "ScriptGo"), icon("pin", 16)),
    ),
    step(
      3,
      false,
      "Coba di WhatsApp Web",
      h("span", {}, "Buka chat mana saja, ketik ", h("kbd", {}, "/salam"), ", lalu tekan Enter."),
      h(
        "a",
        { class: "button primary", href: "https://web.whatsapp.com/", target: "_blank", rel: "noopener" },
        icon("message-circle", 18),
        "Buka WhatsApp Web",
      ),
    ),
  );
  app.replaceChildren(brand(40), h("h1", {}, session ? "Siap dipakai. Dua langkah lagi." : "Extension terpasang. Tiga langkah lagi."), steps);
  if (!session) await mountSignIn(signIn, () => void draw(), "");
}

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && KEY_SESSION in changes) void draw();
});
void draw();
