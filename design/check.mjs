// Validates the design sources and every asset generated from them. No dependencies, so CI can run
// it with plain Node: node design/check.mjs
import { existsSync, readFileSync } from "node:fs";

const root = new URL("../", import.meta.url);
const read = (path) => readFileSync(new URL(path, root));
const problems = [];

// Generated PNGs exist at the declared size.
const assets = JSON.parse(read("design/assets.json"));
for (const { source, out, size } of assets) {
  if (!existsSync(new URL(`design/${source}`, root))) problems.push(`${source}: source missing`);
  if (!existsSync(new URL(out, root))) {
    problems.push(`${out}: missing (run node design/render.mjs)`);
    continue;
  }
  const png = read(out);
  if (png.readUInt32BE(0) !== 0x89504e47) {
    problems.push(`${out}: not a PNG`);
    continue;
  }
  const [width, height] = [png.readUInt32BE(16), png.readUInt32BE(20)];
  if (width !== size || height !== size) problems.push(`${out}: ${width}×${height}, expected ${size}×${size}`);
}

// Tokens: every colour from the spec, in both themes.
const COLOR_TOKENS = ["accent-start", "accent-end", "accent-solid", "accent-ink", "accent-soft", "on-accent", "ink", "on-ink", "bg", "card", "line", "muted", "glow", "danger"];
const HEX = /^#[0-9A-F]{6}$/;
if (!existsSync(new URL("design/tokens.json", root))) problems.push("design/tokens.json: missing");
else {
  const tokens = JSON.parse(read("design/tokens.json"));
  for (const name of COLOR_TOKENS) {
    const token = tokens.color?.[name];
    if (!token) problems.push(`tokens.color.${name}: missing`);
    else for (const theme of ["light", "dark"]) {
      if (!HEX.test(token[theme] ?? "")) problems.push(`tokens.color.${name}.${theme}: expected #RRGGBB, got ${token[theme]}`);
    }
  }
  for (const group of ["radius", "space", "type"]) if (!tokens[group]) problems.push(`tokens.${group}: missing`);
}

// Font files for every platform, each folder with its licence.
const FONTS = [
  "design/fonts/OFL.txt",
  ...["regular", "medium", "semibold", "bold", "extrabold"].map((name) => `android/app/src/main/res/font/urbanist_${name}.ttf`),
  ...["extension/static/fonts", "site/static/fonts"].flatMap((dir) => [
    `${dir}/OFL.txt`,
    ...[400, 500, 600, 700, 800].map((weight) => `${dir}/urbanist-${weight}.woff2`),
  ]),
];
for (const path of FONTS) if (!existsSync(new URL(path, root))) problems.push(`${path}: missing`);

if (problems.length) {
  console.error(problems.join("\n"));
  process.exit(1);
}
console.log(`design: ${assets.length} assets, ${COLOR_TOKENS.length} colour tokens, ${FONTS.length} font files ok`);
