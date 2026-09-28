// Draws the phone screens for the Play screenshots into store/play/raw/ (1080×2340), recreated from
// the app's own design: tokens (design/tokens.json), Urbanist, the Lucide icons in ScriptGoIcons.kt,
// the layout of Components.kt (title row, cards, floating nav), and the app's Indonesian strings.
// Sample data only (Kak Rina, Bandung), never real customers. Then run render.mjs to frame them.
// Run from the repo root after `npm ci` in site/: node store/play/mockups.mjs
import { createRequire } from "node:module";
import { mkdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const require = createRequire(new URL("../../site/package.json", import.meta.url));
const { chromium } = require("playwright");

const root = new URL("../../", import.meta.url);
const read = (path) => readFileSync(new URL(path, root));
const rawDir = new URL("raw/", import.meta.url);
mkdirSync(rawDir, { recursive: true });

// Lucide paths as the app draws them.
const iconSource = read("android/app/src/main/java/com/magickeyboard/manager/ui/theme/ScriptGoIcons.kt").toString();
const ICONS = Object.fromEntries(
  [...iconSource.matchAll(/val (\w+): ImageVector by lazy \{ icon\("\w+", (.*?)\) \}/g)].map(([, name, args]) => [name, [...args.matchAll(/"([^"]+)"/g)].map((m) => m[1])]),
);
const icon = (name, size = 22, color = "currentColor") =>
  `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${(ICONS[name] ?? []).map((d) => `<path d="${d}"/>`).join("")}</svg>`;

const mark = `data:image/svg+xml;base64,${read("design/logo/scriptgo-mark.svg").toString("base64")}`;
const font = (w) => `@font-face{font-family:U;font-weight:${w};src:url(data:font/woff2;base64,${read(`site/static/fonts/urbanist-${w}.woff2`).toString("base64")})}`;

// design/tokens.json, light.
const css = `${[500, 600, 700, 800].map(font).join("")}
:root{--ink:#0E1116;--muted:#5B6675;--bg:#EEF2F6;--card:#FFFFFF;--line:#E3E8EF;--field:#F5F7FA;--accent:linear-gradient(135deg,#0EA5E9,#6366F1);--soft:#E0F2FE;--accent-ink:#0369A1;--solid:#0284C7}
*{box-sizing:border-box}html,body{margin:0}
body{width:412px;height:892px;overflow:hidden;font-family:U;color:var(--ink);background:var(--bg);position:relative;font-weight:500}
.status{height:34px;display:flex;align-items:center;justify-content:space-between;padding:0 22px;font-size:14px;font-weight:700}
.status .r{display:flex;gap:6px;align-items:center}
.glow{position:absolute;inset:0 0 auto 0;height:320px;background:radial-gradient(200px 200px at 22% 28%,rgba(14,165,233,.42),transparent),radial-gradient(200px 200px at 86% 18%,rgba(99,102,241,.42),transparent);pointer-events:none}
.top{position:relative;display:flex;align-items:center;gap:10px;padding:16px 16px 12px 20px}
.top h1{flex:1;margin:0;font-size:32px;font-weight:800;letter-spacing:-.6px}
.top img{width:34px;height:34px}
.circle{width:44px;height:44px;border-radius:50%;background:var(--card);display:grid;place-items:center;box-shadow:0 1px 2px rgba(14,17,22,.04),0 8px 24px rgba(14,17,22,.06)}
.content{position:relative;padding:4px 16px 0;display:grid;gap:12px}
.card{background:var(--card);border-radius:22px;padding:14px 16px;box-shadow:0 1px 2px rgba(14,17,22,.04),0 8px 24px rgba(14,17,22,.06)}
.row{display:flex;gap:14px;align-items:flex-start}
.badge{flex:none;width:44px;height:44px;border-radius:14px;background:var(--accent);color:#fff;display:grid;place-items:center;font-weight:800;font-size:18px}
.t{font-size:17px;font-weight:700}.s{font-size:13.5px;color:var(--muted);line-height:1.4}
.chip{display:inline-block;border:1.5px solid var(--line);border-radius:12px;padding:4px 10px;font-size:13px;font-weight:700;margin-top:8px}
.search{display:flex;align-items:center;gap:10px;background:var(--card);border-radius:999px;padding:13px 18px;color:var(--muted);font-size:15px;box-shadow:0 1px 2px rgba(14,17,22,.04),0 8px 24px rgba(14,17,22,.06)}
.nav{position:absolute;left:16px;right:16px;bottom:22px;display:flex;gap:10px}
.nav .bar{flex:1;height:62px;border-radius:999px;background:var(--card);display:flex;padding:6px;box-shadow:0 8px 24px rgba(14,17,22,.1)}
.nav .bar div{flex:1;border-radius:999px;display:grid;place-items:center;color:var(--muted)}
.nav .bar .on{background:var(--line);color:var(--ink)}
.nav .add{width:62px;height:62px;border-radius:50%;background:var(--card);display:grid;place-items:center;box-shadow:0 8px 24px rgba(14,17,22,.1)}
.fade{position:absolute;left:0;right:0;bottom:0;height:120px;background:linear-gradient(transparent,var(--bg))}
.var{display:inline-flex;align-items:center;gap:4px;background:var(--soft);color:var(--accent-ink);border-radius:8px;padding:1px 7px;font-weight:700;font-size:.95em}
.var small{font-weight:600;opacity:.75}
.btn{display:grid;place-items:center;height:52px;border-radius:999px;background:var(--ink);color:#fff;font-weight:700;font-size:16px}
.btn.o{background:var(--card);color:var(--ink);box-shadow:inset 0 0 0 1.5px var(--line)}
.label{font-size:12px;font-weight:600;letter-spacing:.96px;text-transform:uppercase;color:var(--muted);margin:6px 4px 0}
.field{background:var(--field);border-radius:16px;padding:12px 14px;font-size:15px}
.field b{display:block;font-size:12px;color:var(--muted);font-weight:600;margin-bottom:2px}
.pill{display:inline-grid;place-items:center;height:36px;padding:0 14px;border-radius:12px;background:var(--field);font-weight:700;font-size:14px}
.pill.on{background:var(--ink);color:#fff}
.bubble{background:var(--field);border-radius:18px 18px 18px 6px;padding:12px 14px;font-size:15px;line-height:1.5}
`;

const status = `<div class="status"><span>09.41</span><span class="r">${icon("Smartphone", 14)}<span>100%</span></span></div>`;
const nav = (on) =>
  `<div class="fade"></div><div class="nav"><div class="bar">${["Templates", "Contacts", "Fields", "Account"].map((n) => `<div class="${n === on ? "on" : ""}">${icon(n)}</div>`).join("")}</div><div class="add">${icon("Plus")}</div></div>`;
const top = (title, actions = "") => `<div class="glow"></div>${status}<div class="top"><img src="${mark}"><h1>${title}</h1>${actions}</div>`;
const v = (label, hint = "") => `<span class="var">${label}${hint ? `<small>· ${hint}</small>` : ""}</span>`;

const templateCard = (title, preview, shortcut) =>
  `<div class="card row"><div class="badge">${title[0]}</div><div style="flex:1;min-width:0"><div class="t">${title}</div><div class="s">${preview}</div><span class="chip">/${shortcut}</span></div></div>`;

// A WhatsApp-like chat with the ScriptGo keyboard and its template strip.
const keyboardRow = (keys, extra = "") =>
  `<div style="display:flex;gap:6px;justify-content:center;${extra}">${keys.map((k) => `<div style="flex:${k.length > 1 ? 1.5 : 1};height:46px;border-radius:9px;background:#fff;display:grid;place-items:center;font-size:${k.length > 1 ? 13 : 20}px;font-weight:600;box-shadow:0 1px 0 rgba(0,0,0,.12)">${k}</div>`).join("")}</div>`;
const keyboard = (strip) => `
  <div style="position:absolute;left:0;right:0;bottom:0;background:#E7ECF1;padding:8px 6px 26px">
    <div style="display:flex;gap:8px;overflow:hidden;padding:2px 4px 10px">${strip}</div>
    <div style="display:grid;gap:10px">
      ${keyboardRow(["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"])}
      ${keyboardRow(["a", "s", "d", "f", "g", "h", "j", "k", "l"], "padding:0 18px")}
      ${keyboardRow(["⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"])}
      ${keyboardRow(["?123", "/", "Indonesia", ".", "↵"])}
    </div>
  </div>`;
const stripChip = (label, first = false) =>
  `<div style="flex:none;height:40px;padding:0 16px;border-radius:14px;background:#fff;display:grid;place-items:center;font-size:15px;font-weight:${first ? 800 : 600};${first ? "box-shadow:inset 0 0 0 1.5px #0284C7" : ""}">${label}</div>`;
const chatBubble = (text, me = false) =>
  `<div style="max-width:78%;${me ? "margin-left:auto;background:#D9FDD3;border-radius:12px 4px 12px 12px" : "background:#fff;border-radius:4px 12px 12px 12px"};padding:9px 12px;font-size:15.5px;line-height:1.4;box-shadow:0 1px 1px rgba(0,0,0,.08)">${text}</div>`;
const chat = (typed, strip) => `
  <div style="position:absolute;inset:0;background:#EFE7DE">
    <div style="height:34px;background:#0B6E60;color:#fff" class="status"><span>09.41</span><span class="r">100%</span></div>
    <div style="background:#0B6E60;color:#fff;display:flex;align-items:center;gap:12px;padding:10px 14px">
      ${icon("Back", 22, "#fff")}<div style="width:38px;height:38px;border-radius:50%;background:#A7D8CF;display:grid;place-items:center;font-weight:800;color:#0B6E60">R</div>
      <div><div style="font-weight:700;font-size:17px">Kak Rina</div><div style="font-size:12.5px;opacity:.85">online</div></div>
    </div>
    <div style="display:grid;gap:8px;padding:14px 12px">
      ${chatBubble("Kak, gamis yang navy masih ada?")}
      ${chatBubble("Masih ada Kak, ukuran M dan L ✨", true)}
      ${chatBubble("Ongkir ke Bandung berapa ya? 🙏")}
    </div>
    <div style="position:absolute;left:8px;right:8px;bottom:318px;display:flex;gap:8px;align-items:center">
      <div style="flex:1;background:#fff;border-radius:24px;padding:12px 16px;font-size:16px">${typed}<span style="display:inline-block;width:2px;height:20px;background:#0B6E60;vertical-align:-4px;margin-left:1px"></span></div>
      <div style="width:48px;height:48px;border-radius:50%;background:#0B6E60;display:grid;place-items:center">${icon("Insert", 20, "#fff")}</div>
    </div>
    ${keyboard(strip)}
  </div>`;

const screens = {
  "1-saran.png": chat("/ongk", stripChip("Info ongkir", true) + stripChip("Ongkir COD") + stripChip("Ongkir instan")),

  "2-form.png": `<div class="glow"></div>${status}
    <div class="top">${`<div class="circle">${icon("Back")}</div>`}<h1 style="font-size:24px">Info ongkir</h1></div>
    <div class="content">
      <div class="card row" style="align-items:center"><div class="badge" style="border-radius:50%">R</div><div style="flex:1"><div class="t">Untuk Rina</div><div class="s">+62 812-3456-7890 · WhatsApp</div></div><span class="pill">Ganti</span></div>
      <div class="label">Isi variabel</div>
      <div class="field"><b>Kota</b>Bandung</div>
      <div class="field"><b>Ongkir</b>Rp18.000</div>
      <div><div class="s" style="margin:0 4px 8px;font-weight:600">Kurir</div><span class="pill on">JNE</span> <span class="pill">J&amp;T</span> <span class="pill">SiCepat</span></div>
      <div class="label">Pratinjau</div>
      <div class="bubble">Halo Kak <b>Rina</b>, ongkir ke <b>Bandung</b> <b>Rp18.000</b> via <b>JNE</b> (2–3 hari). Mau langsung diproses? 🙏</div>
      <div class="btn" style="margin-top:6px">Sisipkan</div>
    </div>`,

  "3-template.png": `${top("Template", `<div class="circle">${icon("Settings")}</div><div class="circle">${icon("Keyboard")}</div>`)}
    <div class="content">
      <div class="search">${icon("Search", 20)}Cari template</div>
      ${templateCard("Info ongkir", "Halo Kak {nama}, ongkir ke {kota} {ongkir} via {kurir}…", "ongkir")}
      ${templateCard("Nomor rekening", "Pembayaran bisa ke BCA 123 456 7890 a.n. Toko Rina…", "rekening")}
      ${templateCard("Salam pembuka", "Halo Kak {nama|Kak} 👋 Terima kasih sudah menghubungi…", "salam")}
      ${templateCard("Follow up pesanan", "Kak {nama}, pesanan {produk} masih kami simpan sampai…", "followup")}
      ${templateCard("Cara order", "Format order: Nama, alamat lengkap, nomor HP…", "order")}
    </div>${nav("Templates")}`,

  "4-editor.png": `<div class="glow"></div>${status}
    <div class="top"><div class="circle">${icon("Back")}</div><h1 style="font-size:24px">Ubah template</h1><span class="pill on">Simpan</span></div>
    <div class="content">
      <div class="field"><b>Judul</b>Info ongkir</div>
      <div class="field"><b>Shortcut</b>/ongkir</div>
      <div class="card" style="padding:16px;font-size:16px;line-height:2">Halo Kak ${v("Nama", "Kak")}, ongkir ke ${v("Kota")} ${v("Ongkir")} via ${v("Kurir", "JNE, J&amp;T, SiCepat")} (2–3 hari). Mau langsung diproses? 🙏</div>
      <div class="label">Sisipkan variabel</div>
      <div style="display:flex;flex-wrap:wrap;gap:6px">${["Nama", "Nomor HP", "Kota", "Ongkir", "Channel", "Tanggal", "Hari", "Kursor"].map((n) => `<span class="var" style="padding:7px 12px;border-radius:11px;font-size:13.5px">+ ${n}</span>`).join("")}</div>
      <div class="btn o" style="gap:8px;display:flex;justify-content:center;align-items:center">${icon("Sparkles", 18, "#0284C7")}Rapikan dengan AI</div>
      <div class="label">Tag</div>
      <div><span class="chip" style="margin:0">pengiriman</span> <span class="chip" style="margin:0">ongkir</span></div>
    </div>`,

  "5-ai.png": `<div class="glow"></div>${status}
    <div class="top"><div class="circle">${icon("Back")}</div><h1 style="font-size:24px">Template baru</h1></div>
    <div style="position:absolute;left:0;right:0;bottom:0;top:170px;background:var(--card);border-radius:28px 28px 0 0;box-shadow:0 -8px 30px rgba(14,17,22,.12);padding:14px 20px">
      <div style="width:40px;height:4px;border-radius:2px;background:var(--line);margin:0 auto 18px"></div>
      <div class="row" style="align-items:center"><div class="badge" style="background:var(--soft);color:#0284C7">${icon("Sparkles", 22, "#0284C7")}</div><div><div class="t" style="font-size:20px">Buat dengan AI</div><div class="s">Sisa 18 dari 20 hari ini</div></div></div>
      <div class="field" style="margin-top:18px"><b>Mau template untuk apa?</b>Balas pembeli yang tanya stok dan ukuran gamis, tawarkan ukuran lain kalau habis</div>
      <div class="label" style="margin-top:18px">Hasil</div>
      <div class="bubble" style="margin-top:8px;border-radius:16px"><div class="t">Cek stok gamis</div><span class="chip" style="margin:4px 0 8px">/stok</span><div>Halo Kak ${v("Nama")} 😊 Gamis ${v("Warna")} ukuran ${v("Ukuran")} ${v("Status", "tersedia, habis")} ya. Kalau habis, ukuran ${v("Alternatif")} masih ada. Mau dibantu order sekarang?</div></div>
      <div style="display:flex;gap:12px;margin-top:16px"><div class="btn o" style="flex:1">Coba lagi</div><div class="btn" style="flex:1">Pakai</div></div>
      <div class="s" style="margin-top:14px;text-align:center">Laporkan hasil ini</div>
    </div>`,

  "6-kontak.png": `${top("Kontak", `<div class="circle">${icon("Import")}</div>`)}
    <div class="content">
      <div class="search">${icon("Search", 20)}Cari nama atau nomor</div>
      ${[
        ["Rina", "+62 812-3456-7890", "Bandung · WhatsApp"],
        ["Dewi Lestari", "+62 857-2211-3344", "Surabaya · Instagram"],
        ["Budi Santoso", "+62 813-9876-5432", "Jakarta · WhatsApp"],
        ["Sari Wulandari", "+62 821-5544-7788", "Yogyakarta · Shopee"],
        ["Andi Pratama", "+62 878-1122-3355", "Medan · WhatsApp"],
        ["Maya Putri", "+62 896-4433-2211", "Makassar · TikTok"],
      ]
        .map(([name, phone, meta]) => `<div class="card row" style="align-items:center"><div class="badge" style="border-radius:50%">${name[0]}</div><div style="flex:1"><div class="t">${name}</div><div class="s">${phone}</div><div class="s" style="color:var(--accent-ink);font-weight:600">${meta}</div></div>${icon("ChevronRight", 20, "#5B6675")}</div>`)
        .join("")}
    </div>${nav("Contacts")}`,

  "8-hemat.png": `${top("Akun")}
    <div class="content">
      <div class="card" style="background:var(--accent);color:#fff;padding:22px">
        <div style="font-size:14px;font-weight:600;opacity:.9">Waktu yang kamu hemat</div>
        <div style="font-size:52px;font-weight:800;letter-spacing:-1.5px;margin:4px 0">6 jam 40 menit</div>
        <div style="font-size:14px;opacity:.9">dari 1.284 template yang disisipkan</div>
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
        <div class="card"><div class="s">Template</div><div style="font-size:30px;font-weight:800">24</div></div>
        <div class="card"><div class="s">Kontak</div><div style="font-size:30px;font-weight:800">318</div></div>
      </div>
      <div class="label">Paling sering dipakai</div>
      ${[["Info ongkir", 412], ["Nomor rekening", 336], ["Salam pembuka", 221], ["Cara order", 149]]
        .map(([t, n]) => `<div class="card row" style="align-items:center"><div class="badge">${t[0]}</div><div style="flex:1" class="t">${t}</div><div style="font-weight:800">${n}×</div></div>`)
        .join("")}
      <div class="s" style="text-align:center;margin-top:4px">Statistik hanya menghitung jumlah karakter, bukan isi chat.</div>
    </div>${nav("Account")}`,
};

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 412, height: 892 }, deviceScaleFactor: 1080 / 412 });
for (const [file, body] of Object.entries(screens)) {
  await page.setContent(`<!doctype html><html><head><style>${css}</style></head><body>${body}</body></html>`);
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all([...document.images].map((img) => img.decode()));
  });
  await page.screenshot({ path: fileURLToPath(new URL(file, rawDir)) });
  console.log(`store/play/raw/${file}`);
}
await browser.close();
