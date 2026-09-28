// Renders the link preview image (static/og.png, 1200×630) shown when scriptgo.id is shared on
// WhatsApp, Facebook, or X. Run after changing the text or the logo: node og.mjs
import { readFileSync } from "node:fs";
import { chromium } from "playwright";

const mark = readFileSync("static/mark.svg", "utf8");
const font = (w) => `@font-face{font-family:U;font-weight:${w};src:url(data:font/woff2;base64,${readFileSync(`static/fonts/urbanist-${w}.woff2`).toString("base64")})}`;
const html = `<!doctype html><html><head><style>${font(600)}${font(800)}
body{margin:0;width:1200px;height:630px;background:#0b1220;color:#f1f5fb;font-family:U;position:relative;overflow:hidden}
.g{position:absolute;inset:0;background:radial-gradient(560px 420px at 88% 30%,rgba(14,165,233,.45),transparent 70%),radial-gradient(520px 420px at 96% 95%,rgba(99,102,241,.4),transparent 70%)}
.c{position:absolute;left:84px;top:78px;right:420px}
.b{display:flex;align-items:center;gap:16px;font-weight:800;font-size:40px;letter-spacing:-.5px}
.b img{width:64px;height:64px}
h1{font-size:76px;line-height:1.02;letter-spacing:-3px;font-weight:800;margin:58px 0 26px}
h1 span{background:linear-gradient(120deg,#38bdf8,#818cf8 70%);-webkit-background-clip:text;color:transparent}
p{font-size:32px;font-weight:600;color:#a9b6c8;margin:0}
.chat{position:absolute;right:70px;top:150px;width:330px;display:grid;gap:14px;font-weight:600;font-size:23px;line-height:1.35}
.q,.a{padding:14px 18px;border-radius:18px;color:#111b21}
.q{background:#fff;border-top-left-radius:6px;margin-right:40px}
.a{background:#dcf8c6;border-top-right-radius:6px;margin-left:10px}
.u{position:absolute;left:84px;bottom:64px;font-size:26px;font-weight:800;color:#38bdf8;letter-spacing:.5px}
.k{justify-self:end;background:linear-gradient(135deg,#0ea5e9,#6366f1);color:#fff;padding:8px 18px;border-radius:999px;font-weight:800}
</style></head><body><div class="g"></div>
<div class="c"><div class="b"><img src="data:image/svg+xml;base64,${Buffer.from(mark).toString("base64")}">ScriptGo</div>
<h1>Balas chat pelanggan <span>dalam hitungan detik</span></h1><p>Keyboard template untuk seller &amp; CS</p></div>
<div class="chat"><div class="q">Kak, ongkir ke Bandung berapa?</div><div class="k">/ongkir</div><div class="a">Halo Kak Rina, ongkir ke Bandung Rp18.000 via JNE (2–3 hari) 🙏</div></div>
<div class="u">scriptgo.id · Beta gratis</div>
</body></html>`;

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1200, height: 630 } });
await page.setContent(html);
await page.evaluate(() => document.fonts.ready);
await page.screenshot({ path: "static/og.png" });
await browser.close();
console.log("static/og.png");
