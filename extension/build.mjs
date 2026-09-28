// Bundles the extension into dist/ and zips it as dist/scriptgo-extension.zip.
import { build } from "esbuild";
import { cpSync, mkdirSync, readFileSync, rmSync } from "node:fs";
import { execFileSync } from "node:child_process";

rmSync("dist", { recursive: true, force: true });
mkdirSync("dist/extension", { recursive: true });

// Minified: the content script runs on every page, so less code to parse means faster pages.
const common = { bundle: true, minify: true, target: "chrome116", outdir: "dist/extension", logLevel: "info", legalComments: "none" };
// Content scripts cannot be ES modules; everything else can.
await build({ ...common, entryPoints: ["src/content.ts"], format: "iife" });
await build({ ...common, entryPoints: ["src/background.ts", "src/form.ts", "src/popup.ts", "src/welcome.ts", "src/manage.ts", "src/auth.ts", "src/help.ts", "src/account.ts"], format: "esm" });
cpSync("static", "dist/extension", { recursive: true });

const manifest = JSON.parse(readFileSync("static/manifest.json", "utf8"));
const pkg = JSON.parse(readFileSync("package.json", "utf8"));
if (manifest.version !== pkg.version) throw new Error(`manifest version ${manifest.version} != package.json ${pkg.version}`);

execFileSync("zip", ["-qr", "../scriptgo-extension.zip", "."], { cwd: "dist/extension" });
console.log("dist/scriptgo-extension.zip");
