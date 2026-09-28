// Toolbar popup: sign in (mockup 6b, 6c), then the account, this site's switch, stats, sync, and
// the trigger character (mockup 6g).

import { SUBSCRIBE_URL } from "./engine/plan";
import { formatDuration, secondsSaved } from "./engine/trigger";
import { icon } from "./icons";
import {
  KEY_CHARS,
  KEY_LAST_SYNC,
  KEY_PROFILE,
  KEY_SESSION,
  KEY_SETTINGS,
  KEY_SETTINGS_EDITED_AT,
  displayName,
  referralLink,
  EXTENSION_PRO_ONLY,
  extensionAllowed,
  referralShareText,
  loadData,
  send,
  type Profile,
  type Reply,
  type Session,
} from "./store";
import { brand, h, mountSignIn } from "./ui";

const app = document.getElementById("app")!;
let busy = false;
let message = "";

async function perform(action: () => Promise<Reply>): Promise<void> {
  busy = true;
  message = "";
  await draw();
  let reply: Reply;
  try {
    reply = await action();
  } catch {
    reply = { ok: false, error: "Terjadi kesalahan" };
  }
  busy = false;
  message = reply.ok ? (reply.message ?? "") : reply.error;
  await draw();
}

function relative(time: number): string {
  const minutes = Math.floor((Date.now() - time) / 60_000);
  if (minutes < 1) return "baru saja";
  if (minutes < 60) return `${minutes} menit lalu`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} jam lalu`;
  return new Date(time).toLocaleDateString("id-ID", { day: "numeric", month: "long", year: "numeric" });
}

/** Hostname of the tab the popup was opened on (activeTab), or null on browser pages. */
async function currentSite(): Promise<string | null> {
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const url = tab?.url ? new URL(tab.url) : null;
    return url && /^https?:$/.test(url.protocol) ? url.hostname : null;
  } catch {
    return null;
  }
}

async function draw(): Promise<void> {
  const stored = await chrome.storage.local.get([KEY_SESSION, KEY_LAST_SYNC, KEY_CHARS, KEY_PROFILE]);
  const session = stored[KEY_SESSION] as Session | undefined;
  if (!session) {
    const body = h("div", { class: "stack" });
    app.replaceChildren(brand(), body);
    // After a session ended (e.g. a failed sync), say why the sign-in screen is back.
    await mountSignIn(body, () => void draw(), message || "Masuk dengan akun yang sama seperti di HP. Template dan kontakmu langsung tersedia di sini.");
    return;
  }
  app.replaceChildren(...(await signedIn(session, stored)));
}

async function signedIn(session: Session, stored: Record<string, unknown>): Promise<HTMLElement[]> {
  const data = await loadData();
  const profile = stored[KEY_PROFILE] as Profile | undefined;
  const name = displayName(profile, session.email);
  const lastSync = stored[KEY_LAST_SYNC] as number | undefined;
  const saved = formatDuration(secondsSaved(Number(stored[KEY_CHARS] ?? 0)));
  const site = await currentSite();

  // The name opens Akun (account.html): name, password, theme, privacy.
  const header = h(
    "button",
    { class: "row profile-head", title: "Buka pengaturan akun", click: () => void chrome.tabs.create({ url: chrome.runtime.getURL("account.html") }) },
    h("span", { class: "badge-letter", "aria-hidden": "true" }, (name.trim()[0] ?? "S").toUpperCase()),
    h("div", { class: "grow tight" }, h("b", { class: "name" }, name), h("span", { class: "small" }, session.email)),
  );

  const siteCard: HTMLElement[] = [];
  if (site) {
    const active = !data.settings.disabledSites.includes(site);
    const toggle = h("button", { class: "switch", role: "switch", "aria-checked": String(active), "aria-label": `Aktif di ${site}` });
    toggle.addEventListener("click", async () => {
      const disabled = new Set(data.settings.disabledSites);
      if (active) disabled.add(site);
      else disabled.delete(site);
      await chrome.storage.local.set({ [KEY_SETTINGS]: { ...data.settings, disabledSites: [...disabled] } });
      message = active ? `ScriptGo dimatikan di ${site}.` : `ScriptGo aktif lagi di ${site}.`;
      await draw();
    });
    siteCard.push(
      h(
        "div",
        { class: "tile row" },
        h("span", { class: "tint" }, icon("globe", 18)),
        h("div", { class: "grow tight" }, h("b", {}, "Aktif di situs ini"), h("span", { class: "small" }, site)),
        toggle,
      ),
    );
  }

  const trigger = h("input", { class: "trigger", maxlength: "1", "aria-label": "Karakter pemicu" });
  trigger.value = data.settings.trigger;
  trigger.addEventListener("change", async () => {
    const value = trigger.value.trim();
    if (value.length !== 1 || /[\p{L}\p{Nd}_-]/u.test(value)) {
      message = "Pemicu harus satu simbol, misalnya / atau ;";
    } else {
      await chrome.storage.local.set({ [KEY_SETTINGS]: { ...data.settings, trigger: value }, [KEY_SETTINGS_EDITED_AT]: Date.now() });
      message = `Pemicu diganti ke ${value}. Ikut ke app di HP setelah sinkron.`;
      void send({ type: "syncNow" });
    }
    await draw();
  });

  // "Ajak teman": the account's referral link (filled in by loadProfile on sign-in and sync).
  const referral: HTMLElement[] = [];
  if (profile?.referralCode) {
    const link = referralLink(profile.referralCode);
    const joined = profile.referralJoined ?? 0;
    const field = h("input", { class: "grow", readonly: "", "aria-label": "Link ajak teman" });
    field.value = link;
    const copy = h("button", { class: "outline small" }, "Salin");
    copy.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(link);
        copy.textContent = "Tersalin";
      } catch {
        field.select();
      }
    });
    referral.push(
      h(
        "div",
        { class: "tile stack refer" },
        h(
          "div",
          { class: "row" },
          h("span", { class: "tint" }, icon("users", 18)),
          h(
            "div",
            { class: "grow tight" },
            h("b", {}, "Ajak teman"),
            h("span", { class: "small" }, joined > 0 ? `${joined} teman sudah daftar. +1 bulan Pro gratis per teman (maks. 6)` : "+1 bulan Pro gratis per teman yang daftar (maks. 6)"),
          ),
        ),
        h("div", { class: "row" }, field, copy),
        h(
          "button",
          { class: "outline small", click: () => void chrome.tabs.create({ url: `https://wa.me/?text=${encodeURIComponent(referralShareText(link))}` }) },
          icon("send", 18),
          "Bagikan ke WhatsApp",
        ),
      ),
    );
  }

  const stat = (value: string, label: string) => h("div", { class: "stat" }, h("b", {}, value), h("span", {}, label));

  // Free plan: no suggestions and no sync (PRD §9). Chrome has no Play-style rule against a link.
  const locked: HTMLElement[] = extensionAllowed(profile, Date.now())
    ? []
    : [
        h(
          "div",
          { class: "tile stack locked", role: "status" },
          h("div", { class: "row" }, h("span", { class: "tint" }, icon("lock", 18)), h("div", { class: "grow tight" }, h("b", {}, "Paket Gratis"), h("span", { class: "small" }, EXTENSION_PRO_ONLY))),
          h("a", { class: "button primary", href: SUBSCRIBE_URL, target: "_blank", rel: "noopener" }, "Langganan Pro"),
        ),
      ];

  return [
    header,
    ...locked,
    ...(locked.length ? [] : siteCard),
    h("div", { class: "stats" }, stat(String(data.templates.length), "template"), stat(String(data.contacts.length), "kontak"), stat(saved, "dihemat")),
    ...referral,
    h(
      "div",
      { class: "list" },
      h(
        "div",
        { class: "li" },
        h("span", { class: "tint" }, icon("refresh-cw", 18)),
        h("div", { class: "grow tight" }, h("b", {}, "Sinkron"), h("span", { class: "small" }, lastSync ? `Terakhir ${relative(lastSync)}` : "Belum pernah")),
        h("button", { class: "outline small", disabled: busy, click: () => void perform(() => send({ type: "syncNow" })) }, busy ? "Menyinkronkan…" : "Sinkron"),
      ),
      h(
        "div",
        { class: "li" },
        h("span", { class: "tint" }, icon("message-square-text", 18)),
        h("div", { class: "grow tight" }, h("b", {}, "Template dan field"), h("span", { class: "small" }, "Tambah, ubah, atau hapus")),
        h("button", { class: "outline small", click: () => void chrome.tabs.create({ url: chrome.runtime.getURL("manage.html") }) }, "Kelola"),
      ),
      h(
        "div",
        { class: "li" },
        h("span", { class: "tint" }, icon("users", 18)),
        h("div", { class: "grow tight" }, h("b", {}, "Akun"), h("span", { class: "small" }, "Data pribadi, kata sandi, tema")),
        h("button", { class: "outline small", click: () => void chrome.tabs.create({ url: chrome.runtime.getURL("account.html") }) }, "Buka"),
      ),
      h(
        "div",
        { class: "li" },
        h("span", { class: "tint" }, icon("message-circle", 18)),
        h("div", { class: "grow tight" }, h("b", {}, "Pusat bantuan"), h("span", { class: "small" }, "Tanya, kirim saran, lapor masalah")),
        h("button", { class: "outline small", click: () => void chrome.tabs.create({ url: chrome.runtime.getURL("help.html") }) }, "Buka"),
      ),
      h(
        "div",
        { class: "li" },
        h("span", { class: "tint" }, icon("type", 18)),
        h("div", { class: "grow tight" }, h("b", {}, "Karakter pemicu"), h("span", { class: "small" }, "Diketik sebelum shortcut")),
        trigger,
      ),
    ),
    ...(message ? [h("p", { class: "small", role: "status" }, message)] : []),
    h("p", { class: "small" }, `Ketik ${data.settings.trigger} di kolom teks mana pun, misalnya WhatsApp Web. Template dan field bisa diatur di sini atau di app ScriptGo di HP; kontak lewat app.`),
    h(
      "button",
      { class: "text small start", disabled: busy, click: () => void perform(() => send({ type: "signOut" })) },
      icon("log-out", 18),
      "Keluar",
    ),
  ];
}

void draw();
