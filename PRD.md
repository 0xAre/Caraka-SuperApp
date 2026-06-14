# 📐 PRD — CARAKA

**Product & Technical Requirements Document**
**Resilient Offline Mesh Communication for Android**

> **Version**: 3.0 · **Last updated**: 14 Juni 2026 · **Status**: Aktif (build diverifikasi)
> **Audience**: engineering, reviewer teknis, kontributor.
> **Sumber kebenaran**: dokumen ini mengikuti kode pada branch `main`. Bila dokumen dan kode berbeda, **kode yang benar** — perbarui dokumen ini.
> **Pendamping**: [`README.md`](README.md) memuat narasi produk & nilai bisnis; PRD ini memuat detail teknis.

---

## 1. Ringkasan Produk

**CARAKA** adalah aplikasi Android (Jetpack Compose) yang membentuk **mesh komunikasi lokal** antarperangkat tanpa server pusat dan tanpa internet. Perangkat di sekitar saling menemukan, bertukar identitas, dan meneruskan pesan multi-hop melalui transport lokal yang tersedia (Wi-Fi Aware, Wi-Fi Direct, Google Nearby Connections, dan LAN). Tujuan utamanya adalah **jalur komunikasi cadangan** ketika internet, BTS, atau layanan pusat tidak dapat diandalkan.

CARAKA bukan pengganti internet. Ia adalah lapisan komunikasi *store-carry-forward* yang tetap berjalan saat kanal utama terputus, dengan enkripsi end-to-end untuk chat langsung, tanda tangan Ed25519 untuk autentikasi, dan penyimpanan lokal terenkripsi.

**Tagline produk**: *"When The Grid Falls, We Rise."*
**Konteks asal**: dibangun untuk **WRECK-IT 7.0** (track IoT Resilience, tema *Cyber Warfare: Silent War on The Fifth Domain*).

---

## 2. Tujuan & Batasan

### 2.1 Tujuan (Goals)
- **G1** — Pertukaran pesan teks antar-perangkat tanpa internet/akun/backend.
- **G2** — Penerusan multi-hop sehingga node di luar jangkauan langsung tetap terjangkau.
- **G3** — Broadcast darurat (SOS) berprioritas tinggi yang dapat dibaca semua node penerima.
- **G4** — Kerahasiaan & autentikasi: chat terenkripsi E2E, seluruh pesan dapat ditandatangani, data lokal terenkripsi at rest.
- **G5** — Bertahan di latar belakang (foreground service) dan hemat daya melalui *duty cycling*.
- **G6** — Dapat dipakai di lapangan: dua bahasa, aksesibilitas, anti-mistap, onboarding.

### 2.2 Non-Goals (di luar cakupan saat ini)
- iOS / lintas-platform.
- Transport non-Wi-Fi/BT (LoRa, satelit, radio HF).
- Pesan suara/video, peta offline, berbagi berkas besar.
- Verifikasi identitas berbasis blockchain/PKI tersentralisasi atau CA online.
- Deteksi deepfake / moderasi konten otomatis.
- Sinkronisasi cloud (saat online, app tidak mengunggah riwayat ke server mana pun).

---

## 3. Persona & Skenario Inti

| Persona | Kebutuhan lapangan | Jalur di CARAKA |
|---|---|---|
| **Responder** (BPBD, Polri, PMI) | Koordinasi saat infrastruktur tumbang | Identitas peran, chat terenkripsi, broadcast/SOS, peta node |
| **Relawan** | Menemukan tim & melaporkan kondisi | Discovery lokal, QR identity, relay multi-hop |
| **Warga** | Minta bantuan tanpa sinyal | SOS lokal, lokasi opsional, chat dengan kontak terdekat |
| **Komunitas rawan bencana** | Kanal cadangan siap pakai | Mesh berbasis ponsel Android yang sudah dimiliki |

**Skenario acuan**: gempa/banjir/serangan siber merusak konektivitas pusat → perangkat di lapangan membentuk mesh → SOS dan koordinasi tetap mengalir lewat node perantara hingga tujuan, lalu sinkron status saat sebagian internet pulih.

---

## 4. Arsitektur Sistem

CARAKA memakai pola **MVVM + manual dependency injection**. Tidak menggunakan Hilt/Dagger; seluruh objek inti dirakit di `CarakaApp` dan dibagikan melalui `Application`.

```
┌───────────────────────────────────────────────────────────────┐
│ PRESENTATION  — Jetpack Compose (Material 3), Navigation        │
│   MainActivity · 10 screen · komponen UI · theme/tokens         │
│   MainViewModel  (StateFlow → UI)                               │
├───────────────────────────────────────────────────────────────┤
│ DOMAIN/REPOSITORY                                               │
│   MeshRepository  (single source of truth: DB + crypto + outbox)│
│   MeshPolicy      (kuota, TTL, retry, drop-priority — D1/D4/D7) │
├───────────────────────────────────────────────────────────────┤
│ SECURITY & STORAGE                                              │
│   CryptoManager (X25519 · Ed25519 · XSalsa20-Poly1305)          │
│   IdentityManager (DataStore)                                   │
│   DatabasePassphraseManager (Android Keystore + fallback)       │
│   Room + SQLCipher  (messages · peers · relayed · outbox)       │
├───────────────────────────────────────────────────────────────┤
│ NETWORK / TRANSPORT (facade: MeshManager : MeshTransport)       │
│   WifiDirectManager  ← "brain" + LAN backbone + fallback        │
│   WifiAwareManager + MeshRouter + MeshSocketManager (primary)   │
│   NearbyTransport    (overlay BT+Wi-Fi, butuh Play Services)    │
├───────────────────────────────────────────────────────────────┤
│ LIFECYCLE                                                       │
│   MeshForegroundService (WifiLock+WakeLock, queue-processor)    │
│   ConnectivityMonitor (ONLINE / HYBRID / MESH_ONLY)             │
└───────────────────────────────────────────────────────────────┘
```

**Perakitan dependency** (`CarakaApp.onCreate`):
1. `SQLiteDatabase.loadLibs()` (SQLCipher native) → `CarakaDatabase.getDatabase()`.
2. `CryptoManager` → `IdentityManager` → `MeshRepository` (menerima 4 DAO + crypto + identity).
3. `MeshManager` (facade transport) dibuat, lalu `repository.transport = transport` untuk memutus dependensi sirkular.
4. `ConnectivityMonitor` untuk status online/mesh.

---

## 5. Lapisan Transport

`MeshManager` adalah facade yang mengimplementasikan `MeshTransport` dan memilih transport berdasarkan kemampuan perangkat. Semua transport menyalurkan pesan ke **satu "brain"** (`WifiDirectManager`) sehingga handler, crypto, persistensi Room, dan notifikasi dipakai ulang. Pengiriman ganda (mis. LAN + Aware) aman karena ada dedup anti-replay.

| Transport | Peran | Syarat | Catatan |
|---|---|---|---|
| **Wi-Fi Direct + LAN** (`WifiDirectManager`) | Brain + backbone + fallback. Selalu aktif | API 26+ | Discovery via UDP, transfer via TCP length-prefixed; pemilihan Group Owner battery-aware (`GoIntentCalculator`) |
| **Wi-Fi Aware / NAN** (`WifiAwareManager` + `MeshRouter` + `MeshSocketManager`) | **Primary** multi-hop any-to-any | `FEATURE_WIFI_AWARE` (HW) | PSK per-pasangan diturunkan dari `peerId`; data path IPv6 + socket TCP |
| **Google Nearby Connections** (`NearbyTransport`) | **Primary overlay** di HP tanpa NAN | Google Play Services | `play-services-nearby:19.3.0`; auto medium upgrade BT→Wi-Fi; di-skip senyap pada perangkat de-Googled |
| **LAN socket** (`MeshSocketManager`) | Jalur unicast/broadcast atas Aware/Direct | — | Framing length-prefixed; `isDuplicate()` dedup |

**Logika seleksi** (`MeshManager.init`): Wi-Fi Aware diaktifkan bila HW mendukung; Nearby diaktifkan bila Play Services tersedia; Wi-Fi Direct + LAN selalu hidup sebagai brain/fallback. Setiap transport dipasang sebagai *overlay sink* (`addOverlayBroadcastSink` / `addOverlayUnicastSink`) sehingga relay, handshake, dan gossip melintas di semua jalur aktif.

**Handshake**: saat tetangga baru tersambung, perangkat mengirim `HANDSHAKE` berisi `peerId`, nama, role, public key X25519, dan signing key Ed25519 — sehingga lawan menyimpan kunci kita untuk verifikasi & enkripsi.

---

## 6. Routing, Relay & DTN

### 6.1 MeshRouter (ala BATMAN/OLSR)
- **Routing table**: `destinationPeerId → nextHopPeerId`, diperbarui dari setiap pesan masuk dan dari isi *heartbeat*.
- **Heartbeat/Originator**: tiap **10 dtk** broadcast `ROUTING_HEARTBEAT` berisi daftar peer yang dapat dijangkau; penerima belajar "jika X bisa capai Y, saya bisa capai Y lewat X".
- **Route timeout**: rute kedaluwarsa setelah **35 dtk** tanpa pembaruan (`cleanupStaleRoutes`).
- **Forwarding**: TTL di-*decrement* tiap hop; jika `ttl ≤ 1` pesan di-drop. Jika tidak ada rute ke tujuan, dilakukan **managed flood** (broadcast TTL-bounded, keputusan D16) alih-alih membuang pesan — dedup mencegah loop.
- **Anti-replay router**: `seenIds` LRU (maks **2000**) mencegah pesan non-heartbeat diproses dua kali (jalur Aware tidak punya dedup dispatcher).

### 6.2 Parameter pesan
- **TTL**: TEXT = `5` hop, SOS = `10` hop.
- **Priority**: `EMERGENCY` (SOS) > `HIGH`/`NORMAL` (chat). Dipetakan ke `DropPriority` (`STATUS < NORMAL < EMERGENCY`) untuk keputusan eviksi.
- **Dedup**: persisten via `MessageDao.messageExists()` (bertahan setelah restart) + LRU in-memory.

### 6.3 Store-Carry-Forward / Outbox (DTN)
Outbox adalah tabel Room terpisah dari log chat (blocker B4). Kebijakan terpusat di `MeshPolicy`:

| Parameter | Nilai | Sumber |
|---|---|---|
| `OUTBOX_MAX_MESSAGES` | 500 | D7 |
| `OUTBOX_MAX_TOTAL_BYTES` | ≈ 2 MB | D7 |
| `MESSAGE_MAX_AGE_MS` | 24 jam (batas kasar; gate utama = hop/TTL) | D1 |
| `UNICAST_MAX_ATTEMPTS` | 4 | D4 |
| `UNICAST_RETRY_BASE_MS` | 10 dtk (backoff eksponensial) | D4 |
| `UNICAST_RETRY_MAX_MS` | 90 dtk (cap) | D4 |
| `RETRY_JITTER_MS` | 3 dtk | D4 |
| `QUEUE_PROCESSOR_TICK_MS` | 15 dtk | D10 |
| `IDLE_THRESHOLD_MS` | 60 dtk → interval ×4 | D14 |
| `DEEP_IDLE_THRESHOLD_MS` | 5 mnt → suspend active discovery | D15 |

**Alur retry & carry** (`MeshRepository.retryDueMessages`, dipanggil queue-processor tiap 15 dtk + saat kirim baru):
1. Buang unit kedaluwarsa (`deleteExpired`).
2. Untuk tiap unit jatuh tempo (`nextAttemptAt ≤ now`): kirim ulang, naikkan `attemptCount`, jadwalkan ulang dengan backoff+jitter.
3. Saat `attemptCount` melewati cap: hanya **kontak terverifikasi** yang terus di-*carry* (cadence lambat hingga `ttlExpiry`); penerima non-verified → `FAILED` (membatasi penyalahgunaan & pertumbuhan storage).
4. **Opportunistic flush** (`flushForPeer`): saat peer kembali terjangkau, langsung kirim antrian untuknya (di-debounce `PEER_FLUSH_DEBOUNCE_MS`).
5. **Kuota** (`enforceOutboxQuota`): bila melebihi cap pesan/byte, eviksi worst-first (prioritas terendah → tertua → paling banyak direplikasi) dan tandai `FAILED` agar UI jujur.

**Delivery status** (`MessageEntity.deliveryStatus`): `SENT | DELIVERED | EXPIRED | FAILED`. `DELIVERED` **hanya** di-set dari ACK end-to-end asli untuk pesan yang benar-benar kita kirim (`markUnicastDelivered` memverifikasi unit ada di outbox kita) — tidak pernah dari *overhearing*/implicit ACK (D5).

---

## 7. Kriptografi & Model Trust

Implementasi via **Lazysodium (libsodium) 5.1.0** di `CryptoManager`.

| Lapisan | Algoritma / mekanisme | Implementasi |
|---|---|---|
| Enkripsi chat langsung | **X25519 + XSalsa20-Poly1305** via `crypto_box` (`cryptoBoxEasy`/`OpenEasy`) | Payload disimpan sebagai `nonceB64:ciphertext` |
| Autentikasi pesan | **Ed25519** detached signature (`cryptoSignDetached`/`VerifyDetached`) | Ditandatangani pada payload terenkripsi (chat) atau plaintext (SOS) |
| Peer ID / fingerprint | **BLAKE2b** generic hash dari public key, diambil 16 karakter hex | Dipakai sebagai ID peer di seluruh app |
| Identitas kontak | QR berisi `peerId`, nama, role, encPub, signPub | Verifikasi tatap muka → `isVerified = true` |
| Penyimpanan lokal | Room di atas **SQLCipher** (konten DB: AES-256-CBC + HMAC) | DB `caraka_secure.db` |
| Passphrase DB | 32-byte acak, dibungkus **AES-256-GCM** dengan kunci di **Android Keystore** | Fallback ke SharedPreferences ter-obfuscate bila Keystore/TEE gagal |

> **Catatan implementasi penting**
> - **SOS tidak dienkripsi E2E** (agar semua node penerima bisa membaca) tetapi **tetap ditandatangani Ed25519** bila identitas pengirim tersedia.
> - **Kunci identitas pengguna disimpan di Android DataStore**, *belum* di Android Keystore (`IdentityManager` punya TODO migrasi). Yang dilindungi Keystore saat ini adalah *passphrase database*, bukan kunci identitas.
> - Identitas otoritas demo (BPBD/Polri/PMI) memakai **seed deterministik** (`GARUDA_MESH_AUTHORITY_<role>`) agar semua perangkat mengenali kunci otoritas yang sama — ini fitur demo, **bukan** PKI produksi.

**Prinsip trust (Zero-Trust ringkas)**: jangan percaya tanpa verifikasi (tanda tangan per pesan), verifikasi eksplisit saat tatap muka (QR), relay tidak bisa membaca chat (E2E), serta dedup + anti-replay untuk membatasi flooding.

---

## 8. Model Data

Database: **Room v3**, nama `caraka_secure.db`, dienkripsi SQLCipher. Migrasi: `1→2` (kolom state koneksi peer), `2→3` (kolom `deliveryStatus` + tabel `outbox`). `exportSchema = false`.

**Entitas:**
- `messages` (`MessageEntity`): id (UUID), type (`TEXT`/`SOS`/`SYSTEM`), sender/recipient + role, content (plaintext lokal), encryptedPayload, timestamp, ttl, priority, signature, sosCategory, lat/lng, isIncoming, isRead, flagCount, isRelayed, **deliveryStatus**.
- `peers` (`PeerEntity`): id (fingerprint), deviceName, displayName, role, publicKey (X25519), signingKey (Ed25519), isVerified, isAuthority, macAddress, lastSeen, connectionId, **status** (`ConnectionStatus`), direction, lastAttempt, rejectionCount, hopCount.
- `relayed_messages` (`RelayedMessageEntity`): cache dedup untuk mencegah loop relay.
- `outbox` (`OutboxEntity`): unit transport DTN — id, recipientId, payloadJson, state, priority, attemptCount, nextAttemptAt, createdAt, ttlExpiry, replicaCount.

**State machine koneksi peer** (`ConnectionStatus`): `DISCOVERED → PENDING_REQUEST → CONNECTED → ACTIVE_MESH`.

**Wire protocol** (`MeshProtocol`, JSON via Gson, length-prefixed): tipe `HANDSHAKE | TEXT | SOS | FLAG | ACK | CONNECTION_REQUEST/ACCEPT/REJECT | PEER_LIST | ROUTING_HEARTBEAT`. Field mencakup id, sender/recipient, content, encryptedPayload, ttl, priority, signature, sosCategory, lat/lng, flagCount, publicKey/signingKey (handshake), targetId/lanIp/seenBy (routing), dan `peers: List<PeerShare>` (gossip full-mesh awareness).

---

## 9. Lifecycle, Background & Power

### 9.1 MeshForegroundService
- `foregroundServiceType="connectedDevice"`, `START_STICKY` (restart bila dibunuh sistem), notifikasi persisten dengan aksi **Stop Mesh**.
- **Locks**: `WifiLock` (`FULL_LOW_LATENCY` di Q+, jika tidak `FULL_HIGH_PERF`) agar interface Wi-Fi tidak di-suspend; `WakeLock` partial dengan timeout **12 jam** (cukup untuk satu hari skenario darurat).
- **Queue-processor**: coroutine timer tiap `QUEUE_PROCESSOR_TICK_MS` (15 dtk) memanggil `repository.retryDueMessages()` — driver DTN periodik (D10), idempotent.

### 9.2 ConnectivityMonitor
- `NetworkCallback`-based, meng-emit `ONLINE` / `HYBRID` / `MESH_ONLY` untuk banner status di Home.

### 9.3 Duty cycling (hemat daya)
- Tanpa aktivitas mesh selama `IDLE_THRESHOLD_MS` (60 dtk) → state IDLE, interval beacon/gossip/discovery dikalikan `IDLE_INTERVAL_MULTIPLIER` (×4).
- `DEEP_IDLE_THRESHOLD_MS` (5 mnt) → suspend *active discovery/scanning* (paling boros), tetapi **receive path** (LAN listener) tetap hidup agar node tetap reachable; aktivitas masuk mengembalikan ke ACTIVE. (Power-down Wi-Fi penuh menunggu kanal presence BLE — D15, ditunda.)

---

## 10. Functional Requirements (status saat ini)

| ID | Fitur | Status | Implementasi inti |
|---|---|---|---|
| F1 | Discovery & koneksi P2P lokal | ✅ | Wi-Fi Direct/LAN + Aware + Nearby; state machine peer |
| F2 | Chat teks (direct & broadcast) | ✅ | `sendDirectMessage` (E2E) / broadcast; persist Room |
| F3 | Relay multi-hop berbasis TTL | ✅ | `MeshRouter` + brain re-broadcast; TTL decrement, dedup |
| F4 | Enkripsi E2E + tanda tangan | ✅ | X25519 + XSalsa20-Poly1305; Ed25519 |
| F5 | SOS broadcast (4 kategori) | ✅ | `broadcastSos` (Medical/Fire/Security/Disaster), TTL 10, EMERGENCY |
| F6 | Visualisasi jaringan | ✅ | `NetworkScreen` force-directed Canvas, node/relay stats |
| F7 | QR identity & verifikasi | ✅ | `QrIdentityManager` + ZXing; `saveVerifiedPeer` |
| F8 | Community flagging anti-hoax | ✅ | `flagAndBroadcast`; konsensus ≥3 flag → label peringatan |
| F9 | Hybrid/connectivity mode | ✅ | `ConnectivityMonitor` → banner ONLINE/HYBRID/MESH_ONLY |
| F10 | Anti-replay & dedup | ✅ | LRU `seenIds` (2000) + drift waktu ±5 mnt + dedup persisten |
| F11 | DTN outbox: retry/carry/quota | ✅ | `MeshPolicy` + `retryDueMessages`/`enforceOutboxQuota` |
| F12 | DB terenkripsi + secure wipe | ✅ | SQLCipher + `DatabasePassphraseManager` + `secureWipe` |
| F13 | Battery-aware GO election | ✅ | `GoIntentCalculator` (battery + role + load) |
| F14 | Background mesh + power saving | ✅ | `MeshForegroundService` + duty cycling |
| F15 | ACK end-to-end & delivery status | ✅ | `markUnicastDelivered`; status SENT/DELIVERED/EXPIRED/FAILED |
| F16 | HCI & aksesibilitas | ✅ | hold-to-confirm, haptics, high-contrast, big-text, onboarding |
| F17 | i18n (ID + EN) | ✅ | 280 string per locale |

---

## 11. Non-Functional Requirements

- **Keamanan**: enkripsi E2E untuk chat, tanda tangan per pesan, DB terenkripsi at rest, secure wipe, anti-replay + dedup. Caveat keamanan didokumentasikan (§7, §15).
- **Ketahanan**: relay multi-hop, managed flood saat tanpa rute, retry berbatas + carry untuk kontak terverifikasi, kuota outbox.
- **Daya**: duty cycling IDLE/DEEP_IDLE; WakeLock/WifiLock hanya saat service aktif.
- **Kinerja**: target discovery & pembentukan mesh dalam orde detik di 4 perangkat uji; pengiriman ganda aman karena dedup.
- **Aksesibilitas**: WCAG-oriented — high-contrast mode, big-text (+25%), semantics (role/contentDescription/stateDescription), haptic, hold-to-confirm (Nielsen #5 error prevention).
- **Lokalisasi**: Bahasa Indonesia (default) + English, 280 string per locale (`values/`, `values-en/`).
- **Kompatibilitas**: `minSdk 26` (Android 8.0) — degradasi anggun bila Wi-Fi Aware/Play Services tidak ada.

---

## 12. UI/UX & Design System

**Estetika saat ini: light enterprise "super app"** (migrasi dari tema dark navy lama).

- **Palet** (`ui/theme/Color.kt`): canvas terang `#F5F7FA`, surface putih, brand **Telegram Blue `#229ED9`**, semantik success `#168A4B` / warning `#D98200` / danger `#D93025`. Alias kompatibilitas dipertahankan selama migrasi layar ke role M3 semantik.
- **Tipografi** (`ui/theme/Type.kt`): **Manrope** (display/judul/metrik), **Inter** (body/chat), **JetBrains Mono** (peer ID/koordinat/hash). Bundled sebagai font lokal (`res/font/`) — bukan lagi Rajdhani/Downloadable Fonts. Token semantik di `CarakaTextStyles`.
- **Token**: `Dimens.kt`, `Shape.kt`, `StatusColors.kt`.

**Navigasi** (`MainActivity`, Navigation Compose, start = Home, bottom nav + badge): **10 layar** —
`Home` (status, SOS, statistik, shortcut), `Messages`, `Chat` (per-peer, long-press flag, indikator "✓✓ Mesh"), `Network` (graph force-directed), `Sos` (4 kategori + hold-to-confirm), `Settings`, `Help` (FAQ + replay tour), `QrIdentity` (tampil + scan), `Alerts`, `ProfileSetup` (onboarding identitas/role).

**HCI**: hold-to-confirm SOS 2 dtk (arc sweep), haptic semantic (tick/light/heavy/SOS-waveform), onboarding tour 5 langkah replay-able, snackbar feedback bus, tooltip, badge unread/SOS.

---

## 13. Permissions (AndroidManifest)

- **Wi-Fi/Network**: `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`, `NEARBY_WIFI_DEVICES` (API 33+, `neverForLocation`).
- **Location**: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (syarat discovery Wi-Fi Direct di Android 8+).
- **Bluetooth** (Nearby Connections): `BLUETOOTH`/`BLUETOOTH_ADMIN` (≤API 30), `BLUETOOTH_ADVERTISE`/`CONNECT`/`SCAN` (API 31+).
- **Background**: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (kritis untuk HiOS/MIUI/XOS).
- **Lainnya**: `VIBRATE`, `POST_NOTIFICATIONS`, `CAMERA` (scan QR).
- **Features (optional)**: `android.hardware.wifi.aware` (`required=false`), `android.hardware.camera` (`required=false`).

---

## 14. Tech Stack & Versi

| Area | Teknologi | Versi |
|---|---|---|
| Bahasa | Kotlin | 2.0.21 |
| Build | Android Gradle Plugin / Gradle | 8.13.2 |
| SDK | minSdk **26** · target/compile **36** | — |
| UI | Jetpack Compose (BOM) + Material 3 + Navigation Compose | BOM 2024.09.00 · nav 2.8.4 |
| State | ViewModel · Coroutines · StateFlow | coroutines 1.9.0 |
| DB | Room + SQLCipher | 2.6.1 + 4.5.4 |
| Crypto | Lazysodium-Android (libsodium) | 5.1.0 |
| Identity/QR | ZXing (core 3.5.3 + embedded) | 4.3.0 |
| Transport | Google Nearby Connections | 19.3.0 |
| Serialisasi | Gson + kotlinx.serialization | 2.11.0 · 1.7.3 |
| Prefs | DataStore Preferences | 1.1.1 |
| Build tools | KSP | 2.0.21-1.0.27 |
| DI | **Manual** (`CarakaApp`) — bukan Hilt/Dagger | — |

Kode Kotlin: ~13.5k baris (`app/app/src/main/java`).

---

## 15. Keterbatasan & Caveat yang Diketahui

1. **Kunci identitas di DataStore**, belum di Android Keystore (`IdentityManager` TODO). Hanya passphrase DB yang dibungkus Keystore.
2. **Identitas otoritas demo deterministik** (seed `GARUDA_MESH_AUTHORITY_*`) — tidak aman untuk produksi; perlu PKI/atestasi nyata.
3. **SOS tidak terenkripsi** (by design, dapat dibaca semua node) — hanya ditandatangani.
4. **Carry untuk stranger ditunda** (D8): pesan transit tanpa rute hanya di-managed-flood, tidak disimpan; carry persisten dibatasi ke kontak terverifikasi.
5. **Power-down Wi-Fi penuh ditunda** (D15) hingga ada kanal presence BLE; saat ini hanya suspend active scanning.
6. **Nearby butuh Google Play Services**; perangkat de-Googled jatuh ke Wi-Fi Direct + LAN saja.
7. **Pengujian multi-device** sebagian belum diverifikasi pada 3+ perangkat fisik secara menyeluruh.
8. **Lisensi belum dideklarasikan** (tidak ada file `LICENSE`).

---

## 16. Roadmap / Open Items

- [ ] Migrasi kunci identitas ke Android Keystore.
- [ ] Presence channel BLE → deep-idle Wi-Fi power-down penuh (D15).
- [ ] Carry persisten terbatas untuk transit non-kontak dengan kontrol abuse (D8).
- [ ] PKI/atestasi otoritas yang sesungguhnya (gantikan seed demo).
- [ ] QA TalkBack & uji-pengguna think-aloud (5 partisipan).
- [ ] Palet color-blind (deuteranopia/protanopia), voice input SOS, locale tambahan (Jawa/Sunda).
- [ ] Verifikasi end-to-end menyeluruh di ≥3 perangkat fisik + tambah unit/instrumented test.
- [ ] Tambahkan file `LICENSE`.

---

## 17. Build & Verifikasi

```powershell
git clone https://github.com/Fatihmaull/cakra-mesh.git
cd cakra-mesh/app
.\gradlew.bat assembleDebug
# APK: app/app/build/outputs/apk/debug/app-debug.apk
```

**Uji dua perangkat**: instal di ≥2 ponsel fisik → buat identitas → beri izin nearby/lokasi/Bluetooth/notifikasi → buka **Network** dan tunggu node muncul → verifikasi via QR → kirim chat/SOS dan amati relay count.

**Dokumentasi pendukung**: [`docs/architecture/`](docs/architecture/) (baseline, gap analysis, migration review — referensi keputusan D1–D16), [`docs/implementation/`](docs/implementation/), [`docs/TECHNICAL_WRITEUP.md`](docs/TECHNICAL_WRITEUP.md), [`docs/TEST_CHECKLIST.md`](docs/TEST_CHECKLIST.md), [`docs/HCI_EVALUATION.md`](docs/HCI_EVALUATION.md).
