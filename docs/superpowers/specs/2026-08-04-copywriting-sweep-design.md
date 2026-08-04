# Copywriting & Konsistensi Microcopy — Desain

**Tanggal**: 2026-08-04
**Status**: Disetujui, siap dieksekusi
**Bagian dari**: [Roadmap Enterprise UX](2026-08-04-enterprise-ux-roadmap.md), Tahap 2
**Prasyarat**: Tahap 1 (Onboarding Wizard) sudah dieksekusi & direview
lebih dulu — tahap ini juga menulis ulang copy untuk layar-layar baru
hasil Tahap 1.

## Masalah

Dua gap konkret, hasil audit:

1. **13 pesan snackbar di `CourierViewModel.kt` di-hardcode langsung**
   di Kotlin, bypass `strings.xml` sepenuhnya — gak konsisten dengan
   disiplin string-resource yang dipakai di 100% tempat lain di app
   (gak bisa di-translate ke `values-en`, gak bisa di-audit tone-nya
   bareng string lain). Contoh nyata (baris di `CourierViewModel.kt`):
   - baris 135: `"Kredensial dikirim ke ${recipientId.take(8)} via chat terenkripsi."`
   - baris 241: `"📦 Bundle kurir diterima. Kamu sedang membawa ${event.mode} bundle."`
   - baris 247: `"🔁 Caraka Mode aktif — broadcast stealth token ke sekitar."`
   - baris 292: `"❌ Pengiriman gagal: ${event.reason}"`
   - baris 354: `"📤 Permintaan Caraka terkirim! Bundle: ${bundle.bundleId.take(8)}"`
   - baris 382: `"🔮 Bundle stealth dibuat. Bagikan EPK_priv + nonce ke Z secara out-of-band."`
   - baris 393: `"✅ Kamu siap menjadi kurir."`
   (dan 6 lainnya — lihat prompt untuk daftar lengkap baris)

   Ini kedengaran seperti log debug developer yang kepasang, bukan
   microcopy produk: emoji dipakai gak konsisten (kadang ada kadang
   nggak), istilah teknis internal bocor ke user ("bundle", "EPK_priv",
   "stealth token" — istilah dari desain protokol, bukan bahasa yang
   dipahami relawan/warga awam di lapangan).

2. **`strings.xml` (596 baris, ~550+ entri)** — tone-nya sudah lumayan
   (formal Indonesia, jelas), tapi belum ada voice guide eksplisit
   yang bisa dijadiin patokan konsistensi, jadi kualitasnya bervariasi
   antar layar tergantung siapa yang nulis kapan.

## Voice & Tone Guide

Dasar: CARAKA adalah alat komunikasi darurat offline dipakai relawan
BPBD/POLRI/PMI dan warga sipil saat internet/listrik/seluler mati.
Konteks pemakaian real: tangan gemetar, sinyal darurat, mungkin dalam
gelap, mungkin panik. Copy HARUS:

- **Jelas dahulu, ramah kemudian.** Instruksi darurat (SOS, status
  jaringan) harus bisa dibaca sekali lihat, gak butuh mikir ulang.
  Microcopy non-darurat (kontak, pengaturan) boleh sedikit lebih hangat.
- **Bahasa manusia, bukan bahasa protokol.** User gak perlu tau istilah
  internal seperti "bundle", "EPK_priv", "Directed/Stealth mode",
  "nonce". Terjemahkan ke akibat yang user rasakan: "pesan dikirim",
  "kamu jadi kurir buat pesan ini", bukan jargon implementasi.
- **Tanpa emoji di teks fungsional** (snackbar, error, status). Emoji
  boleh dipertimbangkan HANYA di tempat yang memang sudah dekoratif
  (mis. ilustrasi kosong), bukan di pesan status/error — biar
  konsisten dan terasa serius untuk konteks darurat, bukan playful.
- **Aktif, bukan pasif.** "Pesan terkirim ke kurir" bukan "Pesan telah
  berhasil dikirimkan oleh sistem kepada kurir".
- **Konsisten menyebut entitas yang sama.** Kalau sudah dipilih istilah
  "kurir" buat peer pembawa pesan (mode Caraka/Directed), jangan
  ganti-ganti sebutan (courier/carrier/pembawa) di string lain.

## Cakupan

**Prioritas 1 — WAJIB dikerjakan:**
- Semua 12 string hardcoded di `CourierViewModel.kt` → pindah ke
  `strings.xml` + `values-en/strings.xml`, tulis ulang sesuai voice
  guide (hilangkan emoji fungsional, hilangkan istilah internal).
- String baru hasil Tahap 1 (`onboarding_*` di
  `2026-08-04-onboarding-wizard-prompt.md`) — polish final sesuai
  voice guide (sebelumnya cuma placeholder).
- String `caraka_*`, `qr_*`, `tour_*`, `setup_*` (semua yang berkaitan
  langsung dengan flow onboarding/courier yang baru saja disentuh) —
  review konsistensi istilah.

**Prioritas 2 — kerjakan kalau nemu pas nyisir, jangan dicari-cari:**
- String lain di `strings.xml` yang jelas melanggar voice guide (pasif
  berlebihan, istilah teknis bocor, typo) boleh dibetulkan sambil
  lewat. TIDAK WAJIB comprehensive 100% dari 550+ entri — kalau ketemu
  yang jelek, benerin; kalau enggak, biarkan.

**Di luar cakupan:**
- String murni teknis/debug yang gak pernah dilihat user awam (label
  developer, content description internal yang gak fungsional untuk
  a11y).
- Nama role/entity (BPBD/POLRI/PMI/CIVILIAN) dan istilah brand
  ("CARAKA", "Caraka Mode") — tidak diubah.
- Struktur/layout UI — task ini teks doang, bukan visual.

## Error handling / risiko

- Setiap string yang dipindah dari hardcode ke resource **harus** ada
  di KEDUA file (`values/strings.xml` id, `values-en/strings.xml` en)
  — kalau cuma satu, build tetap sukses tapi versi EN pecah di
  runtime (fallback ke id, tidak crash, tapi harus dihindari).
- String dengan interpolasi (`${recipientId.take(8)}`,
  `${event.reason}`, dst) → pakai `String.format(stringResource(R.string.xxx), value)`
  dengan placeholder `%1$s` dst, ikuti pola yang SUDAH ADA di
  `ProfileSetupScreen`/`QrIdentityScreen` (`String.format(stringResource(R.string.setup_enter_password), ...)`,
  `String.format(connectedToastTpl, parsed.name)`) — jangan pakai
  pola baru.

## Testing

Tidak ada logic baru — ini task teks murni. Verifikasi = build sukses
+ baca ulang tiap string yang diganti (tidak ada unit test yang
bernilai untuk copy).
