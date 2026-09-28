// Opened by the sign-in link in the email (redirect to chrome-extension://…/auth.html?code=…):
// exchanges the code with the verifier kept by the background worker, then says it is done.

import { icon } from "./icons";
import { send } from "./store";
import { brand, h } from "./ui";

const app = document.getElementById("app")!;

async function main(): Promise<void> {
  app.replaceChildren(brand(), h("h1", {}, "Menyelesaikan masuk…"));
  const reply = await send({ type: "completeLink", url: location.href }).catch(() => ({ ok: false as const, error: "Terjadi kesalahan" }));
  // Drop the code from the address bar so a reload or a shared screenshot cannot reuse it.
  history.replaceState(null, "", location.pathname);
  if (reply.ok) {
    app.replaceChildren(
      brand(),
      h("span", { class: "done-badge" }, icon("check", 30)),
      h("h1", {}, "Kamu sudah masuk"),
      h("p", { class: "lead" }, "Template dan kontakmu sudah ada di browser ini. Buka WhatsApp Web atau situs chat lain, lalu ketik / untuk memakai template."),
      h("a", { class: "button primary", href: "https://web.whatsapp.com/" }, "Buka WhatsApp Web"),
    );
  } else {
    app.replaceChildren(
      brand(),
      h("h1", {}, "Belum berhasil masuk"),
      h("p", { class: "error" }, reply.error),
      h("p", { class: "lead" }, "Buka ikon ScriptGo di toolbar Chrome dan masukkan kode 6 digit dari email yang sama."),
    );
  }
}

void main();
