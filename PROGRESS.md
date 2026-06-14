# CARAKA — Progress Tracker
> WRECK-IT 7.0 · Track: IoT Resilience · Theme: *Cyber Warfare: Silent War on The Fifth Domain*
> **Last updated: 14 Juni 2026** · Sumber kebenaran: kode branch `main` + [`PRD.md`](PRD.md)

> 📐 Detail teknis lengkap ada di [`PRD.md`](PRD.md). README = nilai produk; PRD = spesifikasi teknis; **PROGRESS = status pengerjaan**.

---

## 🧭 Status Sekilas

| Area | Status |
|------|--------|
| Core mesh (chat, SOS, relay multi-hop) | ✅ Live |
| DTN migration (Fase 0–3) | ✅ Code complete · field-test berjalan |
| Multi-transport (Aware/Nearby/Direct/LAN) | ✅ Live |
| Keamanan (E2E, sign, SQLCipher, secure wipe) | ✅ Live |
| QR identity & verifikasi | ✅ Live |
| UI light enterprise super app | ✅ Live |
| HCI & aksesibilitas (i18n 280 string, a11y) | ✅ Live |
| Verifikasi end-to-end ≥3 device fisik | 🔄 Berjalan |
| Lisensi repo (`LICENSE`) | 🔲 Belum |

Build `assembleDebug` terakhir diverifikasi berhasil **13 Juni 2026** (JDK 21, Android Studio).

---

## ✅ Completed

### 🔄 DTN Migration (Fase 0–3) — store-carry-forward
> Mengikuti `docs/architecture/caraka-architecture-baseline.md` (D1–D16) & `docs/implementation/caraka-migration-program.md` (EU-0.1 … EU-3.2). Commit acuan: `30e23b9 feat(migration): phase 2 limited DTN + phase 3 power optimization`.

- [x] **Fase 0 — Foundation**
  - Gerbang anti-replay berbasis **ID** (bukan jam) + cek drift waktu ±5 mnt sebagai hint *(EU-0.1)*
  - Dedup persisten `messageExists()` yang bertahan setelah restart + LRU in-memory *(EU-0.2)*
  - `MeshPolicy` — kontrak resource terpusat (kuota, TTL, retry, drop-priority) *(EU-0.3)*
  - DB **v3**: tabel `outbox` + kolom `deliveryStatus`, migrasi `2→3` additive *(EU-0.4)*
- [x] **Fase 1 — Reliable Unicast**
  - Tulis ke outbox saat kirim unicast *(EU-1.1)*; ACK end-to-end *(EU-1.2/1.3)*
  - Retry berbatas: 4 attempt, backoff eksponensial (10s→90s) + jitter, lalu `FAILED` *(EU-1.4)*
  - Status delivery jujur di UI: `SENT/DELIVERED/EXPIRED/FAILED`; `DELIVERED` hanya dari ACK nyata *(EU-1.5, D5)*
- [x] **Fase 2 — Limited DTN**
  - Queue-processor timer di Foreground Service (tiap 15s) *(EU-2.1, D10)*
  - Carry-and-forward untuk **kontak terverifikasi** yang sedang offline *(EU-2.2, D7)*
  - Penegakan kuota outbox (500 msg / ~2MB, eviksi prioritas→umur→replika) *(EU-2.3)*
  - `MeshRouter` **berhenti men-drop** saat no-route → managed flood TTL-bounded *(EU-2.4, D16)*
- [x] **Fase 3 — Power Optimization**
  - Duty-cycle dua-state: IDLE setelah 60s (interval ×4), DEEP_IDLE setelah 5 mnt (suspend active scanning, receive path tetap hidup) *(EU-3.1/3.2, D14)*

### 📡 Transport & Routing
- [x] **Facade `MeshManager : MeshTransport`** — memilih transport per kemampuan device, semua disalurkan ke satu "brain" (`WifiDirectManager`)
- [x] **Wi-Fi Aware (NAN)** — transport multi-hop primary saat HW mendukung (`WifiAwareManager` + `MeshRouter` + `MeshSocketManager`)
- [x] **Google Nearby Connections 19.3.0** — overlay primary di HP tanpa NAN (butuh Play Services; di-skip senyap bila absen)
- [x] **Wi-Fi Direct + LAN** — brain + backbone + fallback (selalu aktif); GO election battery-aware (`GoIntentCalculator`)
- [x] **MeshRouter ala BATMAN/OLSR** — routing table, heartbeat originator 10s, route timeout 35s, anti-replay seenIds (2000 LRU)
- [x] **PEER_LIST gossip** — full-mesh awareness (belajar peer lewat GO)

### 🔒 Keamanan
- [x] **E2E chat** — X25519 + **XSalsa20-Poly1305** via `crypto_box` (Lazysodium)
- [x] **Tanda tangan Ed25519** per pesan (TEXT pada payload terenkripsi; SOS pada plaintext)
- [x] **SQLCipher** — Room terenkripsi at rest; passphrase 32-byte acak dibungkus AES-256-GCM via Android Keystore, dengan fallback bila TEE gagal
- [x] **Secure wipe** — `CarakaDatabase.secureWipe()` (hapus passphrase + file DB → permanen tak terbaca)
- [x] **QR identity (ZXing)** — `QrIdentityManager`; scan peer → `isVerified = true`
- [x] **FLAG protocol** — community hoax reporting, konsensus ≥3 flag → label peringatan

### 🎨 UI/UX
- [x] **Migrasi ke light enterprise "super app"** — palet Telegram Blue `#229ED9` + surface terang (`f91d0db`, `bbc7682`); meninggalkan tema dark navy lama
- [x] **Tipografi** — Manrope (display) + Inter (body) + JetBrains Mono (data), font lokal bundled (bukan lagi Rajdhani/Downloadable Fonts)
- [x] **Design tokens** — `Color.kt`, `Dimens.kt`, `Shape.kt`, `Type.kt`/`CarakaTextStyles`, `StatusColors.kt`
- [x] **10 layar** — Home, Messages, Chat, Network, SOS, Settings, Help, QR Identity, Alerts, ProfileSetup
- [x] **NetworkScreen** — graph force-directed (physics), radar sweep, node color-coded, relay stats
- [x] **ChatScreen** — long-press flag, indikator "✓✓ Mesh", badge authority/peringatan
- [x] **HCI** — hold-to-confirm SOS 2 dtk (arc sweep), haptic semantic, onboarding tour 5-langkah, snackbar bus, tooltip

### ♿ Aksesibilitas & i18n
- [x] **i18n dual-locale** — Bahasa Indonesia (default) + English, **280 string per locale**
- [x] **High-Contrast mode** + **Big-Text mode (+25%)**
- [x] **Accessibility semantics** — role/contentDescription/stateDescription di kontrol custom
- [x] **HCI Evaluation doc** — `docs/HCI_EVALUATION.md` (pemetaan Nielsen + WCAG)

### 🔌 Lifecycle & Connectivity
- [x] **MeshForegroundService** — WifiLock (`FULL_LOW_LATENCY`) + WakeLock (12 jam), `START_STICKY`, aksi Stop Mesh
- [x] **ConnectivityMonitor** — emit `ONLINE` / `HYBRID` / `MESH_ONLY` ke banner Home

### 📄 Dokumentasi
- [x] **README** — landing page produk (nilai bisnis + promosi), diselaraskan dengan kode
- [x] **PRD v3.0** — spesifikasi teknis lengkap, diselaraskan dengan kode
- [x] **Architecture docs** — baseline (D1–D16), gap analysis, migration review (`docs/architecture/`)
- [x] **Implementation docs** — migration program (EU), device-test plan, session 14 Juni (`docs/implementation/`)
- [x] **Demo package** — script video + 8 title cards (`demo/`)

---

## 🔲 Remaining / In Progress

| Task | Prioritas | Catatan |
|------|-----------|---------|
| Verifikasi end-to-end di ≥3 device fisik (unicast offline→online, carry via kurir, SOS multi-hop, baterai/jam) | 🔴 Tinggi | Gerbang field test wajib setelah Fase 2 (baseline). Sesi 2-device (Infinix + Redmi) sudah ter-setup & APK terpasang; **checklist eksekusi masih pending**. Lihat `docs/implementation/device-test-session-2026-06-14.md` |
| Migrasi kunci identitas ke Android Keystore | 🟡 Sedang | Saat ini di DataStore (TODO di `IdentityManager`) |
| Tambah file `LICENSE` | 🟡 Sedang | Repo belum mendeklarasikan lisensi |
| Unit/instrumented test untuk crypto, outbox, router | 🟡 Sedang | Coverage test masih minimal |
| PKI/atestasi otoritas nyata (gantikan seed demo `GARUDA_MESH_AUTHORITY_*`) | 🟢 Future | Hanya untuk produksi |
| Item POSTPONE baseline (D8 carry orang asing, D12 gossip, D13 adaptive beaconing, D15 BLE) | 🟢 Future | Dibuka hanya oleh data field test |

---

## 📊 Progress Overview

```
Core mesh           ████████████████████ 100%
DTN migration F0-3  ████████████████████ 100%  ← code complete (30e23b9)
Multi-transport     ████████████████████ 100%
Keamanan (E2E+DB)   ████████████████████ 100%
QR identity         ████████████████████ 100%
UI light enterprise ████████████████████ 100%  ← f91d0db
i18n + a11y         ████████████████████ 100%
Dokumentasi         ████████████████████ 100%  ← README + PRD selaras
Field test ≥3 HP    ██████░░░░░░░░░░░░░░  ~30%  ← 2-device ter-setup, eksekusi pending
Test coverage       ████░░░░░░░░░░░░░░░░░  ~20%
LICENSE             ░░░░░░░░░░░░░░░░░░░░    0%
```

---

## 🏗️ Tech Stack (ringkas — detail di [PRD §14](PRD.md#14-tech-stack--versi))

| Layer | Teknologi |
|-------|-----------|
| Bahasa / Build | Kotlin 2.0.21 · AGP/Gradle 8.13.2 · minSdk 26 · target/compile 36 |
| UI | Jetpack Compose (Material 3) + Navigation Compose |
| State / DI | MVVM · Coroutines · StateFlow · **manual DI** (`CarakaApp`, bukan Hilt) |
| Transport | Wi-Fi Aware (NAN) · Google Nearby 19.3.0 · Wi-Fi Direct · LAN UDP/TCP |
| Routing/DTN | MeshRouter (BATMAN/OLSR) · outbox Room · `MeshPolicy` (kuota/TTL/retry) |
| Enkripsi | X25519 + **XSalsa20-Poly1305** (Lazysodium 5.1.0) |
| Signing | Ed25519 per-message |
| Database | Room 2.6.1 + **SQLCipher 4.5.4** (v3, terenkripsi at rest) |
| Identity/QR | ZXing 4.3.0 · DataStore |

---

## 📁 Struktur Folder

```
CARAKA-APP/
├── app/app/src/main/java/com/example/caraka/
│   ├── crypto/        # CryptoManager, IdentityManager, QrIdentityManager, DatabasePassphraseManager
│   ├── network/       # MeshManager, WifiDirectManager, WifiAwareManager, NearbyTransport,
│   │                  #   MeshRouter, MeshSocketManager, MeshPolicy, MeshProtocol, ConnectivityMonitor
│   ├── repository/    # MeshRepository (single source of truth: DB + crypto + outbox)
│   ├── service/       # MeshForegroundService (queue-processor, WifiLock/WakeLock)
│   ├── data/local/    # CarakaDatabase (SQLCipher v3), entity/, dao/ (message, peer, relay, outbox)
│   ├── viewmodel/     # MainViewModel
│   └── ui/            # screens/ (10), components/, theme/, prefs/, util/, dialogs/
├── docs/              # architecture/ (baseline D1–D16), implementation/, TECHNICAL_WRITEUP, HCI_EVALUATION
├── demo/              # DEMO_VIDEO_SCRIPT.md + title_cards/
├── README.md          # Landing page produk (nilai bisnis)
├── PRD.md             # Spesifikasi teknis (v3.0)
└── PROGRESS.md        # File ini
```
