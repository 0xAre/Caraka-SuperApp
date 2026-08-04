# Prompt eksekusi — Onboarding Wizard (Tahap 1 Enterprise UX)

Companion dari [2026-08-04-onboarding-wizard-design.md](2026-08-04-onboarding-wizard-design.md).
Bagian dari [Roadmap Enterprise UX](2026-08-04-enterprise-ux-roadmap.md).

## Cara pakai

1. Buka Codex (atau agent coding lain yang punya akses ke repo ini).
2. Copy seluruh isi blok kode di bawah, paste sebagai prompt pertama.
3. Pastikan agent-nya punya akses baca/tulis ke `E:\01-Proyek\CARAKA-APP`.
4. Review diff-nya, jalankan build manual kalau agent belum jalanin
   sendiri (perintah ada di bagian VERIFIKASI di dalam prompt).
5. **Jangan langsung lanjut ke Tahap 2 (copywriting)** sebelum Tahap 1
   ini direview — Tahap 2 nulis ulang copy buat layar-layar baru yang
   dibuat di sini.

## Prompt

```
Kamu mengerjakan repo Android Kotlin/Jetpack Compose "CARAKA" di
E:\01-Proyek\CARAKA-APP. PENTING: root Gradle project ada di subfolder
app/ (bukan di root repo), modul aplikasi ada di app/app/. Jalankan
semua perintah gradlew dari app/, bukan dari root repo.

Baca dulu docs/superpowers/specs/2026-08-04-onboarding-wizard-design.md
kalau tersedia — itu spec yang sudah disetujui untuk task ini. Task di
bawah adalah ringkasannya, tapi spec itu sumber kebenaran kalau ada
yang ambigu.

TUJUAN: Ganti alur first-run yang sekarang (satu form statis
`ProfileSetupScreen` lalu, terpisah, carousel tur otomatis yang numpuk
sama dialog izin sistem) jadi satu wizard 4 langkah yang koheren.

═══════════════════════════════════════════════════════════════
KONDISI SEKARANG (baca dulu sebelum ubah apa pun)
═══════════════════════════════════════════════════════════════

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

═══════════════════════════════════════════════════════════════
YANG HARUS DIKERJAKAN
═══════════════════════════════════════════════════════════════

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
      sekarang — copy final di-polish di task terpisah, jangan spend
      waktu di wording di sini), tombol "Lanjut" → onNext().

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
   waktu poles wording, itu dikerjakan di task copywriting terpisah
   nanti.

═══════════════════════════════════════════════════════════════
DI LUAR SCOPE — jangan kerjakan
═══════════════════════════════════════════════════════════════
- Jangan ubah logic validasi password authority atau daftar
  authorityPasswords di IdentityStep — pindahkan apa adanya.
- Jangan ganti library permission (tetap
  ActivityResultContracts.RequestMultiplePermissions()).
- Jangan bikin ilustrasi/aset gambar baru — reuse drawable yang sudah
  ada di res/drawable.
- Jangan poles/tulis-ulang copy secara mendalam — string baru boleh
  seadanya asal masuk akal, ada task terpisah buat itu.
- Jangan sentuh CourierViewModel/CarakaTab/MessagesScreen — tidak
  terkait task ini.

═══════════════════════════════════════════════════════════════
VERIFIKASI
═══════════════════════════════════════════════════════════════
Build dari folder app/ (BUKAN root repo):
  cd app
  ./gradlew.bat :app:assembleDebug

Di Windows, kalau JAVA_HOME error "invalid directory":
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

Build harus BUILD SUCCESSFUL. Setelah itu, jalankan manual di
emulator/device kalau memungkinkan: hapus data app (biar hasIdentity
balik false), buka app, pastikan urutannya: Welcome → Identity →
Permission rationale → Feature highlight → Home, dan dari Home →
Settings/Help → "lihat tur lagi" masih memunculkan
OnboardingTourOverlay seperti sebelumnya. Jangan ubah
versionCode/versionName — di luar scope task ini.
```
