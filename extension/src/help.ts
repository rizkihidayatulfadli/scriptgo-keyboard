// Pusat bantuan (help.html): common questions, then a form to ask for help, suggest something, or
// report a problem. The background worker sends it to support_messages, like the app does.

import { icon } from "./icons";
import { KEY_SESSION, send, type Session } from "./store";
import { brand, h } from "./ui";

const FAQ: Array<[string, string]> = [
  [
    "Bagaimana cara memakai template?",
    "Di kolom teks mana saja, misalnya WhatsApp Web, ketik tanda pemicu (biasanya /) diikuti shortcut, misalnya /ongkir, lalu tekan Enter atau pilih dari daftar saran.",
  ],
  [
    "Saran template tidak muncul, kenapa?",
    "Pastikan kamu sudah masuk dan ScriptGo aktif di situs itu (buka popup extension, lihat saklar Aktif di situs ini). Setelah memasang atau memperbarui extension, muat ulang tab yang sudah terbuka.",
  ],
  [
    "Kenapa {nama} atau {kota} tidak terisi otomatis?",
    "Nilai diambil dari kontak yang dipilih saat memakai template. Pastikan kontaknya punya data itu, atau beri fallback di field atau di template, misalnya {kota|Bandung}.",
  ],
  [
    "Apakah ScriptGo membaca yang saya ketik?",
    "Tidak. Extension hanya mencari tanda pemicu di kolom yang sedang kamu ketik, di browsermu sendiri. Ketikanmu tidak dikirim ke mana pun. Yang disinkronkan hanya template, kontak, dan field milikmu.",
  ],
  [
    "Bagaimana mengubah kontak?",
    "Kontak dikelola di app ScriptGo di HP, lalu ikut ke extension saat sinkron. Template dan field bisa diatur di halaman Kelola extension maupun di app.",
  ],
  [
    "Lupa kata sandi?",
    "Masuk saja dengan kode email dari popup extension. Kata sandi baru bisa dibuat di app ScriptGo di HP: Akun → Kata sandi.",
  ],
];

const KINDS: Array<[string, string]> = [
  ["help", "Butuh bantuan"],
  ["idea", "Saran"],
  ["bug", "Lapor masalah"],
];

const HINTS: Record<string, string> = {
  help: "Ceritakan apa yang ingin kamu lakukan dan di mana kamu bingung.",
  idea: "Fitur atau perubahan apa yang akan membantumu?",
  bug: "Apa yang terjadi, dan apa yang seharusnya terjadi? Di situs mana?",
};

const app = document.getElementById("app")!;

function faqList(): HTMLElement {
  return h(
    "div",
    { class: "faq" },
    ...FAQ.map(([question, answer]) =>
      h("details", {}, h("summary", {}, h("span", {}, question), icon("chevron-down", 18)), h("p", { class: "small" }, answer)),
    ),
  );
}

async function draw(): Promise<void> {
  const session = (await chrome.storage.local.get(KEY_SESSION))[KEY_SESSION] as Session | undefined;
  let kind = "help";
  const message = h("textarea", { id: "help-message", maxlength: "4000", rows: "6", placeholder: HINTS.help });
  const email = h("input", { id: "help-email", type: "email", maxlength: "254", autocomplete: "email", placeholder: "nama@tokokamu.id" });
  const status = h("p", { class: "small", role: "status" });
  const sendButton = h("button", { class: "primary full", type: "submit", disabled: true }, icon("send", 18), "Kirim");
  const validEmail = () => !email.value.trim() || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.value.trim());
  const refresh = () => (sendButton.disabled = !message.value.trim() || !validEmail());
  message.addEventListener("input", refresh);
  email.addEventListener("input", refresh);

  const kinds = h("div", { class: "chips", role: "radiogroup", "aria-label": "Jenis pesan" });
  const drawKinds = () =>
    kinds.replaceChildren(
      ...KINDS.map(([code, label]) =>
        h(
          "button",
          {
            type: "button",
            class: code === kind ? "pill-choice sel" : "pill-choice",
            role: "radio",
            "aria-checked": String(code === kind),
            click: () => {
              kind = code;
              message.placeholder = HINTS[code];
              drawKinds();
            },
          },
          label,
        ),
      ),
    );
  drawKinds();

  const form = h(
    "form",
    { class: "stack" },
    kinds,
    h("div", { class: "field" }, h("label", { for: "help-message" }, "Pesanmu"), message),
    ...(session ? [] : [h("div", { class: "field" }, h("label", { for: "help-email" }, "Email untuk balasan (opsional)"), email)]),
    h("p", { class: "small" }, "Versi extension dan browser ikut terkirim supaya masalah lebih cepat ditemukan. Isi halaman yang kamu buka tidak ikut."),
    sendButton,
    status,
  );
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    sendButton.disabled = true;
    status.textContent = "Mengirim…";
    status.className = "small";
    const reply = await send({
      type: "sendSupport",
      kind,
      message: message.value,
      replyEmail: session ? "" : email.value,
    }).catch(() => ({ ok: false as const, error: "Terjadi kesalahan. Coba lagi." }));
    if (reply.ok) {
      message.value = "";
      status.textContent = "Terkirim. Terima kasih, kami baca semua pesan.";
    } else {
      status.textContent = reply.error;
      status.className = "small error";
    }
    refresh();
  });

  app.replaceChildren(
    brand(40),
    h("h1", {}, "Pusat bantuan"),
    h("p", { class: "lead" }, "Cek dulu pertanyaan yang sering ditanyakan. Kalau belum terjawab, tulis ke kami di bawah."),
    h("h2", {}, "Pertanyaan umum"),
    faqList(),
    h("h2", {}, "Tulis ke kami"),
    form,
  );
}

void draw();
