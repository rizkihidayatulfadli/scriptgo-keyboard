// Builds the public site into site/dist/: the landing page (index.html), sign-up (/daftar) and
// email confirmation (/konfirmasi) pages, sitemap and robots.txt, and the privacy policy and
// account deletion pages from docs/*.md.
// Deploy dist/ to any static host; Google Play and the Chrome Web Store need these URLs.
import { createHash } from "node:crypto";
import { cpSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { marked } from "marked";

const pages = [
  { source: "../docs/privacy-policy.md", out: "privacy.html" },
  { source: "../docs/hapus-akun.md", out: "hapus-akun.html" },
];

const origin = "https://scriptgo.id";

rmSync("dist", { recursive: true, force: true });
mkdirSync("dist", { recursive: true });

const layout = (title, body, mainClass = "doc") => `<!doctype html>
<html lang="id">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>${title}</title>
<link rel="icon" href="favicon.png" />
<link rel="stylesheet" href="style.css" />
</head>
<body>
<header><a href="index.html"><img src="mark.svg" alt="" width="32" height="32" /> ScriptGo</a></header>
<main class="${mainClass}">${body}</main>
<footer><a href="privacy.html">Kebijakan privasi</a> · <a href="hapus-akun.html">Hapus akun</a></footer>
</body>
</html>
`;

for (const page of pages) {
  const markdown = readFileSync(page.source, "utf8");
  const title = markdown.match(/^# (.+)$/m)?.[1] ?? "";
  // Links between docs point at the published pages.
  const html = marked.parse(markdown).replaceAll('href="privacy-policy.md"', 'href="privacy.html"').replaceAll('href="hapus-akun.md"', 'href="hapus-akun.html"');
  writeFileSync(`dist/${page.out}`, layout(title, html));
  console.log(`dist/${page.out}`);
}

// Landing and sign-up pages are full documents with their own stylesheets and scripts.
const copied = ["landing.css", "landing.js", "style.css", "signup.css", "supabase.js", "daftar.html", "daftar.js", "konfirmasi.html", "konfirmasi.js", "bayar.html", "bayar.js"];
for (const file of copied) cpSync(file, `dist/${file}`);

// FAQ structured data comes from the FAQ on the page itself, so the two never drift apart.
const index = readFileSync("index.html", "utf8");
const strip = (html) => html.replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim();
const faq = [...index.matchAll(/<details[^>]*><summary>([\s\S]*?)<\/summary><p>([\s\S]*?)<\/p><\/details>/g)].map(([, q, a]) => ({
  "@type": "Question",
  name: strip(q),
  acceptedAnswer: { "@type": "Answer", text: strip(a) },
}));
if (faq.length === 0) throw new Error("index.html: no FAQ entries found");
const faqJson = JSON.stringify({ "@context": "https://schema.org", "@type": "FAQPage", mainEntity: faq }).replaceAll("<", "\\u003c");
writeFileSync("dist/index.html", index.replace("<!--faq-jsonld-->", `<script type="application/ld+json">${faqJson}</script>`));

// Icons rendered from design/logo (node design/render.mjs).
cpSync("static", "dist", { recursive: true });

// Cache busting: every page asks for its CSS, JS and logo images with a hash of the file's content
// (landing.css?v=1a2b3c4d), so browsers never mix a new page with an old stylesheet or an old logo.
// Favicons and link previews are cached especially long, so they get a new URL whenever they change.
const hash = (file) => createHash("sha256").update(readFileSync(`dist/${file}`)).digest("hex").slice(0, 8);
const assets = ["style.css", "landing.css", "signup.css", "landing.js", "supabase.js", "daftar.js", "konfirmasi.js", "bayar.js", "mark.svg", "favicon.png", "icon.png", "og.png"];
const versions = Object.fromEntries(assets.map((file) => [file, hash(file)]));
for (const page of readdirSync("dist").filter((f) => f.endsWith(".html"))) {
  let html = readFileSync(`dist/${page}`, "utf8");
  for (const [file, v] of Object.entries(versions)) {
    html = html.replaceAll(`"${file}"`, `"${file}?v=${v}"`).replaceAll(`"${origin}/${file}"`, `"${origin}/${file}?v=${v}"`);
  }
  writeFileSync(`dist/${page}`, html);
}
// Cloudflare Pages headers: pages and assets are always revalidated; fonts never change.
writeFileSync("dist/_headers", `/*
  Cache-Control: public, max-age=0, must-revalidate
/fonts/*
  Cache-Control: public, max-age=31536000, immutable
`);

// Search engines: public pages only (/konfirmasi and /bayar are noindex).
const urls = ["/", "/daftar", "/privacy.html", "/hapus-akun.html"];
writeFileSync("dist/sitemap.xml", `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls.map((u) => `  <url><loc>${origin}${u}</loc></url>`).join("\n")}
</urlset>
`);
writeFileSync("dist/robots.txt", `User-agent: *\nAllow: /\nDisallow: /konfirmasi\nDisallow: /bayar\n\nSitemap: ${origin}/sitemap.xml\n`);
// Passkeys: Android only uses a passkey for scriptgo.id when this file lists the app's signing
// certificate. ANDROID_CERT_SHA256 holds one or more SHA-256 fingerprints (upload key and Play app
// signing key), comma-separated; see docs/auth-setup.md. Not secret, but only known at deploy time.
const fingerprints = (process.env.ANDROID_CERT_SHA256 ?? "").split(",").map((f) => f.trim().toUpperCase()).filter(Boolean);
if (fingerprints.length) {
  mkdirSync("dist/.well-known", { recursive: true });
  const statement = [
    {
      relation: ["delegate_permission/common.handle_all_urls", "delegate_permission/common.get_login_creds"],
      target: { namespace: "android_app", package_name: "id.scriptgo.keyboard", sha256_cert_fingerprints: fingerprints },
    },
  ];
  writeFileSync("dist/.well-known/assetlinks.json", JSON.stringify(statement, null, 2) + "\n");
  console.log("dist/.well-known/assetlinks.json");
} else {
  console.log("ANDROID_CERT_SHA256 not set: no assetlinks.json, passkeys will not work in the app");
}
console.log("dist/index.html");
