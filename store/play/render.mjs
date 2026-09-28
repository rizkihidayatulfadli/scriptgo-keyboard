// Renders the Google Play store graphics into store/play/out/:
//   icon-512.png          app icon (512×512, as in design/assets.json launcher icons)
//   feature-graphic.png   1024×500, no alpha (Play rejects transparency here)
//   logo-200.png          200×200 logo for payment gateway merchant profiles (Duitku)
//   logo-200-name.png     the same with the ScriptGo name under the mark
//   logo-100.png          100×100 project logo for Duitku's project form
//   duitku-bg.png         1920×1080 background for Duitku's payment page (no alpha)
//   screenshot-N.png      1080×1920 phone screenshots with a caption, from raw/ (screenshots.json)
// Uses the Chromium that Playwright installs for the site tests. Run from the repo root after
// `npm ci` in site/: node store/play/render.mjs
import { createRequire } from "node:module";
import { existsSync, mkdirSync, readFileSync } from "node:fs";
import { extname } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(new URL("../../site/package.json", import.meta.url));
const { chromium } = require("playwright");

const root = new URL("../../", import.meta.url);
const here = new URL("./", import.meta.url);
const read = (path, base = root) => readFileSync(new URL(path, base));
const out = (name) => fileURLToPath(new URL(`out/${name}`, here));
mkdirSync(new URL("out/", here), { recursive: true });

const mark = `data:image/svg+xml;base64,${read("design/logo/scriptgo-mark.svg").toString("base64")}`;
const font = (w) => `@font-face{font-family:U;font-weight:${w};src:url(data:font/woff2;base64,${read(`site/static/fonts/urbanist-${w}.woff2`).toString("base64")})}`;
const fonts = [500, 600, 700, 800].map(font).join("");
// design/tokens.json, plus the dark hero background of the site and og.png.
const night = "#0b1220";
const glow = `radial-gradient(620px 520px at 85% 12%,rgba(14,165,233,.45),transparent 70%),radial-gradient(640px 560px at 10% 95%,rgba(99,102,241,.38),transparent 70%)`;
const gradientText = `background:linear-gradient(120deg,#38bdf8,#818cf8 70%);-webkit-background-clip:text;background-clip:text;color:transparent`;

const browser = await chromium.launch();
const page = await browser.newPage();

async function shot(name, width, height, html, omitBackground = false) {
  await page.setViewportSize({ width, height });
  await page.setContent(`<!doctype html><html><head><style>${fonts}*{box-sizing:border-box}body{margin:0;font-family:U}</style></head><body>${html}</body></html>`);
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all([...document.images].map((img) => img.decode()));
  });
  await page.screenshot({ path: out(name), omitBackground, clip: { x: 0, y: 0, width, height } });
  console.log(`store/play/out/${name} (${width}×${height})`);
}

// Launcher icon at Play size: same look as the phone's (light tile, rounded by Play itself).
await shot("icon-512.png", 512, 512, `<div style="width:512px;height:512px;background:#EAF7F5;padding:92px"><img src="${mark}" style="display:block;width:100%;height:100%"></div>`);

// Merchant logo (Duitku asks for 200×200): the launcher icon's tile, and a version with the name.
await shot("logo-200.png", 200, 200, `<div style="width:200px;height:200px;background:#EAF7F5;padding:36px"><img src="${mark}" style="display:block;width:100%;height:100%"></div>`);
await shot("logo-200-name.png", 200, 200, `<div style="width:200px;height:200px;background:#EAF7F5;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px"><img src="${mark}" style="display:block;width:104px;height:104px"><div style="font-weight:800;font-size:30px;letter-spacing:-.02em;color:${night}">ScriptGo</div></div>`);
await shot("logo-100.png", 100, 100, `<div style="width:100px;height:100px;background:#EAF7F5;padding:18px"><img src="${mark}" style="display:block;width:100%;height:100%"></div>`);
// Duitku's payment page sits on this background, so it stays quiet: the site's dark hero glow only.
await shot("duitku-bg.png", 1920, 1080, `<div style="width:1920px;height:1080px;background:${glow.replaceAll("620px 520px", "1300px 1000px").replaceAll("640px 560px", "1300px 1000px")},${night}"></div>`);

// Feature graphic: brand, one line of value, and the keyboard moment (trigger → reply).
await shot(
  "feature-graphic.png",
  1024,
  500,
  `<div style="position:relative;width:1024px;height:500px;background:${night};color:#f1f5fb;overflow:hidden">
    <div style="position:absolute;inset:0;background:${glow}"></div>
    <div style="position:absolute;left:64px;top:70px;width:520px">
      <div style="display:flex;align-items:center;gap:14px;font-weight:800;font-size:34px;letter-spacing:-.5px"><img src="${mark}" style="width:54px;height:54px">ScriptGo</div>
      <div style="font-size:60px;line-height:1.04;letter-spacing:-2.4px;font-weight:800;margin:42px 0 20px">Balas chat pelanggan <span style="${gradientText}">dalam hitungan detik</span></div>
      <div style="font-size:25px;font-weight:600;color:#a9b6c8">Keyboard template untuk seller, CS, dan admin</div>
    </div>
    <div style="position:absolute;right:56px;top:96px;width:330px;display:grid;gap:14px;font-weight:600;font-size:21px;line-height:1.35">
      <div style="background:#fff;color:#111b21;padding:14px 18px;border-radius:18px;border-top-left-radius:6px;margin-right:36px">Kak, ongkir ke Bandung berapa?</div>
      <div style="justify-self:end;background:linear-gradient(135deg,#0ea5e9,#6366f1);color:#fff;padding:8px 18px;border-radius:999px;font-weight:800">/ongkir</div>
      <div style="background:#dcf8c6;color:#111b21;padding:14px 18px;border-radius:18px;border-top-right-radius:6px;margin-left:10px">Halo Kak Rina, ongkir ke Bandung Rp18.000 via JNE (2–3 hari) 🙏</div>
    </div>
  </div>`,
);

// Screenshots: caption on top, the raw screenshot in a phone-like frame below.
const { shots } = JSON.parse(read("screenshots.json", here));
let n = 0;
for (const s of shots) {
  const raw = new URL(`raw/${s.file}`, here);
  if (!existsSync(raw)) {
    console.log(`skip ${s.file} (not in store/play/raw/)`);
    continue;
  }
  n += 1;
  const type = extname(s.file).toLowerCase() === ".png" ? "png" : "jpeg";
  const image = `data:image/${type};base64,${readFileSync(raw).toString("base64")}`;
  await shot(
    `screenshot-${n}.png`,
    1080,
    1920,
    `<div style="position:relative;width:1080px;height:1920px;background:${night};color:#f1f5fb;overflow:hidden">
      <div style="position:absolute;inset:0;background:${glow}"></div>
      <div style="position:absolute;left:80px;right:80px;top:110px;text-align:center">
        <div style="font-size:78px;line-height:1.05;letter-spacing:-2.6px;font-weight:800;text-wrap:balance">${s.title}<br><span style="${gradientText}">${s.accent}</span></div>
        <div style="font-size:34px;line-height:1.35;font-weight:600;color:#a9b6c8;margin-top:26px;text-wrap:balance">${s.sub}</div>
      </div>
      <div style="position:absolute;left:50%;top:560px;transform:translateX(-50%);width:700px;height:1480px;border-radius:72px;background:#1a2028;padding:18px;box-shadow:0 40px 90px rgba(0,0,0,.45),inset 0 0 0 2px #2a323d">
        <img src="${image}" style="display:block;width:100%;height:100%;object-fit:cover;object-position:top;border-radius:56px">
      </div>
    </div>`,
  );
}
console.log(n ? `${n} screenshot(s)` : "No raw screenshots yet: add them to store/play/raw/ (see screenshots.json).");
await browser.close();
