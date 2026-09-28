# Spesifikasi Sintaks Template

Dokumen ini adalah **sumber kebenaran** untuk template engine. Implementasi Kotlin (Android) dan TypeScript (Chrome extension) harus menghasilkan output yang identik dan lolos semua kasus di [`template-fixtures.json`](template-fixtures.json).

Kalau aturan di sini berubah, perbarui dokumen ini **dan** fixtures-nya di commit yang sama.

## Ringkasan

| Sintaks | Arti | Contoh output |
|---|---|---|
| `{nama}` | Variabel: diisi dari kontak, atau ditanyakan di form | `Budi` |
| `{nama\|Kak}` | Variabel dengan nilai default | `Kak` (jika kosong) |
| `{kurir:JNE,J&T,SiCepat}` | Variabel pilihan (dropdown); opsi pertama jadi default | `JNE` |
| `{@tanggal}` | Variabel bawaan, tidak pernah ditanyakan | `26 September 2026` |
| `{@kursor}` | Posisi kursor setelah template disisipkan | *(kosong)* |
| `\{` `\}` `\\` | Karakter literal | `{` `}` `\` |

## Variabel

Bentuk: `{key}`, `{key|default}`, atau `{key:opsi1,opsi2,...}`.

- **Key** hanya boleh berisi huruf, angka, dan `_`. Key tidak peka huruf besar/kecil: `{Kota}` sama dengan `{kota}`, dan dinormalisasi ke huruf kecil.
- Key yang tidak valid (mengandung spasi atau karakter lain, atau kosong seperti `{}`) membuat seluruh `{...}` diperlakukan sebagai **teks biasa**. Editor template di app sebaiknya memberi peringatan.
- **Default** (`|`): teks setelah `|` sampai `}`.
- **Pilihan** (`:`): daftar opsi dipisah koma. Opsi pertama menjadi default. Spasi di sekitar opsi dipangkas.
- Pemisahnya adalah `|` atau `:` **pertama** (yang tidak di-escape) setelah key. Karakter `|` atau `:` sesudahnya menjadi bagian dari teks: `{kurir:JNE|Reguler,J&T}` punya opsi `JNE|Reguler` dan `J&T`, sedangkan `{jam|10:00}` punya default `10:00`.
- Key yang muncul lebih dari sekali hanya **ditanyakan sekali** dan memakai nilai yang sama di semua tempat. Default/opsi diambil dari kemunculan pertama.

### Key khusus

| Key | Diambil dari |
|---|---|
| `nama` | Nama kontak |
| `nomor` | Nomor HP pertama kontak, diformat seperti tipe `phone` (`081234567890`) |

Key lainnya dicocokkan dengan `FieldDefinition.key` milik kontak.

`channel` adalah field bawaan biasa (tipe pilihan, id tetap `builtin-channel`, dibuat otomatis untuk setiap pengguna, pilihannya bisa diubah dan tidak bisa dihapus; lihat `engine/.../contacts/Channels.kt` dan port-nya di `extension/src/engine/contacts.ts`). Untuk template ia diperlakukan sama seperti field lain, jadi `{channel}` berisi nilai Channel kontak.

### Urutan pengisian nilai

Saat render, nilai setiap variabel diambil dari sumber pertama yang tersedia:

1. **Input user** di form (termasuk nilai yang sudah diprefill lalu diedit)
2. **Nilai dari kontak** yang dipilih (jika tidak kosong)
3. **Default** (`|`)
4. **Opsi pertama** (untuk variabel pilihan)
5. **Fallback field** (jika tidak kosong, lihat di bawah)
6. String kosong

Form di keyboard diprefill dengan urutan yang sama (langkah 2–5), lalu user bisa mengubahnya.

### Fallback field

Sebuah field kontak boleh punya **fallback**: nilai yang dipakai di semua template kalau kontak belum punya data untuk field itu, misalnya field `kota` dengan fallback `Bandung`. Disimpan sekali di field (`FieldDefinition.fallback`, kolom `field_definitions.fallback`, kosong = tanpa fallback), bukan di template.

- Fallback yang ditulis di template (`{kota|Jakarta}`) atau opsi pilihan (`{kurir:JNE,J&T}`) selalu menang, karena lebih spesifik untuk template itu.
- Disimpan dalam bentuk mentah seperti nilai kontak (`150000` untuk mata uang) dan diformat dengan `formatFieldValue` sebelum render. Untuk tipe `choice`, fallback harus salah satu pilihan.
- Dipakai juga saat template disisipkan tanpa kontak.
- Form menandai nilai dari fallback field sama seperti fallback template, dan nilai itu tidak disimpan ke kontak kecuali user mengubahnya.

### Sisipkan tanpa form

Saat template disisipkan tanpa form, variabel yang tidak punya sumber nilai (langkah 1–5 kosong) ditulis sebagai `[key]`, misalnya `[total]`, supaya mudah ditemukan dan diedit di kolom chat. Input user berupa string kosong tetap dihitung sebagai nilai.

## Format Nilai per Tipe Field

Nilai field disimpan dalam **bentuk mentah**, lalu diubah jadi teks lewat fungsi murni `formatFieldValue(type, raw)` sebelum masuk ke `render`. Jika key variabel cocok dengan sebuah `FieldDefinition`, tipe field itu juga menentukan bentuk input di form, dan input user diformat dengan fungsi yang sama.

| Tipe | Disimpan sebagai | Output | Input di form |
|---|---|---|---|
| `text` | Teks | Apa adanya | Satu baris |
| `longtext` | Teks (boleh multi-baris) | Apa adanya | Multi-baris |
| `number` | Angka desimal dengan titik (`1500.5`) | Pemisah ribuan titik, desimal koma (`1.500,5`) | Keyboard angka |
| `currency` | Angka dengan titik desimal (`150000`, `1500.5`); mata uang diambil dari field | `Rp150.000`, `US$1.500,50` (lihat tabel mata uang) | Keyboard angka |
| `date` | ISO `YYYY-MM-DD` | `26 September 2026` | Pemilih tanggal |
| `choice` | Salah satu opsi | Apa adanya | Dropdown |
| `phone` (P1) | E.164 (`+6281234567890`) | Nomor Indonesia: format nasional tanpa pemisah (`081234567890`). Nomor negara lain: E.164 apa adanya | Keyboard telepon |
| `email` (P1) | Teks | Apa adanya | Keyboard email, divalidasi |
| `url` (P1) | Teks | Apa adanya | Keyboard URL, divalidasi |
| `boolean` (P1) | `true` / `false` | `Ya` / `Tidak` | Toggle |
| `multichoice` (P1) | Daftar opsi | Digabung dengan `, ` (`A, B, C`) | Centang beberapa |

- Nilai mentah kosong (atau daftar kosong) selalu menghasilkan string kosong, sehingga default di template tetap berlaku.
- Nilai mentah yang tidak valid untuk tipenya (misalnya `abc` untuk `currency`) dikembalikan apa adanya. `formatFieldValue` tidak pernah melempar error.
- Mata uang negatif ditulis `-Rp150.000` (tanda minus di depan simbol).

### Mata uang

Field `currency` menyimpan kode mata uangnya di `FieldDefinition.options` (satu elemen, misalnya `["USD"]`). Opsi kosong atau kode yang tidak dikenal berarti `IDR`. Fungsinya menjadi `formatFieldValue(type, raw, currency)`; parameter `currency` hanya dipakai tipe `currency` dan default-nya `IDR`.

Semua mata uang memakai gaya angka Indonesia (pemisah ribuan titik, desimal koma) supaya konsisten dengan tipe `number`. Simbol ditulis di depan angka tanpa spasi, kecuali simbol berupa huruf yang ditandai di bawah.

| Kode | Simbol | Desimal | Contoh mentah → output |
|---|---|---|---|
| `IDR` (default) | `Rp` | Tidak | `150000` → `Rp150.000` |
| `USD` | `US$` | Maks. 2 | `1500.5` → `US$1.500,50` |
| `SGD` | `S$` | Maks. 2 | `2500` → `S$2.500` |
| `MYR` | `RM` | Maks. 2 | `99.9` → `RM99,90` |
| `EUR` | `€` | Maks. 2 | `1200` → `€1.200` |
| `AUD` | `A$` | Maks. 2 | `75` → `A$75` |
| `JPY` | `¥` | Tidak | `15000` → `¥15.000` |
| `CNY` | `CN¥` | Maks. 2 | `888` → `CN¥888` |
| `SAR` | `SAR ` (dengan spasi) | Maks. 2 | `3500` → `SAR 3.500` |

- Bilangan bulat ditulis tanpa desimal (`US$1.500`). Jika ada desimal, selalu dua digit (`1500.5` → `US$1.500,50`).
- Nilai dengan desimal untuk mata uang tanpa desimal (`IDR`, `JPY`), atau lebih dari dua digit desimal, dianggap tidak valid dan dikembalikan apa adanya.
- Kasus uji: [`field-format-fixtures.json`](field-format-fixtures.json).

## Variabel Bawaan

Diawali `@`, dihitung dari waktu lokal perangkat saat template disisipkan, dan tidak pernah muncul di form.

| Sintaks | Contoh output |
|---|---|
| `{@tanggal}` | `26 September 2026` |
| `{@tanggal_pendek}` | `26/09/2026` |
| `{@hari}` | `Sabtu` |
| `{@bulan}` | `September` |
| `{@tahun}` | `2026` |
| `{@jam}` | `14.30` (24 jam, pemisah titik) |
| `{@kursor}` | Tidak menghasilkan teks; menandai posisi kursor |

- Nama hari: Minggu, Senin, Selasa, Rabu, Kamis, Jumat, Sabtu.
- Nama bulan: Januari, Februari, Maret, April, Mei, Juni, Juli, Agustus, September, Oktober, November, Desember.
- Variabel bawaan yang tidak dikenal (misalnya `{@cuaca}`) diperlakukan sebagai teks biasa.
- Hanya `{@kursor}` **pertama** yang dipakai; sisanya dihapus tanpa output. Tanpa `{@kursor}`, kursor berada di akhir teks.

## Escape

| Tulis | Hasil |
|---|---|
| `\{` | `{` |
| `\}` | `}` |
| `\\` | `\` |
| `\,` (di dalam opsi pilihan) | `,` yang tidak memisahkan opsi |
| `\|` (di dalam variabel) | `|` literal |

Backslash yang diikuti karakter lain dibiarkan apa adanya.

## Kasus Error

Template engine **tidak pernah melempar error** saat render. Sintaks yang rusak dirender sebagai teks biasa:

- `{` tanpa pasangan `}` → teks biasa sampai akhir
- Key tidak valid → seluruh `{...}` jadi teks biasa

## Kontrak API Engine

Konseptual, berlaku untuk kedua bahasa:

```
parse(template: String) -> ParsedTemplate
ParsedTemplate.variables -> List<Variable>
    // urut kemunculan pertama, tanpa duplikat, tanpa variabel bawaan
    // Variable { key, default?, options? }

formatFieldValue(type: FieldType, raw) -> String
    // lihat "Format Nilai per Tipe Field"; dipanggil sebelum render untuk nilai kontak dan input user

render(parsed, contact?, inputs: Map<String, String>, now: LocalDateTime, markMissing = false,
       fieldFallbacks: Map<String, String> = {}) -> RenderResult
    // contact.fields, inputs, dan fieldFallbacks sudah berupa teks hasil formatFieldValue
    // RenderResult { text: String, cursor: Int? }
    // cursor = indeks dalam unit UTF-16 (String.length di Kotlin dan JS); null jika tidak ada {@kursor}
```

`now` selalu di-inject sebagai parameter supaya bisa dites. Jangan membaca jam sistem di dalam engine.

## Format Fixtures

Setiap kasus di [`template-fixtures.json`](template-fixtures.json):

```json
{
  "name": "deskripsi singkat",
  "template": "Halo {nama}!",
  "contact": { "name": "Budi", "phones": ["+6281234567890"], "fields": { "kota": "Bandung" } },
  "inputs": { "nama": "Pak Budi" },
  "now": "2026-09-26T14:30:00",
  "expected": {
    "variables": [{ "key": "nama" }],
    "text": "Halo Pak Budi!",
    "cursor": null
  }
}
```

`contact`, `inputs`, `now`, `markMissing` (default `false`), dan `fieldFallbacks` (fallback per field key, sudah diformat) boleh tidak ada (artinya: tidak ada kontak, input kosong, waktu tidak relevan). `now` adalah waktu lokal tanpa zona waktu.
