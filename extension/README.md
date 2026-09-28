# Chrome extension

Ketik `/shortcut` di kolom teks mana pun (WhatsApp Web, X, Gmail, dll.) untuk memilih template, isi variabelnya, lalu sisipkan. Data template, kontak, dan field datang dari akun yang sama dengan aplikasi Android (sync Supabase, M7).

## Mencoba di Chrome

1. Unduh artifact `scriptgo-extension` dari run CI **Chrome extension** terbaru, lalu ekstrak. Atau build sendiri: `npm ci && npm run build`, hasilnya di `dist/extension/`.
2. Buka `chrome://extensions`, aktifkan **Developer mode**, klik **Load unpacked**, pilih folder hasil ekstrak (atau `dist/extension/`).
3. Klik ikon extension, masuk dengan email yang sama seperti di HP, masukkan kode 6 digit dari email.
4. Buka WhatsApp Web, ketik `/` di kolom chat, pilih template dengan ↑↓ lalu Enter.

## Perintah

| Perintah | Isi |
|---|---|
| `npm test` | Unit test: port template engine (kedua file fixtures di `docs/`), trigger, nomor HP, form, auto-detect kontak, aturan kelola template/field, dan sync di service worker |
| `npm run check` | Typecheck |
| `npm run build` | Bundel ke `dist/extension/` dan `dist/scriptgo-extension.zip` |
| `npm run e2e` | Memuat extension ke Chromium (Playwright) dan mengetik di textarea, input, `contenteditable`, editor **Lexical** (dipakai WhatsApp Web), dan halaman tiruan WhatsApp Web. Jalankan `npm run build` dulu |

## Struktur

| File | Isi |
|---|---|
| `src/engine/` | Logika murni, port dari `engine/` (Kotlin). Harus lolos fixtures yang sama; ubah keduanya bersamaan |
| `src/content.ts` | Content script: deteksi trigger, daftar saran (shadow DOM), menyisipkan teks. **Tanpa panggilan jaringan** |
| `src/form.ts` + `static/form.html` | Form "Untuk siapa?" + variabel, tampil di iframe milik extension supaya shortcut keyboard halaman tidak ikut campur |
| `src/background.ts` | Service worker: login kode OTP, sync push/pull ke Supabase (last-write-wins). Satu-satunya kode yang memakai jaringan |
| `src/popup.ts` + `static/popup.html` | Popup ikon toolbar: masuk, status sync, karakter pemicu, tombol ke halaman kelola |
| `src/manage.ts` + `static/manage.html` | Halaman kelola: tambah, ubah, hapus template dan field kontak. Simpan lewat service worker (baris ditandai `dirty`, lalu langsung diunggah), jadi ikut ke app di HP. Aturannya (shortcut, key field dari nama) ada di `src/engine/manage.ts`, sama dengan app. Kontak diubah lewat app; kontak baru bisa dibuat dari form ("+ Kontak baru"). Tombol "Buat/Rapikan dengan AI" di editor template (`src/aiPanel.ts`, logika di `src/ai.ts`) memanggil Edge Function `ai-template` lewat service worker, dengan jatah yang sama seperti di app |
| `src/store.ts` | Bentuk data di `chrome.storage.local` dan pesan antar-bagian |

### Kapan daftar saran muncul

- Daftar muncul begitu trigger diketik (misalnya `#` saja) dan hilang saat trigger dihapus.
- `input` / `textarea` dicek di event `input`. Editor kaya dicek juga di `selectionchange`, karena Lexical (WhatsApp Web) menangani sendiri huruf pertama di chat kosong dan Backspace tanpa event `input`.
- Esc menutup daftar untuk kata itu (misalnya saat user memang mau menulis `#hashtag`); daftar muncul lagi saat trigger baru diketik.

### Cara menyisipkan

- `input` / `textarea`: `document.execCommand("insertText")`, supaya undo dan state React ikut benar.
- Editor kaya (`contenteditable`): seleksi diperluas ke belakang untuk menutupi trigger, lalu `selectionchange` dikirim supaya editor seperti Lexical membaca seleksi baru, lalu teks dikirim sebagai event `paste`. Editor biasa yang tidak menangani paste buatan memakai `insertText`.
- Form di iframe hanya mau dibuka dengan nonce sekali pakai dari content script, jadi halaman lain tidak bisa menyematkan form dan membaca data kontak.

## Tes manual sebelum rilis

Otomatis (e2e) sudah mencakup textarea, input, `contenteditable`, dan Lexical. Yang wajib dicek manual di situs aslinya, karena DOM-nya berubah-ubah:

- [ ] WhatsApp Web: `/` di kolom chat, Enter tidak mengirim pesan saat daftar saran terbuka, template banyak baris, `{@kursor}`
- [ ] WhatsApp Web: di chat 1:1 dengan nomor yang ada di kontak, form menandai kontak itu dengan "Chat ini"
- [ ] WhatsApp Web: di chat dengan orang yang belum jadi kontak, "+ Kontak baru" terisi nama dan nomor dari chat, Channel "WhatsApp", lalu kontaknya muncul di app HP setelah sync
- [ ] X (twitter.com / x.com): kolom post dan balasan
- [ ] Satu situs lain dengan `textarea` biasa (misalnya Gmail atau Google Form)
- [ ] Halaman Kelola: mengetik, menempel teks, Enter, dan Backspace di sekitar chip variabel di isi pesan; klik chip → ubah fallback → Simpan template → teks tersimpan benar di app HP
- [ ] Akun: ganti nama, buat kata sandi lalu masuk dengan kata sandi, ganti tema (popup dan Kelola ikut)
