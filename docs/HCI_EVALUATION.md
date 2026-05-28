# HCI Evaluation — CARAKA Mesh

> Dokumen ini memetakan implementasi UI/UX CARAKA terhadap kerangka evaluasi
> standar **Human-Computer Interaction**: 10 heuristik usability Nielsen,
> WCAG 2.1 Accessibility Guidelines, dan inclusive-design checklist.
>
> Setiap klaim dilengkapi referensi ke berkas/baris kode sehingga juri dapat
> melakukan verifikasi independen.

**Versi:** 1.1 · **Tanggal:** Januari 2026 · **Target:** WRECK-IT 7.0

---

## 1. Ringkasan Eksekutif

CARAKA adalah aplikasi komunikasi mesh offline untuk skenario *cyber-warfare*
di mana pengguna (warga, BPBD, Polri, PMI) harus tetap berkomunikasi tanpa
internet, listrik, dan jaringan seluler. Kondisi tersebut menempatkan UI/UX
di bawah tekanan tinggi (panik, perangkat berbagi, pencahayaan minim,
mungkin gemetar), sehingga rancangan antarmuka harus **mencegah kesalahan,
memberikan umpan balik jelas, mudah diakses, dan dapat dipahami lintas
peran/bahasa.**

Hasil: aplikasi memenuhi **10/10** heuristik Nielsen dan setidaknya level
**WCAG 2.1 AA** (target AAA opsional via mode kontras tinggi).

---

## 2. Pemetaan terhadap 10 Heuristik Nielsen

### N1 — Visibility of System Status

| Implementasi | Referensi |
|---|---|
| **Banner konektivitas** 🟢/🟡/🔴 selalu tampil di Beranda, dengan titik berdenyut saat `MESH_ONLY`. | `HomeScreen.kt → ConnectivityBanner` |
| **LiveStatsRow**: jumlah node, jangkauan, alarm aktif, paket di-relay. | `HomeScreen.kt → LiveStatsRow` |
| **Floating heads-up alert** muncul saat pesan masuk, otomatis hilang setelah 4 detik. | `FloatingChatAlert.kt` |
| **Snackbar global** menampilkan konfirmasi aksi: "Pesan terkirim", "SOS disiarkan ke X node", "Pesan diteruskan". | `SnackbarBus.kt`, `MainActivity.kt` |
| **Indikator GPS**: badge "GPS TERDETEKSI" hijau vs "LOKASI DEFAULT" kuning. | `SosScreen.kt` (location card) |

### N2 — Match Between System and the Real World

| Implementasi | Referensi |
|---|---|
| Ikon emergency mengikuti konvensi universal: 🚨 Medis, 🔥 Kebakaran, ⚠️ Keamanan, 🌊 Bencana. | `strings.xml → sos_cat_*` |
| **Bahasa Indonesia default** — sesuai pengguna target (BPBD, Polri, PMI), dengan toggle ke English. | `UiPreferences.kt`, `values/`, `values-en/` |
| Terminologi memakai istilah operasional yang dikenal: "BROADCAST SOS", "PEER", "MESH", dijelaskan via **tooltip** & layar Bantuan. | `HelpScreen.kt`, `InfoTooltip.kt` |
| Warna mengikuti semiotika krisis: merah=darurat, kuning=peringatan, hijau=aman, biru=relay/informasi. | `Color.kt` |

### N3 — User Control and Freedom

| Implementasi | Referensi |
|---|---|
| Tombol kembali tegas di setiap layar (SOS, Chat, Help). Navigasi `popBackStack`. | `SosScreen.kt`, `ChatScreen.kt`, `HelpScreen.kt` |
| **Hold-to-confirm** SOS — melepas tombol < 2 detik = batal, tanpa konsekuensi. | `HoldToConfirmButton.kt` |
| Dialog konfirmasi pada *flag* pesan & *reset identity* memberi tombol "Batal". | `ChatScreen.kt` (flag dialog), `PlaceholderScreens.kt` (reset dialog) |
| Toggle bahasa & mode aksesibilitas reversibel kapan saja dari Pengaturan. | `PlaceholderScreens.kt` (SettingsScreen) |

### N4 — Consistency and Standards

| Implementasi | Referensi |
|---|---|
| Material 3 dipakai konsisten (TopAppBar, Scaffold, Switch, AlertDialog). | semua `*Screen.kt` |
| Skema warna per peran konsisten: BPBD=biru, Polri=amber, PMI=merah, Sipil=mint. | `PlaceholderScreens.kt → roleColor` |
| **Sistem tipografi** terpusat: Rajdhani (display/title), Inter (body), JetBrains Mono (peer ID). | `Type.kt` |
| Pola animasi denyut (pulsing) konsisten untuk indikator status & SOS. | `ConnectivityBanner`, `AnimatedSosButton`, `NetworkScreen` |

### N5 — Error Prevention

| Implementasi | Referensi |
|---|---|
| **Hold-to-confirm 2 detik** sebelum SOS disiarkan — mencegah salah tekan. | `HoldToConfirmButton.kt` |
| Tombol SOS *disabled* sampai kategori dipilih + label peringatan kuning di bawah. | `SosScreen.kt → enabled = selectedCategory != null` |
| **Konfirmasi double-check** sebelum melaporkan hoaks (AlertDialog), karena dampak ke seluruh mesh. | `ChatScreen.kt → showFlagDialog` |
| Konfirmasi reset identitas dengan ikon Warning dan tombol merah tegas. | `PlaceholderScreens.kt → showResetDialog` |
| Validasi field: nama wajib untuk Civilian; password salah memunculkan inline error. | `ProfileSetupScreen.kt → errorMessage` |

### N6 — Recognition Rather Than Recall

| Implementasi | Referensi |
|---|---|
| **Onboarding tour 5 langkah** menampilkan fitur utama saat pertama dibuka. Dapat diputar ulang dari Help. | `OnboardingTourOverlay.kt` |
| Badge peran (BPBD/Polri/PMI/Civilian) tampil di chat header, daftar pesan, dan settings. | `MessagesScreen.kt`, `ChatScreen.kt`, `PlaceholderScreens.kt` |
| Ikon ✓ Verified menempel pada pesan dari otoritas — tanpa perlu mengingat siapa otoritas. | `IncomingMessageBubble`, `FloatingChatAlert` |
| Status koneksi & jumlah node selalu terlihat — tidak perlu diingat. | `ConnectivityBanner`, `LiveStatsRow` |

### N7 — Flexibility and Efficiency of Use

| Implementasi | Referensi |
|---|---|
| **Hold gesture** untuk SOS — power user bisa langsung tahan tanpa menunggu animasi. | `HoldToConfirmButton.kt` |
| **Long-press** pada pesan = shortcut untuk lapor hoaks (selain tombol). | `IncomingMessageBubble → combinedClickable(onLongClick=…)` |
| Bottom-nav 5 tab — akses semua fungsi dalam 1 tap. | `BottomNavBar.kt` |
| Toggle bahasa & aksesibilitas — tidak perlu restart aplikasi (recomposition langsung). | `LocalUiPrefs.kt` |
| **Floating in-app alert** dapat di-tap langsung untuk membuka chat (1 langkah, bukan 3). | `FloatingChatAlert.kt` |

### N8 — Aesthetic and Minimalist Design

| Implementasi | Referensi |
|---|---|
| Tema gelap taktis (`#0A1628` navy + `#F59E0B` amber) — kontras tinggi tanpa "berisik". | `Color.kt`, `Theme.kt` |
| Glass-morphism halus (`GlassSurface 0x3314243D`) memberi kedalaman tanpa menutupi konten. | seluruh `Modifier.background(GlassSurface)` |
| **Hierarki visual jelas**: judul Rajdhani Bold, body Inter Regular, label kecil ekspresif. | `Type.kt` |
| Tidak ada informasi redundant atau iklan. Setiap kartu punya satu tujuan. | desain seluruh screen |

### N9 — Help Users Recognize, Diagnose, and Recover from Errors

| Implementasi | Referensi |
|---|---|
| Pesan error **inline** dengan saran konkret: "Display name is required for Civilian role." | `ProfileSetupScreen.kt → nameRequiredMsg` |
| Authentication error spesifik: "Kata sandi salah! Akses ditolak." | `ProfileSetupScreen.kt → wrongPasswordMsg` |
| SOS tidak bisa dikirim tanpa kategori — error message di-render dengan ikon kuning + arahan jelas. | `SosScreen.kt → pickFirstMsg` |
| Status koneksi gagal: badge "Disconnected" di chat header (vs "Terhubung via Mesh"). | `ChatScreen.kt → TopBar` |
| Mesh node hilang → snackbar "Peer terputus" + warna node berubah ke biru gelap (offline). | `MeshNetworkGraph` |

### N10 — Help and Documentation

| Implementasi | Referensi |
|---|---|
| **HelpScreen** lengkap dengan 6 FAQ + ringkasan heuristik HCI + aksesibilitas. | `HelpScreen.kt` |
| Onboarding tour 5 langkah dapat diputar ulang. | `OnboardingTourOverlay.kt` |
| **Info tooltip** komponen reusable untuk menjelaskan istilah teknis (TTL, hop, peer ID). | `InfoTooltip.kt` |
| Setiap dialog konfirmasi menjelaskan dampak aksi (contoh: "Jika 3+ pengguna menandai, ⚠️ peringatan muncul untuk semua orang"). | `chat_flag_desc` di `strings.xml` |

---

## 3. Pemetaan terhadap WCAG 2.1

### 3.1 Perceivable (Dapat Dipersepsi)

| Kriteria WCAG | Level | Implementasi |
|---|---|---|
| **1.1.1 Non-text content** | A | Semua ikon interaktif punya `contentDescription` non-null. Lihat `cd_*` di `strings.xml`. |
| **1.3.1 Info and Relationships** | A | `Modifier.semantics { role = Role.Button; stateDescription = … }` pada tombol custom. `HoldToConfirmButton.kt` |
| **1.4.3 Contrast (Minimum)** | AA | Palet default lulus rasio 4.5:1 untuk text body, 3:1 untuk display. |
| **1.4.6 Contrast (Enhanced)** | AAA | Mode **High Contrast** opsional menaikkan rasio ke 7:1. `Theme.kt → HighContrastScheme` |
| **1.4.4 Resize Text** | AA | Sistem font-scale dihormati. Mode **Big Text** memperbesar tambahan 25%. `Theme.kt → density.fontScale * 1.25f` |
| **1.4.10 Reflow** | AA | LazyColumn responsif; konten tidak terpotong saat font diperbesar. |

### 3.2 Operable (Dapat Dioperasikan)

| Kriteria WCAG | Level | Implementasi |
|---|---|---|
| **2.1.1 Keyboard** | A | Compose mendukung navigasi DPAD/keyboard via `Modifier.focusable()` (default). |
| **2.4.4 Link Purpose** | A | Setiap tombol/ikon punya `contentDescription` deskriptif (bukan "click here"). |
| **2.5.5 Target Size** | AAA | Semua tombol primer ≥48dp (IconButton, NavigationBarItem, ChipShape). |
| **2.2.4 Interruptions** | AAA | In-app alert auto-dismiss 4s, tidak menghalangi navigasi. User dapat menutup manual. |

### 3.3 Understandable (Dapat Dimengerti)

| Kriteria WCAG | Level | Implementasi |
|---|---|---|
| **3.1.1 Language of Page** | A | Locale di-set via `createConfigurationContext` (`ProvideLocalizedContext.kt`). |
| **3.1.2 Language of Parts** | AA | Saat user pilih Bahasa Indonesia atau English, seluruh tree me-recompose dengan locale benar. |
| **3.2.4 Consistent Identification** | AA | Tombol dengan fungsi sama (back, send, flag) konsisten di tiap layar. |
| **3.3.3 Error Suggestion** | AA | Pesan error memberi arahan (lihat heuristik N9). |

### 3.4 Robust

| Kriteria WCAG | Level | Implementasi |
|---|---|---|
| **4.1.2 Name, Role, Value** | A | `Modifier.semantics { role = Role.Button; contentDescription = …; stateDescription = … }` pada setiap kontrol custom. |

---

## 4. Inclusive Design & Affective UX

| Aspek | Implementasi |
|---|---|
| **Multibahasa** | Bahasa Indonesia (default) + English, toggle 1 tap dari Settings. |
| **Mode Teks Besar** | Switch dedicated yang menggandakan font-scale 25% — krusial saat panik / pencahayaan rendah. |
| **Mode Kontras Tinggi** | Beralih ke palet WCAG AAA (`#000814` bg, `#FFFFFF` text, amber/mint yang lebih terang). |
| **Umpan Balik Haptik** | `Haptics.kt`: tick (toggle), light (kirim), heavy (SOS), waveform 3-pulsa (siaran berhasil). |
| **Audio + Vibrasi pada SOS** | Pola escalating 3-pulsa menjamin terasa walau perangkat di saku. |
| **Pengingat singkat** | Floating heads-up alert + snackbar — pesan & status disampaikan tanpa menutup layar. |
| **TalkBack-ready** | Semua kontrol custom memakai `semantics{}` agar dapat dibacakan screen reader. |

---

## 5. User Flow Analysis (HCI Lens)

### Skenario A — Warga panik mengirim SOS

1. **Buka aplikasi** → status `🔴 MESH ONLY` langsung terlihat (N1).
2. **Tap tombol SOS pulsing besar** (148dp, jauh di atas batas 48dp; N4 + WCAG 2.5.5). Haptic "heavy" memberi konfirmasi tap (Affective UX).
3. **Layar SOS** memandu: "Apa keadaan darurat Anda?" → 4 chip kategori dengan ikon universal (N2).
4. **Hold 2 detik** untuk konfirmasi — mencegah salah pencet di tengah panik (N5).
5. Pola haptic 3-pulsa + label "SIARAN SOS TERKIRIM" + snackbar "SOS disiarkan ke X node" (N1).
6. Kembali otomatis ke Beranda setelah 1.8 detik (N3 — user tidak perlu cari tombol kembali saat darurat).

### Skenario B — Otoritas (BPBD) login & broadcast info evakuasi

1. Profile setup → pilih "BPBD Response" → input password "Tangguh" → klik **AUTHORIZE & JOIN**.
2. Salah password → inline error merah + ikon (N9), tidak menutup form (user tetap dalam konteks; N3).
3. Login berhasil → identitas BPBD tampil di Settings dengan badge ✓ Verified biru (N6).
4. Kirim pesan → otomatis muncul dengan badge ✓ Verified untuk semua penerima — tidak bisa dipalsukan (N4, security).
5. Pesan otoritas tidak dapat di-*flag* (N5 — prevention; verified messages immutable to community flagging).

### Skenario C — Pengguna pertama kali, butuh edukasi

1. Setelah profile setup → **Onboarding Tour** otomatis tampil 5 langkah (N10, N6).
2. Setiap langkah ada **Lewati**, **Lanjut**, indicator dots untuk progress (N1).
3. Selesai → flag `onboardingDone=true` di DataStore.
4. Bisa ulang lagi dari **Settings → Bantuan → Tur Interaktif** (N3 + N10).
5. **HelpScreen** punya FAQ dan ringkasan HCI principles — transparan & edukatif untuk juri/dosen.

---

## 6. Kekurangan / Areas for Improvement

| Item | Catatan |
|---|---|
| TalkBack manual testing | Belum di-verify pada device. Code semantics sudah lengkap, perlu QA. |
| Voice input | SOS belum mendukung input suara (dapat membantu user dengan disabilitas motorik). |
| Color-blind palette | Default sudah lolos contrast tapi belum ada profile khusus deuteranopia/protanopia. |
| Localization beyond ID/EN | Saat ini 2 bahasa; ekspansi ke regional (Jawa, Sunda) untuk skenario nyata. |
| User-testing report | Belum dilakukan think-aloud usability test formal (rekomendasi untuk iterasi selanjutnya). |

---

## 7. Cara Memverifikasi Klaim Ini

1. Buka project di Android Studio.
2. Jalankan di device fisik (min SDK 26).
3. Aktifkan **TalkBack**: Setelan Sistem → Aksesibilitas → TalkBack.
4. Telusuri semua tab; perhatikan label terdengar dengan benar.
5. Aktifkan **Big Text** di Pengaturan CARAKA → lihat teks membesar.
6. Aktifkan **High Contrast** → palet berubah ke #000814/#FFFFFF dasar.
7. Ulang Onboarding via Pengaturan → Bantuan → "Tur Interaktif".
8. Tekan SOS → tahan < 2 detik → progress bar kembali ke nol (bukti hold-to-confirm).
9. Tahan ≥ 2 detik → SOS terkirim + haptic + snackbar.

---

> *Dokumen ini dirancang untuk dilampirkan ke proposal/laporan WRECK-IT 7.0 dan kuliah/mata-kuliah HCI.*
