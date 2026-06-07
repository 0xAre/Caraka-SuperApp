# 🛡️ CARAKA — Cakra Mesh

> **"When The Grid Falls, We Rise."**

**CARAKA** is a fully-offline, encrypted mesh communication platform for Android — built for crisis coordination when internet, cellular, and power infrastructure fail. Designed for first responders (BPBD, Polri, PMI), disaster survivors, and national cybersecurity resilience.

Built for **WRECK-IT 7.0** · Theme: *Cyber Warfare — Silent War on The Fifth Domain*

[![Build](https://img.shields.io/badge/build-passing-brightgreen?style=flat-square)]()
[![minSdk](https://img.shields.io/badge/minSdk-26-blue?style=flat-square)]()
[![Encryption](https://img.shields.io/badge/encryption-XChaCha20%2FPoly1305-purple?style=flat-square)]()
[![License](https://img.shields.io/badge/license-proprietary-red?style=flat-square)]()

---

## 🧭 The Problem

Indonesia faces a perfect storm of threats:

- **5.5 billion cyber attacks** recorded in 2025 (BSSN data)
- **PDNS Ransomware — June 2024**: Brain Cipher paralyzed 200+ government institutions, taking offline immigration, education, and emergency services
- **InaRISK (BNPB)** — Indonesia's official disaster app — is **100% internet-dependent** and fails exactly when disasters strike
- First responders fall back to HT radios and *kentongan* (wooden drums) when digital systems fail

CARAKA fills the critical gap: **a secure, fully-offline mesh network that works when nothing else does.**

---

## ✨ Features

| Feature | Status | Detail |
|---------|--------|--------|
| 📡 **WiFi Direct P2P** | ✅ Live | Device mesh up to ~100m per hop, no infrastructure |
| 🔐 **End-to-End Encryption** | ✅ Live | X25519 (ECDH) + XChaCha20-Poly1305 via libsodium |
| ✍️ **Message Signing** | ✅ Live | Ed25519 signatures — anti-impersonation |
| 🔄 **Multi-hop Relay** | ✅ Live | TTL-based gossip flooding, 3–5 hop range |
| 🚨 **SOS Broadcast** | ✅ Live | 4 categories, 2s hold-to-confirm, multi-hop priority relay |
| 🕸️ **Network Map** | ✅ Live | Real-time force-directed mesh topology, 30fps |
| 🛡️ **Authority Roles** | ✅ Live | BPBD / Polri / PMI verified roles with signed identities |
| 🚩 **Anti-Disinformation** | ✅ Live | Community flagging (≥3 flags = warning badge) |
| 🔒 **SQLCipher Encryption** | ✅ **NEW** | All messages & peers encrypted at rest (AES-256) |
| 📲 **QR Identity** | ✅ **NEW** | Share/scan identity QR for in-person peer verification |
| 🔋 **Smart GO Election** | ✅ **NEW** | Battery-aware WiFi Direct Group Owner negotiation |
| 🌐 **Hybrid Mode** | ✅ Live | Auto-detects internet availability, falls back to mesh |
| ♿ **HCI / Accessibility** | ✅ Live | WCAG AAA, i18n (ID/EN), haptic, big-text, onboarding |
| 🔄 **Anti-Replay** | ✅ Live | LRU seenIds cache + timestamp drift detection |
| 🏠 **LAN Discovery** | ✅ Live | UDP broadcast discovery on same WiFi network |

---

## 🏗️ Architecture

```
┌───────────────────────────────────────────────────────────────────┐
│                     CARAKA — System Architecture                  │
├───────────────┬───────────────────────┬───────────────────────────┤
│  Presentation │       Domain          │         Data              │
│  ─────────────│  ─────────────────── │  ───────────────────────  │
│  Jetpack       │  WifiDirectManager  │  Room + SQLCipher         │
│  Compose UI   │  ├─ GoIntentCalc.   │  ├─ MessageEntity          │
│  ─────────── │  ├─ MeshSocketMgr   │  ├─ PeerEntity             │
│  HomeScreen   │  └─ LAN Discovery   │  └─ RelayedMessageEntity   │
│  ChatScreen   │                      │                            │
│  NetworkScreen│  MeshRepository     │  Android Keystore          │
│  SosScreen    │  ├─ saveVerifiedPeer │  └─ DB Passphrase (TEE)   │
│  QrIdentity   │  └─ broadcastSos    │                            │
│  Settings     │                      │  DataStore (Prefs)         │
│               │  CryptoManager      │  └─ Identity Keys          │
│  ─────────── │  ├─ X25519 ECDH     │                            │
│  ViewModels   │  ├─ XChaCha20 E2E  │  DatabasePassphraseMgr    │
│  MainViewModel│  └─ Ed25519 Sign    │  └─ AES-256-GCM (Keystore)│
│               │                      │                            │
│               │  IdentityManager    │  QrIdentityManager         │
│               │  └─ Keypair gen     │  └─ ZXing QR gen/scan      │
├───────────────┴───────────────────────┴───────────────────────────┤
│                   Transport Layer                                  │
│  WiFi Direct (P2P)           LAN UDP Broadcast                    │
│  ├─ TCP socket (MeshSocket)  ├─ 255.255.255.255:8890             │
│  ├─ Smart GO election        └─ HANDSHAKE peer discovery          │
│  └─ Group Owner failover                                          │
└───────────────────────────────────────────────────────────────────┘
```

### Mesh Protocol Flow

```
Device A                   Device B (relay)              Device C
   │                            │                             │
   │── HANDSHAKE ──────────────>│── HANDSHAKE ───────────────>│
   │                            │                             │
   │── TEXT (TTL=5) ───────────>│── TEXT (TTL=4) ────────────>│
   │                            │   [relay, TTL-1]            │
   │                            │                             │
   │── SOS (TTL=10, EMERGENCY) >│── SOS (TTL=9) ─────────────>│
   │                            │   [relay, priority=HIGHEST] │
```

### Security Layers

```
┌─────────────────────────────────────────────────────┐
│  Layer 4: Data at Rest (SQLCipher AES-256-CBC)      │
│  ─────────────────────────────────────────────────  │
│  Layer 3: Message Auth (Ed25519 signature)          │
│  ─────────────────────────────────────────────────  │
│  Layer 2: E2E Encryption (X25519 + XChaCha20-Poly)  │
│  ─────────────────────────────────────────────────  │
│  Layer 1: Transport (WiFi Direct, local only)       │
└─────────────────────────────────────────────────────┘

DB Passphrase: SecureRandom(32B) → AES-256-GCM → Android Keystore TEE
Key Storage:   Ed25519/X25519 keypairs → DataStore (migration: Keystore)
```

---

## 🔋 Smart GO Election

WiFi Direct uses Group Owner (GO) election to assign which device becomes the "hub" of the P2P group. The original implementation used hardcoded `groupOwnerIntent = 15` (always prefer GO), which creates a critical risk: **if a low-battery device wins GO and drops out, the entire group collapses**.

CARAKA now implements **battery-aware GO election**:

```
GO Intent Score = f(battery, role, relay_load)

  battery ≤ 10%     → intent = 0    (never GO, save power for messaging)
  battery ≤ 20%     → intent = 2-6  (prefer client role)
  authority + OK    → intent = 13+  (BPBD/Polri/PMI preferred as GO)
  relay load high   → intent lower  (busy relay nodes yield GO role)
```

---

## 📲 QR Identity Verification

Authority verification is now done **face-to-face via QR code** instead of hardcoded demo passwords:

1. **Share**: Settings → "Lihat / Scan QR Identitas" → show your QR
2. **Scan**: Tap "Buka Kamera & Scan" → scan peer's QR  
3. **Verify**: Peer's `peerId`, `role`, `encPub`, and `signPub` are extracted
4. **Trust**: Peer saved to Room DB as `isVerified = true`

QR payload encodes `{ v, peerId, name, role, encPub, signPub }` as JSON.  
Generated with ZXing using CARAKA's tactical navy + amber color scheme.

---

## 🗂️ Project Structure

```
cakra-mesh/
├── app/                        # Android Gradle project root
│   └── app/src/main/java/com/example/caraka/
│       ├── CarakaApp.kt        # Application class, manual DI
│       ├── MainActivity.kt     # NavHost, permissions, scaffold
│       ├── crypto/
│       │   ├── CryptoManager.kt          # X25519, XChaCha20, Ed25519
│       │   ├── IdentityManager.kt        # Keypair gen, DataStore storage
│       │   ├── DatabasePassphraseManager.kt  ← NEW: Keystore-backed DB key
│       │   └── QrIdentityManager.kt          ← NEW: QR gen/parse (ZXing)
│       ├── network/
│       │   ├── WifiDirectManager.kt      # P2P lifecycle, auto-connect
│       │   ├── GoIntentCalculator.kt         ← NEW: Smart GO election
│       │   ├── MeshSocketManager.kt      # TCP server/client, anti-replay
│       │   ├── MeshProtocol.kt           # JSON wire format
│       │   └── ConnectivityMonitor.kt    # Online/hybrid/mesh-only states
│       ├── data/local/
│       │   ├── CarakaDatabase.kt         # Room + SQLCipher ← UPDATED
│       │   ├── entity/                   # MessageEntity, PeerEntity, RelayedMsgEntity
│       │   └── dao/                      # MessageDao, PeerDao, RelayDao
│       ├── repository/
│       │   └── MeshRepository.kt        # Single source of truth
│       ├── viewmodel/
│       │   └── MainViewModel.kt         # StateFlows → UI
│       └── ui/
│           ├── screens/
│           │   ├── HomeScreen.kt         # Mesh status, quick actions
│           │   ├── ChatScreen.kt         # E2E encrypted DMs
│           │   ├── MessagesScreen.kt     # Conversation list
│           │   ├── NetworkScreen.kt      # Topology map + node list
│           │   ├── SosScreen.kt          # Emergency broadcast
│           │   ├── QrIdentityScreen.kt       ← NEW: Show/scan identity QR
│           │   ├── HelpScreen.kt         # In-app help + HCI guide
│           │   └── PlaceholderScreens.kt # Settings (incl. GO battery chip)
│           ├── components/              # BottomNavBar, SOS badge, alerts
│           └── theme/                  # Color, Type, Theme (WCAG AAA)
├── PRD.md                      # Full product requirements
├── PROGRESS.md                 # Development milestone tracker
├── Garuda-Mesh.md              # Architecture narrative + hackathon context
├── docs/HCI_EVALUATION.md      # Nielsen heuristics + WCAG 2.1 assessment
└── proposal/                   # Competition proposal documents
```

---

## 🚀 Getting Started

### Requirements

- **Android Studio** Ladybug (2024.2) or newer
- **JDK 11+**
- **Physical Android devices** — emulator has no WiFi Direct support
- `minSdk 26` (Android 8.0 Oreo), `targetSdk 36`

### Build & Run

```bash
# Clone
git clone https://github.com/Fatihmaull/cakra-mesh.git
cd cakra-mesh/app

# Build debug APK
./gradlew assembleDebug

# APK output
# app/app/build/outputs/apk/debug/app-debug.apk
```

Or open the `app/` folder in Android Studio and run the `app` configuration directly on a physical device.

### Permissions Required

| Permission | Reason |
|-----------|--------|
| `ACCESS_FINE_LOCATION` | Required for WiFi Direct peer discovery (Android 8+) |
| `NEARBY_WIFI_DEVICES` | WiFi Direct on Android 13+ |
| `CAMERA` | QR code scanning for peer identity verification |
| `POST_NOTIFICATIONS` | SOS and chat message notifications |
| `VIBRATE` | Haptic feedback for SOS alerts |
| `FOREGROUND_SERVICE` | Keep mesh alive in background |

---

## 🧪 Testing the Mesh

You need **2+ physical Android devices**. Emulators do not support WiFi Direct.

```
Device A (BPBD role)          Device B (Civilian)         Device C (PMI role)
        │                              │                           │
   Setup profile               Setup profile               Setup profile
        │                              │                           │
   Open Network tab            Open Network tab            Open Network tab
        │                              │                           │
   [Auto-discovery + connect]──────────┤───────────────────────────┤
        │                              │                           │
   Send SOS ──────────────────> Receive alert ──────────> Receive alert
        │                       [relay if 1-hop]                   │
   Send DM to Device C ────────────────────────────────> Receive (decrypted)
```

**Multi-hop test**: Place Device C out of range of Device A but within range of Device B. Messages from A should arrive at C via B relay.

---

## 🔐 Security Architecture

### What's protected

- ✅ **Messages in transit**: XChaCha20-Poly1305 authenticated encryption (libsodium)
- ✅ **Messages at rest**: SQLCipher AES-256-CBC (database file encrypted)
- ✅ **DB key**: Random 256-bit key, wrapped in AES-256-GCM inside Android Keystore TEE
- ✅ **Identity**: Ed25519 keypairs (cannot be faked without private key)
- ✅ **Anti-replay**: LRU seenId cache + ±5 minute timestamp window
- ✅ **Authority verification**: QR code in-person exchange (no central server)

### Known limitations (hackathon scope)

- Authority credentials still use deterministic key derivation from role string — production would use hardware-bound Android Keystore keypairs
- No certificate revocation list for authority credentials
- Forward secrecy not yet implemented (each session uses same keypair)

---

## 🌏 Why CARAKA for Indonesia

| Context | Data |
|---------|------|
| Cyber attacks 2025 | **5.5 billion** (BSSN) |
| PDNS Incident 2024 | **200+ institutions** paralyzed by ransomware |
| InaRISK offline capability | **None** — 100% internet-dependent |
| Current backup comms | HT radio + kentongan (traditional drums) |
| RUU KKS 2026 | Mandates backup communication for critical orgs |
| ASEAN Vision 2025 | Resilient emergency telecom as regional priority |

CARAKA is the digital bridge between HT radio (analog, no encryption, no relay) and the internet (unavailable during crises) — a **zero-infrastructure encrypted mesh** that any Android device can join.

---

## 📚 Dependencies

| Library | Purpose |
|---------|---------|
| Jetpack Compose + Material 3 | UI framework |
| Room | Local database ORM |
| **SQLCipher 4.5.4** | Database encryption at rest |
| DataStore | Identity keys + UI preferences |
| Lazysodium (libsodium) | X25519, Ed25519, XChaCha20-Poly1305 |
| **ZXing Android Embedded 4.3.0** | QR code generation + scanning |
| Kotlinx Serialization | JSON + QR payload serialization |
| Google Fonts (Compose) | Rajdhani, Inter, JetBrains Mono |

---

## 📄 License

Not yet specified. Contact the repository owner for usage terms.

---

*CARAKA — Built with ❤️ for Indonesia's digital resilience.*
