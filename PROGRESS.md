# CARAKA — Progress Tracker
> WRECK-IT 7.0 · Track: IoT Resilience · Theme: Cyber Warfare: Silent War on The Fifth Domain  
> Deadline Proposal: **14 Juni 2026** · Last updated: **7 Juni 2026**

---

## ✅ Completed

### 🔒 Phase 0 — Security Hardening & Demo Polish (7 Juni 2026)
- [x] **SQLCipher — Database Encryption at Rest** — Room database dimigrasi dari plain SQLite ke SQLCipher `SupportFactory`. Passphrase 32-byte random di-generate sekali, di-encrypt dengan AES-256-GCM key yang disimpan di Android Keystore (TEE hardware). `DatabasePassphraseManager.kt` baru. `CarakaDatabase.secureWipe()` untuk panic wipe. Build: `✅ libsqlcipher.so` masuk APK.
- [x] **QR Code Identity (ZXing)** — `QrIdentityManager.kt` baru untuk build/parse JSON payload QR (`peerId`, `name`, `role`, `encPub`, `signPub`). `QrIdentityScreen.kt` dua panel: tampil QR identitas sendiri (navy+amber tactical colors) + scan QR peer → simpan sebagai `isVerified = true` di Room. Navigasi dari Settings → "Lihat / Scan QR Identitas". `CAMERA` permission ditambah di Manifest.
- [x] **Smart GO Election (Battery-Aware)** — `GoIntentCalculator.kt` baru menghitung `groupOwnerIntent` (0–15) berdasarkan battery level, authority role, dan relay load. Menggantikan hardcoded `intent = 15`. Logic: kritis (≤10%) → intent=0, authority + OK battery → intent 13+, civilian normal → intent 6-10. `batteryLevel: StateFlow<Int>` di-expose ke Settings UI via ViewModel.
- [x] **README Rewrite** — README lengkap dengan ASCII arsitektur diagram, security layer diagram, mesh protocol flow diagram, tabel fitur update, security architecture section, Indonesia threat context table, dan getting started guide.


### 🔧 Core Code Upgrades
- [x] **Multi-hop Relay** — TTL-based message forwarding (TEXT + SOS) di `WifiDirectManager`; node relay pesan yang bukan miliknya, TTL decremented tiap hop
- [x] **Anti-replay Protection** — `seenIds` LRU LinkedHashSet (2000 entries) + timestamp drift check ±5 menit di `MeshSocketManager`; mencegah replay attack & flood
- [x] **FLAG Protocol** — Packet type baru untuk community hoax reporting; propagasi via mesh, consensus ≥3 flag = pesan ditandai ⚠️
- [x] **Relay Count Tracker** — `_relayedMessageCount: MutableStateFlow<Int>` di `WifiDirectManager`, di-expose ke UI via ViewModel

### 📡 Connectivity Monitor
- [x] **ConnectivityMonitor.kt** — `NetworkCallback`-based real-time detection; emit `ConnectivityStatus` enum: `ONLINE` / `HYBRID` / `MESH_ONLY`
- [x] **Integrasi ViewModel** — `connectivityStatus: StateFlow<ConnectivityStatus>` di-observe oleh HomeScreen

### 🐛 Bug Fix (1 Juni 2026)
- [x] **Fix 3-device connection** — `MeshSocketManager.startServer()` dijadikan idempotent (`if (serverJob?.isActive == true) return`). Root cause: tiap device ke-3 join group, GO restart server & drop koneksi existing. Sekarang bisa handle banyak client sekaligus. *(belum diverifikasi di 3 device fisik)*

### 🎨 UI/UX Polish Round 2 (1 Juni 2026)
- [x] **HomeScreen SOS** — Hapus `AnimatedSosButton` lama (pulsing ring), ganti hold-to-confirm 2 detik dengan arc putih menyapu 0→360° (Approach A). Label berubah jadi "TAHAN…"/"HOLD…" saat ditekan
- [x] **HomeScreen LiveStatsRow** — Emoji 🌐📡🆘🔀 diganti Material icons (Hub / SettingsInputAntenna / Warning / SwapHoriz); range metric dapat prefix `~` (tilde)
- [x] **BottomNavBar** — Tambah active indicator pill (`indicatorColor` tadinya Transparent → `accent.copy(alpha = 0.16f)`); badge count merah di tab SOS saat ada alert aktif (wired dari `viewModel.activeAlerts` di MainActivity)
- [x] **ProfileSetupScreen** — Role cards dapat icon per-role (BPBD=Shield/biru, POLRI=LocalPolice/amber, PMI=LocalHospital/merah, Civilian=Person/mint) + animated border color + AnimatedVisibility check icon saat dipilih
- [x] **ChatScreen** — Outgoing bubble dapat indikator "✓✓ Mesh" (DoneAll icon, NeonMint) = bukti terkirim via mesh tanpa internet
- [x] **SosScreen** — Haptic `tick()` di tiap category chip onClick
- [x] **MessagesScreen** — Timestamp hardcoded English ("Just now"/"m ago") dilokalkan ke string resources
- [x] **FloatingChatAlert** — Label "· new message" & "Tap" dilokalkan ke string resources
- [x] **SettingsScreen** — About section row labels dilokalkan (App/Protocol/Encryption/Relay)
- [x] **strings.xml (ID + EN)** — Tambah 12 string baru: `home_sos_hold`, `messages_just_now`, `messages_min_ago`, `alert_new_message`, `alert_tap_to_open`, `home_stat_range_unit`, `chat_sent_via_mesh`, `chat_mesh_label`, `settings_label_*`
- [x] **Build** — `BUILD SUCCESSFUL` 0 error, 1 pre-existing warning (deprecated ArrowForward di OnboardingTourOverlay)

### 🖥️ UI / UX Overhaul
- [x] **HomeScreen rewrite** — `AnimatedSosButton` (double pulsing ring), `ConnectivityBanner` (🟢🟡🔴 + pulsing dot di MESH_ONLY), `LiveStatsRow` (nodes/range/alerts/relayed), `AttackSimulatorCard` (toggle switch demo grid-down)
- [x] **NetworkScreen rewrite** — Force-directed graph dengan physics simulation (spring + repulsion, 30fps), Canvas radar sweep, animated data-flow dots, color-coded nodes (gold=self, green=authority, blue=relay, amber=direct)
- [x] **ChatScreen update** — Long-press to flag (hoax reporting), flag confirmation dialog, `combinedClickable`, authority badge (✓ Verified icon), warning badge saat flagCount ≥ 3

### 📄 Proposal & Dokumentasi
- [x] **Proposal PDF** — 15 halaman, 655KB · `proposal/PROPOSAL_GARUDA_MESH_WRECK-IT-7.0.pdf`
  - Cover, Executive Summary, Latar Belakang, Arsitektur Teknis, Security Stack (X25519 + Ed25519 + XChaCha20-Poly1305), Feature Matrix, **3.7 Evaluasi HCI (Heuristik Nielsen + WCAG 2.1)**, Roadmap, Kesimpulan
- [x] **Proposal HTML source** — Editable di `proposal/proposal_caraka.html`
- [x] **HCI Evaluation doc** — `docs/HCI_EVALUATION.md` (14KB, pemetaan lengkap ke Nielsen + WCAG dengan code refs)

### 🎨 UI/UX & HCI Upgrade (Jan 2026)
- [x] **i18n dual-locale** — Bahasa Indonesia (default) + English (175 string per locale)
- [x] **Tipografi taktis** — Rajdhani (display) + Inter (body) + JetBrains Mono (peer ID) via Downloadable Google Fonts
- [x] **Hold-to-confirm SOS** (2 detik) — Nielsen #5 Error Prevention
- [x] **Haptic feedback** — Util semantic (tick/light/heavy/SOS-waveform 3-pulsa)
- [x] **Onboarding tour 5-langkah** — replay-able dari Help
- [x] **HelpScreen** + InfoTooltip — 6 FAQ + ringkasan heuristik HCI + a11y
- [x] **High-Contrast mode** (WCAG AAA) + **Big-Text mode** (+25%)
- [x] **Snackbar feedback bus** — visibility of system status
- [x] **Accessibility semantics** — role/contentDescription/stateDescription di semua kontrol custom

### 🎬 Demo Video Package
- [x] **Demo Script** — Narasi word-for-word 4 menit, shot list, timing cues, setup checklist, editing guide · `demo/DEMO_VIDEO_SCRIPT.md`
- [x] **Title Cards HTML** — 8 kartu A4 landscape (297×210mm) · `demo/title_cards.html`
- [x] **Title Card PNGs** — 2105×1489px exports (2.5× scale) · `demo/title_cards/card_01.png` s/d `card_08.png`
  | Card | Isi |
  |------|-----|
  | 01 | Opening Title — CARAKA |
  | 02 | Grid Down — semua infrastruktur offline |
  | 03 | Mesh Active — 4 nodes, E2E, ~400m, 0 servers |
  | 04 | SOS Emergency — medical alert + GPS + relay |
  | 05 | Authority Verified — BPBD Jakarta Ed25519 |
  | 06 | Hoax Flagged — community consensus |
  | 07 | Final Stats — 12 msg · 3 SOS · 47 hops · 0 server |
  | 08 | Closing — tagline + team credit |

---

## 🔲 Remaining Tasks

### 🔴 Wajib (sebelum 14 Juni)

| Task | File | Catatan |
|------|------|---------|
| Isi nama tim | `proposal/proposal_garuda_mesh.html` → regenerate PDF | Cari-replace `[NAMA TIM]` |
| Isi nama tim | `demo/title_cards.html` line 324 → regenerate PNGs | Card 08 closing card |
| **Submit Proposal PDF** | Portal WRECK-IT 7.0 | Deadline 14 Juni 2026 |

### 🟡 Demo Day

| Task | Catatan |
|------|---------|
| Rekam demo video | 3 HP Android, WiFi Direct aktif, ikuti `DEMO_VIDEO_SCRIPT.md`, ~4 menit |
| Test end-to-end di 3 device | Install APK, pair WiFi Direct, test SOS + relay + flag sebelum hari-H |

### 🟢 Optional (nambah poin & prize)

| Task | Est. | Impact |
|------|------|--------|
| **SQLCipher** — enkripsi Room database | ~3 jam | 🔒 Security data-at-rest; disimpan untuk 3 jam coding di babak final |
| **QR Code identity** — scan peer via ZXing | ~4 jam | 📲 UX demo lebih mulus |
| **Best Write-up** — dokumen teknis terpisah | ~5 jam | 💰 Prize Rp 1.000.000 terpisah |
| **README update** — arsitektur diagram + setup guide | ~2 jam | ⭐ Kesan profesional di GitHub |

---

## 📊 Progress Overview

```
Code upgrades    ████████████████████ 100%
UI/UX polish     ████████████████████ 100%  ← selesai 1 Juni
Proposal PDF     ████████████████████ 100%  ← tinggal isi nama tim & submit (Sabtu)
Demo script      ████████████████████ 100%
Title cards      ████████████████████ 100%
GitHub repo      ░░░░░░░░░░░░░░░░░░░░   0%  ← Sabtu
Demo video       ░░░░░░░░░░░░░░░░░░░░   0%  ← Sabtu, perlu 3 HP
SQLCipher        ░░░░░░░░░░░░░░░░░░░░   0%  (disimpan untuk 3 jam coding final)
QR Code          ░░░░░░░░░░░░░░░░░░░░   0%  (optional)
Write-up         ░░░░░░░░░░░░░░░░░░░░   0%  (optional, prize terpisah)
README           ░░░░░░░░░░░░░░░░░░░░   0%  (optional)
```

---

## 🏗️ Tech Stack

| Layer | Teknologi |
|-------|-----------|
| Network | Android WiFi Direct (IEEE 802.11p2p) |
| Encryption | X25519 key exchange + XChaCha20-Poly1305 (Lazysodium) |
| Signing | Ed25519 per-message digital signatures |
| Relay | TTL-based multi-hop, max 5 hops |
| Anti-replay | LRU seenIds cache (2000 entries) + ±5min timestamp drift |
| Database | Room + (SQLCipher planned) |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture, Kotlin Coroutines, StateFlow |

---

## 📁 Struktur Folder

```
cakra-mesh/
├── app/                        # Android app source
│   └── app/src/main/java/com/example/garudamesh/
│       ├── network/            # MeshSocketManager, WifiDirectManager, ConnectivityMonitor
│       ├── repository/         # MeshRepository
│       ├── viewmodel/          # MainViewModel
│       ├── ui/screens/         # HomeScreen, ChatScreen, NetworkScreen, SosScreen
│       └── data/               # Room entities, DAOs
├── proposal/
│   ├── proposal_garuda_mesh.html   # Editable source
│   └── PROPOSAL_GARUDA_MESH_WRECK-IT-7.0.pdf
├── demo/
│   ├── DEMO_VIDEO_SCRIPT.md        # Full production script
│   ├── title_cards.html            # 8 title cards source (A4 landscape)
│   └── title_cards/
│       ├── all_cards.pdf
│       └── card_01.png ... card_08.png
└── PROGRESS.md                 # This file
```
