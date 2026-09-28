// Akun (account.html), opened from the popup: the same account settings as the app's Akun tab.
// Data pribadi (full name), Kata sandi, Passkey (app only), Tema, privacy policy, and deleting the
// account. The background worker talks to Supabase; this page only sends messages.

import { planText, SUBSCRIBE_URL, subscribeLabel } from "./engine/plan";
import { icon } from "./icons";
import { displayName, KEY_PROFILE, KEY_SESSION, KEY_SETTINGS_EDITED_AT, send, type Profile, type Reply, type Session } from "./store";
import { KEY_THEME, THEMES, themeOf, type Theme } from "./theme";
import { brand, h, mountSignIn } from "./ui";

const app = document.getElementById("app")!;

/** A titled card with an icon, like the app's account sections. */
function section(iconName: string, title: string, subtitle: string, ...body: HTMLElement[]): HTMLElement {
  return h(
    "section",
    { class: "tile stack account-card" },
    h("div", { class: "row" }, h("span", { class: "tint" }, icon(iconName, 18)), h("div", { class: "grow tight" }, h("b", {}, title), h("span", { class: "small" }, subtitle))),
    ...body,
  );
}

/** Runs [action] from [button], showing its reply in [status]. */
async function run(button: HTMLButtonElement, status: HTMLElement, action: () => Promise<Reply>): Promise<boolean> {
  button.disabled = true;
  status.textContent = "Menyimpan…";
  status.className = "small";
  const reply = await action().catch(() => ({ ok: false as const, error: "Terjadi kesalahan. Coba lagi." }));
  button.disabled = false;
  status.textContent = reply.ok ? (reply.message ?? "Tersimpan.") : reply.error;
  status.className = reply.ok ? "small" : "small error";
  return reply.ok;
}

/** Akun → Paket: beta, trial with days left, Pro with its end date, or free (as in the app). */
function plan(profile: Profile | undefined): HTMLElement | null {
  if (!profile?.plan) return null;
  const text = planText(profile.plan, Date.now());
  const label = subscribeLabel(profile.plan, Date.now());
  const link = label ? [h("a", { class: "button primary", href: SUBSCRIBE_URL, target: "_blank", rel: "noopener" }, label)] : [];
  const card = section("sparkles", `Paket: ${text.title}`, text.subtitle, ...link);
  card.classList.add("wide");
  return card;
}

function personal(session: Session, profile: Profile | undefined): HTMLElement {
  const name = h("input", { id: "account-name", type: "text", maxlength: "120", autocomplete: "name", placeholder: "Contoh: Rina Hijab" });
  name.value = profile?.fullName ?? "";
  const email = h("input", { id: "account-email", type: "email", readonly: "" });
  email.value = session.email;
  const status = h("p", { class: "small", role: "status" });
  const save = h("button", { class: "primary", type: "submit" }, icon("check", 18), "Simpan nama");
  const form = h(
    "form",
    { class: "stack" },
    h("div", { class: "field" }, h("label", { for: "account-name" }, "Nama lengkap"), name),
    h("div", { class: "field" }, h("label", { for: "account-email" }, "Email"), email, h("span", { class: "hint" }, "Email akun tidak bisa diubah dari sini.")),
    h("div", { class: "row" }, save, status),
  );
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    void run(save, status, () => send({ type: "saveFullName", fullName: name.value }));
  });
  return section("users", "Data pribadi", "Nama ini tampil di popup dan di app ScriptGo di HP.", form);
}

function password(): HTMLElement {
  const first = h("input", { id: "account-password", type: "password", minlength: "8", autocomplete: "new-password" });
  const again = h("input", { id: "account-password-again", type: "password", minlength: "8", autocomplete: "new-password" });
  const status = h("p", { class: "small", role: "status" });
  const save = h("button", { class: "primary", type: "submit" }, icon("lock", 18), "Simpan kata sandi");
  const form = h(
    "form",
    { class: "stack" },
    h("div", { class: "field" }, h("label", { for: "account-password" }, "Kata sandi baru"), first, h("span", { class: "hint" }, "Minimal 8 karakter, campur huruf dan angka.")),
    h("div", { class: "field" }, h("label", { for: "account-password-again" }, "Ulangi kata sandi"), again),
    h("div", { class: "row" }, save, status),
  );
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (first.value !== again.value) {
      status.textContent = "Kedua kata sandi belum sama.";
      status.className = "small error";
      return;
    }
    if (await run(save, status, () => send({ type: "setPassword", password: first.value }))) {
      first.value = "";
      again.value = "";
    }
  });
  return section("lock", "Kata sandi", "Buat atau ganti kata sandi untuk masuk dengan email dan kata sandi.", form);
}

function passkey(): HTMLElement {
  return section(
    "shield-check",
    "Passkey",
    "Masuk dengan sidik jari atau kunci layar HP.",
    h(
      "p",
      { class: "small" },
      "Passkey dibuat dan dipakai di app ScriptGo di HP (Akun → Passkey). Chrome belum mengizinkan extension memakai passkey untuk scriptgo.id, jadi di sini masuk dengan Google, kode email, atau kata sandi.",
    ),
  );
}

async function theme(): Promise<HTMLElement> {
  let current: Theme = themeOf((await chrome.storage.local.get(KEY_THEME))[KEY_THEME]);
  const group = h("div", { class: "chips", role: "radiogroup", "aria-label": "Tema" });
  const draw = () =>
    group.replaceChildren(
      ...THEMES.map(([value, label]) =>
        h(
          "button",
          {
            type: "button",
            class: value === current ? "pill-choice sel" : "pill-choice",
            role: "radio",
            "aria-checked": String(value === current),
            click: async () => {
              current = value;
              await chrome.storage.local.set({ [KEY_THEME]: value, [KEY_SETTINGS_EDITED_AT]: Date.now() });
              void send({ type: "syncNow" });
              draw();
            },
          },
          label,
        ),
      ),
    );
  draw();
  return section("settings-2", "Tema", "Tampilan popup, halaman Kelola, dan form. Ikut ke app di HP.", group);
}

function more(): HTMLElement {
  const status = h("p", { class: "small", role: "status" });
  const remove = h("button", { class: "outline danger-outline", type: "button" }, icon("trash-2", 18), "Hapus akun");
  remove.addEventListener("click", async () => {
    const ok = confirm(
      "Hapus akun ScriptGo?\n\nAkun, template, kontak, field, dan pengaturan yang tersinkron akan dihapus dari server dan tidak bisa dikembalikan. Data yang masih ada di HP tetap di HP.",
    );
    if (!ok) return;
    if (await run(remove, status, () => send({ type: "deleteAccount" }))) setTimeout(() => void draw(), 1500);
  });
  return section(
    "shield-check",
    "Privasi dan akun",
    "Apa yang kami simpan, dan cara menghapusnya.",
    h(
      "div",
      { class: "row wrap" },
      h("a", { class: "button outline", href: "https://scriptgo.id/privacy.html", target: "_blank", rel: "noopener" }, "Kebijakan privasi"),
      h("a", { class: "button outline", href: "https://scriptgo.id/hapus-akun.html", target: "_blank", rel: "noopener" }, "Tentang hapus akun"),
    ),
    h("div", { class: "row" }, remove, status),
  );
}

async function draw(): Promise<void> {
  const stored = await chrome.storage.local.get([KEY_SESSION, KEY_PROFILE]);
  const session = stored[KEY_SESSION] as Session | undefined;
  if (!session) {
    const body = h("div", { class: "stack narrow" });
    app.replaceChildren(brand(40), h("h1", {}, "Akun"), body);
    await mountSignIn(body, () => void draw(), "Masuk dulu untuk mengatur akunmu.");
    return;
  }
  const profile = stored[KEY_PROFILE] as Profile | undefined;
  app.replaceChildren(
    brand(40),
    h("h1", {}, "Akun"),
    h("p", { class: "lead" }, `Masuk sebagai ${displayName(profile, session.email)} (${session.email}).`),
    h("div", { class: "account-grid" }, ...[plan(profile)].filter((x): x is HTMLElement => x !== null), personal(session, profile), password(), await theme(), passkey(), more()),
    h("button", { class: "text small start", click: async () => (await send({ type: "signOut" }), void draw()) }, icon("log-out", 18), "Keluar"),
  );
}

void draw();
