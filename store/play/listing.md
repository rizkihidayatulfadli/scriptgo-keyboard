# Listing Google Play: ScriptGo

Teks untuk Play Console → **Grow users → Store presence → Main store listing**. Bahasa default **Indonesia (id-ID)**, terjemahan **English (en-US)**. Batas Play: nama 30 karakter, deskripsi singkat 80, deskripsi lengkap 4.000. Aturan Play yang dijaga di sini:

- Tanpa harga, promo, atau ajakan membeli di luar app. Pembayaran ScriptGo lewat web, dan app tidak boleh mengarahkan ke sana (PRD §9).
- Tanpa klaim "terbaik", "#1", atau testimoni.
- Tanpa daftar kata kunci.

Gambar: `node store/play/render.mjs` → `store/play/out/`:

| Aset | Ukuran |
|---|---|
| Ikon (`icon-512.png`) | 512×512 |
| Feature graphic (`feature-graphic.png`) | 1024×500, tanpa transparansi |
| Screenshot HP (`screenshot-N.png`) | 1080×1920, 2–8 buah, dari `raw/` dan teks di `screenshots.json` |

Layar di `raw/` sekarang digambar ulang dari desain app oleh `node store/play/mockups.mjs` (token, font, ikon, teks, dan tata letak yang sama dengan app; data contoh). Play meminta screenshot menggambarkan app apa adanya: bandingkan dengan app di HP sebelum diunggah, dan ganti file di `raw/` dengan screenshot asli kapan saja (nama file sama), lalu jalankan `render.mjs` lagi.

## Indonesia (id-ID)

**Nama app** (8/30)

```
ScriptGo
```

**Deskripsi singkat** (72/80)

```
Keyboard template untuk balas chat pelanggan cepat: ketik /ongkir, jadi.
```

**Deskripsi lengkap**

```
Balas chat pelanggan dalam hitungan detik, langsung dari keyboard.

ScriptGo adalah keyboard Android dengan template balasan untuk seller online, CS, dan admin. Ketik shortcut seperti /ongkir atau /rekening di WhatsApp, Instagram, marketplace, atau app mana pun, lalu pilih template yang muncul di atas keyboard. Balasan lengkap langsung tertulis.

KENAPA SCRIPTGO
• Pertanyaan yang sama tiap hari (harga, ongkir, cara order, nomor rekening) cukup dibalas dengan satu shortcut.
• Nama, kota, dan data pelanggan lain terisi sendiri dari kontak, jadi balasan tetap terasa personal.
• Tetap bisa dipakai sebagai keyboard sehari-hari: mengetik biasa, emoji, dan clipboard.

FITUR
• Template dengan shortcut: /salam, /ongkir, /rekening, /followup, atau buat sendiri.
• Variabel: {nama}, {kota}, tanggal, jam, dan pilihan seperti kurir. Isi sekali, dipakai di semua template.
• Kontak pelanggan dengan field tambahan (kota, ukuran, catatan), bisa diimpor dari kontak HP atau CSV.
• Tahan spasi untuk membuka daftar template tanpa mengingat shortcut.
• Bantuan AI: buat template dari deskripsi singkat, atau rapikan kalimat jadi lebih sopan, singkat, atau santai.
• Tag untuk mengelompokkan template, dan pencarian cepat.
• Sinkron antar HP dan ke Chrome extension ScriptGo untuk WhatsApp Web.
• Statistik waktu yang kamu hemat.
• Tampilan terang dan gelap, bahasa Indonesia dan Inggris.

PRIVASI
• Keyboard ScriptGo tidak memakai internet dan tidak pernah menyimpan atau mengirim teks yang kamu ketik.
• Statistik hanya menghitung jumlah karakter, bukan isi pesan.
• Sinkronisasi opsional: hanya template, kontak, dan pengaturan yang kamu simpan, lewat koneksi terenkripsi. Akun dan datanya bisa dihapus kapan saja dari app.

COCOK UNTUK
Seller online dan reseller, admin toko, customer service, dan tim operasional yang membalas puluhan sampai ratusan chat setiap hari.

ScriptGo dibangun di atas HeliBoard dan berlisensi GPL-3.0.
Kebijakan privasi: https://scriptgo.id/privacy.html
```

## English (en-US)

**App name**

```
ScriptGo
```

**Short description** (75/80)

```
Template keyboard to answer customer chats fast: type /shipping, it's done.
```

**Full description**

```
Answer customer chats in seconds, right from your keyboard.

ScriptGo is an Android keyboard with reply templates for online sellers, customer service, and admins. Type a shortcut like /shipping or /bank in WhatsApp, Instagram, marketplaces, or any app, then pick the template shown above the keys. The full reply is written for you.

WHY SCRIPTGO
• The same questions every day (price, shipping, how to order, bank details) take one shortcut.
• Your customer's name, city, and other details fill in from your contacts, so replies stay personal.
• Still a full everyday keyboard: normal typing, emoji, and clipboard.

FEATURES
• Templates with shortcuts: /hello, /shipping, /bank, /followup, or your own.
• Variables: {name}, {city}, date, time, and choices such as the courier. Fill them once, use them in every template.
• Customer contacts with extra fields (city, size, notes), importable from phone contacts or CSV.
• Hold the space bar to open the template list without remembering shortcuts.
• AI help: create a template from a short description, or make a reply more polite, shorter, or more casual.
• Tags to group templates, and quick search.
• Sync between phones and to the ScriptGo Chrome extension for WhatsApp Web.
• See how much time you save.
• Light and dark themes, Indonesian and English.

PRIVACY
• The ScriptGo keyboard does not use the internet and never stores or sends what you type.
• Statistics only count characters, never message content.
• Sync is optional: only the templates, contacts, and settings you save, over an encrypted connection. Delete your account and its data any time from the app.

MADE FOR
Online sellers and resellers, shop admins, customer service, and operations teams who answer dozens to hundreds of chats a day.

ScriptGo is built on HeliBoard and licensed under GPL-3.0.
Privacy policy: https://scriptgo.id/privacy.html
```

## Isian lain di Play Console

| Isian | Nilai |
|---|---|
| Kategori | Productivity (Produktivitas) |
| Tag | Keyboard, Productivity, Business |
| Email kontak | halo@scriptgo.id |
| Situs | https://scriptgo.id |
| Kebijakan privasi | https://scriptgo.id/privacy.html |
| Hapus akun | https://scriptgo.id/hapus-akun.html |
