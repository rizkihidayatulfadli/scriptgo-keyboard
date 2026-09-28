// Shared building blocks for the extension pages (popup, welcome tab, sign-in link page).

import { icon } from "./icons";
import { send, type Reply } from "./store";
import { followTheme } from "./theme";

// Every extension page that uses these blocks also follows Akun → Tema.
followTheme();

export type Attrs = Record<string, string | boolean | ((e: Event) => void)>;

export function h<K extends keyof HTMLElementTagNameMap>(tag: K, attrs: Attrs = {}, ...children: (Node | string)[]) {
  const el = document.createElement(tag);
  for (const [name, value] of Object.entries(attrs)) {
    if (typeof value === "function") el.addEventListener(name, value);
    else if (typeof value === "boolean") (el as unknown as Record<string, boolean>)[name] = value;
    else el.setAttribute(name, value);
  }
  el.append(...children);
  return el;
}

/** The ScriptGo mark with the product name. */
export function brand(size = 32): HTMLElement {
  return h("div", { class: "brand" }, h("img", { src: "icons/icon48.png", alt: "", width: String(size), height: String(size) }), "ScriptGo");
}

const GOOGLE_PATHS: Array<[string, string]> = [
  ["#EA4335", "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"],
  ["#4285F4", "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"],
  ["#FBBC05", "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"],
  ["#34A853", "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"],
];

function googleLogo(): SVGSVGElement {
  const ns = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("viewBox", "0 0 48 48");
  svg.setAttribute("width", "20");
  svg.setAttribute("height", "20");
  svg.setAttribute("aria-hidden", "true");
  for (const [fill, d] of GOOGLE_PATHS) {
    const path = document.createElementNS(ns, "path");
    path.setAttribute("fill", fill);
    path.setAttribute("d", d);
    svg.append(path);
  }
  return svg;
}

export function orDivider(): HTMLElement {
  return h("div", { class: "or" }, "atau");
}

// --- Sign-in (mockup 6b and 6c) -------------------------------------------------------------

/** Survives the popup closing while the user checks their email. */
const KEY_PENDING = "pendingSignIn";
const RESEND_SECONDS = 60;

interface Pending {
  email: string;
  sentAt: number;
}

async function readPending(): Promise<Pending | null> {
  return ((await chrome.storage.local.get(KEY_PENDING))[KEY_PENDING] as Pending | undefined) ?? null;
}

/**
 * Renders the sign-in flow into [root]: Google, email → 6-digit code (the same email also has
 * a sign-in link), or email and password. Calls [onSignedIn] after a successful sign-in.
 */
export async function mountSignIn(root: HTMLElement, onSignedIn: () => void, intro: string): Promise<void> {
  let busy = false;
  let error = "";
  let timer: number | undefined;
  let usePassword = false;
  // Kept across redraws, so switching to a password does not clear the email.
  let typedEmail = "";

  async function run(action: () => Promise<Reply>): Promise<Reply> {
    busy = true;
    error = "";
    await render();
    let reply: Reply;
    try {
      reply = await action();
    } catch {
      reply = { ok: false, error: "Terjadi kesalahan. Coba lagi." };
    }
    busy = false;
    if (!reply.ok) error = reply.error;
    return reply;
  }

  async function render(): Promise<void> {
    clearInterval(timer);
    const pending = await readPending();
    root.replaceChildren(...(pending ? codeView(pending) : chooseView()));
  }

  function status(): HTMLElement[] {
    return error ? [h("p", { class: "error", role: "alert" }, error)] : [];
  }

  function chooseView(): HTMLElement[] {
    const email = h("input", { type: "email", id: "email", placeholder: "nama@tokokamu.id", autocomplete: "email", disabled: busy });
    email.value = typedEmail;
    const password = h("input", { type: "password", id: "password", autocomplete: "current-password", maxlength: "72", disabled: busy });
    const sendButton = h(
      "button",
      { class: "primary full", disabled: true, type: "submit" },
      usePassword ? (busy ? "Masuk…" : "Masuk") : busy ? "Mengirim…" : "Kirim kode",
    );
    const valid = () => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.value.trim()) && (!usePassword || password.value !== "");
    const refresh = () => {
      typedEmail = email.value;
      sendButton.disabled = busy || !valid();
    };
    email.addEventListener("input", refresh);
    password.addEventListener("input", refresh);
    refresh();
    const passwordField = usePassword ? [h("div", { class: "field" }, h("label", { for: "password" }, "Kata sandi"), password)] : [];
    // Switches between the emailed code and a password set in the app (Akun → Kata sandi).
    const toggle = h(
      "button",
      {
        class: "text small",
        type: "button",
        disabled: busy,
        click: async () => {
          usePassword = !usePassword;
          error = "";
          await render();
          (usePassword ? document.getElementById("password") : document.getElementById("email"))?.focus();
        },
      },
      usePassword ? "Pakai kode email saja" : "Masuk dengan kata sandi",
    );
    const form = h("form", { class: "stack" }, h("div", { class: "field" }, h("label", { for: "email" }, "Email"), email), ...passwordField, sendButton, toggle);
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      if (!valid()) return;
      const address = email.value.trim();
      if (usePassword) {
        const reply = await run(() => send({ type: "passwordSignIn", email: address, password: password.value }));
        if (reply.ok) onSignedIn();
        else await render();
        return;
      }
      const reply = await run(() => send({ type: "sendCode", email: address }));
      if (reply.ok) await chrome.storage.local.set({ [KEY_PENDING]: { email: address, sentAt: Date.now() } satisfies Pending });
      await render();
    });
    const google = h(
      "button",
      {
        class: "google full",
        disabled: busy,
        click: async () => {
          // The popup may close while Google's window is open; the background finishes the sign-in.
          const reply = await run(() => send({ type: "googleSignIn" }));
          if (reply.ok) onSignedIn();
          else await render();
        },
      },
      googleLogo(),
      "Lanjut dengan Google",
    );
    return [
      ...(intro ? [h("p", { class: "lead" }, intro)] : []),
      google,
      orDivider(),
      form,
      ...status(),
      h(
        "p",
        { class: "small center" },
        usePassword
          ? "Kata sandi dibuat di app ScriptGo di HP: Akun → Kata sandi."
          : "Belum pakai ScriptGo di HP? Tetap bisa masuk, akunmu dibuat otomatis.",
      ),
    ];
  }

  function codeView(pending: Pending): HTMLElement[] {
    const input = h("input", {
      class: "code-input",
      inputmode: "numeric",
      autocomplete: "one-time-code",
      maxlength: "6",
      "aria-label": "Kode 6 digit",
      disabled: busy,
    });
    const boxes = Array.from({ length: 6 }, () => h("i"));
    const paint = () => {
      const v = input.value;
      boxes.forEach((b, i) => {
        b.textContent = v[i] ?? "";
        b.className = i === Math.min(v.length, 5) && document.activeElement === input ? "cur" : "";
      });
    };
    input.addEventListener("focus", paint);
    input.addEventListener("blur", paint);
    input.addEventListener("input", async () => {
      input.value = input.value.replace(/\D/g, "").slice(0, 6);
      paint();
      if (input.value.length === 6) {
        const reply = await run(() => send({ type: "verifyCode", email: pending.email, code: input.value }));
        if (reply.ok) {
          await chrome.storage.local.remove(KEY_PENDING);
          onSignedIn();
        } else await render();
      }
    });

    const resend = h("span", { class: "small" });
    const resendButton = h("button", { class: "text small", type: "button" }, "Kirim ulang");
    const tick = () => {
      const left = RESEND_SECONDS - Math.floor((Date.now() - pending.sentAt) / 1000);
      if (left > 0) {
        resend.textContent = `Kirim ulang dalam 0:${String(left).padStart(2, "0")}`;
        resendButton.hidden = true;
      } else {
        resend.textContent = "";
        resendButton.hidden = false;
        clearInterval(timer);
      }
    };
    resendButton.addEventListener("click", async () => {
      const reply = await run(() => send({ type: "sendCode", email: pending.email }));
      if (reply.ok) await chrome.storage.local.set({ [KEY_PENDING]: { ...pending, sentAt: Date.now() } });
      await render();
    });
    tick();
    timer = window.setInterval(tick, 1000);

    const back = async () => {
      await chrome.storage.local.remove(KEY_PENDING);
      error = "";
      await render();
    };
    setTimeout(() => input.focus());
    return [
      h(
        "div",
        { class: "row" },
        h("button", { class: "icon-button", type: "button", "aria-label": "Kembali", click: back }, icon("chevron-left")),
        h("h2", {}, "Cek email kamu"),
      ),
      h("p", { class: "small" }, "Kode dikirim ke ", h("b", {}, pending.email), "."),
      h("div", { class: "code" }, ...boxes, input),
      h("div", { class: "row between" }, resend, resendButton, h("button", { class: "text small", type: "button", click: back }, "Ganti email")),
      ...status(),
      h(
        "div",
        { class: "note" },
        h("span", { class: "tint" }, icon("mail", 18)),
        h("span", {}, "Atau klik ", h("b", {}, "Masuk ke ScriptGo"), " di email itu. Tab baru terbuka dan extension langsung masuk."),
      ),
    ];
  }

  await render();
}
