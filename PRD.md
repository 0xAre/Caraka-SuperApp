# 📋 PRD — CARAKA
# Offline Crisis Communication Network

> **Version**: 2.0 | **Date**: 7 Juni 2026 | **Status**: Phase 0 Complete ✅  
> **Track**: WRECK-IT 7.0 Hackathon — **IoT Resilience** ✅  
> **Tema**: *"Cyber Warfare: Silent War on The Fifth Domain"*  
> **Deadline Submission**: 14 Juni 2026 (7 hari lagi)  
> **Finals**: 8 Juli 2026 (3 jam finalize + presentasi)  
> **Tim**: 4 anggota (2 active devs + 2 support) + agentic AI  
> **Nama Tim**: TBD  
> **Test Devices**: 4 Android (2023+, termasuk Tecno Pova 5)  

---

## 1. Executive Summary

**CARAKA** adalah aplikasi Android yang membangun **offline mesh communication network** via WiFi Direct, memungkinkan komunikasi darurat terenkripsi tanpa internet. Diposisikan sebagai **Zero Trust communication backbone untuk infrastruktur kritis saat cyber warfare**.

**Tagline**: *"When The Grid Falls, We Rise."*

---

## 2. Problem Statement

```
Hour 0: DDoS attack         → Internet down
Hour 1: Power grid sabotage → Electricity down  
Hour 2: Cell tower jamming   → Mobile network down
Hour 3: Disinformation       → Chaos & panic
```

**Gap**: 99% tools komunikasi bergantung pada internet. Tidak ada solusi saat infrastruktur itu sendiri yang diserang.  
**Solusi**: Decentralized, encrypted, offline mesh network yang tetap jalan saat semua infra lumpuh.

---

## 3. Target Users

| User | Kebutuhan |
|---|---|
| **First Responders** (BPBD, Polri, PMI) | Koordinasi darurat tanpa internet |
| **Tenaga Medis** | Broadcast SOS & terima permintaan bantuan |
| **Masyarakat Umum** | Kirim pesan darurat, terima info terverifikasi |
| **Pemerintah Daerah** | Distribusi informasi resmi saat krisis |

---

## 4. Scope & Feature Matrix

### ✅ IN SCOPE — IMPLEMENTED (Status: 7 Juni 2026)

| # | Feature | Status | Deskripsi |
|---|---|---|---|
| **F1** | WiFi Direct P2P Connection | ✅ **LIVE** | Device-to-device tanpa internet; auto-discovery ~100m; 5+ concurrent peers |
| **F2** | Text Messaging | ✅ **LIVE** | Kirim/terima pesan; SQLCipher encrypted storage; offline queue |
| **F3** | Multi-hop Relay | ✅ **LIVE** | TTL-based forwarding; message dedup; max 5 hops; relay count tracking |
| **F4** | E2E Encryption | ✅ **LIVE** | X25519 (key exchange) + Ed25519 (signatures) + XChaCha20-Poly1305 (symmetric) |
| **F5** | SOS Broadcast | ✅ **LIVE** | 4 kategori (Medical/Fire/Security/Disaster); hold-to-confirm 2s; GPS attach |
| **F6** | Network Visualization | ✅ **LIVE** | Force-directed Canvas graph; real-time topology; node stats + legend |
| **F7** | Verified Identity (QR) | ✅ **LIVE** | QR code identity scan (ZXing); in-person verification; authority badges |
| **F8** | Message Flagging | ✅ **LIVE** | Long-press flag; ≥3 flags = warning label; consensus anti-hoax |
| **F9** | Hybrid Mode | ✅ **LIVE** | ConnectivityMonitor; 🟢Online/🟡Hybrid/🔴MeshOnly status banner |
| **F10** | Anti-replay Protection | ✅ **LIVE** | LRU seenIds (2000) + ±5min timestamp drift check; flood prevention |
| **F11** | Database Encryption | ✅ **LIVE** | SQLCipher with AES-256-GCM; passphrase in Android Keystore (TEE) |
| **F12** | Smart GO Election | ✅ **LIVE** | Battery-aware group owner selection; role + load based intent calculation |
| **F13** | Attack Simulator | ✅ **LIVE** | Demo toggle for "grid down" scenario; shows mesh resilience |
| **F14** | HCI/Accessibility | ✅ **LIVE** | Hold-to-confirm error prevention; High-Contrast mode; Big-Text (+25%); i18n (ID+EN) |

### ❌ OUT OF SCOPE

iOS, LoRa/Satellite, voice messages, offline maps, blockchain verification, deepfake detection, video streaming.

---

## 5. Fitur Baru Phase 0 (Implementasi Terbaru — 7 Juni 2026)

### F10: Anti-replay Protection
- **LRU Cache**: `seenIds` LinkedHashSet (max 2000 entries) melacak message ID yang sudah diproses
- **Timestamp Drift Check**: Menerima pesan jika timestamp ±5 menit dari waktu lokal
- **Purpose**: Mencegah replay attacks dan message flooding di mesh
- **Implementation**: `MeshSocketManager.kt` — dedup sebelum relay

### F11: Database Encryption (SQLCipher)
- **Encryption**: AES-256-GCM via SQLCipher `SupportFactory`
- **Key Management**: Passphrase 32-byte random, disimpan di Android Keystore (TEE hardware)
- **New Class**: `DatabasePassphraseManager.kt` — generate/load/wipe passphrase
- **Secure Wipe**: `CarakaDatabase.secureWipe()` untuk panic/reset
- **Compliance**: NIST SP 800-175B, mencegah offline database access jika device dicuri

### F12: Smart GO Election (Battery-Aware)
- **Dynamic Intent**: `groupOwnerIntent` (0–15) calculated berdasarkan:
  - Battery level (kritis ≤10% → intent=0, normal ≥50% → intent=10-13)
  - Role (authority → intent preference tinggi; civilian → lower)
  - Current relay load (busy relay → defer GO selection)
- **Implementation**: `GoIntentCalculator.kt` — menggantikan hardcoded intent value
- **Benefit**: Memperpanjang battery life; authority devices lebih likely menjadi hub
- **StateFlow**: Expose battery level ke Settings UI untuk transparansi user

### F13: Attack Simulator Button
- **Demo Feature**: Toggle switch di HomeScreen → simulasi "grid down" scenario
- **Visual Feedback**: Mesh network tetap aktif saat simulasi (no internet connectivity)
- **Use Case**: Demo untuk menunjukkan resilience sistem saat semua infra lumpuh
- **Judging Value**: Mendemonstrasikan tema hackathon ("Cyber Warfare")

### F14: HCI & Accessibility Improvements
- **Hold-to-Confirm SOS** (2 detik): Arc sweep 0→360° dari white, label "TAHAN…" (Nielsen Error Prevention #5)
- **Haptic Feedback**: Semantic feedback (tick/light/heavy) pada interaksi kritis
- **High-Contrast Mode**: WCAG AAA compliance untuk user dengan low vision
- **Big-Text Mode**: Font size +25% untuk elderly/accessibility
- **i18n Support**: Bahasa Indonesia (default) + English; 175 string per locale
- **Accessibility Semantics**: Role/contentDescription/stateDescription di semua custom controls
- **Onboarding Tour**: 5-step guided tour (replay dari Help screen)

---

## 5A. Functional Requirements Detail (Original)

### F1: WiFi Direct P2P (P0)
- Auto-discovery device dalam radius ~100m
- Establish connection tanpa internet
- Auto-reconnect jika putus
- Support minimal 5 concurrent peers
- Status: connected/searching/disconnected

### F2: Text Messaging (P0)
- Kirim ke specific peer atau broadcast
- Local encrypted storage (SQLCipher)
- Timestamp + sender identity per pesan
- Offline queue, max 4KB per message

### F3: Multi-hop Relay (P0)
- Relay via 1+ intermediate nodes
- Message ID dedup (no duplicate relay)
- TTL default: 5 hops
- Priority: emergency > normal
- Simplified flooding + gossip propagation

### F4: E2E Encryption (P0)
- X25519 keypair untuk key exchange
- Ed25519 untuk digital signatures
- XChaCha20-Poly1305 symmetric encryption
- Keys di Android Keystore
- Library: Lazysodium-Android

### F5: SOS Broadcast (P0)
- 4 kategori: 🚨 Medical, 🔥 Fire, ⚠️ Security, 🌊 Disaster
- Short message (max 280 chars)
- Auto-attach last known GPS
- Highest relay priority, visual + audio alert
- SOS = signed but NOT encrypted (semua harus bisa baca)
- Studi kasus utama: **skenario perang/evakuasi korban**, komunikasi antar BPBD, Polri, PMI saat internet mati

### F6: Network Visualization (P1)
- Force-directed graph (Compose Canvas)
- Color-coded nodes (active/SOS/authority)
- Real-time update saat nodes join/leave
- Stats: total nodes, estimated coverage

### F7: Verified Identity (P1)
- 3 pre-registered authority identities (hardcoded demo):
  - 🛡️ **BPBD** — Koordinasi evakuasi & shelter
  - 🚔 **Polri** — Keamanan & area restriction
  - 🏥 **PMI** — Medis & distribusi logistik
- ✓ Verified badge pada authority messages
- QR code untuk in-person verification
- Non-verified = "Civilian"

### F8: Message Flagging (P1)
- Flag pesan sebagai "suspicious"
- 3+ flags = warning label
- Verified authority messages tidak bisa di-flag

### F9: Hybrid Mode (P1)
- Auto-detect internet connectivity
- Status: 🟢 Online, 🟡 Hybrid, 🔴 Mesh Only
- Basic sync saat internet kembali

---

## 6. Architecture

```
┌──────────────────────────────────────────────┐
│               CARAKA                     │
├──────────────────────────────────────────────┤
│  Presentation    │  Domain        │  Data    │
│  ─────────────   │  ──────────    │  ─────── │
│  Compose UI      │  MessageSvc    │  Room DB │
│  ViewModels      │  MeshRouter    │  Keystore│
│  Navigation      │  CryptoMgr     │  Prefs   │
│  Canvas (Graph)  │  IdentityMgr   │  Sockets │
├──────────────────────────────────────────────┤
│  Network Layer: WiFi P2P Manager + Sockets   │
└──────────────────────────────────────────────┘
```

---

## 7. Tech Stack

| Component | Technology | Status |
|---|---|---|
| Language | Kotlin | ✅ |
| UI Framework | Jetpack Compose + Material 3 | ✅ |
| Architecture | MVVM + Clean Architecture | ✅ |
| Dependency Injection | Hilt | ✅ |
| Networking | Android WiFi P2P API (P2P Manager) | ✅ |
| **Encryption Core** | **Lazysodium-Android (libsodium)** | ✅ |
| - Key Exchange | X25519 (ECDH) | ✅ |
| - Signing | Ed25519 (EdDSA) | ✅ |
| - Symmetric | XChaCha20-Poly1305 (AEAD) | ✅ |
| Key Storage | Android Keystore (TEE hardware) | ✅ |
| **Database** | **Room + SQLCipher (AES-256-GCM)** | ✅ **NEW** |
| QR Code Generation/Scan | ZXing Library | ✅ **NEW** |
| Async Runtime | Kotlin Coroutines + StateFlow | ✅ |
| Graphics | Canvas (Force-directed graph physics) | ✅ |
| Localization | i18n (ID + EN, 175+ strings) | ✅ **NEW** |
| Min SDK | **API 26 (Android 8.0)** | ✅ |
| Target SDK | API 34 (Android 14) | ✅ |

---

## 8. Data Models

### Message
```kotlin
data class Message(
    val id: String,            // UUID for dedup
    val type: MessageType,     // TEXT, SOS, SYSTEM, FLAG
    val senderId: String,      // Public key fingerprint
    val senderName: String,
    val recipientId: String?,  // null = broadcast
    val content: String,       // Encrypted payload
    val timestamp: Long,
    val location: Location?,   // For SOS
    val ttl: Int,              // Default: 5
    val priority: Priority,    // EMERGENCY, HIGH, NORMAL
    val signature: ByteArray,  // Ed25519
    val flagCount: Int,
    val sosCategory: SosCategory?
)
```

### Peer
```kotlin
data class Peer(
    val id: String,
    val displayName: String,
    val publicKey: ByteArray,
    val signingKey: ByteArray,
    val isVerified: Boolean,
    val role: PeerRole,        // CIVILIAN, AUTHORITY
    val lastSeen: Long,
    val isDirectPeer: Boolean
)
```

---

## 9. Security Design (Zero Trust)

| Principle | Implementation |
|---|---|
| Never Trust, Always Verify | Ed25519 signature on every message |
| Least Privilege | Civilian cannot impersonate authority |
| Assume Breach | E2E encryption, relay nodes can't read |
| Verify Explicitly | QR code face-to-face verification |

### Crypto Flow
```
Sending Direct Message:
  shared_secret = X25519(my_private, their_public)
  msg_key = HKDF(shared_secret, message_id)
  ciphertext = XChaCha20Poly1305(msg_key, plaintext)
  signature = Ed25519_Sign(my_signing_key, ciphertext)

Sending Broadcast/SOS:
  plaintext (readable by all) + Ed25519 signature (authenticity)
```

---

## 10. UI Screens

```
Onboarding → Home Dashboard → [Messages | Network | SOS | Settings]
```

| Screen | Key Elements |
|---|---|
| **Onboarding** | Set name, generate keys, choose role |
| **Home** | Status banner, 🆘 SOS button, alerts feed, stats |
| **Messages** | Peer list, chat screen, flag indicators |
| **Network Map** | Force-directed graph, node details, legend |
| **SOS** | Category picker, message input, location, broadcast |
| **Settings** | Profile, QR code, security info |

**Design**: Dark mode, military/tactical aesthetic, navy `#0A1628` + amber `#F59E0B`.

---

## 11. Timeline & Actual Progress

| Sprint | Target Dates | Goals | Status |
|---|---|---|---|
| **Sprint 1** | 12-18 Mei | Project setup, WiFi Direct PoC, basic UI, key generation | ✅ Complete |
| **Sprint 2** | 19-25 Mei | Multi-hop relay, E2E encryption, SOS system | ✅ Complete |
| **Sprint 3** | 26 Mei-1 Jun | Network viz, PKI, flagging, hybrid mode, proposal draft | ✅ Complete |
| **Sprint 4** | 2-7 Jun | **Phase 0**: SQLCipher, QR Identity, Smart GO, Anti-replay, Accessibility | ✅ **Complete (7 Juni)** |
| **Sprint 5** | 8-14 Jun | GitHub cleanup, README, demo video, proposal finalize, submit | 🔄 **IN PROGRESS** |
| **Finals Prep** | 8 Juli | 3 jam: final polish, rehearsal, presentation | ⏳ Pending |

---

## 11A. Key Differentiators (Competitive Advantage)

Mengapa CARAKA menang di WRECK-IT 7.0 IoT Resilience track:

| Aspek | CARAKA | Kompetitor Standar |
|---|---|---|
| **Threat Model** | Cyber warfare (DDoS + infrastructure sabotage) | Generic offline comm |
| **Encryption** | E2E + signature per-pesan + anti-replay | Single cipher, no signing |
| **Identity Trust** | QR code in-person verification + authority verification | No PKI |
| **Anti-hoax** | Community consensus flagging (≥3 flags) | No hoax protection |
| **Security at Rest** | SQLCipher AES-256-GCM in TEE | Plain SQLite |
| **Resilience** | Multi-hop relay + smart battery-aware GO election | Single-hop only |
| **Accessibility** | WCAG AAA (high-contrast, big-text, hold-to-confirm) | Tidak ada a11y |
| **Localization** | i18n Indonesian + English, cultural context | English-only |
| **Demo Drama** | Attack Simulator toggle (grid down scenario) | Static screenshot |

---

## 12. Submission Deliverables (14 Juni)

| Deliverable | Format |
|---|---|
| Proposal | PDF, 10-15 pages |
| Demo Video | MP4, 3-5 minutes |
| GitHub Repository | Public, clean code + docs |
| APK | Debug/release build |

---

## 13. Demo Scenario (Finals — 8 Juli)

**Skenario**: Konflik bersenjata / serangan siber terkoordinasi → evakuasi korban sipil

**Devices**: 4 HP tim → role-play sebagai BPBD, Polri, PMI, dan Warga

```
Scene 1: "Serangan Terkoordinasi" (2 min)
  → Narasi: DDoS + infrastructure sabotage, semua internet mati
  → Show: 4 devices offline, no connectivity

Scene 2: "CARAKA Aktif" (2 min)
  → Buka app di semua device, mesh auto-connects
  → Show: network visualization — 4 nodes terhubung

Scene 3: "Evakuasi Darurat" (3 min)
  → Device 1 (Warga): SOS Medical — "Korban luka di lokasi X"
  → Device 2-3 relay otomatis
  → Device 4 (PMI): Terima SOS, respond "Tim medis menuju lokasi"

Scene 4: "Koordinasi Otoritas" (3 min)
  → BPBD broadcast: "Shelter evakuasi di Gedung Y" [✓ Verified]
  → Polri broadcast: "Hindari area Z, aktif konflik" [✓ Verified]
  → Fake message dari unknown: "Semua gedung runtuh!" → community flags → ⚠️ Warning

Scene 5: "Pemulihan" (1 min)
  → Internet kembali → app auto-sync
  → Dashboard: "X pesan terkirim, Y SOS ditangani"
```

---

## 14. Risks

| Risk | Mitigation |
|---|---|
| WiFi Direct unstable | Test early di 4 device tim (termasuk Tecno Pova 5); BLE fallback |
| Multi-hop packet loss | Retry + ACK mechanism |
| Scope creep | Strict P0 first, NO P2 |
| Hanya 2 active devs | Leverage agentic AI untuk boilerplate, docs, testing |
| Weak proposal | Start writing Week 3, AI-assisted |

---

## 15. Resolved Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Test devices | ✅ 4 Android devices tersedia (4 anggota tim, termasuk Tecno Pova 5) |
| 2 | Authority accounts | ✅ 3 identitas: BPBD, Polri, PMI — skenario perang/evakuasi |
| 3 | Nama tim | ⏳ TBD — belum ditentukan |
| 4 | Track | ✅ **IoT Resilience** confirmed |
| 5 | Min SDK | ✅ **API 26 (Android 8.0)** — semua device 2023+, aman |

---

## 16. Team Structure

| Role | Person | Responsibility |
|---|---|---|
| **Dev 1 (Networking Lead)** | Anggota 1 | WiFi Direct, mesh protocol, encryption, relay |
| **Dev 2 (Frontend Lead)** | Anggota 2 | UI/UX, SOS system, visualization, integration |
| **Support 1** | Anggota 3 | Proposal writing, video editing, testing |
| **Support 2** | Anggota 4 | Presentation, demo rehearsal, documentation |
| **Agentic AI** | — | Boilerplate, docs, proposal draft, UI components |

---

## 17. Implementation Summary (Status: 7 Juni 2026)

### ✅ What's Complete & Live

**Core Network:**
- ✅ WiFi Direct P2P (auto-discovery, 5+ concurrent peers)
- ✅ Multi-hop relay with TTL (max 5 hops, message dedup)
- ✅ Anti-replay: LRU cache + timestamp drift check (±5min)

**Security:**
- ✅ E2E encryption: X25519 + Ed25519 + XChaCha20-Poly1305
- ✅ Database encryption: SQLCipher with AES-256-GCM in TEE
- ✅ QR identity: ZXing-based peer verification

**Features:**
- ✅ Text messaging (peer + broadcast)
- ✅ SOS with 4 categories (Medical/Fire/Security/Disaster)
- ✅ Message flagging (hoax consensus ≥3 flags)
- ✅ Hybrid mode (🟢Online / 🟡Hybrid / 🔴MeshOnly)
- ✅ Network visualization (force-directed Canvas graph)
- ✅ Attack Simulator (demo grid-down scenario)

**UX/Accessibility:**
- ✅ Hold-to-confirm SOS (2 second arc sweep)
- ✅ High-Contrast mode (WCAG AAA)
- ✅ Big-Text mode (+25% font size)
- ✅ i18n: Indonesian + English
- ✅ Onboarding tour (5-step replay)

**Build Quality:**
- ✅ Build: 0 errors, clean warnings (1 pre-existing deprecated API)
- ✅ App running on Tecno Pova 5 (device connected)

### ⏳ Next Steps (Before 14 Juni Submission)

**Critical (Wajib):**
1. Update proposal PDF dengan fitur-fitur baru (Phase 0 features)
2. Finalize tim name (replace `[NAMA TIM]` everywhere)
3. Record demo video (4 menit, WiFi Direct mesh active)
4. Submit ke portal WRECK-IT 7.0 sebelum deadline

**High Priority (Rekomendasi):**
1. GitHub cleanup + push all code + README update
2. Test end-to-end di 3+ devices
3. Record backup demo video (contingency)

**Optional (Nambah Poin):**
1. Write technical whitepaper (Best Write-up prize Rp 1jt)
2. Create architecture diagram di README
3. Extended security analysis document

### 🎯 Hackathon Strategy

**For Judges:**
- **Innovation**: E2E encryption + anti-replay + SQLCipher = comprehensive security
- **Resilience**: Multi-hop relay + smart GO election = network stability
- **Usability**: Accessibility features + i18n = real-world deployment readiness
- **Context**: Cyber warfare theme → disinformation protection (message flagging)
- **Drama**: Attack Simulator button makes "grid down" scenario tangible

**For Demo Day (8 Juli):**
- 4 devices, 3-scene scenario: Grid down → Mesh active → Emergency response
- Live network graph showing relay count in real-time
- QR code verification + SOS flagging demonstration
- Accessibility feature walkthrough (for judges with a11y concerns)
