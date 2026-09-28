# Kebijakan Privasi

*Terakhir diperbarui: 28 September 2026 (draf untuk versi beta; ganti alamat email kontak sebelum dipublikasikan)*

Kebijakan ini menjelaskan bagaimana aplikasi keyboard **ScriptGo** ("aplikasi") menangani data kamu.

## Ringkasan

- Template, kontak, dan field kustom **disimpan di HP kamu**. Data ini hanya dikirim ke server jika kamu masuk ke akun untuk sinkronisasi (opsional).
- Keyboard **tidak mengirim teks yang kamu ketik** ke internet dan tidak menyimpan isinya.
- Kami tidak menjual data dan tidak menampilkan iklan.

## Data yang diproses

| Data | Untuk apa | Disimpan di mana |
|---|---|---|
| Teks yang kamu ketik | Prediksi kata, autocorrect, dan mendeteksi shortcut template (misalnya `/salam`) | Diproses di HP. Kata yang sering diketik dapat disimpan di kamus pribadi di HP untuk prediksi. |
| Template | Menyisipkan pesan yang kamu buat | Database lokal di HP |
| Kontak dan field kustom | Mengisi variabel template, misalnya `{nama}` dan `{kota}` | Database lokal di HP |
| Email akun (opsional) | Masuk untuk sinkronisasi antar-HP, lewat kode atau link di email atau lewat akun Google. Kami juga mengirim dua email soal paketmu: 3 hari sebelum trial Pro berakhir dan saat trial berakhir | Server sinkronisasi (Supabase, Singapura) |
| Nama dan email dari akun Google (opsional) | Masuk dengan Google; kami hanya menerima nama, email, dan foto profil dari Google | Server sinkronisasi |
| Profil usaha (diisi saat onboarding) | Nama usaha, jenis usaha, provinsi dan kota/kabupaten, peran, tempat jualan, jumlah admin, perkiraan chat per hari, perkiraan omzet, dan nomor WhatsApp. Dipakai untuk menyesuaikan ScriptGo dengan usahamu, analisa pemakaian secara agregat, dan menghubungi kamu soal masa trial jika kamu menyetujuinya | Server sinkronisasi |
| Pendaftaran beta dan waitlist di scriptgo.id | Email (wajib), serta nomor WhatsApp dan perangkat yang kamu pakai (Android, iPhone, laptop) jika kamu mengisinya. Kami mengirim satu email konfirmasi; pendaftaran yang tidak dikonfirmasi tidak diundang ke beta. Dipakai untuk mengirim link beta, mengundang ke uji Google Play, memberi harga pendiri, dan mengabari saat rilis. Minta hapus kapan saja lewat email di bawah | Server (Supabase, Singapura) |
| Uji judul di scriptgo.id | Situs menyimpan kode acak dan versi judul yang kamu lihat di penyimpanan browser (localStorage, bukan cookie), lalu mencatat bahwa kode itu melihat halaman dan menekan tombol daftar. Tidak ada IP, nama, atau data perangkat yang disimpan. Dipakai untuk membandingkan dua versi judul halaman; kalau kamu mendaftar, versi judulnya ikut tersimpan di data pendaftaranmu. Hapus dengan menghapus data situs di browser | Browser kamu dan server (Supabase, Singapura) |
| Statistik pemakaian | Menampilkan "waktu dihemat" | Hanya jumlah pemakaian dan jumlah karakter, **bukan isi teks**, di HP |

## Izin aplikasi

- **Internet (INTERNET)**: hanya dipakai app pengelola untuk masuk akun dan sinkronisasi. Keyboard tidak memakai internet.
- **Kontak HP (READ_CONTACTS)**: hanya dipakai saat kamu memilih "Impor dari kontak HP", dan untuk saran nama kontak di keyboard jika fitur itu diaktifkan. Data kontak tidak dikirim ke luar HP.
- **Peringatan keyboard dari Android**: saat keyboard diaktifkan, Android menampilkan peringatan standar bahwa keyboard dapat membaca teks yang diketik. Peringatan ini muncul untuk semua keyboard pihak ketiga. Aplikasi ini tidak mengirim teks tersebut ke mana pun.

## Profil usaha

Saat pertama kali memakai aplikasi, kamu mengisi profil usaha singkat (nama usaha, jenis usaha, lokasi, peran, tempat jualan, jumlah admin, perkiraan chat per hari, perkiraan omzet dengan pilihan "Rahasia", dan nomor WhatsApp). Profil disimpan di HP dan dikirim ke server setelah kamu masuk ke akun. Semua isian bisa diubah kapan saja di tab **Akun → Profil usaha**.

**Lokasi**: jika kamu mengetuk "Deteksi lokasi saya" dan mengizinkan lokasi perkiraan (bukan GPS presisi), aplikasi mengubah posisi HP menjadi nama kota/kabupaten dan provinsi memakai layanan geocoding bawaan Android. Yang disimpan hanya nama kota dan provinsi; titik koordinat tidak disimpan dan tidak dikirim ke server kami. Keyboard tidak pernah memakai lokasi.

Nomor WhatsApp hanya dipakai untuk menghubungi kamu (misalnya pengingat masa trial) jika kamu mencentang persetujuan. Profil usaha tidak dijual atau dibagikan, dipakai untuk analisa pemakaian secara agregat, dan ikut terhapus saat akun dihapus.

## Sinkronisasi (opsional)

Jika kamu masuk (dengan Google, atau dengan kode atau link di email) di menu **Akun**, template, kontak, dan field kustom disalin ke server kami (Supabase, pusat data Singapura) supaya bisa dipakai di HP lain dengan akun yang sama. Data dikirim lewat koneksi terenkripsi (HTTPS) dan hanya bisa diakses oleh akunmu. Pengaturan keyboard dan app (tema, bahasa, tata letak, dan pilihan lain di menu pengaturan) juga ikut disimpan, supaya tetap sama setelah install ulang. Teks yang kamu ketik dan statistik pemakaian **tidak** disinkronkan, dan keyboard sendiri tidak pernah terhubung ke internet; sinkronisasi hanya dilakukan oleh app pengelola.

Tanpa masuk akun, semua data tetap hanya di HP. Keluar dari akun tidak menghapus data di HP. Untuk menghapus akun beserta semua data di server, buka tab **Akun** di aplikasi lalu pilih **Hapus akun**, atau ikuti [panduan hapus akun](hapus-akun.md).

## Riwayat clipboard (opsional)

Jika kamu masuk ke akun dan mengatur lama simpan riwayat clipboard keyboard ke **tanpa batas**, riwayat clipboard berupa teks ikut disimpan di akunmu supaya tetap ada setelah aplikasi dipasang ulang. Setiap item **dienkripsi di HP** (AES-256-GCM) sebelum dikirim. Kuncinya dibuat di HP dan disimpan di Block Store Google (cadangan Google yang terenkripsi end-to-end dengan kunci layar HP-mu), tidak pernah dikirim ke server ScriptGo, sehingga server hanya menyimpan data acak yang tidak bisa dibaca, termasuk oleh tim ScriptGo. Gambar dan file di clipboard tidak ikut. Atur lama simpan ke batas waktu mana pun untuk menghentikannya.

## Chrome extension

Extension membaca teks di kolom yang sedang kamu ketik **hanya di browser** untuk mendeteksi shortcut seperti `/salam`; teks itu tidak dikirim ke mana pun. Di WhatsApp Web, extension membaca nama dan nomor chat yang sedang terbuka untuk menyarankan kontak, juga hanya di browser. Template, kontak, dan field diunduh dari akunmu lewat sinkronisasi dan disimpan di penyimpanan lokal browser; keluar dari akun menghapusnya. Satu-satunya server yang dihubungi extension adalah server sinkronisasi di atas.

## AI untuk template

Jika kamu memakai **Buat dengan AI** atau **Rapikan dengan AI** (di app, atau di halaman Kelola pada Chrome extension), deskripsi atau isi template yang kamu kirim (beserta nama field seperti `kota`, tanpa isinya) diteruskan server kami ke penyedia model AI lewat OpenRouter untuk menghasilkan template. Data kontak dan isi chat tidak pernah dikirim. Model AI gratis dapat menyimpan atau memakai teks tersebut untuk melatih modelnya, jadi jangan menulis data pribadi pelanggan di deskripsi. Kami hanya mencatat jumlah pemakaian untuk jatah harian, bukan isi permintaannya. Jika kamu menekan **Laporkan hasil ini** di bawah hasil AI, kami menyimpan deskripsi atau teks yang kamu kirim, hasil AI tersebut, dan alasan laporannya bersama akunmu, supaya tim ScriptGo bisa memeriksa dan memperbaiki AI. Laporan ikut terhapus saat akun dihapus.

## Pembayaran

Jika kamu berlangganan ScriptGo Pro di scriptgo.id, kami menyimpan pesananmu (paket, jumlah, status, waktu bayar, dan nomor referensi) bersama akunmu untuk mengaktifkan Pro. Pembayaran diproses oleh payment gateway Duitku: kami mengirim email, nama, dan jumlah tagihan ke Duitku, dan data kartu, rekening, atau e-wallet kamu dimasukkan langsung di halaman Duitku, tidak pernah melewati server kami. Data pesanan ikut terhapus saat akun dihapus; catatan transaksi di Duitku mengikuti kebijakan Duitku.

## Pusat bantuan

Pesan yang kamu kirim dari Pusat bantuan (app atau extension) disimpan bersama jenis pesannya, email balasan jika kamu mengisinya, versi app, tipe perangkat, bahasa app, dan akunmu jika kamu sedang masuk. Isinya hanya dibaca tim ScriptGo untuk membantu dan memperbaiki aplikasi, tidak pernah berisi teks yang kamu ketik di keyboard, dan ikut terhapus kaitannya dengan akunmu saat akun dihapus.


## Menghapus data

Hapus template atau kontak dari dalam aplikasi, atau hapus data aplikasi melalui Pengaturan Android → Aplikasi → ScriptGo → Penyimpanan → Hapus data. Menghapus aplikasi juga menghapus semua datanya.

## Kode sumber

Aplikasi ini open-source di bawah lisensi GPL-3.0 dan merupakan turunan dari HeliBoard. Kode sumbernya dapat diperiksa siapa saja.

## Kontak

Pertanyaan tentang privasi: halo@scriptgo.id.
