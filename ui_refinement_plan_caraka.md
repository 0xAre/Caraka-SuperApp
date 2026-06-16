# CARAKA Courier Mode — UI Refinement Plan

> **Status fondasi**: Backend Courier (Directed + Stealth) SELESAI & compile hijau (lihat `implementation_plan_caraka` + memory `caraka-courier-audit-2026`).
> **Scope plan ini**: UI refinement — fungsional (F1–F3) + design-system (D2, D4, D5). Emoji di composable/snackbar (D1) & efek glow (D3) **sengaja DIPERTAHANKAN** atas keputusan user.
> **Keputusan dikonfirmasi**: warna Stealth = **token baru di `StatusColors`** (bukan ungu neon hardcode).
> **Prinsip**: tanpa migrasi DB; sentuhan backend dibatasi pada hal trivial & prasyarat (sweeper expiry + query read-only).

---

## Konteks & Masalah

Lapisan UI Courier sudah ada kerangkanya — `ui/screens/CourierScreen.kt`, `ui/courier/CourierComponents.kt`, `viewmodel/CourierViewModel.kt`, ter-wire ke nav lewat `HomeScreen` → `Screen.Courier` — **tetapi belum siap pakai & belum konsisten**:

| Kode | Masalah | Bukti |
|---|---|---|
| **F1** | Alur Stealth **putus** — `CourierViewModel.stealthCredentials` (EPK_priv + nonce yang A harus bagikan ke Z) **tak pernah dikonsumsi UI** | `CourierViewModel.kt:106-108` |
| **F2** | Tak ada layar **riwayat** tugas kurir / pengiriman | tak ada query history di `CourierDao` |
| **F3** | Tak ada **badge** "membawa N paket" di Home | `activeCarryCount` ada di VM, `ServiceTile` courier (`HomeScreen.kt:205`) cuma navigasi |
| **D2** | Warna **ungu neon hardcode** `Color(0xFF7C4DFF)` ×7 (identik di kedua tema) | `CourierScreen.kt:350`; `CourierComponents.kt:374,527,533,560,571,593` |
| **D4** | **Shape hardcode** `RoundedCornerShape(topStart=28.dp)` ×3 | `CourierComponents.kt:113,728,933` |
| **D5** | Semua string **hardcoded** (belum i18n) | hanya `service_label_courier` ada di `strings.xml` |
| **D6** | **Tidak adaptif dark vs light** — tombol solid Stealth (`containerColor` tanpa `contentColor`) → teks ikut `LocalContentColor` (gelap@light / terang@dark) → gagal kontras saat token Stealth versi dark = lavender muda; `statusColors` tak punya cabang high-contrast | `CourierComponents.kt:593` + `Theme.kt:120-140` |
| **Bonus** | Badge "Terverifikasi" menyesatkan — cek keberadaan string, **bukan verifikasi signature** | `CourierComponents.kt:853-903` |

**Outcome target**: alur Directed & Stealth dapat dijalankan end-to-end dari UI (termasuk berbagi kredensial via QR/chat), ada riwayat + badge, dan UI Courier konsisten dengan token Operational Enterprise + ter-i18n (ID/EN).

---

## Arah Desain

Pertahankan **Operational Enterprise** (GitHub-dark canvas, Telegram blue primary, token di `ui/theme/`). Label mode (emoji "🔮 Stealth" / "📍 Directed") + glow sukses **DIPERTAHANKAN** sesuai keputusan user; yang berubah hanya **warna** (jadi token) dan **adaptivitas tema**:
- **Directed** → label + `colorScheme.primary` (sudah adaptif).
- **Stealth** → label + token baru `statusColors.stealth` (violet teredam per-tema, bukan neon hardcode) + pasangan `onStealth` untuk konten di atas fill Stealth.

App mengikuti **dark mode sistem** (`CarakaTheme(darkTheme = isSystemInDarkTheme())`) + ada `HighContrastScheme`. Semua warna Courier harus terbaca di **light, dark, dan high-contrast**.

---

## Aset yang Di-reuse (sudah diverifikasi)

| Kebutuhan | Reuse | Lokasi |
|---|---|---|
| Generate QR | `QrIdentityManager.generateQrBitmap(content, sizePx)` (ZXing) | `crypto/QrIdentityManager.kt` |
| Tampil QR | pola `IdentityQrCard()` | `ui/components/IdentityQrCard.kt` |
| Scan QR | `ScanContract()` + `CarakaQrCaptureActivity` + `rememberLauncherForActivityResult` | `ui/scanner/CarakaQrCaptureActivity.kt`, pola di `QrIdentityScreen.kt:75-95` |
| Kirim chat E2E | `MainViewModel.sendDirectMessage(recipientId, content)` → `MeshRepository.sendDirectMessage` | `viewmodel/MainViewModel.kt:308`, `repository/MeshRepository.kt:343` |
| Badge angka | `BadgedBox` + `Badge` | pola `ui/components/BottomNavBar.kt:152-178` |
| Carry count | `CourierViewModel.activeCarryCount: StateFlow<Int>` | `viewmodel/CourierViewModel.kt:98` |
| History source | `courier_task` (tidak dihapus saat delivered, hanya status di-update) | `data/local/entity/CourierTaskEntity.kt` |
| Sweeper hook | loop periodik `startQueueProcessor()` (15s) | `service/MeshForegroundService.kt:115-132` |

---

## Pekerjaan per Item

### Token (D2 + D4 + D6) — fondasi

Tambah **dua** field ke `StatusColors`: `stealth: Color` (aksen di atas surface) + `onStealth: Color` (konten di atas fill Stealth), lalu ganti semua `Color(0xFF7C4DFF)`.

Update di **3 file** (sudah dipetakan):
1. `ui/theme/StatusColors.kt` — `val stealth: Color` + `val onStealth: Color` di data class + keduanya `= Color.Unspecified` di default `staticCompositionLocalOf`.
2. `ui/theme/Color.kt` — definisikan konstanta baru (tak ada violet pre-existing). Usulan (tune saat build, target kontras AA):
   - light: `StealthViolet = Color(0xFF6B5E95)` (violet medium), `OnStealthLight = Color.White`
   - dark: `StealthVioletDark = Color(0xFFC9B7E0)` (lavender muda — agar terbaca di canvas gelap), `OnStealthDark = Color(0xFF2A1A40)` (ungu sangat gelap)
3. `ui/theme/Theme.kt:120-140` — isi `stealth`/`onStealth` di **blok light DAN dark** (lihat D6). Catatan: blok ini hanya bercabang `if (darkTheme)` → mode **high-contrast memakai nilai light**; pastikan `StealthViolet` light lolos kontras tinggi (atau tambahkan cabang HC bila perlu).

Penggunaan (penting untuk dark vs light):
- Aksen teks/ikon **di atas surface** (`tint`, label, border) → `statusColors.stealth` (sudah per-tema).
- Background tint → `statusColors.stealth.copy(alpha = 0.12f/0.14f)`.
- **Fill solid** (tombol "Klaim Token" `CourierComponents.kt:593`) → `ButtonDefaults.buttonColors(containerColor = statusColors.stealth, contentColor = statusColors.onStealth)` — **wajib set contentColor**, jangan biarkan default `LocalContentColor`.

Lokasi 7× `Color(0xFF7C4DFF)`: `CourierScreen.kt:350`; `CourierComponents.kt:374,527,533,560,571,593`.

Shape: ganti `RoundedCornerShape(topStart=28.dp, topEnd=28.dp)` (`CourierComponents.kt:113,728,933`) → `MaterialTheme.shapes.extraLarge` / `LocalCarakaShapes.current.xl`. Hindari radius 24dp+ (skala enterprise xl=18dp).

> **Dipertahankan (tidak diubah)**: emoji di composable/snackbar (D1) dan glow radial-gradient di `DeliverySuccessSheet` (D3). Glow tetap memakai `colorScheme.primary` sehingga sudah adaptif dark/light.

### F1 — Berbagi kredensial Stealth (A → Z) **[centerpiece]**

1. **Serialisasi** di `crypto/QrIdentityManager.kt` (ikuti pola `QrIdentityPayload`/`parseQrPayload`):
   - `@Serializable data class StealthCredentialPayload(v=1, bundleId, epkPrivB64, nonceB64)`
   - `buildStealthPayload(...)` + `parseStealthPayload(raw): StealthCredentialPayload?`
2. **Composable baru** `StealthCredentialShareSheet` (di `ui/courier/CourierComponents.kt`):
   - Di-trigger `CourierScreen` saat `viewModel.stealthCredentials` non-null (`collectAsState`).
   - Isi: peringatan kanal-aman + **QR** (dari `generateQrBitmap`) + **Salin EPK_priv**/**Salin nonce** (clipboard) + **Bagikan via chat CARAKA** (`sendDirectMessage` ke Z — perlu pilih/isi recipient) + **Selesai** → `viewModel.clearStealthCredentials()`.
3. **Sisi Z**: tombol **"Scan QR"** di `StealthChallengeDialog` & `DeliveryReceivedSheet` → launch `scanLauncher` → `parseStealthPayload` → auto-isi field `epkPriv`. Hilangkan double-entry: ingat EPK_priv dari tahap challenge agar tak diketik ulang saat decrypt.

### F3 — Badge "membawa N paket" di Home

- `MainActivity.kt`: collect `courierViewModel.activeCarryCount`, teruskan sebagai `courierCarryCount: Int` ke `HomeScreen`.
- `ui/screens/HomeScreen.kt`: bungkus `ServiceTile` Kurir (`:205`) dengan `BadgedBox` saat count > 0 (atau tambah param opsional `badgeCount` ke `ServiceTile` di `EnterpriseComponents.kt`).

### F2 — `CourierHistoryScreen` (log tugas kurir)

Sumber data: tabel **`courier_task`** (persisten; tanpa migrasi DB).
- `data/local/dao/CourierDao.kt`: tambah query read-only `getAllTasksFlow(): Flow<List<CourierTaskEntity>>` (ORDER BY acceptedAt DESC) dan/atau `getCompletedTasksFlow()` (status != 'ACTIVE').
- `repository/CourierRepository.kt`: passthrough Flow.
- `viewmodel/CourierViewModel.kt`: ekspos `historyTasks: StateFlow<List<CourierTaskEntity>>`.
- **Layar baru** `ui/screens/CourierHistoryScreen.kt`: `LazyColumn` riwayat (bundleId pendek mono, status chip DELIVERED/CANCELLED/EXPIRED/ACTIVE, waktu accepted/delivered, location hint bila ada) + empty-state pola `EmptyBundleCard`. Pakai `CarakaCard`/list-row yang ada.
- **Nav**: `object CourierHistory : Screen("courier_history", R.string.courier_history_title, ...)` di `ui/components/BottomNavBar.kt`; `composable(Screen.CourierHistory.route){...}` di `MainActivity.kt` (pola non-bottom-nav seperti `Screen.Help`); ikon "Riwayat" di TopAppBar `CourierScreen`.

### Bonus — verifikasi signature sungguhan

`DecryptedMessageDisplay` (`CourierComponents.kt:853-903`): jangan tandai "Terverifikasi" dari sekadar keberadaan string. Pakai hasil verifikasi nyata — backend punya `CourierRepository.verifyInnerPayload()`; kembalikan status verifikasi dari `decryptDirectedDelivery`/`decryptStealthDelivery` dan tampilkan badge sesuai hasil (atau hilangkan klaim "asli" bila tak terverifikasi).

### D6 — Paritas Dark vs Light (audit menyeluruh)

Setelah token Stealth beres, sapu **semua** layar/dialog/sheet Courier dan pastikan terbaca di **light, dark, & high-contrast**:
- **Fill solid + content**: setiap `containerColor` non-`colorScheme` wajib pasangan `contentColor` (kasus utama tombol Klaim `:593`). Hindari mengandalkan `LocalContentColor` di atas warna kustom.
- **Tint alpha di atas surface**: `DecryptedMessageDisplay` (`CourierComponents.kt:866-903`) pakai `primaryContainer.copy(alpha = 0.4f)` — cek keterbacaan teks `onSurface` di atasnya pada dark (primaryContainer dark = `TelegramBlueDarkCont`). Naikkan alpha atau pakai `surfaceContainer` bila pudar.
- **Stealth tint** `statusColors.stealth.copy(alpha = 0.12f/0.14f)` — verifikasi kontras di kedua canvas (`CanvasLight`/`SurfaceLow` vs `CanvasDarkBg`/`SurfaceDarkLow`).
- **QR bitmap**: `generateQrBitmap` default warna navy/amber legacy — saat memanggil untuk Stealth credential, set `darkColor`/`lightColor` agar QR kontras & on-brand di kedua tema (mis. modul gelap = `onSurface`, latar = `surface`/putih agar scannable).
- **High-contrast**: `statusColors` hanya bercabang `darkTheme` (HC pakai nilai light). Pastikan `StealthViolet` light lolos, atau tambah cabang `highContrast` di `Theme.kt` bila kontras kurang.
- Tidak ada `Color.White`/`Color.Black` literal baru di UI Courier untuk teks/ikon — pakai `colorScheme.onX` / token.

### D5 — i18n (~40–50 string, ID + EN)

Ekstrak semua literal Indonesia di `CourierScreen.kt`, `CourierComponents.kt`, `CourierHistoryScreen.kt`, dan snackbar `CourierViewModel.kt` ke `res/values/strings.xml` + `res/values-en/strings.xml` (**parity wajib**). Pola key: `courier_*` (mis. `courier_title`, `courier_mode_card_title`, `courier_send_assign_btn`, `courier_offer_title`, `courier_stealth_share_title`, `courier_history_title`, `courier_history_empty`, `courier_snack_offer_sent`, ...) + `cd_courier_*` untuk contentDescription. Ganti `Screen.Courier.titleRes` dari placeholder `R.string.nav_home` → `R.string.courier_title`.

### Sentuhan backend minimal (prasyarat trivial)

- **Sweeper expiry**: panggil `courierRepository.cleanupExpiredBundles()` di loop `MeshForegroundService.startQueueProcessor()` (`:115-132`) bersama `retryDueMessages()`/`flushCarry()`.
- Agar **EXPIRED nyata di history**, set `courier_task.status='EXPIRED'` untuk task bundle kedaluwarsa **sebelum** delete (di `cleanupExpiredBundles`).
- *Nol-sentuhan-backend opsional*: sweeper boleh di-skip — history cukup tampilkan DELIVERED/CANCELLED/ACTIVE.

---

## Urutan Eksekusi (high-impact dulu)

1. **Token + tema** (`StatusColors` `stealth`/`onStealth` → `Color.kt` → `Theme.kt` light+dark) + ganti 7 warna hardcode + shape token. *(fondasi, cepat)*
2. **F1** Stealth credential share (payload+parser, `StealthCredentialShareSheet`, scan sisi Z, verifikasi nyata). *(membuka alur Stealth)*
3. **F3** badge Home.
4. **F2** history (DAO → repo → VM → screen → nav) + sweeper backend trivial.
5. **D6** audit paritas dark/light/high-contrast (fill+content, tint alpha, QR colors) setelah semua composable selesai.
6. **D5** i18n sweep (sekaligus semua teks baru) + a11y contentDescription.
7. **Build hijau** + grep sweep + uji tema (light/dark/HC) + uji 3-device.

---

## Aksesibilitas

- Mode dibedakan oleh emoji + teks label + warna (token) — tetap ada sinyal non-warna walau emoji dipertahankan.
- Kontras token Stealth di-tune ≥4.5:1 (AA) di light **dan** dark; `onStealth` di atas `stealth` lolos di kedua tema.
- `contentDescription` pada semua icon-only button baru (Scan QR, Riwayat, Salin, Bagikan).
- Touch target ≥48dp untuk aksi utama (Tugaskan Kurir, Klaim, Verifikasi, Scan).
- Hormati `highContrast` + `bigText` (didukung `CarakaTheme`); cek sheet tak terpotong.

---

## Verifikasi

1. **Build**: `cd app && ./gradlew :app:assembleDebug` (Gradle di subfolder `app/`).
2. **Grep anti-pattern bersih**: `Color(0xFF000000)`, `0xFF7C4DFF` (harus 0 — sudah jadi token), `RoundedCornerShape(2[4-9]`, `Color.White`/`Color.Black` literal baru di UI Courier. *(emoji & `radialGradient` sengaja dibiarkan)*
3. **Uji tema**: render tiap layar/dialog/sheet Courier di **light, dark, high-contrast** + `bigText` — cek kontras tombol Stealth solid, tint alpha, kotak pesan terdekripsi, dan QR tetap scannable.
4. **String parity**: jumlah key courier di `values/strings.xml` == `values-en/strings.xml`.
5. **Uji 3-device (manual)** — sesuai verification plan `implementation_plan_caraka`:
   - **Directed**: A→B→Z; Z decrypt + badge verifikasi benar; A terima receipt; badge Home B naik/turun.
   - **Stealth**: A buat bundle → `StealthCredentialShareSheet` muncul → bagikan via QR (Z scan) / chat → B Caraka Mode broadcast → Z klaim + challenge (auto-isi dari scan) → Z decrypt. B & A tetap tak tahu identitas.
   - **History**: tugas muncul di `CourierHistoryScreen` dengan status & waktu benar.
   - **Expiry**: bundle lewat expiry → tersapu sweeper; (jika diaktifkan) muncul EXPIRED di history.

---

## Risiko / Catatan

- `sendDirectMessage` butuh `recipientId` Z yang sudah ter-QR-exchange (punya `publicKey`). Untuk Stealth murni-anonim, QR/clipboard adalah jalur utama; opsi "Bagikan via chat" hanya muncul bila kontak Z dikenal — sembunyikan/nonaktifkan jika tidak.
- History dari `courier_task` hanya perspektif **kurir (B)**. Riwayat lintas-peran (A pengirim, Z penerima) butuh tabel `courier_history` + migrasi v4→v5 — **peningkatan opsional di masa depan, di luar scope ini**.
- QR default colors di `generateQrBitmap` masih navy/amber legacy (param fungsi) — boleh diset ke token enterprise saat memanggil untuk konsistensi.
