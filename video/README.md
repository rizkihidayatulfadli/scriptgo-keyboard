# Video promo ScriptGo

Dua video motion graphic (15 dan 30 detik) dalam tiga ukuran: 9:16 (Reels, TikTok, Status WA), 1:1 (feed IG/FB), dan 16:9 (YouTube). Spec: `docs/superpowers/specs/2026-09-28-promo-videos-design.md`.

- `scene.html`, `scene.css`, `scene.js`: animasinya. Semua teks dan waktu ada di `STORIES` dan `DEMOS` di `scene.js`.
- `render.mjs`: membuka `scene.html` di Chromium, memotret setiap frame (30 fps), membuat efek suara ketikan (disintesis, tanpa file audio), lalu menyusunnya jadi MP4 dengan ffmpeg.

```sh
cd video && npm ci
npm run render                                   # semua video → out/
npm run render -- --story 15 --format 9x16       # satu video saja
```

Lihat satu momen di browser tanpa render: buka `video/scene.html?story=30&t=12.5` lewat server lokal dari root repo (misalnya `npx serve .`).

Hasil di `out/` (tidak di-commit): `scriptgo-{15,30}s-{9x16,1x1,16x9}.mp4`, versi web 1280×720, dan poster. Versi web 30 detik dan posternya disalin ke `site/static/video/` (diputar dari tombol "Tonton 30 detik" di scriptgo.id). CI `video.yml` merender ulang saat file di sini berubah dan menyimpannya sebagai artifact `scriptgo-videos`.

Musik tidak disertakan: tambahkan dari library musik Instagram, TikTok, atau CapCut saat upload supaya aman hak cipta.
