# Prompt eksekusi — Autopilot Tahap 1 + Tahap 2 (Enterprise UX)

Gabungan [2026-08-04-onboarding-wizard-prompt.md](2026-08-04-onboarding-wizard-prompt.md)
dan [2026-08-04-copywriting-sweep-prompt.md](2026-08-04-copywriting-sweep-prompt.md)
jadi satu prompt berurutan, buat dijalankan tanpa pendampingan (Codex
lanjut sendiri dari Tahap 1 ke Tahap 2 begitu Tahap 1 kelar & lolos
verifikasi).

## Cara pakai

1. Buka Codex di repo `E:\01-Proyek\CARAKA-APP`.
2. Copy **seluruh isi blok kode** di bawah, paste sebagai prompt
   pertama, lalu tinggal — Codex akan jalan lewat kedua tahap sendiri.
3. Prompt ini sengaja bikin Codex **commit lokal** di akhir tiap tahap
   (checkpoint) tapi **tidak push, tidak bikin tag, tidak ubah
   versionCode/versionName**. Itu bagian gua kerjain manual pas lo
   balik, sama seperti alur v0.1.1-beta / v0.1.2-beta kemarin — biar
   ada titik review sebelum apa pun naik ke GitHub.
4. Kalau Codex mentok di Tahap A (build gagal terus), dia bakal
   **berhenti total** dan gak lanjut ke Tahap B — jadi lo gak akan
   balik dan nemu Tahap B numpuk di atas Tahap A yang rusak.
5. Pas balik, kabarin gua — gua review diff tiap checkpoint, verifikasi
   ulang build+test, baru bump versi & rilis.

## Prompt

```
Kamu mengerjakan repo Android Kotlin/Jetpack Compose "CARAKA" di
E:\01-Proyek\CARAKA-APP. Root Gradle project ada di subfolder app/
(bukan di root repo), modul aplikasi ada di app/app/. Jalankan semua
perintah gradlew dari folder app/. Jalankan semua perintah git dari
root repo E:\01-Proyek\CARAKA-APP.

Kamu akan mengerjakan 2 TAHAP SECARA BERURUTAN, otomatis, TANPA nunggu
konfirmasi manusia di antara keduanya — orang yang minta ini akan
pergi kira-kira 2 jam. Kerjakan sampai selesai kedua tahap, atau
sampai kamu ketemu blocker yang beneran gak bisa dilanjutkan setelah
percobaan wajar (jangan infinite-loop mencoba hal yang sama berkali-
kali tanpa progress — kalau sudah coba 2-3 pendekatan berbeda dan
masih gagal, berhenti dan laporkan, jangan terus mencoba).

ATURAN CHECKPOINT (WAJIB diikuti persis):
- Sebelum lanjut dari TAHAP A ke TAHAP B: verifikasi build TAHAP A
  sukses (lihat instruksi VERIFIKASI di masing-masing tahap). Kalau
  sukses → buat SATU git commit LOKAL untuk seluruh perubahan Tahap A
  (git add -A && git commit -m "..."), JANGAN push, JANGAN bikin tag,
  JANGAN ubah versionCode/versionName di app/app/build.gradle.kts.
  Baru lanjut ke TAHAP B.
- Kalau build TAHAP A GAGAL setelah kamu coba perbaiki dengan 2-3
  pendekatan berbeda dan masih gagal: STOP TOTAL. Jangan mulai TAHAP
  B. Langsung ke bagian LAPORAN AKHIR di bawah dan jelaskan apa yang
  gagal + apa yang sudah dicoba.
- Hal yang sama berlaku untuk TAHAP B: verifikasi sukses → commit lokal
  → LAPORAN AKHIR. Gagal → STOP, jangan buat commit setengah jalan,
  LAPORAN AKHIR jelaskan apa yang gagal.
- JANGAN PERNAH menjalankan `git push`, `git tag`, atau mengubah
  `versionCode`/`versionName` — itu di luar scope prompt ini, akan
  dikerjakan manual oleh orangnya setelah review.

═══════════════════════════════════════════════════════════════════
TAHAP A — Onboarding Wizard
═══════════════════════════════════════════════════════════════════

Baca dulu docs/superpowers/specs/2026-08-04-onboarding-wizard-design.md
kalau tersedia — itu spec yang sudah disetujui untuk task ini. Task di
bawah adalah ringkasannya, tapi spec itu sumber kebenaran kalau ada
yang ambigu.

TUJUAN: Ganti alur first-run yang sekarang (satu form statis
`ProfileSetupScreen` lalu, terpisah, carousel tur otomatis yang numpuk
sama dialog izin sistem) jadi satu wizard 4 langkah yang koheren.

── KONDISI SEKARANG (baca dulu sebelum ubah apa pun) ──

- app/app/src/main/java/com/example/caraka/ui/screens/ProfileSetupScreen.kt
  — satu Composable, Scaffold penuh, isinya: role picker (4 role:
  CIVILIAN/BPBD/POLRI/PMI, masing-masing punya warna & icon lewat
  `roleColorFor`/`roleIconFor`), field password kalau role authority
  (validasi terhadap `authorityPasswords` map hardcoded di situ —
  JANGAN diubah/dibenerin, itu di luar scope, biarkan persis sama),
  field nama kalau role civilian, tombol submit yang panggil
  `onSetupComplete(name, role)`.

- app/app/src/main/java/com/example/caraka/ui/components/OnboardingTourOverlay.kt
  — composable `OnboardingTourOverlay(visible, onDismiss)`, isinya
  private val tourSteps = listOf(TourStep(...) x5) yang reference
  R.string.tour_step1_title..tour_step5_desc, ditampilkan sebagai
  carousel modal (dot indicator + Next/Skip).

- app/app/src/main/java/com/example/caraka/MainActivity.kt, composable
  `CarakaNav` (baris ~210-480):
  - baris ~221-224: `if (!hasIdentity) { ProfileSetupScreen { name, role -> viewModel.setupIdentity(name, role) }; return }`
  - baris ~229-253: `LaunchedEffect(Unit)` yang OTOMATIS minta
    permission (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION,
    NEARBY_WIFI_DEVICES, POST_NOTIFICATIONS, BLUETOOTH_ADVERTISE/
    CONNECT/SCAN tergantung SDK version) lewat `permissionLauncher`
    (`rememberLauncherForActivityResult(RequestMultiplePermissions())`
    yang manggil `activity?.startMeshService()` di callback-nya).
  - baris ~268: `var showTour by remember(onboardingDoneFlag) { mutableStateOf(!onboardingDoneFlag) }`
    — auto-true kalau onboarding belum pernah selesai.
  - `CarakaNav` menerima parameter `onOnboardingDismissed: () -> Unit`
    dari caller (`CarakaRoot`), yang SUDAH terhubung ke
    `scope.launch { uiPrefs.setOnboardingDone(true) }`.
  - `OnboardingTourOverlay(visible = showTour, onDismiss = { showTour = false; onOnboardingDismissed() })`
    dipanggil di suatu tempat di body (cari pemanggilannya).
  - `HelpScreen`-nya dipanggil dengan `onLaunchTour = { showTour = true }`
    (baris ~413) — ini tombol "lihat tur lagi" di layar Help, HARUS
    tetap jalan setelah perubahan.

── YANG HARUS DIKERJAKAN ──

1. Buat package baru `ui/screens/onboarding/` berisi:

   a. `OnboardingWizardScreen.kt` — composable utama:
        @Composable
        fun OnboardingWizardScreen(onComplete: (name: String, role: String) -> Unit)
      Pegang `var stepIndex by remember { mutableStateOf(0) }` (0..3).
      Render progress indicator (dot row, boleh contoh dari pola dot
      indicator yang sudah ada di OnboardingTourOverlay.kt) di atas,
      lalu Crossfade/AnimatedContent antar 4 step composable di bawah
      ini berdasar stepIndex. TopAppBar dengan tombol back yang mundur
      stepIndex (kecuali di step 0, gak ada tombol back).

   b. `WelcomeStep.kt` — layar baru, sederhana: ilustrasi/icon besar
      (boleh reuse R.drawable.ill_profile_identity atau drawable ill_*
      lain yang relevan yang sudah ada di res/drawable), headline +
      subheadline (string baru, isi placeholder yang masuk akal buat
      sekarang — copy final di-polish di Tahap B, jangan spend waktu
      di wording di sini), tombol "Lanjut" → onNext().

   c. `IdentityStep.kt` — PINDAHKAN (bukan tulis ulang) seluruh isi
      body Composable dari ProfileSetupScreen.kt ke sini. Ganti
      signature dari `ProfileSetupScreen(onSetupComplete: (String, String) -> Unit)`
      jadi:
        @Composable
        fun IdentityStep(onNext: (name: String, role: String) -> Unit)
      Hapus Scaffold/TopAppBar pembungkusnya (parent
      OnboardingWizardScreen yang punya TopAppBar/progress, step ini
      cukup Column konten). Semua logic role picker, validasi
      authorityPasswords, error message: SALIN PERSIS, jangan diubah.

   d. `PermissionRationaleStep.kt` — BARU:
        @Composable
        fun PermissionRationaleStep(onRequestPermissions: () -> Unit)
      Isi: ikon (Icons.Default.LocationOn atau sejenis), penjelasan
      singkat kenapa app butuh Location/Bluetooth/Nearby-WiFi (dipakai
      buat mesh P2P offline, bukan tracking — pakai string baru,
      placeholder wajar), tombol "Izinkan & Lanjutkan" yang manggil
      onRequestPermissions(). Step ini TIDAK menahan user kalau
      permission ditolak — tombol lanjut jalan terus begitu callback
      permission (accepted ATAU denied) selesai; tambahkan teks kecil
      "Bisa diaktifkan lagi nanti lewat Settings" di bawah tombol.

   e. `FeatureHighlightStep.kt` — reuse `tourSteps` (pindahkan definisi
      list `TourStep`/`tourSteps` dari OnboardingTourOverlay.kt ke file
      kecil baru `OnboardingContent.kt` di package yang sama, supaya
      bisa diimpor dari DUA tempat: OnboardingTourOverlay.kt yang lama
      DAN FeatureHighlightStep.kt yang baru — jangan duplikasi list-nya).
      Tampilkan sebagai mini-carousel di dalam step ini (dot indicator
      + Next/Selesai). Tombol terakhir: "Mulai Pakai CARAKA" →
      onFinish().

2. Di `OnboardingWizardScreen`, sambungkan alur:
   - step 0 (Welcome) → onNext → step 1
   - step 1 (Identity) → onNext(name, role) → simpan name/role di state
     lokal wizard → step 2
   - step 2 (PermissionRationale) → onRequestPermissions() diteruskan
     ke PARAMETER `onRequestPermissions: () -> Unit` yang diterima
     OnboardingWizardScreen dari caller (lihat langkah 3 di bawah) →
     lanjut ke step 3 SEGERA setelah dipanggil (tidak menunggu hasil
     permission)
   - step 3 (FeatureHighlight) → onFinish → panggil
     `onComplete(savedName, savedRole)` (parameter OnboardingWizardScreen)

3. Di `MainActivity.kt`:
   - Hapus `import com.example.caraka.ui.screens.ProfileSetupScreen`,
     tambah import `com.example.caraka.ui.screens.onboarding.OnboardingWizardScreen`.
   - Ganti blok `if (!hasIdentity) { ProfileSetupScreen { name, role -> viewModel.setupIdentity(name, role) }; return }`
     jadi:
       if (!hasIdentity) {
           OnboardingWizardScreen(
               onComplete = { name, role ->
                   viewModel.setupIdentity(name, role)
                   onOnboardingDismissed()
               }
           )
           return
       }
     CATATAN: di titik ini permission-request BELUM bisa dipanggil
     karena `permissionLauncher` didefinisikan SETELAH blok early-return
     ini di kode yang sekarang. Kamu perlu menaikkan (hoist) definisi
     `permissionLauncher` dan daftar permission (baris ~229-253 yang
     sekarang) ke SEBELUM blok `if (!hasIdentity)`, ubah dari
     `LaunchedEffect(Unit)` otomatis jadi sebuah fungsi lokal
     `fun requestMeshPermissions()` yang isinya logic yang sama (cek
     `ContextCompat.checkSelfPermission` lalu `permissionLauncher.launch`
     atau langsung `activity?.startMeshService()` kalau semua sudah
     granted) — TAPI dipanggil dari DUA tempat:
       a. dari `OnboardingWizardScreen(onRequestPermissions = { requestMeshPermissions() })`
          saat user belum punya identity (first run)
       b. tetap dipanggil sekali secara otomatis (LaunchedEffect(Unit))
          untuk kasus user yang SUDAH punya identity dari sebelumnya
          (`hasIdentity == true` saat app dibuka, mis. reinstall data
          masih ada / update app) — supaya mesh service tetap start
          otomatis buat existing user, perilaku ini JANGAN berubah.
   - Hapus `var showTour by remember(onboardingDoneFlag) { mutableStateOf(!onboardingDoneFlag) }`,
     ganti jadi `var showTour by remember { mutableStateOf(false) }`
     (default false — gak auto-muncul lagi setelah onboarding, karena
     kontennya sekarang ada di step 4 wizard). Parameter
     `onboardingDoneFlag` yang jadi nganggur di signature `CarakaNav`
     boleh dihapus KALAU sudah tidak dipakai di tempat lain — cek dulu
     dengan grep sebelum menghapus parameter.
   - Pemanggilan `OnboardingTourOverlay(...)` dan `HelpScreen(onLaunchTour = { showTour = true })`
     TETAP ADA, tidak diubah — ini jalur "lihat tur lagi" dari Help,
     harus tetap berfungsi persis seperti sekarang.

4. Hapus file `ui/screens/ProfileSetupScreen.kt` setelah isinya
   dipindah ke IdentityStep.kt — jangan tinggalkan file kosong atau
   import yang menganggur ke file yang sudah dihapus (grep referensi
   `ProfileSetupScreen` di seluruh project untuk pastikan tidak ada
   pemanggil lain yang kelewat).

5. Tambah string resource baru (id + en) di
   app/app/src/main/res/values/strings.xml dan values-en/strings.xml,
   dekat string `setup_*`/`tour_*` yang sudah ada:
   - onboarding_welcome_headline
   - onboarding_welcome_subheadline
   - onboarding_welcome_cta ("Lanjut")
   - onboarding_permission_title
   - onboarding_permission_desc
   - onboarding_permission_cta ("Izinkan & Lanjutkan")
   - onboarding_permission_hint ("Bisa diaktifkan lagi nanti lewat Settings")
   - onboarding_finish_cta ("Mulai Pakai CARAKA")
   Isi teksnya boleh sederhana/placeholder-quality — JANGAN habiskan
   waktu poles wording, itu dikerjakan di TAHAP B nanti.

── DI LUAR SCOPE TAHAP A — jangan kerjakan ──
- Jangan ubah logic validasi password authority atau daftar
  authorityPasswords di IdentityStep — pindahkan apa adanya.
- Jangan ganti library permission (tetap
  ActivityResultContracts.RequestMultiplePermissions()).
- Jangan bikin ilustrasi/aset gambar baru — reuse drawable yang sudah
  ada di res/drawable.
- Jangan poles/tulis-ulang copy secara mendalam — string baru boleh
  seadanya asal masuk akal, itu kerjaan TAHAP B.
- Jangan sentuh CourierViewModel/CarakaTab/MessagesScreen — tidak
  terkait Tahap A.

── VERIFIKASI TAHAP A ──
cd app
./gradlew.bat :app:assembleDebug
(Windows, kalau JAVA_HOME error "invalid directory":
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr")

Harus BUILD SUCCESSFUL. Kalau memungkinkan, jalankan juga manual di
emulator/device: hapus data app (biar hasIdentity balik false), buka
app, pastikan urutannya: Welcome → Identity → Permission rationale →
Feature highlight → Home, dan dari Home → Settings/Help → "lihat tur
lagi" masih memunculkan OnboardingTourOverlay seperti sebelumnya.

── CHECKPOINT SETELAH TAHAP A SUKSES ──
cd E:\01-Proyek\CARAKA-APP
git add -A
git commit -m "feat(onboarding): wizard 4 langkah menggantikan setup statis + tur otomatis"

Baru lanjut ke TAHAP B di bawah ini.

═══════════════════════════════════════════════════════════════════
TAHAP B — Copywriting & Konsistensi Microcopy
═══════════════════════════════════════════════════════════════════

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

── TASK 1 (WAJIB) — Pindahkan string hardcoded di CourierViewModel.kt ──

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

Baris yang harus diubah (nomor baris sekarang, bisa geser sedikit
karena Tahap A tidak menyentuh file ini — cari berdasar isi teksnya
kalau nomor baris meleset):

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

── TASK 2 (WAJIB) — Polish string onboarding hasil Tahap A ──

Cari string dengan prefix `onboarding_` di
app/app/src/main/res/values/strings.xml (dibuat di Tahap A sebelumnya:
onboarding_welcome_headline, onboarding_welcome_subheadline,
onboarding_welcome_cta, onboarding_permission_title,
onboarding_permission_desc, onboarding_permission_cta,
onboarding_permission_hint, onboarding_finish_cta). Isinya sekarang
placeholder-quality. Tulis ulang sesuai voice guide — headline welcome
harus jelas nyampein VALUE PROP (kenapa app ini ada, kapan dipakai),
bukan generik "Selamat datang". Update juga values-en/strings.xml
dengan padanan Inggris yang setara (bukan terjemahan literal
kaku — padanan natural).

── TASK 3 (OPSIONAL, kalau nemu pas nyisir — JANGAN dicari-cari aktif) ──

Kalau pas mengerjakan Task 1-2 kamu baca string lain di strings.xml
yang JELAS melanggar voice guide di atas (pasif berlebihan, istilah
teknis bocor ke user, typo, atau tidak konsisten sama istilah yang
sudah dibetulkan di Task 1), boleh dibetulkan sekalian. TIDAK WAJIB
menyisir seluruh 550+ entri strings.xml satu per satu — kalau ketemu,
benerin; kalau tidak, biarkan. Prioritaskan family string yang
berkaitan sama flow yang baru saja disentuh (`caraka_*`, `qr_*`,
`tour_*`, `setup_*`, `courier_*`, `onboarding_*`).

── DI LUAR SCOPE TAHAP B — jangan kerjakan ──
- Jangan ubah nama role/entity (BPBD/POLRI/PMI/CIVILIAN) atau istilah
  brand ("CARAKA", "Caraka Mode").
- Jangan ubah struktur/layout UI — teks doang.
- Jangan sentuh logic non-copy (validasi, network, crypto).
- Jangan coba "menyisir semua 550+ string" secara paksa/mekanis kalau
  itu bikin kualitas turun (asal ganti kata tanpa mikir) — lebih baik
  sedikit tapi tepat daripada banyak tapi asal.

── VERIFIKASI TAHAP B ──
cd app
./gradlew.bat :app:assembleDebug
(Windows, kalau JAVA_HOME error "invalid directory":
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr")

Harus BUILD SUCCESSFUL. Lalu:
  grep -c "string name" app/app/src/main/res/values/strings.xml
  grep -c "string name" app/app/src/main/res/values-en/strings.xml
Dua angka itu HARUS SAMA — kalau beda, ada string yang cuma ditambah
di satu file (id atau en saja), perbaiki sebelum lanjut.

── CHECKPOINT SETELAH TAHAP B SUKSES ──
cd E:\01-Proyek\CARAKA-APP
git add -A
git commit -m "feat(copy): sweep microcopy + voice guide, hardcoded snackbar ke string resource"

═══════════════════════════════════════════════════════════════════
LAPORAN AKHIR (tulis ini di respons terakhir kamu, apa pun hasilnya)
═══════════════════════════════════════════════════════════════════
- Tahap A: selesai / berhenti di mana, build sukses/gagal, commit
  dibuat atau tidak.
- Tahap B: selesai / berhenti di mana / tidak dimulai (karena Tahap A
  gagal), build sukses/gagal, commit dibuat atau tidak.
- Daftar file yang berubah (git diff --stat sejak sebelum Tahap A).
- Kalau ada yang gagal: apa pendekatan yang sudah dicoba, dan
  rekomendasi langkah selanjutnya buat manusia yang balik nanti.

INGAT: JANGAN git push, JANGAN git tag, JANGAN ubah
versionCode/versionName di app/app/build.gradle.kts — itu dikerjakan
manual setelah review.
```
