// Builds the Supabase Auth email templates (HTML + subjects) from one ScriptGo layout.
// Run: node supabase/templates/build.mjs. Outputs *.html next to this file, subjects.json, and
// preview.html (all templates with sample values). Apply with scripts/apply-supabase-auth.mjs, or
// paste each file into Supabase → Authentication → Email Templates.
// Also builds the beta emails (beta/*.html and beta/beta_templates.sql): the invite, day-3 tips,
// and day-10 feedback request, sent by the database (docs/beta-launch.md); and the plan emails
// (plan/*.html and plan/plan_templates.sql): the trial ends in 3 days, and the trial has ended.
import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

// design/tokens.json (light). Email clients ignore CSS variables, so values are inlined.
const c = {
  bg: "#EEF2F6", card: "#FFFFFF", ink: "#0E1116", onInk: "#FFFFFF", muted: "#5B6675", line: "#E3E8EF", field: "#F5F7FA",
  accentInk: "#0369A1", accentSoft: "#E0F2FE", accentSolid: "#0284C7", danger: "#C62A2F",
};
const font = "Urbanist, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif";
const site = "https://scriptgo.id";
// The logo URL carries a hash of the image (like the site's own assets), so a new logo gets a new
// URL and Gmail's image proxy cannot keep showing the old one.
const iconVersion = createHash("sha256").update(readFileSync(join(here, "../../site/static/icon.png"))).digest("hex").slice(0, 8);

const p = (text, style = "") =>
  `<p style="margin:0 0 16px;font-size:16px;line-height:1.6;color:${c.muted};${style}">${text}</p>`;
const strong = (text) => `<strong style="color:${c.ink};font-weight:700;">${text}</strong>`;

/** The 6-digit code, big and spaced, on a soft mint panel. */
const code = (label) => `
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:4px 0 20px;">
  <tr><td align="center" style="background:${c.accentSoft};border-radius:20px;padding:20px 12px;">
    <div style="font-size:12px;font-weight:700;letter-spacing:1.2px;text-transform:uppercase;color:${c.accentInk};margin-bottom:6px;">${label}</div>
    <div style="font-size:38px;line-height:1.1;font-weight:800;letter-spacing:10px;color:${c.ink};font-family:${font};">{{ .Token }}</div>
  </td></tr>
</table>`;

/** Pill button (a table so Outlook keeps the shape). */
const button = (label, href = "{{ .ConfirmationURL }}") => `
<table role="presentation" cellpadding="0" cellspacing="0" style="margin:4px 0 20px;">
  <tr><td style="background:${c.ink};border-radius:999px;">
    <a href="${href}" style="display:inline-block;padding:15px 28px;font-size:16px;font-weight:700;color:${c.onInk};text-decoration:none;font-family:${font};">${label}</a>
  </td></tr>
</table>`;

const or = `
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 16px;">
  <tr>
    <td style="border-top:1px solid ${c.line};width:45%;"></td>
    <td align="center" style="font-size:13px;font-weight:700;color:${c.muted};padding:0 12px;white-space:nowrap;">atau</td>
    <td style="border-top:1px solid ${c.line};width:45%;"></td>
  </tr>
</table>`;

/** A small note on a soft panel, for "not you?" and expiry hints. */
const note = (text) => `
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:8px 0 0;">
  <tr><td style="background:${c.field};border-radius:16px;padding:14px 16px;font-size:14px;line-height:1.55;color:${c.muted};">${text}</td></tr>
</table>`;

const warning = (text) => `
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:8px 0 0;">
  <tr><td style="background:#FDECEC;border-radius:16px;padding:14px 16px;font-size:14px;line-height:1.55;color:${c.ink};">${text}</td></tr>
</table>`;

const layout = ({ preheader, title, body }) => `<!doctype html>
<html lang="id">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light">
<meta name="supported-color-schemes" content="light">
<link href="https://fonts.googleapis.com/css2?family=Urbanist:wght@500;700;800&display=swap" rel="stylesheet">
<title>${title}</title>
</head>
<body style="margin:0;padding:0;background:${c.bg};">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:${c.bg};">${preheader}</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:${c.bg};font-family:${font};">
  <tr><td align="center" style="padding:32px 16px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:520px;">
      <tr><td style="padding:0 8px 20px;">
        <table role="presentation" cellpadding="0" cellspacing="0"><tr>
          <td style="vertical-align:middle;"><img src="${site}/icon.png?v=${iconVersion}" width="36" height="36" alt="" style="display:block;border:0;"></td>
          <td style="vertical-align:middle;padding-left:10px;font-size:20px;font-weight:800;letter-spacing:-0.3px;color:${c.ink};">ScriptGo</td>
        </tr></table>
      </td></tr>
      <tr><td style="background:${c.card};border-radius:28px;padding:0;overflow:hidden;">
        <div style="padding:32px 28px;">
        <h1 style="margin:0 0 12px;font-size:26px;line-height:1.2;font-weight:800;letter-spacing:-0.5px;color:${c.ink};">${title}</h1>
        ${body}
        </div>
      </td></tr>
      <tr><td style="padding:20px 8px 0;font-size:13px;line-height:1.6;color:${c.muted};">
        Email otomatis dari ScriptGo untuk {{ .Email }}. Tim ScriptGo tidak pernah meminta kode lewat chat atau telepon.<br>
        <a href="${site}" style="color:${c.accentInk};font-weight:600;text-decoration:none;">scriptgo.id</a> ·
        <a href="${site}/privacy.html" style="color:${c.muted};text-decoration:underline;">Kebijakan privasi</a>
      </td></tr>
    </table>
  </td></tr>
</table>
</body>
</html>
`;

const signIn = (title, intro) => ({
  title,
  preheader: "Kode masuk kamu {{ .Token }}. Berlaku 1 jam.",
  body:
    p(intro) +
    code("Kode masuk") +
    or +
    p(`Buka email ini di HP yang terpasang ScriptGo, lalu ketuk tombol di bawah. Aplikasi terbuka dan kamu langsung masuk.`) +
    button("Masuk ke ScriptGo") +
    note("Kode dan tombol berlaku 1 jam dan hanya bisa dipakai sekali. Kalau kamu tidak sedang masuk ke ScriptGo, abaikan email ini; akunmu tetap aman."),
});

const security = (title, text) => ({
  title,
  preheader: text.replace(/<[^>]+>/g, ""),
  body:
    p(text) +
    warning(`${strong("Bukan kamu?")} Hubungi kami di ${strong("halo@scriptgo.id")} secepatnya supaya akunmu bisa kami amankan.`),
});

// Keys follow the Management API names: mailer_subjects_<key> and mailer_templates_<key>_content.
const templates = {
  magic_link: { subject: "Kode masuk ScriptGo: {{ .Token }}", ...signIn("Masuk ke ScriptGo", "Masukkan kode ini di aplikasi ScriptGo atau Chrome extension:") },
  confirmation: {
    subject: "Kode masuk ScriptGo: {{ .Token }}",
    ...signIn("Selamat datang di ScriptGo", `Satu langkah lagi. Masukkan kode ini di aplikasi untuk menyelesaikan pendaftaran ${strong("{{ .Email }}")}:`),
  },
  invite: {
    subject: "Kamu diundang ke ScriptGo",
    title: "Kamu diundang ke ScriptGo",
    preheader: "Terima undangan untuk memakai template dan kontak tim bersama.",
    body:
      p("Kamu diundang untuk memakai ScriptGo: keyboard dengan template balasan dan kontak pelanggan, supaya membalas chat cukup beberapa ketukan.") +
      button("Terima undangan") +
      note("Belum pasang aplikasinya? Unduh ScriptGo dulu di HP, lalu buka lagi email ini dan ketuk tombol di atas."),
  },
  email_change: {
    subject: "Konfirmasi email baru ScriptGo: {{ .Token }}",
    title: "Konfirmasi email baru",
    preheader: "Pastikan {{ .NewEmail }} adalah email barumu.",
    body:
      p(`Kamu meminta mengganti email akun ScriptGo dari ${strong("{{ .Email }}")} ke ${strong("{{ .NewEmail }}")}. Masukkan kode ini di aplikasi:`) +
      code("Kode konfirmasi") +
      or +
      button("Konfirmasi email baru") +
      note("Kalau kamu tidak meminta perubahan ini, abaikan email ini; emailmu tidak akan berubah."),
  },
  recovery: {
    subject: "Kode pemulihan akun ScriptGo: {{ .Token }}",
    title: "Pulihkan akunmu",
    preheader: "Kode pemulihan {{ .Token }}. Berlaku 1 jam.",
    body:
      p("Ada permintaan untuk memulihkan akses ke akun ScriptGo kamu. Masukkan kode ini di aplikasi:") +
      code("Kode pemulihan") +
      or +
      button("Pulihkan akun") +
      note("Berlaku 1 jam dan hanya bisa dipakai sekali. Kalau kamu tidak memintanya, abaikan email ini."),
  },
  reauthentication: {
    subject: "Kode verifikasi ScriptGo: {{ .Token }}",
    title: "Konfirmasi bahwa ini kamu",
    preheader: "Kode verifikasi {{ .Token }}.",
    body:
      p("Untuk melanjutkan tindakan penting di akunmu, masukkan kode ini di aplikasi:") +
      code("Kode verifikasi") +
      note("Jangan berikan kode ini ke siapa pun, termasuk yang mengaku dari ScriptGo."),
  },
  // Security notifications (sent only when enabled in the project).
  password_changed_notification: { subject: "Kata sandi akun ScriptGo diubah", ...security("Kata sandi diubah", "Kata sandi untuk akun ScriptGo kamu baru saja diubah.") },
  email_changed_notification: {
    subject: "Email akun ScriptGo diubah",
    ...security("Email akun diubah", `Email akun ScriptGo kamu sudah diganti dari ${strong("{{ .OldEmail }}")} ke ${strong("{{ .Email }}")}.`),
  },
  phone_changed_notification: {
    subject: "Nomor HP akun ScriptGo diubah",
    ...security("Nomor HP diubah", `Nomor HP akun ScriptGo kamu sudah diganti dari ${strong("{{ .OldPhone }}")} ke ${strong("{{ .Phone }}")}.`),
  },
  identity_linked_notification: {
    subject: "Cara masuk baru ditambahkan ke akun ScriptGo",
    ...security("Cara masuk baru ditambahkan", `Akun ${strong("{{ .Provider }}")} sekarang bisa dipakai untuk masuk ke ScriptGo.`),
  },
  identity_unlinked_notification: {
    subject: "Cara masuk dihapus dari akun ScriptGo",
    ...security("Cara masuk dihapus", `Akun ${strong("{{ .Provider }}")} tidak lagi bisa dipakai untuk masuk ke ScriptGo.`),
  },
  mfa_factor_enrolled_notification: {
    subject: "Verifikasi baru ditambahkan ke akun ScriptGo",
    ...security("Verifikasi baru ditambahkan", `Metode verifikasi ${strong("{{ .FactorType }}")} baru saja ditambahkan ke akunmu.`),
  },
  mfa_factor_unenrolled_notification: {
    subject: "Verifikasi dihapus dari akun ScriptGo",
    ...security("Verifikasi dihapus", `Metode verifikasi ${strong("{{ .FactorType }}")} baru saja dihapus dari akunmu.`),
  },
};

const subjects = {};
for (const [key, t] of Object.entries(templates)) {
  writeFileSync(join(here, `${key}.html`), layout(t));
  subjects[key] = t.subject;
}
writeFileSync(join(here, "subjects.json"), JSON.stringify(subjects, null, 2) + "\n");

// Preview: every template with sample values, for review in a browser.
const sample = {
  "{{ .Token }}": "481209", "{{ .Email }}": "rina@tokohijab.id", "{{ .NewEmail }}": "admin@tokohijab.id",
  "{{ .OldEmail }}": "rina@tokohijab.id", "{{ .Phone }}": "+6281234567890", "{{ .OldPhone }}": "+6285711223344",
  "{{ .Provider }}": "Google", "{{ .FactorType }}": "TOTP", "{{ .ConfirmationURL }}": "#",
};
const fill = (s) => Object.entries(sample).reduce((acc, [k, v]) => acc.replaceAll(k, v), s);
const frames = Object.entries(templates).map(([key, t]) =>
  `<section><h2>${key} · <span>${fill(t.subject)}</span></h2><iframe title="${key}" srcdoc="${fill(layout(t)).replaceAll("&", "&amp;").replaceAll('"', "&quot;")}"></iframe></section>`,
).join("\n");
writeFileSync(join(here, "preview.html"), `<!doctype html><html lang="id"><head><meta charset="utf-8"><title>ScriptGo email templates</title>
<style>body{margin:0;padding:24px;background:#DCE3EA;font:500 15px system-ui,sans-serif;color:#0E1116}section{margin:0 auto 32px;max-width:640px}h2{font-size:15px}h2 span{font-weight:500;color:#5B6675}iframe{width:100%;height:760px;border:0;border-radius:16px;background:#EEF2F6}</style>
</head><body>${frames}</body></html>
`);
console.log(`${Object.keys(templates).length} templates written`);

// Beta emails: not Auth templates. The database sends them through Resend (docs/beta-launch.md):
// the invite when a waitlist row's beta_status becomes 'invited', the tips 3 days later and the
// feedback request 10 days later (daily pg_cron job). {{name}} and {{email}} are filled in there.
const PLAY_OPT_IN = "https://play.google.com/apps/testing/id.scriptgo.keyboard";
const PLAY_LISTING = "https://play.google.com/store/apps/details?id=id.scriptgo.keyboard";
const steps = (items) => `
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 20px;">
${items.map((text, i) => `  <tr>
    <td style="width:34px;vertical-align:top;padding:2px 0 12px;"><div style="width:26px;height:26px;border-radius:13px;background:${c.accentSoft};color:${c.accentInk};font-size:14px;font-weight:800;line-height:26px;text-align:center;">${i + 1}</div></td>
    <td style="vertical-align:top;padding:4px 0 12px;font-size:16px;line-height:1.5;color:${c.muted};">${text}</td>
  </tr>`).join("\n")}
</table>`;
/** A tip: bold heading and a line of explanation, on a soft card. */
const tip = (heading, text) => `
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 12px;">
  <tr><td style="background:${c.bg};border-radius:18px;padding:14px 16px;">
    <div style="font-size:16px;font-weight:800;color:${c.ink};margin-bottom:4px;">${heading}</div>
    <div style="font-size:15px;line-height:1.55;color:${c.muted};">${text}</div>
  </td></tr>
</table>`;
const chip = (text) => `<span style="background:${c.accentSoft};color:${c.accentInk};font-weight:700;border-radius:6px;padding:1px 6px;">${text}</span>`;
const stopNote = `Tidak mau menerima email beta lagi? Balas dengan kata ${strong("STOP")}.`;

const beta = {
  beta_confirm: {
    subject: "Konfirmasi email kamu untuk beta ScriptGo",
    title: "Konfirmasi email kamu",
    preheader: "Satu klik lagi, lalu kamu masuk daftar beta.",
    body:
      p(`Hai! Kamu baru saja mendaftar beta ScriptGo dengan email ${strong("{{email}}")}.`) +
      p(`Klik tombol di bawah untuk memastikan email ini benar milikmu. Undangan beta hanya kami kirim ke email yang sudah dikonfirmasi.`) +
      button("Konfirmasi email", "{{link}}") +
      p(`Tombol tidak bisa diketuk? Buka link ini: <a href="{{link}}" style="color:${c.accentInk};font-weight:700;word-break:break-all;">{{link}}</a>`, "font-size:14px;") +
      note(`Link berlaku 24 jam dan hanya bisa dipakai sekali. Tidak merasa mendaftar? Abaikan email ini.`),
  },
  beta_has_account: {
    subject: "Email ini sudah punya akun ScriptGo",
    title: "Kamu sudah bisa pakai ScriptGo",
    preheader: "Tidak perlu daftar beta lagi. Cukup masuk dengan email ini.",
    body:
      p(`Hai! Ada yang mendaftar beta ScriptGo dengan email ${strong("{{email}}")}. Email ini sudah punya akun ScriptGo, jadi kamu ${strong("tidak perlu daftar lagi")}.`) +
      steps([
        `Buka app ScriptGo di HP Android kamu.`,
        `Buka tab ${strong("Akun")}, lalu masuk dengan email ini. Template, kontak, dan pengaturanmu ikut kembali.`,
        `Di laptop, masuk dengan email yang sama di Chrome extension ScriptGo.`,
      ]) +
      button("Buka ScriptGo di Google Play", PLAY_LISTING) +
      note(`Tidak merasa mendaftar? Abaikan email ini; akunmu tetap aman dan tidak ada yang berubah.`),
  },
  beta_invite: {
    subject: "Kamu diterima di beta ScriptGo",
    title: "Selamat datang di beta ScriptGo",
    preheader: "Gabung lewat Google Play dan pasang ScriptGo di HP Android kamu.",
    body:
      p(`Halo ${strong("{{name}}")}, kamu termasuk pengguna pertama ScriptGo. Terima kasih sudah mau mencoba dan memberi masukan.`) +
      steps([
        `Buka email ini di ${strong("HP Android")}. Pastikan Play Store di HP itu masuk dengan akun Google ${strong("{{email}}")}.`,
        `Ketuk tombol di bawah, lalu ketuk ${strong("Jadi penguji")} (Become a tester).`,
        `Ketuk ${strong("Download di Google Play")}, pasang, lalu buka ScriptGo dan ikuti langkahnya.`,
      ]) +
      button("Gabung beta di Google Play", PLAY_OPT_IN) +
      p(`Sudah jadi penguji? Pasang langsung dari <a href="${PLAY_LISTING}" style="color:${c.accentInk};font-weight:700;">halaman ScriptGo di Play Store</a>. Update beta datang otomatis lewat Play Store.`, "font-size:15px;") +
      note(`Harga pendiri 50% tetap berlaku untuk email ini. Ada masukan atau kendala? Cukup balas email ini, kami baca semuanya. Tolong tetap pasang ScriptGo selama minimal 14 hari: itu syarat Google sebelum ScriptGo bisa rilis untuk umum.`),
  },
  beta_tips: {
    subject: "4 trik ScriptGo yang bikin balas chat makin cepat",
    title: "4 trik supaya ScriptGo makin terasa",
    preheader: "Isi nama pelanggan otomatis, tahan spasi, ganti karakter pemicu, dan pakai di laptop.",
    body:
      p(`Halo ${strong("{{name}}")}, sudah 3 hari kamu pakai ScriptGo. Ini 4 trik yang paling sering bikin pengguna bilang "kok baru tahu".`) +
      tip("1. Nama pelanggan terisi sendiri",
        `Tulis ${chip("{nama}")} di template, misalnya ${chip("Halo Kak {nama}")}. Saat dipakai, ScriptGo menanyakan "Untuk siapa?" dan mengisi nama, kota, atau data lain dari kontak.`) +
      tip("2. Tahan spasi untuk daftar template",
        `Lupa nama shortcut? Tahan tombol spasi sekitar 1 detik: tombol keyboard berubah jadi daftar template, tinggal ketuk. Lama tahannya bisa diatur di pengaturan keyboard.`) +
      tip("3. Ganti karakter pemicu kalau bentrok",
        `Pakai WhatsApp Business? Tanda ${chip("/")} juga dipakai balas cepat WhatsApp. Di tab Template, ketuk ikon pengaturan dan ganti dengan ${chip(";")} atau ${chip("\\")}.`) +
      tip("4. Template yang sama di laptop",
        `Masuk ke akun di tab Akun, lalu pasang Chrome extension ScriptGo. Template dan kontak ikut, bisa dipakai di WhatsApp Web dan kolom chat lain di browser.`) +
      note(`Ada yang bikin bingung? Balas email ini, kami bantu. ${stopNote}`),
  },
  beta_feedback: {
    subject: "{{name}}, 3 pertanyaan singkat soal ScriptGo",
    title: "Boleh minta 2 menit?",
    preheader: "Balas email ini dengan jawabanmu. Masukanmu menentukan fitur berikutnya.",
    body:
      p(`Halo ${strong("{{name}}")}, sudah 10 hari kamu ikut beta ScriptGo. Kami ingin tahu apa yang sudah membantu dan apa yang masih mengganggu. Cukup ${strong("balas email ini")}, jawab singkat saja:`) +
      steps([
        `Template apa yang paling sering kamu pakai, dan kira-kira berapa kali sehari?`,
        `Apa yang paling bikin repot, atau hampir bikin kamu berhenti pakai ScriptGo?`,
        `Kalau besok ScriptGo tidak bisa dipakai lagi, seberapa kecewa kamu? ${strong("Sangat kecewa")}, ${strong("agak kecewa")}, atau ${strong("biasa saja")}?`,
      ]) +
      warning(`${strong("Tolong jangan copot ScriptGo dulu.")} Google mewajibkan penguji tetap memasang app selama 14 hari sebelum ScriptGo boleh rilis untuk umum. Tinggal beberapa hari lagi. Terima kasih banyak!`) +
      note(`Harga pendiri 50% tetap berlaku untuk email ini. ${stopNote}`),
  },
};

mkdirSync(join(here, "beta"), { recursive: true });
const sqlText = (value) => "$tpl$" + value + "$tpl$";
const betaRows = [];
for (const [key, t] of Object.entries(beta)) {
  const html = layout(t)
    .replaceAll("{{ .Email }}", "{{email}}")
    .replace("Tim ScriptGo tidak pernah meminta kode lewat chat atau telepon.", "Kamu menerima email ini karena mendaftar beta di scriptgo.id.");
  writeFileSync(join(here, "beta", `${key}.html`), html);
  betaRows.push(`insert into private.email_templates (key, subject, html) values ('${key}', ${sqlText(t.subject)}, ${sqlText(html)})
on conflict (key) do update set subject = excluded.subject, html = excluded.html, updated_at = now();`);
}
writeFileSync(join(here, "beta", "beta_templates.sql"), `-- Generated by supabase/templates/build.mjs. Run in the Supabase SQL editor after changing a beta email.
${betaRows.join("\n\n")}
`);

// Plan emails (PRD §9), sent by the daily job private.send_trial_emails(): 3 days before the trial
// ends, and once it has ended. {{name}}, {{email}}, {{days}}, {{date}} and {{link}} are filled in
// there. Emails are outside the app, so they may link to the pricing page (the app may not).
const freeLimits = steps([
  `Maksimal ${strong("20 template")}, dan ${strong("30 pemakaian template")} (sekali saja, tidak di-reset).`,
  `${strong("Sync antar HP dan Chrome extension")} berhenti. Template dan kontak tetap aman di HP kamu.`,
  `Keyboard tetap bisa dipakai seperti biasa.`,
]);
const plan = {
  trial_ending: {
    subject: "Trial Pro ScriptGo kamu berakhir {{days}} hari lagi",
    title: "Trial Pro kamu tinggal {{days}} hari",
    preheader: "Setelah {{date}}, akunmu pindah ke paket Gratis. Ini yang berubah.",
    body:
      p(`Halo ${strong("{{name}}")}, trial Pro ScriptGo untuk ${strong("{{email}}")} berakhir pada ${strong("{{date}}")}. Setelah itu akunmu pindah ke paket Gratis:`) +
      freeLimits +
      p(`Mau tetap pakai semuanya tanpa batas? Paket Pro mulai Rp49 rb per bulan, atau lebih hemat dengan paket 3 dan 12 bulan.`) +
      button("Lihat paket Pro", "{{link}}") +
      note(`Ada pertanyaan soal paket? Balas email ini, kami bantu.`),
  },
  trial_ended: {
    subject: "Trial Pro ScriptGo kamu sudah berakhir",
    title: "Trial Pro kamu sudah selesai",
    preheader: "Akunmu sekarang di paket Gratis. Template dan kontakmu tetap aman.",
    body:
      p(`Halo ${strong("{{name}}")}, terima kasih sudah mencoba ScriptGo Pro selama 14 hari. Mulai sekarang akun ${strong("{{email}}")} ada di paket Gratis:`) +
      freeLimits +
      p(`Semua fitur Pro kembali begitu kamu berlangganan, termasuk sync dan Chrome extension. Data di server tetap tersimpan.`) +
      button("Lanjut dengan Pro", "{{link}}") +
      note(`Ada yang kurang dari ScriptGo? Balas email ini dan ceritakan; masukanmu kami baca semuanya.`),
  },
};

mkdirSync(join(here, "plan"), { recursive: true });
const planRows = [];
for (const [key, t] of Object.entries(plan)) {
  const html = layout(t)
    .replaceAll("{{ .Email }}", "{{email}}")
    .replace("Tim ScriptGo tidak pernah meminta kode lewat chat atau telepon.", "Kamu menerima email ini karena punya akun ScriptGo.");
  writeFileSync(join(here, "plan", `${key}.html`), html);
  planRows.push(`insert into private.email_templates (key, subject, html) values ('${key}', ${sqlText(t.subject)}, ${sqlText(html)})
on conflict (key) do update set subject = excluded.subject, html = excluded.html, updated_at = now();`);
}
writeFileSync(join(here, "plan", "plan_templates.sql"), `-- Generated by supabase/templates/build.mjs. Run in the Supabase SQL editor after changing a plan email.
${planRows.join("\n\n")}
`);
