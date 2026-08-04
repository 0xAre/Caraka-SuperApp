# Onboarding Wizard — Desain

**Tanggal**: 2026-08-04
**Status**: Disetujui, siap dieksekusi
**Bagian dari**: [Roadmap Enterprise UX](2026-08-04-enterprise-ux-roadmap.md), Tahap 1

## Masalah

Alur first-run sekarang, ditelusuri dari `MainActivity.kt` (`CarakaNav`):

1. `hasIdentity == false` → tampilkan `ProfileSetupScreen` (satu form:
   pilih role, isi nama/password, submit). Selesai.
2. `hasIdentity` jadi `true` → `CarakaNav` lanjut lewat early-return,
   lalu **langsung** (`LaunchedEffect(Unit)`, baris ~233-253) minta
   izin lokasi/Bluetooth/nearby-WiFi ke sistem — **tanpa penjelasan
   apa pun sebelumnya** kenapa app butuh izin itu.
3. **Bersamaan**, karena `onboardingDone` masih `false`,
   `OnboardingTourOverlay` (carousel 5 kartu statis, selalu di tengah
   layar, gak nunjuk ke elemen UI beneran) juga muncul di atas Home
   yang lagi render di belakang.

Jadi user yang baru pertama buka app kena: form → dialog izin sistem
+ carousel tur, muncul bersamaan, gak ada narasi yang nyambungin
kenapa app ini ada dan kenapa dia butuh izin segitu banyak. Ini yang
dimaksud "onboarding statis satu halaman."

## Solusi

Satu wizard 4 langkah, linear, dengan progress indicator. Ganti kedua
komponen lama (`ProfileSetupScreen` sebagai full-screen tunggal +
auto-trigger `OnboardingTourOverlay`) jadi bagian dari wizard yang sama.

```
[1. Selamat Datang]  →  [2. Setup Identitas]  →  [3. Kenapa Butuh Izin]  →  [4. Fitur Utama]  →  Home
   (value prop)          (form yang sudah ada)     (rationale + trigger      (reuse tourSteps
                                                      permission request)      sebagai carousel)
```

### Langkah 1 — Selamat Datang
Baru. Full-bleed: logo/ilustrasi besar, headline value-prop ("Komunikasi
darurat offline saat internet, listrik, dan sinyal mati" — bisa reuse
teks `tour_step1_desc` yang sudah ada), tombol "Lanjut".

### Langkah 2 — Setup Identitas
Konten form (role picker + name/password field) **dipindah apa adanya**
dari `ProfileSetupScreen.kt` ke dalam step ini — logic pemilihan role,
validasi password authority, dsb **tidak diubah**, cuma dibungkus ulang
supaya jadi satu step di wizard (bukan `Scaffold` sendiri lagi). Tombol
submit existing (`setup_btn_join`/`setup_btn_authorize`) jadi tombol
"Lanjut" ke step 3, bukan langsung selesai onboarding.

### Langkah 3 — Kenapa Butuh Izin (BARU — ini yang paling penting)
Layar rationale sebelum dialog sistem muncul: ikon + penjelasan singkat
kenapa app butuh Location/Bluetooth/Nearby-WiFi (dipakai buat bikin
mesh network offline P2P — bukan buat tracking). Tombol "Izinkan &
Lanjutkan" adalah yang **memicu** permission request (pindahkan logic
dari `LaunchedEffect(Unit)` otomatis di `CarakaNav` jadi dipanggil dari
sini, on-click). User yang menolak izin tetap bisa lanjut (app memang
sudah handle kondisi permission missing di layar lain — lihat
`NetworkScreen`'s `onRequestPermissions`), cuma dikasih catatan kecil
"bisa diaktifkan lagi nanti di Settings".

### Langkah 4 — Fitur Utama
Reuse **konten** `tourSteps` (5 item, `R.string.tour_step1..5_*`) yang
sudah ada di `OnboardingTourOverlay.kt`, ditampilkan sebagai carousel
di dalam step wizard terakhir ini (pola dot-indicator + next/skip yang
sudah ada di komponen itu bisa dipakai ulang). Tombol terakhir: "Mulai
Pakai CARAKA" → selesai wizard → masuk Home.

## Arsitektur

- **File baru**: `ui/screens/onboarding/OnboardingWizardScreen.kt` —
  composable utama, state step index (0-3), progress dots di atas.
- **File baru**: `ui/screens/onboarding/WelcomeStep.kt`,
  `IdentityStep.kt` (konten dipindah dari `ProfileSetupScreen.kt`),
  `PermissionRationaleStep.kt`, `FeatureHighlightStep.kt` — supaya tiap
  step tetap kecil & fokus (ikut pola "smaller, well-bounded units" —
  jangan taruh semua di satu file besar).
- **`ProfileSetupScreen.kt`**: isinya (role picker, validasi, dsb)
  dipindah ke `IdentityStep.kt`. File `ProfileSetupScreen.kt` sendiri
  dihapus setelah konten dipindah — jangan tinggalkan file kosong/dead
  code.
- **`OnboardingTourOverlay.kt`**: TETAP ADA, TIDAK dihapus — masih
  dipakai buat "Tampilkan tur lagi" dari `HelpScreen` (`onLaunchTour`).
  Isinya (`tourSteps` list + string resource) di-reuse oleh
  `FeatureHighlightStep.kt` supaya kontennya konsisten (satu sumber
  data, dua tempat render) — jangan duplikasi daftar step, ekstrak
  `tourSteps` jadi `internal val` yang bisa diimpor dari kedua file,
  atau pindahkan definisinya ke file kecil bersama
  (`OnboardingContent.kt`) yang diimpor keduanya.
- **`MainActivity.kt`** (`CarakaNav`):
  - Ganti blok `if (!hasIdentity) { ProfileSetupScreen { ... }; return }`
    jadi `if (!hasIdentity) { OnboardingWizardScreen(onComplete = { name, role -> viewModel.setupIdentity(name, role); onOnboardingDismissed() }); return }`.
    `onOnboardingDismissed` **sudah ada** sebagai parameter `CarakaNav`
    (diteruskan dari `CarakaRoot`, sudah terhubung ke
    `scope.launch { uiPrefs.setOnboardingDone(true) }`) — reuse
    langsung, jangan bikin scope/launch baru di `CarakaNav`.
  - Hapus `LaunchedEffect(Unit)` yang otomatis minta permission (baris
    ~233-253) — logic-nya (daftar permission + cek
    `ContextCompat.checkSelfPermission` + `permissionLauncher.launch`)
    **dipindah**, bukan dihapus, jadi callback yang dipanggil dari
    `PermissionRationaleStep`'s tombol "Izinkan & Lanjutkan". Perlu
    diteruskan turun sebagai parameter (`onRequestPermissions: () -> Unit`)
    dari `CarakaNav` ke `OnboardingWizardScreen` ke
    `PermissionRationaleStep`.
  - Hapus auto-show `OnboardingTourOverlay` yang lama (`showTour`
    berdasar `!onboardingDoneFlag`) — `onboardingDone` sekarang di-set
    `true` di akhir wizard (lihat `onComplete` di atas), bukan di
    `OnboardingTourOverlay.onDismiss`. `showTour` state TETAP ADA tapi
    cuma di-set `true` secara manual lewat `onLaunchTour` dari
    `HelpScreen` (tur ulang), default awal `false`.
  - `startMeshService()` (di `MainActivity`) tetap dipanggil dari
    `permissionLauncher`'s callback yang sudah ada — tidak berubah,
    cuma pemicu awal permission request-nya yang pindah.

## Alur data / state

- `OnboardingWizardScreen` pegang `var stepIndex by remember { mutableStateOf(0) }`.
- Data identitas (`name`, `role`) dikumpulkan di `IdentityStep`,
  diteruskan ke atas lewat callback `onIdentityConfirmed: (name, role) -> Unit`
  yang men-set state di level `OnboardingWizardScreen`, dipakai nanti
  saat step 4 selesai buat manggil `onComplete(name, role)`.
- Back-navigation antar step: tombol back di TopAppBar tiap step
  (kecuali step 1) mundur satu step, bukan keluar wizard. Step 1 gak
  ada tombol back (belum ada apa-apa buat di-cancel).

## Error handling

- Validasi password authority & nama kosong: **tidak berubah**, logic
  yang sama persis dipindah dari `ProfileSetupScreen`, cuma sekarang
  errornya nahan user di step 2, gak lanjut ke step 3 kalau gagal
  (sama seperti sekarang tombol submit gak jalan kalau invalid).
- Permission ditolak user di step 3: tidak dianggap error — wizard
  tetap lanjut ke step 4. App sudah punya penanganan permission-missing
  di layar lain (`NetworkScreen`), jadi tidak butuh blocking di sini.

## Testing

Tidak ada logic baru yang bernilai untuk unit test murni (semuanya
Compose UI + orkestrasi navigasi, bukan fungsi pure/parsing). Cukup
verifikasi manual + build sukses. Tidak perlu memaksakan test di sini
— ponytail: jangan bikin test buat kode UI yang gak ada logic
bercabang berarti.

## Di luar scope

- Redesign visual mendalam (ilustrasi baru, animasi kompleks) — pakai
  pola visual yang sudah ada di app (shape/spacing/warna dari
  `CarakaTheme`), fokus ke STRUKTUR alur, bukan bikin aset baru.
- Menulis ulang copy/tone teks onboarding — itu Tahap 2. Step baru
  boleh pakai string placeholder yang wajar (reuse string yang sudah
  ada semaksimal mungkin), tapi polish tone final terjadi di Tahap 2.
- Mengubah sistem permission Android itu sendiri (tetap
  `ActivityResultContracts.RequestMultiplePermissions()`, tidak diganti
  library lain).
