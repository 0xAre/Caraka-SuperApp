# Prompt eksekusi — Copywriting & Konsistensi Microcopy (Tahap 2 Enterprise UX)

Companion dari [2026-08-04-copywriting-sweep-design.md](2026-08-04-copywriting-sweep-design.md).
Bagian dari [Roadmap Enterprise UX](2026-08-04-enterprise-ux-roadmap.md).

## Cara pakai

1. **Jalankan ini SETELAH prompt Tahap 1 (onboarding wizard) selesai
   dan sudah direview** — task ini juga menulis copy final buat layar
   baru hasil Tahap 1. Kalau dijalankan sebelum Tahap 1, layar-layar
   onboarding baru itu belum ada.
2. Buka Codex, copy seluruh isi blok kode di bawah, paste sebagai
   prompt pertama. Pastikan agent-nya punya akses baca/tulis ke
   `E:\01-Proyek\CARAKA-APP`.
3. Review diff-nya — ini task teks, jadi paling gampang direview
   dengan baca ulang tiap string yang berubah.

## Prompt

```
Kamu mengerjakan repo Android Kotlin/Jetpack Compose "CARAKA" di
E:\01-Proyek\CARAKA-APP. Root Gradle project ada di subfolder app/,
modul aplikasi ada di app/app/. Jalankan gradlew dari app/.

Baca dulu docs/superpowers/specs/2026-08-04-copywriting-sweep-design.md
kalau tersedia — itu spec yang sudah disetujui, termasuk voice & tone
guide lengkap. Ringkasan voice guide-nya:

  - Jelas dulu, ramah kemudian — instruksi darurat (SOS, status
    jaringan) harus kebaca sekali lihat.
  - Bahasa manusia, bukan bahasa protokol — user gak perlu tau istilah
    internal ("bundle", "EPK_priv", "Directed/Stealth mode", "nonce").
    Terjemahkan ke akibat yang user rasakan.
  - TANPA emoji di teks fungsional (snackbar/error/status). Emoji cuma
    boleh di tempat yang memang dekoratif (bukan pesan status).
  - Aktif, bukan pasif ("Pesan terkirim ke kurir", bukan "Pesan telah
    berhasil dikirimkan oleh sistem").
  - Konsisten menyebut entitas yang sama — istilah "kurir" untuk peer
    pembawa pesan mode Caraka, jangan ganti-ganti sebutan.

═══════════════════════════════════════════════════════════════
TASK 1 (WAJIB) — Pindahkan string hardcoded di CourierViewModel.kt
═══════════════════════════════════════════════════════════════

File: app/app/src/main/java/com/example/caraka/viewmodel/CourierViewModel.kt

Ada 13 pemanggilan `_snackbar.emit("...")` dengan string literal
langsung di Kotlin (bukan `stringResource`). Untuk SETIAP baris di
bawah: (a) tambah entri baru di app/app/src/main/res/values/strings.xml
DAN values-en/strings.xml dengan key bermakna berawalan `courier_toast_`,
(b) tulis ulang isi teksnya sesuai voice guide (hilangkan emoji, ganti
istilah internal ke bahasa manusia), (c) ganti pemanggilan
`_snackbar.emit("...")` jadi pakai string resource. Untuk string yang
punya variabel interpolasi, gunakan `context`-independent approach:
CourierViewModel adalah ViewModel biasa (bukan AndroidViewModel), JADI
TIDAK punya akses langsung ke `Context`/`stringResource` — cek dulu
apakah CourierViewModel sudah punya cara resolve string resource (mis.
lewat `uiPreferences` yang menyimpan `Context`, atau parameter lain di
constructor). Kalau tidak ada, opsi paling minimal: tambahkan parameter
`context: Context` ke constructor CourierViewModel (via
`CourierViewModel.Factory` yang sudah ada di file yang sama) khusus
untuk resolve string, lalu pakai `context.getString(R.string.xxx, arg1, arg2)`
(placeholder `%1$s` dst di XML). JANGAN import Context kalau ternyata
sudah ada jalur lain yang lebih pas — cek dulu constructor & Factory
yang sudah ada sebelum menambah dependency baru.

Baris yang harus diubah (nomor baris sekarang, bisa geser sedikit —
cari berdasar isi teksnya):

  135: "Kredensial dikirim ke ${recipientId.take(8)} via chat terenkripsi."
       → arah baru: netral, tanpa jargon "kredensial" kalau bisa
         diganti "info" atau "data koneksi" yang lebih dipahami awam.
  138: "Gagal kirim kredensial via chat: ${e.message}"
  191: "Kontak ${payload.name} ditambahkan."
  228: "Kurir ${event.byPeerId.take(8)} menerima — bundle dikirim."
       → "bundle" adalah istilah internal, ganti ke "pesan" atau
         "titipan" (istilah yang sudah user-facing di string caraka_*
         lain — cek strings.xml existing dulu, pakai istilah yang
         SUDAH dipakai di sana biar konsisten, jangan bikin istilah
         baru lagi).
  234: "Kurir menolak untuk membawa pesan."
  241: "📦 Bundle kurir diterima. Kamu sedang membawa ${event.mode} bundle."
       → hilangkan emoji, hilangkan "${event.mode}" mentah (itu string
         teknis "DIRECTED"/"STEALTH" dari kode, bukan bahasa user;
         kalau perlu dibedakan, pakai kalimat yang beda per mode, bukan
         inject raw enum value ke tengah kalimat).
  247: "🔁 Caraka Mode aktif — broadcast stealth token ke sekitar."
       → hilangkan emoji dan istilah "broadcast stealth token".
  292: "❌ Pengiriman gagal: ${event.reason}"
       → hilangkan emoji, pertahankan `${event.reason}` (itu memang
         pesan error yang relevan ditampilkan).
  325: "Gagal mengirim permintaan Caraka: ${e.message}"
  342: "Tidak bisa kirim: kunci publik penerima belum ada. Lakukan QR exchange / terhubung dulu."
       → "kunci publik" & "QR exchange" istilah teknis, ganti ke
         instruksi yang actionable buat user awam.
  354: "📤 Permintaan Caraka terkirim! Bundle: ${bundle.bundleId.take(8)}"
       → hilangkan emoji; ID bundle 8-karakter boleh tetap ditampilkan
         (berguna buat user cocokkan sama riwayat), tapi bahasa
         sekitarnya dihaluskan.
  382: "🔮 Bundle stealth dibuat. Bagikan EPK_priv + nonce ke Z secara out-of-band."
       → ini pesan buat flow Stealth mode yang cukup teknis; sederhanakan
         sebisa mungkin tapi kalau memang butuh instruksi teknis (share
         kredensial secara terpisah), boleh tetap eksplisit — hilangkan
         cuma emoji dan singkatan "EPK_priv"/"out-of-band" kalau ada
         padanan lebih manusiawi, TAPI jangan sampai instruksinya jadi
         ambigu (ini bagian keamanan, kejelasan lebih penting dari
         gaya).
  393: "✅ Kamu siap menjadi kurir."
       → hilangkan emoji saja, kalimatnya sudah oke.

═══════════════════════════════════════════════════════════════
TASK 2 (WAJIB) — Polish string onboarding hasil Tahap 1
═══════════════════════════════════════════════════════════════

Cari string dengan prefix `onboarding_` di
app/app/src/main/res/values/strings.xml (dibuat di task Tahap 1
sebelumnya: onboarding_welcome_headline, onboarding_welcome_subheadline,
onboarding_welcome_cta, onboarding_permission_title,
onboarding_permission_desc, onboarding_permission_cta,
onboarding_permission_hint, onboarding_finish_cta). Isinya sekarang
placeholder-quality. Tulis ulang sesuai voice guide — headline welcome
harus jelas nyampein VALUE PROP (kenapa app ini ada, kapan dipakai),
bukan generik "Selamat datang". Update juga values-en/strings.xml
dengan padanan Inggris yang setara (bukan terjemahan literal
kaku — padanan natural).

═══════════════════════════════════════════════════════════════
TASK 3 (OPSIONAL, kalau nemu pas nyisir — JANGAN dicari-cari aktif)
═══════════════════════════════════════════════════════════════

Kalau pas mengerjakan Task 1-2 kamu baca string lain di strings.xml
yang JELAS melanggar voice guide di atas (pasif berlebihan, istilah
teknis bocor ke user, typo, atau tidak konsisten sama istilah yang
sudah dibetulkan di Task 1), boleh dibetulkan sekalian. TIDAK WAJIB
menyisir seluruh 550+ entri strings.xml satu per satu — kalau ketemu,
benerin; kalau tidak, biarkan. Prioritaskan family string yang
berkaitan sama flow yang baru saja disentuh (`caraka_*`, `qr_*`,
`tour_*`, `setup_*`, `courier_*`).

═══════════════════════════════════════════════════════════════
DI LUAR SCOPE — jangan kerjakan
═══════════════════════════════════════════════════════════════
- Jangan ubah nama role/entity (BPBD/POLRI/PMI/CIVILIAN) atau istilah
  brand ("CARAKA", "Caraka Mode").
- Jangan ubah struktur/layout UI — teks doang.
- Jangan sentuh logic non-copy (validasi, network, crypto).
- Jangan coba "menyisir semua 550+ string" secara paksa/mekanis kalau
  itu bikin kualitas turun (asal ganti kata tanpa mikir) — lebih baik
  sedikit tapi tepat daripada banyak tapi asal.

═══════════════════════════════════════════════════════════════
VERIFIKASI
═══════════════════════════════════════════════════════════════
Build dari folder app/ (BUKAN root repo):
  cd app
  ./gradlew.bat :app:assembleDebug

Di Windows, kalau JAVA_HOME error "invalid directory":
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

Build harus BUILD SUCCESSFUL. Setelah selesai, jalankan:
  grep -c "string name" app/app/src/main/res/values/strings.xml
  grep -c "string name" app/app/src/main/res/values-en/strings.xml
Dua angka itu HARUS SAMA — kalau beda, ada string yang cuma ditambah
di satu file (id atau en saja), perbaiki sebelum selesai. Jangan ubah
versionCode/versionName — di luar scope task ini.
```
