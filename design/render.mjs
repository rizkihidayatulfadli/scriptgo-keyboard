// Renders every PNG listed in design/assets.json from the SVG logo sources, using the Chromium
// that Playwright installs for the extension tests. Run from the repo root after `npm ci` in
// extension/: node design/render.mjs
import { createRequire } from "node:module";
import { mkdirSync, readFileSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(new URL("../extension/package.json", import.meta.url));
const { chromium } = require("playwright");

const root = new URL("../", import.meta.url);
const assets = JSON.parse(readFileSync(new URL("design/assets.json", root), "utf8"));

const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 1 });
for (const { source, out, size, background, shape, padding } of assets) {
  const svg = readFileSync(new URL(`design/${source}`, root), "utf8");
  const inset = Math.round(size * padding);
  const radius = shape === "circle" ? "50%" : shape === "square" ? `${Math.round(size * 0.22)}px` : "0";
  await page.setViewportSize({ width: size, height: size });
  await page.setContent(`<!doctype html><html><body style="margin:0;background:transparent">
    <div style="width:${size}px;height:${size}px;box-sizing:border-box;padding:${inset}px;
      background:${background ?? "transparent"};border-radius:${radius};overflow:hidden">
      <img src="data:image/svg+xml;base64,${Buffer.from(svg).toString("base64")}"
        style="display:block;width:100%;height:100%" alt="">
    </div></body></html>`);
  await page.locator("img").evaluate((img) => img.decode());
  const path = fileURLToPath(new URL(out, root));
  mkdirSync(dirname(path), { recursive: true });
  await page.screenshot({ path, omitBackground: true, clip: { x: 0, y: 0, width: size, height: size } });
  console.log(`${out} (${size}px)`);
}
await browser.close();
