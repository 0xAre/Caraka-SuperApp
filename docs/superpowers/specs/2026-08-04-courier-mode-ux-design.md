# Courier Mode UX — Kontak via QR + Tab Sticky

**Tanggal**: 2026-08-04
**Status**: Disetujui, siap dieksekusi
**Rilis target**: setelah v0.1.1-beta

## Latar belakang

Tab "Caraka" (courier/kurir mode) sudah digabung ke halaman Pesan lewat
refactor sebelumnya (`526a727`, rename `CourierScreen` → `CarakaTab`).
Struktur 2-tab (Pesan / Caraka) di `MessagesScreen` **sudah ada** — bukan
bagian dari perubahan ini.

Tiga gap UX yang tersisa, hasil audit codebase (`app/app/src/main/java/com/example/caraka/`):

1. **Tambah kontak tujuan (Z) 100% manual** — `AddContactDialog` di
   `ui/courier/CarakaTab.kt` cuma punya field teks Peer ID + nama. Padahal
   scanner QR identitas lengkap sudah ada (`QrIdentityScreen`,
   `QrIdentityManager`, `CarakaQrCaptureActivity`) tapi tidak disambungkan
   ke alur kurir.
2. **Tidak ada cara menghapus kontak manual yang salah/gak kepake** —
   `UiPreferences.removeManualContact()` sudah ada di layer data, tapi
   belum dipanggil dari UI manapun.
3. **Tab "Caraka" tidak nempel** — `MessagesScreen.selectedTab` pakai
   `remember { mutableStateOf(0) }`, jadi selalu reset ke tab "Pesan"
   tiap layar dibuka ulang / app di-restart.

## Arsitektur

Tidak ada layar atau rute navigasi baru. Semua perubahan ada di file yang
sudah ada:

- `viewmodel/CourierViewModel.kt` — 2 method baru
- `ui/courier/CarakaTab.kt` — UI: tombol scan QR + ikon hapus di
  `AddContactDialog` / picker kontak
- `ui/prefs/UiPreferences.kt` — 1 key DataStore baru
- `ui/screens/MessagesScreen.kt` — baca/tulis tab terakhir dari
  `UiPreferences`, bukan `remember` lokal
- `res/values/strings.xml` + `res/values-en/strings.xml` — string baru
- `src/test/java/.../crypto/QrIdentityManagerTest.kt` — test baru

## Komponen & alur data

### 1. Tambah kontak via QR

- `AddContactDialog` dapat tombol baru "Scan QR" di samping field manual
  yang sudah ada (field manual **tetap ada** sebagai fallback — berguna
  kalau ID dibacain lewat radio/telepon, bukan discan langsung).
- Tap "Scan QR" → `ScanContract()` dengan `CarakaQrCaptureActivity`,
  opsi sama persis seperti di `QrIdentityScreen` (`ScanOptions.QR_CODE`,
  `setBeepEnabled(true)`, `setOrientationLocked(true)`).
- Hasil scan → `QrIdentityManager.parseQrPayload(raw)`.
  - Null → tampilkan error inline di dialog (lihat Error Handling).
  - Valid (`QrIdentityPayload`) → panggil
    `CourierViewModel.addContactViaQr(payload)`.
- **Method baru** `CourierViewModel.addContactViaQr(payload: QrIdentityManager.QrIdentityPayload)`:
  ```kotlin
  fun addContactViaQr(payload: QrIdentityManager.QrIdentityPayload) {
      viewModelScope.launch {
          meshRepository.saveVerifiedPeer(
              peerId = payload.peerId,
              displayName = payload.name,
              role = payload.role,
              encPubKey = payload.encPub,
              signPubKey = payload.signPub
          )
          _snackbar.emit("Kontak ${payload.name} ditambahkan.")
      }
  }
  ```
  `meshRepository` sudah jadi constructor param `CourierViewModel` —
  tidak perlu inject `MainViewModel` atau `IdentityManager` tambahan.
- `contacts: StateFlow<List<CarakaContact>>` **tidak perlu diubah** — dia
  sudah derive dari `meshRepository.getAllPeers()`, jadi otomatis
  ke-refresh begitu peer baru tersimpan dengan `hasKey = true`.
- Kontak yang sudah ada (scan ulang peer yang sama) otomatis ke-upsert
  oleh `saveVerifiedPeer` yang sudah ada — tidak perlu logic duplikat.

### 2. Hapus kontak manual

- **Method baru** `CourierViewModel.removeContact(peerId: String)`:
  ```kotlin
  fun removeContact(peerId: String) {
      viewModelScope.launch { uiPreferences.removeManualContact(peerId) }
  }
  ```
- Di picker kontak (pemakaian `PickRow` untuk list `contacts` dalam
  `CarakaSendSheet`), tambah trailing `IconButton` (ikon hapus) yang
  **hanya dirender ketika `c.hasKey == false`**.
- Kontak dengan `hasKey == true` (peer terverifikasi, dipakai juga di
  Chat/Network) **tidak** dikasih tombol hapus di picker ini — menghapus
  identitas peer itu di luar scope perubahan ini dan berisiko merusak
  riwayat chat di layar lain.
- Tanpa dialog konfirmasi — aksi murah, gampang ditambah ulang via QR
  atau manual kalau salah pencet.

### 3. Tab Caraka sticky

- `UiPreferences` dapat key baru, pola identik dengan `bigText`/`highContrast`
  yang sudah ada:
  ```kotlin
  fun observeLastMessagesTab(): Flow<Int> = ...  // default 0
  suspend fun setLastMessagesTab(index: Int) { ... }
  ```
- `MessagesScreen`:
  ```kotlin
  val lastTab by uiPrefs.observeLastMessagesTab().collectAsState(initial = 0)
  var selectedTab by remember(lastTab) { mutableStateOf(lastTab) }
  val scope = rememberCoroutineScope() // belum dipanggil di file ini, tambahkan
  ```
  Setiap `Tab.onClick`, selain `selectedTab = i`, tambahkan
  `scope.launch { uiPrefs.setLastMessagesTab(i) }`.

## Error handling

| Kasus | Perilaku |
|---|---|
| QR tidak valid / bukan QR identitas Caraka | Error inline di dalam dialog (pola sama seperti `scanError` di `QrIdentityScreen`), dialog tetap terbuka, user bisa retry scan atau pakai field manual |
| User batal scan (back dari kamera) | Diam saja, tidak ada toast/error |
| Scan peer yang sudah jadi kontak | Upsert otomatis lewat `saveVerifiedPeer` yang sudah ada, tidak ada UI khusus |
| Hapus kontak yang sedang dipilih di `targetId` saat sheet masih terbuka | Tidak masalah — begitu dihapus dari list, `targetId` yang lama tidak match item manapun, tombol kirim otomatis nonaktif via `canSend` check yang sudah ada |

## Testing

Belum ada test untuk `QrIdentityManager` sama sekali. Tambah
`src/test/java/com/example/caraka/crypto/QrIdentityManagerTest.kt`,
JUnit murni tanpa mocking (ikuti pola
`viewmodel/NetworkDiscoveryUiStateTest.kt` yang sudah ada di repo):

- `parseQrPayload` menerima JSON identitas valid → payload ter-parse benar.
- `parseQrPayload` menolak string kosong / JSON rusak / JSON tanpa
  `peerId`/`signPub` → return `null`.

Ini test yang paling bernilai karena jalur "tambah kontak via QR" yang
baru bergantung penuh pada fungsi ini.

## Di luar scope (sengaja tidak dikerjakan)

- Edit nama kontak manual (cuma tambah/hapus).
- Hapus/kelola peer berkunci (`hasKey == true`) dari picker kurir — itu
  ranah layar Network/identity management, bukan courier mode.
- Scanner QR inline di dalam bottom sheet (embed kamera) — pakai
  full-screen activity yang sudah ada (`ScanContract`), sama seperti
  `QrIdentityScreen`.
- Promosi tab Caraka jadi item bottom-nav sendiri — user pilih opsi
  "sticky tab", bukan ini.
