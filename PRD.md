# 📋 PRD — CARAKA
# Offline Crisis Communication Network

> **Version**: 1.1 | **Date**: 12 Mei 2026  
> **Track**: WRECK-IT 7.0 Hackathon — **IoT Resilience** ✅  
> **Tema**: *"Cyber Warfare: Silent War on The Fifth Domain"*  
> **Deadline**: 14 Juni 2026  
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

## 4. Scope (1 Month)

### ✅ IN SCOPE

| # | Feature | Priority | Deskripsi |
|---|---|---|---|
| F1 | WiFi Direct P2P Connection | **P0** | Device-to-device tanpa internet |
| F2 | Text Messaging | **P0** | Kirim/terima pesan antar device |
| F3 | Multi-hop Relay | **P0** | Forward pesan via intermediate nodes |
| F4 | E2E Encryption | **P0** | Semua pesan encrypted end-to-end |
| F5 | SOS Broadcast | **P0** | One-tap emergency signal + kategori |
| F6 | Network Visualization | **P1** | Real-time mesh topology map |
| F7 | Verified Identity (PKI) | **P1** | Authority badges via digital signatures |
| F8 | Message Flagging | **P1** | Community flag pesan hoax |
| F9 | Hybrid Mode | **P1** | Auto-switch online ↔ offline |

### ❌ OUT OF SCOPE

iOS, LoRa/Satellite, voice messages, offline maps, blockchain verification, deepfake detection, video streaming.

---

## 5. Functional Requirements Detail

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

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Networking | Android WiFi P2P API |
| Encryption | Lazysodium-Android (libsodium) |
| Database | Room + SQLCipher |
| Async | Kotlin Coroutines + Flow |
| Min SDK | **API 26 (Android 8.0)** — semua device tim 2023+, safe margin |
| Target SDK | API 34 (Android 14) |

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

## 11. Timeline

| Sprint | Dates | Goals |
|---|---|---|
| **Sprint 1** | 12-18 Mei | Project setup, WiFi Direct PoC, basic UI, key generation. **Milestone**: 2 devices chat |
| **Sprint 2** | 19-25 Mei | Multi-hop relay, E2E encryption, SOS system. **Milestone**: 3+ devices mesh + SOS |
| **Sprint 3** | 26 Mei-1 Jun | Network viz, PKI, flagging, hybrid mode, draft proposal. **Milestone**: All features + proposal draft |
| **Sprint 4** | 2-14 Jun | Polish, bug fix, GitHub docs, demo video, finalize proposal. **SUBMIT ≤ 14 Juni** |

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
