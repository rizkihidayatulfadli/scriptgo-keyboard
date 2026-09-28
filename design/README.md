# Desain ScriptGo

Sumber tunggal identitas visual. Spesifikasi: [`docs/superpowers/specs/2026-09-27-scriptgo-visual-identity-design.md`](../docs/superpowers/specs/2026-09-27-scriptgo-visual-identity-design.md).

| File | Isi |
|---|---|
| `logo/scriptgo-mark.svg` | Logo C2 (tuts mengilap), ukuran 24 px ke atas |
| `logo/scriptgo-mark-small.svg` | Tanpa kilap, untuk ukuran di bawah 24 px |
| `logo/scriptgo-mark-mono.svg` | Satu warna, garis luar (ikon bertema Android, notifikasi) |
| `assets.json` | Daftar PNG yang dibuat dari logo (ikon Android, extension, site) |
| `tokens.json` | Warna (terang/gelap), radius, jarak, dan skala huruf |
| `fonts/` | Urbanist (OFL) dan skrip konversi ke TTF untuk Android |

## Mengubah logo

1. Edit SVG di `logo/`. Vector drawable Android (`android/app/src/main/res/drawable-v24/ic_launcher_*.xml`) ditulis tangan dengan geometri yang sama; ubah juga.
2. Render ulang semua PNG (butuh `npm ci` di `extension/` untuk Chromium dari Playwright): `node design/render.mjs`
3. Cek: `node design/check.mjs` (juga jalan di CI).

## Ikon

Ikon app pengelola memakai [Lucide](https://lucide.dev) (ISC, `icons/LICENSE-lucide.txt`). Daftar ikon yang dipakai ada di `icons/icons.json`; `ScriptGoIcons.kt` dibuat ulang dengan:

```sh
(cd /tmp && npm pack lucide-static@1.48.0 && tar xzf lucide-static-1.48.0.tgz)
node design/icons/generate.mjs /tmp/package/icon-nodes.json
```
