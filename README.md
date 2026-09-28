> **Kode sumber ScriptGo** (GPL-3.0). Salinan ini dibuat dari repo pengembangan (versi `47d8c39`) dan diperbarui setiap rilis. Dokumen internal dan bisnis tidak disertakan, jadi sebagian tautan di bawah tidak tersedia di sini.

# ScriptGo

Keyboard Android dan Chrome extension dengan text expander (`/shortcut` → template) dan mini-CRM (kontak + custom fields yang jadi variabel template), untuk seller, CS, admin, dan tim operasional di Indonesia.

"magic-keyboard" adalah nama internal di kode dan repo; nama produknya **ScriptGo**.

| Folder | Isi |
|---|---|
| `android/` | App Android: keyboard (fork [HeliBoard](https://github.com/HeliBorg/HeliBoard), lihat [`docs/fork.md`](docs/fork.md)) dan app pengelola template/kontak |
| `engine/` | Template engine dan logika kontak, Kotlin murni |
| `extension/` | Chrome extension (TypeScript, Manifest V3), lihat [`extension/README.md`](extension/README.md) |
| `supabase/` | Skema database dan RLS untuk sync akun |
| `site/` | Halaman publik (kebijakan privasi, hapus akun) |
| `docs/` | PRD, spesifikasi sintaks template, test fixtures, panduan rilis |

## Build

- Android: `cd android && ./gradlew assembleDebug` (butuh Android SDK), atau unduh APK dari CI **Android app**.
- Template engine: `cd engine && ./gradlew test`.
- Extension: `cd extension && npm ci && npm test && npm run build`.

## Lisensi

GPL-3.0, lihat [`LICENSE`](LICENSE). Keyboard ini turunan dari HeliBoard, yang juga berlisensi GPL-3.0 (dengan sebagian file Apache-2.0 dan CC-BY-SA-4.0, lihat `android/`).
