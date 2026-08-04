# Prompt eksekusi — Courier Mode UX (QR contact + sticky tab)

Companion dari [2026-08-04-courier-mode-ux-design.md](2026-08-04-courier-mode-ux-design.md).

## Cara pakai

1. Buka ChatGPT 5.5 (atau agent coding lain yang punya akses ke repo ini).
2. Copy **seluruh isi blok kode** di bawah (dari `Kamu mengerjakan repo...`
   sampai baris terakhir `VERIFIKASI`), paste sebagai prompt pertama.
3. Pastikan agent-nya punya akses baca/tulis ke folder
   `E:\01-Proyek\CARAKA-APP` sebelum mulai.
4. Setelah selesai, review diff-nya, lalu jalankan build+test manual
   kalau agent-nya belum jalanin sendiri (perintah ada di bagian
   VERIFIKASI di dalam prompt).
5. Kabari lagi di sini kalau udah jadi — lanjut ke build & release APK
   seperti alur v0.1.1-beta.

## Prompt

```
Kamu mengerjakan repo Android Kotlin/Jetpack Compose "CARAKA" di
E:\01-Proyek\CARAKA-APP. PENTING: root Gradle project ada di subfolder
app/ (bukan di root repo), modul aplikasi ada di app/app/. Jalankan
semua perintah gradlew dari app/, bukan dari root repo.

Kerjakan 3 perubahan UX berikut di courier mode ("Caraka") sesuai spec
yang sudah disetujui di docs/superpowers/specs/2026-08-04-courier-mode-ux-design.md
(baca file itu dulu kalau tersedia). Semua perubahan HANYA menyentuh
file-file yang disebut di bawah — jangan bikin layar atau rute navigasi
baru, jangan refactor di luar scope ini.

═══════════════════════════════════════════════════════════════
TASK 1 — Tambah kontak tujuan (Z) lewat scan QR
═══════════════════════════════════════════════════════════════

File: app/app/src/main/java/com/example/caraka/viewmodel/CourierViewModel.kt

Tambah method baru di class CourierViewModel (dia sudah punya
`meshRepository: MeshRepository` sebagai constructor param, dan sudah
punya `_snackbar: MutableSharedFlow<String>`):

    fun addContactViaQr(payload: com.example.caraka.crypto.QrIdentityManager.QrIdentityPayload) {
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

`MeshRepository.saveVerifiedPeer(peerId, displayName, role, encPubKey, signPubKey)`
sudah ada di repository/MeshRepository.kt — jangan bikin ulang, cukup
panggil. `contacts: StateFlow<List<CarakaContact>>` yang sudah ada di
CourierViewModel TIDAK perlu diubah — dia sudah derive dari
`meshRepository.getAllPeers()` jadi otomatis ke-refresh.

File: app/app/src/main/java/com/example/caraka/ui/courier/CarakaTab.kt

Di composable `AddContactDialog` (baris ~391-430), tambahkan:

1. Import baru yang dibutuhkan:
   - androidx.activity.compose.rememberLauncherForActivityResult
   - androidx.compose.material.icons.filled.QrCodeScanner
   - androidx.compose.material.icons.filled.Delete
   - com.example.caraka.crypto.QrIdentityManager
   - com.example.caraka.ui.scanner.CarakaQrCaptureActivity
   - com.journeyapps.barcodescanner.ScanContract
   - com.journeyapps.barcodescanner.ScanIntentResult
   - com.journeyapps.barcodescanner.ScanOptions

2. Ubah signature `AddContactDialog` dari:
     private fun AddContactDialog(onAdd: (peerId: String, name: String) -> Unit, onDismiss: () -> Unit)
   jadi:
     private fun AddContactDialog(
         onAdd: (peerId: String, name: String) -> Unit,
         onAddViaQr: (com.example.caraka.crypto.QrIdentityManager.QrIdentityPayload) -> Unit,
         onDismiss: () -> Unit
     )

3. Di body-nya, tambahkan state `var scanError by remember { mutableStateOf<String?>(null) }`
   dan sebuah `rememberLauncherForActivityResult(ScanContract())`:
   - on success: parse hasil pakai `QrIdentityManager.parseQrPayload(raw)`.
     - null → set scanError ke string R.string.qr_scan_invalid (sudah ada,
       reuse — jangan bikin string baru untuk pesan ini).
     - valid → panggil `onAddViaQr(parsed)`, lalu dialog ditutup (biarkan
       caller yang set showAddContact = false, sama seperti alur onAdd yang
       sudah ada).
   - kalau user cancel (result.contents == null): JANGAN tampilkan error,
     biarkan dialog tetap seperti semula.

4. Tambahkan Button baru "Scan QR" di ATAS field manual yang sudah ada
   (peerId + nama), pakai style/pattern Button yang sama dengan tombol
   "Scan QR sekarang" di QrIdentityScreen.kt (ikon Icons.Default.QrCodeScanner,
   ScanOptions dengan setCaptureActivity(CarakaQrCaptureActivity::class.java),
   setDesiredBarcodeFormats(ScanOptions.QR_CODE), setBeepEnabled(true),
   setOrientationLocked(true)). Field manual (peerId + nama) TETAP ADA di
   bawahnya sebagai fallback — jangan dihapus.

5. Tampilkan scanError (kalau ada) sebagai teks error kecil di bawah
   tombol scan, warna MaterialTheme.colorScheme.error.

File: app/app/src/main/java/com/example/caraka/ui/courier/CarakaTab.kt,
composable `CarakaSendSheet` (baris ~223-354)

Update pemanggilan AddContactDialog untuk pass param baru:

    AddContactDialog(
        onAdd = { pid, name -> viewModel.addManualContact(pid, name); showAddContact = false },
        onAddViaQr = { payload -> viewModel.addContactViaQr(payload); showAddContact = false },
        onDismiss = { showAddContact = false }
    )

Tambah string resource baru di
app/app/src/main/res/values/strings.xml DAN values-en/strings.xml
(taruh dekat string caraka_* yang sudah ada, sekitar baris 520-535):
  - caraka_scan_qr_btn → id: "Scan QR", en: "Scan QR"
Jangan bikin string baru untuk pesan error — reuse `qr_scan_invalid`
yang sudah ada.

═══════════════════════════════════════════════════════════════
TASK 2 — Hapus kontak manual (yang hasKey == false SAJA)
═══════════════════════════════════════════════════════════════

File: app/app/src/main/java/com/example/caraka/viewmodel/CourierViewModel.kt

Tambah method baru (memanggil `uiPreferences.removeManualContact` yang
SUDAH ADA di UiPreferences.kt, jangan bikin ulang logic-nya):

    fun removeContact(peerId: String) {
        viewModelScope.launch { uiPreferences.removeManualContact(peerId) }
    }

File: app/app/src/main/java/com/example/caraka/ui/courier/CarakaTab.kt

1. Tambah import androidx.compose.material.icons.filled.Delete (kalau
   belum ditambahkan dari Task 1) dan androidx.compose.material3.IconButton
   (kemungkinan sudah terimport).

2. Ubah composable `PickRow` (baris ~356-389) — tambahkan parameter
   opsional baru supaya tetap backward-compatible dengan pemanggilan
   yang sudah ada untuk list `carriers` (kurir):

     @Composable
     private fun PickRow(
         title: String,
         subtitle: String,
         selected: Boolean,
         enabled: Boolean,
         onClick: () -> Unit,
         trailing: @Composable (() -> Unit)? = null   // BARU
     ) {
         ...
         // di akhir Row yang sudah ada, setelah Column(Modifier.weight(1f)) { ... }:
         trailing?.invoke()
     }

3. Di `CarakaSendSheet`, pada blok `items(contacts, key = { it.peerId }) { c -> ... }`
   (baris ~293-304), tambahkan trailing delete icon HANYA untuk kontak
   yang `hasKey == false`, dan reset `targetId` kalau kontak yang lagi
   dipilih itu yang dihapus:

     items(contacts, key = { it.peerId }) { c ->
         PickRow(
             title = c.name,
             subtitle = if (c.hasKey) c.role else stringResource(R.string.caraka_needs_connection),
             selected = targetId == c.peerId,
             enabled = c.hasKey,
             onClick = { if (c.hasKey) targetId = c.peerId },
             trailing = if (!c.hasKey) {
                 {
                     IconButton(onClick = {
                         viewModel.removeContact(c.peerId)
                         if (targetId == c.peerId) targetId = ""
                     }) {
                         Icon(
                             Icons.Default.Delete,
                             contentDescription = stringResource(R.string.caraka_remove_contact),
                             modifier = Modifier.size(18.dp)
                         )
                     }
                 }
             } else null
         )
     }

   PENTING: kontak dengan hasKey == true (peer terverifikasi yang juga
   dipakai di layar Chat/Network) TIDAK dikasih tombol hapus — jangan
   tambahkan opsi hapus untuk itu, di luar scope.

Tidak perlu dialog konfirmasi untuk hapus — langsung eksekusi saat tombol
ditekan.

Tambah string resource baru (id + en):
  - caraka_remove_contact → id: "Hapus kontak", en: "Remove contact"

═══════════════════════════════════════════════════════════════
TASK 3 — Tab "Caraka" di halaman Pesan jadi sticky (persist)
═══════════════════════════════════════════════════════════════

File: app/app/src/main/java/com/example/caraka/ui/prefs/UiPreferences.kt

Tambah key DataStore baru dan 2 fungsi, ikuti PERSIS pola yang sudah
dipakai untuk preference lain di file ini (misal `bigText`/`highContrast`
— cari `Keys` object di file ini untuk lihat pola penamaan key):

    // tambahkan di object Keys yang sudah ada:
    val lastMessagesTab = intPreferencesKey("last_messages_tab")

    fun observeLastMessagesTab(): Flow<Int> =
        context.uiPrefsDataStore.data.map { prefs -> prefs[Keys.lastMessagesTab] ?: 0 }

    suspend fun setLastMessagesTab(index: Int) {
        context.uiPrefsDataStore.edit { prefs -> prefs[Keys.lastMessagesTab] = index }
    }

File: app/app/src/main/java/com/example/caraka/ui/screens/MessagesScreen.kt

1. Ganti:
     var selectedTab by remember { mutableStateOf(0) }
   jadi:
     val lastTab by uiPrefs.observeLastMessagesTab().collectAsState(initial = 0)
     var selectedTab by remember(lastTab) { mutableStateOf(lastTab) }
     val scope = rememberCoroutineScope()

2. Di kedua `Tab(onClick = { selectedTab = 0/1 }, ...)` yang sudah ada,
   tambahkan pemanggilan persist, contoh untuk tab index 0:

     onClick = {
         selectedTab = 0
         scope.launch { uiPrefs.setLastMessagesTab(0) }
     }

   dan sama untuk tab index 1.

3. Pastikan `import androidx.compose.runtime.rememberCoroutineScope` dan
   `import kotlinx.coroutines.launch` sudah ada (tambahkan kalau belum).

═══════════════════════════════════════════════════════════════
TASK 4 — Test
═══════════════════════════════════════════════════════════════

Buat file baru:
app/app/src/test/java/com/example/caraka/crypto/QrIdentityManagerTest.kt

JUnit murni, TANPA mocking framework — ikuti persis gaya file yang
sudah ada di app/app/src/test/java/com/example/caraka/viewmodel/NetworkDiscoveryUiStateTest.kt
(assertEquals/assertTrue/assertFalse dari org.junit.Assert, @Test dari
org.junit.Test).

Test case minimal:
  1. parseQrPayload menerima JSON identitas valid (buat lewat
     QrIdentityManager.buildPayload(...) lalu parse balik) → field-field
     payload hasil parse harus sama dengan yang di-build.
  2. parseQrPayload("") → null
  3. parseQrPayload("{not valid json") → null
  4. parseQrPayload(JSON valid tapi peerId kosong) → null
  5. parseQrPayload(JSON valid tapi signPub kosong) → null

═══════════════════════════════════════════════════════════════
DI LUAR SCOPE — jangan kerjakan
═══════════════════════════════════════════════════════════════
- Edit nama kontak manual yang sudah ada (cuma tambah + hapus).
- Hapus/kelola peer ber-kunci (hasKey == true) dari picker ini.
- Scanner QR inline/embedded di dalam bottom sheet — pakai full-screen
  ScanContract activity yang sudah ada, sama seperti QrIdentityScreen.
- Bikin tab Caraka jadi item bottom navigation sendiri.
- Layar atau route navigasi baru.

═══════════════════════════════════════════════════════════════
VERIFIKASI
═══════════════════════════════════════════════════════════════
Build dari folder app/ (BUKAN root repo):
  cd app
  ./gradlew.bat :app:assembleDebug
  ./gradlew.bat :app:testDebugUnitTest --tests "com.example.caraka.crypto.QrIdentityManagerTest"

Di Windows, kalau JAVA_HOME error "invalid directory", set dulu ke JDK
Android Studio, contoh:
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

Build harus BUILD SUCCESSFUL dan test baru harus PASS sebelum dianggap
selesai. Jangan ubah versionCode/versionName di app/app/build.gradle.kts
— itu di luar scope task ini.
```
