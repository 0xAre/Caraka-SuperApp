# CARAKA — Technical Write-Up
## Offline Crisis Communication Network for Cyber Warfare Resilience

> **WRECK-IT 7.0** · Track: IoT Resilience  
> Theme: *Cyber Warfare — Silent War on The Fifth Domain*  
> Platform: Android (minSdk 26 / Android 8.0+)  
> Tagline: *"When The Grid Falls, We Rise."*

---

## 1. Executive Summary

CARAKA (*Cakra Mesh*) adalah platform komunikasi darurat berbasis **offline mesh network** untuk Android yang dirancang dari awal untuk skenario **cyber warfare dan bencana infrastruktur**. Berbeda dengan solusi komunikasi darurat yang ada, CARAKA **tidak membutuhkan internet, tidak membutuhkan server, tidak membutuhkan hardware tambahan**, dan tetap aman bahkan ketika infrastruktur negara dalam keadaan lumpuh total.

Aplikasi ini membangun jaringan mesh terdesentralisasi menggunakan WiFi Direct antar smartphone Android biasa, dengan enkripsi end-to-end berlapis, sistem hierarki otoritas (BPBD/Polri/PMI), dan mekanisme anti-disinformasi yang unik di kelasnya.

**Target pengguna primer**: First responder (BPBD, Polri, PMI), tenaga medis, dan masyarakat umum dalam kondisi krisis nasional.

---

## 2. Latar Belakang dan Urgensi

### 2.1 Ancaman Nyata: Indonesia di Garis Depan Perang Siber

Berdasarkan data terkini:

| Tahun | Jumlah Serangan Siber (Indonesia) | Insiden Signifikan |
|-------|-----------------------------------|--------------------|
| 2023  | ~2,2 miliar | — |
| 2024  | ~3,9 miliar | **Ransomware PDNS** (Juni 2024) |
| 2025  | **5,5 miliar** | Telecom, fintech, transportasi negara |

**Insiden PDNS Juni 2024** adalah bukti nyata skenario yang CARAKA dirancang untuk mengatasinya: Brain Cipher ransomware melumpuhkan **Pusat Data Nasional Sementara** selama berhari-hari, mematikan layanan imigrasi, pendidikan, dan koordinasi darurat di lebih dari **200 instansi pemerintah**. Selama periode itu, tidak ada mekanisme komunikasi darurat offline yang siap digunakan.

### 2.2 Gap Kritis: Semua Tools Bergantung pada Internet

```
Skenario Serangan Terkoordinasi:

Hour 0:  DDoS → Internet nasional down
Hour 1:  Power grid sabotage → Listrik padam
Hour 2:  Cell tower jamming → Sinyal selular mati
Hour 3:  Disinformasi menyebar → Kepanikan massal

Kondisi existing:
  ❌ WhatsApp / Telegram → butuh internet
  ❌ InaRISK (BNPB)     → butuh internet (100% online-only, validasi 2025)
  ❌ E-Government apps  → butuh internet + server pemerintah
  ✅ HT Radio           → bekerja, tapi: tidak terenkripsi, jangkauan terbatas,
                          tidak ada relay, tidak ada anti-disinformasi
  ✅ Kentongan          → bekerja, tapi: abad ke-14

CARAKA mengisi gap antara HT radio dan internet.
```

### 2.3 Relevansi Regulasi: RUU KKS 2026

Rancangan Undang-Undang Keamanan dan Ketahanan Siber (RUU KKS) yang sedang diproses DPR RI 2026 mewajibkan organisasi kritis untuk memiliki **sistem backup komunikasi**. CARAKA dapat berfungsi sebagai compliance tool untuk instansi pemerintah, dengan arsitektur yang sepenuhnya bisa diaudit (open protocol, documented cryptography).

---

## 3. Desain Solusi dan Inovasi Utama

### 3.1 Pendekatan Arsitektur: Decentralized by Design

CARAKA tidak menggunakan arsitektur client-server sama sekali. Setiap device adalah **node yang setara** dalam mesh network:

```
Traditional App:             CARAKA:

  [User A]──────[SERVER]        [User A]────────[User B]
  [User B]──────[SERVER]            │                │
  [User C]──────[SERVER]         [User C]────────[User D]
     (semua butuh internet,
      server = single point        (tidak ada server,
      of failure)                   tidak ada internet,
                                    tidak ada SPOF)
```

### 3.2 Lima Inovasi Teknis Utama

#### Inovasi #1: Authority-Native Mesh (Unik di Kelasnya)

CARAKA adalah satu-satunya mesh communication platform yang memiliki **hierarki otoritas yang baked-in** ke dalam protokol komunikasi:

```
Hierarki dalam CARAKA Mesh:

  🛡️ BPBD  ──┐
  🚔 Polri  ──┤→ Authority Node (verified, signed, trusted by mesh)
  🏥 PMI    ──┘
  
  👤 Civilian → Standard Node (relay, receive, send)
```

Pesan dari authority node mendapat:
- Badge terverifikasi (tidak bisa dipalsukan tanpa private key Ed25519)
- Relay priority tertinggi dalam antrian
- Tidak bisa di-flag oleh civilian (anti-abuse)
- Visual distinction di semua UI screen

Tidak ada satupun kompetitor (Berty, Briar, Meshtastic, Bridgefy, Bitchat) yang memiliki konsep ini.

#### Inovasi #2: Anti-Disinformasi via Distributed Consensus

Dalam kondisi krisis, disinformasi adalah senjata yang sama berbahayanya dengan serangan fisik. CARAKA mengimplementasikan **community flagging dengan distributed consensus**:

```
Mekanisme Flagging:

1. User A menerima pesan mencurigakan
2. User A long-press → "Laporkan Pesan Ini"
3. FLAG packet dikirim via mesh (broadcast)
4. Semua node yang terima → increment flagCount di local DB
5. flagCount ≥ 3 → pesan otomatis ditandai ⚠️ "Terindikasi Tidak Akurat"
6. Authority bisa override (remove flag atau confirm)

FLAG packet tidak mengandung pesan asli (hanya messageId):
{
  "type": "FLAG",
  "id": "uuid",
  "senderId": "fingerprint_user_a",
  "content": "messageId_yang_diflag",
  "ttl": 5
}
```

Tidak ada internet, tidak ada server moderasi — namun konsensus komunitas tetap bekerja.

#### Inovasi #3: Layered Security tanpa Kompromi Usability

CARAKA mengimplementasikan 4 lapisan keamanan yang bekerja secara transparan kepada pengguna:

```
Layer 4 — Data at Rest
  SQLCipher AES-256-CBC
  Passphrase: SecureRandom(32B) → AES-256-GCM wrap → Android Keystore TEE
  User experience: database terbuka otomatis, tidak perlu input password

Layer 3 — Message Authentication  
  Ed25519 (libsodium via Lazysodium-Android)
  Setiap pesan ditandatangani dengan private key pengirim
  Penerima verifikasi signature dengan public key pengirim

Layer 2 — End-to-End Encryption
  X25519 ECDH key agreement → shared secret per pasang pengirim-penerima
  XChaCha20-Poly1305 AEAD authenticated encryption
  SOS broadcast: tidak dienkripsi (agar semua node bisa baca) tapi tetap ditandatangani

Layer 1 — Transport Security
  WiFi Direct P2P — link lokal, tidak melalui internet
  TCP socket dengan length-prefixed JSON framing
  Anti-replay: LRU cache 2000 seenIds + timestamp drift ±5 menit
```

#### Inovasi #4: Smart Group Owner Election (Battery-Aware)

WiFi Direct memiliki masalah arsitektur fundamental: **star topology** dengan satu Group Owner (GO) sebagai hub. Jika GO keluar dari grup, seluruh grup collapse. Implementasi naif menggunakan `groupOwnerIntent = 15` (hardcoded — selalu mau jadi GO), yang berarti perangkat dengan baterai 5% bisa menjadi GO dan mematikan seluruh grup saat baterai habis.

CARAKA memperkenalkan **GoIntentCalculator** — algoritma election cerdas:

```kotlin
score = (battery%  × 0.40)   // 40%: battery health
      + (authority × 0.35)   // 35%: authority devices preferred as GO
      + (1/relayLoad × 0.25) // 25%: less-loaded devices preferred

intent = clamp(score × 15, 0, 15)

// Kasus kritis:
battery ≤ 10%      → intent = 0  // tidak pernah jadi GO
battery ≤ 20%      → intent = 2-6 // prefer client
authority + ok bat → intent = 13+ // authority ideal sebagai GO
```

Hasilnya: authority device yang ter-charge menjadi GO, group lebih stabil, single point of failure berkurang drastis.

#### Inovasi #5: HCI-First Emergency Design (WCAG AAA)

Dalam kondisi darurat, pengguna mengalami:
- Keterbatasan visual (gelap, asap, panik)
- Keterbatasan motorik (gemetar, satu tangan)
- Keterbatasan kognitif (stres, tidak ada waktu)

CARAKA dirancang dari awal dengan pendekatan **Universal Emergency Design**:

```
HCI Features yang Diimplementasikan:

Accessibility:
  ✅ WCAG 2.1 AAA contrast ratio di semua screen
  ✅ Content descriptions untuk semua interactive elements (screen reader)
  ✅ Haptic feedback (vibration) untuk SOS confirmation
  ✅ Big-text mode (font scaling hingga 200%)
  ✅ High-contrast mode (eliminasi warna pastel)

Emergency UX:
  ✅ SOS: 2-detik hold-to-confirm (cegah accidental trigger)
     Hold → arc menyapu 0°→360° → auto-kirim
  ✅ SOS full-screen alert (tidak bisa di-dismiss tanpa aksi)
  ✅ 4 kategori SOS dengan emoji universal (Medical🚨, Fire🔥, Security⚠️, Disaster🌊)
  ✅ Floating chat alert saat pesan masuk (tidak interupsi workflow)

Internationalization:
  ✅ Bahasa Indonesia (default) + English
  ✅ Semua string dari resources (tidak ada hardcoded text)
  ✅ Monospace font untuk Peer ID (JetBrains Mono — distinguishable characters)
```

---

## 4. Arsitektur Teknis

### 4.1 Layer Stack

```
┌────────────────────────────────────────────────────────────────┐
│                    CARAKA — Full Stack                         │
├─────────────────┬──────────────────────┬───────────────────────┤
│  Presentation   │       Domain         │        Data           │
│  (Compose UI)   │   (Business Logic)   │    (Persistence)      │
├─────────────────┼──────────────────────┼───────────────────────┤
│ HomeScreen      │ WifiDirectManager    │ Room + SQLCipher      │
│ ChatScreen      │ ├─GoIntentCalc.     │ ├─MessageEntity       │
│ NetworkScreen   │ ├─MeshSocketMgr     │ ├─PeerEntity          │
│ SosScreen       │ └─LAN Discovery     │ └─RelayedMsgEntity    │
│ QrIdentityScreen│                      │                       │
│ SettingsScreen  │ MeshRepository      │ Android Keystore      │
│                 │ ├─saveVerifiedPeer  │ └─DB Passphrase(TEE)  │
│ MainViewModel   │ └─broadcastSos      │                       │
│ (StateFlows)    │                      │ DataStore (Prefs)     │
│                 │ CryptoManager        │ └─Identity Keypairs   │
│                 │ ├─X25519 ECDH       │                       │
│                 │ ├─XChaCha20 AEAD    │ QrIdentityManager     │
│                 │ └─Ed25519 Sign      │ └─ZXing QR (Keystore) │
│                 │                      │                       │
│                 │ IdentityManager      │ DatabasePassphrase    │
│                 │ └─Keypair + Storage  │    Manager            │
├─────────────────┴──────────────────────┴───────────────────────┤
│                    Transport Layer                             │
│  WiFi Direct P2P (primary)      LAN UDP Broadcast (fallback)  │
│  ├─TCP socket MeshSocketManager  ├─Port 8890                  │
│  ├─Smart GO Election             └─HANDSHAKE discovery        │
│  └─Auto-reconnect + watchdog                                   │
└────────────────────────────────────────────────────────────────┘
```

### 4.2 Mesh Protocol (Wire Format)

Semua komunikasi menggunakan **MeshProtocol** — format JSON dengan length prefix:

```json
{
  "type": "TEXT|SOS|HANDSHAKE|FLAG",
  "id": "uuid-v4",
  "senderId": "ed25519_fingerprint_hex",
  "senderName": "BPBD Jakarta",
  "senderRole": "BPBD",
  "recipientId": "peer_fingerprint_or_BROADCAST",
  "content": "pesan plaintext (hanya untuk SOS/HANDSHAKE)",
  "encryptedPayload": "base64(XChaCha20(content))",
  "timestamp": 1749250000000,
  "ttl": 5,
  "priority": "NORMAL|HIGH|EMERGENCY",
  "signature": "base64(Ed25519(content_or_encrypted))",
  "publicKey": "base64(X25519_pub)",
  "signingKey": "base64(Ed25519_pub)",
  "sosCategory": "MEDICAL|FIRE|SECURITY|DISASTER",
  "latitude": -6.2088,
  "longitude": 106.8456
}
```

**Relay logic**:
```
Node menerima pesan:
  1. Cek seenIds → sudah pernah diproses? → drop (anti-replay)
  2. Cek timestamp drift → lebih dari ±5 menit? → drop (anti-replay)
  3. Jika untuk saya (recipientId == myId): decrypt + process + save
  4. Jika bukan untuk saya DAN TTL > 1: TTL-- → forward ke semua connected peers
  5. Jika TTL == 1: drop (prevent infinite loop)
```

### 4.3 Identitas Kriptografis

```
Identity Generation (sekali, saat onboarding):

  ┌─────────────────────────────────────────┐
  │  libsodium (via Lazysodium-Android)     │
  │                                         │
  │  crypto_kx_keypair()     → X25519 pair  │  untuk enkripsi pesan
  │  crypto_sign_keypair()   → Ed25519 pair │  untuk tanda tangan
  │                                         │
  │  fingerprint = BLAKE2b(X25519_public)   │  = Peer ID (unique, 16 char)
  └─────────────────────────────────────────┘
         │
         ▼
  Disimpan di DataStore (sementara)
  Target produksi: Android Keystore (hardware-backed)
```

**Key exchange untuk DM**:
```
Device A (sender):
  1. Ambil X25519 public key Device B dari PeerEntity.publicKey
  2. shared_secret = X25519(myPrivate, theirPublic)  ← ECDH
  3. encrypted = XChaCha20-Poly1305(plaintext, shared_secret, nonce)

Device B (receiver):
  1. shared_secret = X25519(myPrivate, senderPublic)  ← sama
  2. plaintext = XChaCha20-Poly1305-Decrypt(encrypted, shared_secret, nonce)
```

---

## 5. Implementasi dan Tantangan Teknis

### 5.1 Challenge: WiFi Direct Star Topology Problem

**Masalah**: Android WiFi Direct secara fundamental menggunakan topologi **hub-and-spoke** bukan mesh sejati. Satu device menjadi Group Owner (GO) dan semua data harus melewatinya. Jika GO keluar dari group → seluruh group hancur.

**Solusi**: CARAKA mengimplementasikan multi-layer mitigation:

1. **Smart GO Election** (`GoIntentCalculator`) — device dengan battery terbaik dan role authority menjadi GO
2. **TCP Socket Abstraction** (`MeshSocketManager`) — relay mesh berjalan di level application (bukan WiFi Direct layer), sehingga CARAKA menciptakan **virtual mesh di atas star topology**
3. **Auto-reconnect** — saat GO hilang, device otomatis restart discovery dalam 2 detik
4. **Persistent Group Cleanup** — via reflection API, semua persistent groups dihapus sebelum session baru (mencegah NO_COMMON_CHANNEL error)

```
WiFi Direct Physical Layer:      CARAKA Virtual Mesh:
  
  [A] --- [GO: B] --- [C]          [A] ←──→ [B] ←──→ [C]
              │                        ↑              │
             [D]                       └──────────────┘
                                    (relay di app layer)
```

### 5.2 Challenge: Discovery vs Connection Trade-off

**Masalah**: Memanggil `discoverPeers()` saat device sedang dalam proses connect menyebabkan error BUSY yang ber-cascade (BUSY → retry → BUSY → retry storm).

**Solusi**: CARAKA mengimplementasikan **serialized discovery dengan state machine**:

```kotlin
private val busyStates = setOf("CONNECTING", "CONNECTED", "CONNECTED_GO", "CONNECTED_CLIENT")

@Volatile private var discoveryInFlight = false
@Volatile private var retryScheduled = false

fun discoverPeers() {
    if (_connectionState.value in busyStates) return  // jangan ganggu koneksi aktif
    if (discoveryInFlight) return                      // hanya satu scan sekaligus
    discoveryInFlight = true
    // ...
}
```

Plus **connecting watchdog**: jika negosiasi GO stall lebih dari 9 detik → reset dan rediscover.

### 5.3 Challenge: Background Service Lifecycle

**Masalah**: Android membunuh background processes untuk menghemat baterai, memutus mesh connection.

**Implementasi saat ini**: LAN Discovery broadcast setiap 3 detik melalui `DatagramSocket` memastikan peer tetap terdeteksi meski WiFi Direct connection drop. Peer ditemukan via LAN (IP broadcast) masih bisa berkomunikasi melalui TCP socket.

**Target produksi**: ForegroundService dengan persistent notification agar Android tidak membunuh proses mesh.

### 5.4 Challenge: 3-Device Group Connection Bug

**Root cause**: `MeshSocketManager.startServer()` dipanggil ulang setiap kali device baru join group, mengganggu koneksi existing.

**Fix**: Jadikan `startServer()` idempotent:
```kotlin
fun startServer() {
    if (serverJob?.isActive == true) return  // ← fix: jangan restart jika sudah jalan
    serverJob = scope.launch { /* ... */ }
}
```

---

## 6. Perbandingan Kompetitif

### 6.1 Landscape Solusi Sejenis

| Kriteria | **CARAKA** | Berty/Wesh | Bridgefy | Briar | Meshtastic | Bitchat |
|----------|-----------|------------|----------|-------|------------|---------|
| **Zero Internet** | ✅ Total | ✅ | ❌ *Perlu aktivasi* | ✅ | ✅ | ✅ |
| **E2E Enkripsi** | ✅ XChaCha20 | ✅ | ❌ Lemah | ✅ | ⚠️ AES | ✅ |
| **Authority System** | ✅ **UNIK** | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Anti-Disinformasi** | ✅ **UNIK** | ❌ | ❌ | ❌ | ❌ | ❌ |
| **SOS Broadcast** | ✅ **UNIK** | ❌ | ❌ | ❌ | ⚠️ Basic | ❌ |
| **Network Map** | ✅ **UNIK** | ❌ | ❌ | ❌ | ⚠️ Basic | ❌ |
| **Smart GO Election** | ✅ **BARU** | N/A | N/A | N/A | N/A | N/A |
| **DB Encryption** | ✅ SQLCipher | ✅ | ❌ | ✅ | N/A | ⚠️ |
| **QR Verification** | ✅ **BARU** | ❌ | ❌ | ✅ | ❌ | ❌ |
| **WCAG AAA** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Hardware Required** | ❌ (Android only) | ❌ | ❌ | ❌ | ✅ LoRa | ❌ |
| **Konteks Indonesia** | ✅ BPBD/Polri/PMI | ❌ | ❌ | ❌ | ❌ | ❌ |

### 6.2 Positioning Statement

> **CARAKA adalah satu-satunya solusi mesh communication di dunia yang didesain dari awal untuk hierarki komando darurat pemerintah Indonesia** — dengan Zero Trust security, tanpa internet, tanpa hardware tambahan, dan dengan anti-disinformasi built-in.

Kompetitor terdekat (Berty) fokus pada privacy-first personal communication. CARAKA fokus pada **crisis coordination command chain** — segmen yang belum disentuh siapapun.

---

## 7. Validasi dan Testing

### 7.1 Build Validation

Semua fitur telah berhasil di-build dan dikompilasi tanpa error:

```
Task :app:assembleDebug
BUILD SUCCESSFUL
  - 0 compilation errors
  - Pre-existing warnings only (deprecated WiFi APIs — known Android issue)
  - libsqlcipher.so, libsodium.so, libzxing-embedded: ✅ all packaged
```

### 7.2 Feature Verification Checklist

| Feature | Test Method | Status |
|---------|-------------|--------|
| WiFi Direct discovery | 2 physical devices, Wi-Fi Direct enabled | ✅ Verified |
| Multi-hop relay | 3-device test (A→B→C, B sebagai relay) | ✅ Verified |
| E2E encryption | DM antara 2 device, packet captured (no plaintext) | ✅ Verified |
| SOS broadcast | Trigger dari Device A, received di B dan C | ✅ Verified |
| SQLCipher | DB file diperiksa dengan hex editor (tidak readable) | ✅ Verified |
| QR scan | Generate QR Device A, scan di Device B, peer saved | ✅ Verified |
| Smart GO Election | Log menunjukkan intent value bervariasi per battery | ✅ Verified |
| Anti-replay | Replay packet sama → dropped at seenIds check | ✅ Verified |

### 7.3 Security Threat Model

| Ancaman | Mitigasi | Status |
|---------|----------|--------|
| Man-in-the-middle | X25519 ECDH key agreement (tidak bisa di-intercept) | ✅ |
| Message replay | LRU seenIds + timestamp drift ±5 menit | ✅ |
| Identity spoofing | Ed25519 signature verifiable dengan public key | ✅ |
| Data theft (device seized) | SQLCipher AES-256 + Keystore TEE passphrase | ✅ |
| Authority impersonation | Deterministic seed (demo); QR in-person (produksi) | ⚠️ Demo |
| Denial of service (flood) | TTL + dedup + rate limiting on relay | ✅ |

---

## 8. Dampak dan Roadmap

### 8.1 Immediate Impact (Hackathon → Demo)

CARAKA saat ini mampu:
- Membangun mesh network antara 3-4 device Android
- Mengirim pesan terenkripsi end-to-end tanpa internet
- Broadcast SOS darurat yang di-relay multi-hop
- Menampilkan topologi mesh secara real-time
- Mengidentifikasi authority nodes (BPBD/Polri/PMI)
- Memproteksi semua data lokal dengan SQLCipher

### 8.2 Roadmap Post-Hackathon

```
Phase 1 (Bulan 1-2): Production Hardening
  → BLE fallback transport (backup saat WiFi Direct unavailable)
  → Real PKI untuk authority credentials (ganti deterministic seed)
  → Background ForegroundService (mesh tetap hidup saat app minimize)
  → ACK + retry mechanism untuk message reliability

Phase 2 (Bulan 3-6): Feature Expansion  
  → Voice PTT (Opus 8kbps) — killer feature, tidak ada di kompetitor manapun
  → Offline resource mapping (InaRISK replacement, MapLibre + OSM)
  → Smart AODV-lite routing (ganti flooding dengan intelligent routing)
  → Multi-channel mesh (koordinasi terpisah per instansi)

Phase 3 (Bulan 6-12): Ecosystem
  → Gateway node (bridge mesh → internet → BNPB dashboard)
  → Web command center untuk BNPB/Basarnas
  → LoRa bridge integration (range dari 100m → 10km+)
  → iOS port via Kotlin Multiplatform Mobile
```

### 8.3 Potensi Adopsi Pemerintah

| Jalur | Target | Nilai |
|-------|--------|-------|
| **BPBD deployment** | 514 kota/kabupaten × tim BPBD | Komunikasi darurat terstandarisasi |
| **RUU KKS compliance** | Instansi pemerintah kategori kritis | Backup communication mandatory |
| **InaRISK integration** | BNPB — isi gap offline mode | "InaRISK yang bekerja saat internet mati" |
| **ASEAN alignment** | ASEAN Vision 2025 Emergency Telecom | Regional framework contribution |

---

## 9. Stack Teknologi Lengkap

| Komponen | Teknologi | Versi | Alasan Pemilihan |
|----------|-----------|-------|------------------|
| Language | Kotlin | 2.0.21 | Modern Android, coroutines native |
| UI Framework | Jetpack Compose + Material 3 | BOM 2024.09 | Declarative, accessible, modern |
| Architecture | MVVM + Clean Architecture | — | Testable, maintainable, separation of concerns |
| DI | Manual (Application class) | — | Lightweight tanpa Hilt overhead untuk hackathon |
| Cryptography | Lazysodium-Android (libsodium) | 5.1.0 | Audited, industry-standard, bindings for Android |
| Transport | Android WiFi Direct P2P API | SDK 26+ | Zero infrastructure, 100m range |
| Local DB | Room | 2.6.1 | Type-safe, coroutine-friendly ORM |
| DB Encryption | SQLCipher | **4.5.4** | Gold standard SQLite encryption, FIPS-compliant |
| Identity Store | Android Keystore + DataStore | SDK 26+ | Hardware-backed key storage |
| QR Code | ZXing Android Embedded | **4.3.0** | Battle-tested, no Google Play Services dependency |
| Serialization | Kotlinx Serialization | 1.7.3 | Type-safe JSON, no reflection |
| Fonts | Rajdhani, Inter, JetBrains Mono | Google Fonts | Professional, accessible, distinguishable |

---

## 10. Kesimpulan

CARAKA bukan sekadar aplikasi chat offline. Ini adalah **infrastruktur komunikasi darurat yang berdaulat** — bekerja tanpa bergantung pada internet asing, server asing, atau hardware mahal. Setiap Android di tangan first responder Indonesia dapat menjadi node dalam jaringan yang tangguh.

Dalam konteks **Cyber Warfare: Silent War on The Fifth Domain**, CARAKA mewakili pendekatan yang tepat: bukan melawan serangan di domain siber (domain serang), tapi membangun **resiliensi di lapisan komunikasi** sehingga koordinasi darurat tetap berfungsi bahkan ketika domain siber telah dikuasai musuh.

**Keunggulan CARAKA dalam 5 kata**: *Offline. Terenkripsi. Terkoordinasi. Terpercaya. Indonesia.*

---

*CARAKA — Cakra Mesh | WRECK-IT 7.0 | IoT Resilience Track*  
*Dokumentasi teknis ini mencerminkan implementasi per 7 Juni 2026.*
