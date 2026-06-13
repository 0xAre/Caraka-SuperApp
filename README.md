# CARAKA — Cakra Mesh

> **"When The Grid Falls, We Rise."**

**CARAKA** is a fully offline, encrypted mesh communication app for Android — built for crisis coordination when internet, cellular, and power infrastructure fail. Target users include first responders (BPBD, Polri, PMI), disaster survivors, and field teams who need resilient comms without central infrastructure.

Originally built for **WRECK-IT 7.0** · Theme: *Cyber Warfare — Silent War on The Fifth Domain*

| | |
|---|---|
| **minSdk** | 26 (Android 8.0) |
| **targetSdk** | 36 |
| **version** | 1.0 (`versionCode` 1) |
| **Package** | `com.example.caraka` |
| **Repo** | [github.com/Fatihmaull/cakra-mesh](https://github.com/Fatihmaull/cakra-mesh) |

---

## The Problem

Indonesia faces overlapping digital and disaster risks:

- **5.5 billion cyber attacks** recorded in 2025 (BSSN data)
- **PDNS Ransomware — June 2024**: Brain Cipher paralyzed 200+ government institutions
- **InaRISK (BNPB)** is internet-dependent and fails when connectivity is down
- First responders still fall back to HT radios and *kentongan* when digital systems fail

CARAKA fills the gap: **a secure, infrastructure-free mesh that works when nothing else does.**

---

## Features (actual implementation status)

| Feature | Status | Implementation |
|---------|--------|----------------|
| **WiFi Direct P2P** | Live | TCP mesh sockets, auto-connect, smart GO election |
| **LAN UDP discovery** | Live | Broadcast on port `8890`, handshake + direct unicast |
| **WiFi Aware (NAN)** | Live (hardware-dependent) | Primary multi-hop transport when supported; falls back to WiFi Direct |
| **Google Nearby Connections** | Live (Play Services) | BT + WiFi overlay when GMS available; skipped on AOSP |
| **Multi-hop relay** | Live | TTL-based flooding + gossip (`PEER_LIST` every 5s) + BATMAN-style router on Aware path |
| **End-to-end encryption** | Live | X25519 (ECDH) + XChaCha20-Poly1305 via Lazysodium 5.1.0 |
| **Message signing** | Live | Ed25519 signatures on TEXT and SOS |
| **TOFU key continuity** | Live | Handshake exchanges X25519 + Ed25519 public keys |
| **SOS broadcast** | Live | 4 categories, 2s hold-to-confirm, `EMERGENCY` priority, multi-hop relay |
| **Network map** | Live | Force-directed topology canvas on `NetworkScreen` |
| **Authority roles** | Live | BPBD / Polri / PMI / Civilian — password gate at setup + signed identity |
| **QR identity verify** | Live | ZXing generate/scan; auto-connect + trust on scan |
| **Anti-disinformation** | Live | Community `FLAG` messages; ≥3 flags shows warning badge |
| **SQLCipher at rest** | Live | Room DB v3 encrypted (AES-256-CBC); passphrase wrapped in Android Keystore (AES-256-GCM) |
| **Hybrid connectivity UI** | Live | `ConnectivityMonitor`: Online / Hybrid / MeshOnly banner |
| **Attack simulator** | Live | Settings toggle simulates grid-down for demo |
| **i18n** | Live | Indonesian (default) + English — ~270 strings per locale |
| **Accessibility** | Live | High-contrast, big-text (+25%), haptics, 5-step onboarding tour |
| **Foreground service** | Live | `MeshForegroundService` — WifiLock + WakeLock, survives screen-off / Doze |
| **Anti-replay (transit)** | Live | LRU `seenIds` cache (2 000 entries) in `MeshSocketManager` + router-level dedup |
| **Anti-replay (persistent)** | Live | `messageExists()` DB check + relay table dedup (migration Phase 0) |
| **Reliable unicast (Phase 1)** | Live | Outbox persist, end-to-end ACK, bounded retry, honest delivery status UI |
| **Limited DTN (Phase 2)** | Code complete, **not device-tested** | Timer queue-processor, verified-contact carry, outbox quota eviction, router no-drop fallback |
| **Power optimization (Phase 3)** | Code complete, **not device-tested** | Two-state duty cycle (idle/deep-idle), suspends active WiFi-Direct scan when deep idle |

---

## Migration roadmap (June 2026)

The app is migrating from fire-and-forget flooding toward a **store-carry-forward (DTN)** model per the binding architecture baseline.

| Phase | Scope | Code status |
|-------|-------|-------------|
| **0 — Foundation** | DB v3, `OutboxEntity`, `MeshPolicy`, `deliveryStatus`, SQLCipher hardening | Shipped |
| **1 — Reliable unicast** | Outbox write, ACK send/handle, bounded retry, status icons | Shipped |
| **2 — Limited DTN** | FGS queue-processor (15s tick), verified carry + flush debounce, quota eviction, MeshRouter no-drop | Shipped, pending 2-device test |
| **3 — Power** | Idle interval ×4 after 60s; deep-idle suspends active discovery after 5 min | Shipped, pending field test |

Authoritative docs: `docs/architecture/caraka-architecture-baseline.md`, `docs/implementation/caraka-migration-program.md`.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CARAKA — System Architecture                     │
├──────────────────┬────────────────────────┬─────────────────────────────┤
│  Presentation    │  Domain / Network      │  Data                       │
│  ─────────────── │  ───────────────────── │  ─────────────────────────  │
│  Jetpack Compose │  MeshManager (facade)  │  Room v3 + SQLCipher        │
│  Material 3      │  ├─ WifiDirectManager  │  ├─ messages (+ deliveryStatus)
│                  │  │   (brain + LAN)     │  ├─ peers                     │
│  HomeScreen      │  ├─ WifiAwareManager   │  ├─ relayed_messages          │
│  MessagesScreen  │  ├─ NearbyTransport    │  └─ outbox (DTN transport)    │
│  ChatScreen      │  ├─ MeshRouter         │                               │
│  NetworkScreen   │  ├─ MeshSocketManager  │  Android Keystore           │
│  SosScreen       │  └─ GoIntentCalculator │  └─ DB passphrase (TEE)      │
│  AlertsScreen    │                        │                             │
│  QrIdentityScreen│  MeshRepository        │  DataStore                  │
│  SettingsScreen  │  CryptoManager         │  └─ identity keys + prefs   │
│  HelpScreen      │  IdentityManager       │                             │
│                  │  MeshForegroundService │  DatabasePassphraseManager  │
│  MainViewModel   │  ConnectivityMonitor   │                             │
│  (manual DI)     │  MeshPolicy (quotas)   │                             │
├──────────────────┴────────────────────────┴─────────────────────────────┤
│  Transport layer (selected automatically by MeshManager)                 │
│  WiFi Direct TCP  ·  LAN UDP :8890  ·  WiFi Aware NAN  ·  Nearby (GMS)  │
└─────────────────────────────────────────────────────────────────────────┘
```

### Mesh protocol (`MeshProtocol.kt`)

JSON over length-prefixed TCP/UDP. Message types:

`HANDSHAKE` · `TEXT` · `SOS` · `FLAG` · `ACK` · `CONNECTION_REQUEST` · `CONNECTION_ACCEPT` · `CONNECTION_REJECT` · `PEER_LIST`

Default unicast TTL = 5 hops. SOS uses `EMERGENCY` priority. ACK carries the original message ID in `content`.

### Security layers

```
Layer 4: Data at rest     — SQLCipher AES-256-CBC (caraka_secure.db)
Layer 3: Message auth     — Ed25519 signature verification
Layer 2: E2E encryption   — X25519 ECDH + XChaCha20-Poly1305
Layer 1: Transport        — Local WiFi / BT only (no internet required)
```

DB passphrase: `SecureRandom(32B)` → AES-256-GCM wrap → Android Keystore TEE.

Identity keys: Ed25519 + X25519 keypairs in DataStore (Keystore migration planned).

---

## Smart GO election

WiFi Direct Group Owner intent is computed by `GoIntentCalculator` from battery level, authority role, and relay load — low-battery devices yield GO to avoid group collapse.

---

## QR identity verification

1. **Share** — Settings → QR identity screen
2. **Scan** — Camera scan peer QR (auto-accept path for verified connect)
3. **Trust** — Peer saved with `isVerified = true` in Room

QR payload: `{ v, peerId, name, role, encPub, signPub }` as JSON (ZXing 4.3.0).

Authority roles at first setup still use a password gate (`ProfileSetupScreen`); QR is the in-field verification path.

---

## Project structure

```
CARAKA-APP/
├── app/                              # Gradle project root (open this in Android Studio)
│   └── app/src/main/java/com/example/caraka/
│       ├── CarakaApp.kt              # Application — manual dependency injection
│       ├── MainActivity.kt           # NavHost, permissions, FGS start
│       ├── crypto/
│       │   ├── CryptoManager.kt
│       │   ├── IdentityManager.kt
│       │   ├── DatabasePassphraseManager.kt
│       │   └── QrIdentityManager.kt
│       ├── network/
│       │   ├── MeshManager.kt        # Transport facade
│       │   ├── MeshTransport.kt      # Interface
│       │   ├── WifiDirectManager.kt  # Brain: handlers, LAN, gossip, duty cycle
│       │   ├── WifiAwareManager.kt
│       │   ├── NearbyTransport.kt
│       │   ├── MeshRouter.kt         # Aware-path routing + no-drop fallback
│       │   ├── MeshSocketManager.kt  # TCP + anti-replay LRU
│       │   ├── MeshProtocol.kt
│       │   ├── MeshPolicy.kt         # DTN quotas, retry, duty-cycle constants
│       │   ├── GoIntentCalculator.kt
│       │   └── ConnectivityMonitor.kt
│       ├── service/
│       │   └── MeshForegroundService.kt
│       ├── data/local/
│       │   ├── CarakaDatabase.kt     # v3 + SQLCipher + migrations 1→2→3
│       │   ├── entity/               # Message, Peer, RelayedMessage, Outbox
│       │   └── dao/
│       ├── repository/MeshRepository.kt
│       ├── viewmodel/MainViewModel.kt
│       └── ui/screens/               # Home, Chat, Messages, Network, SOS,
│           components/ theme/        # Alerts, Settings, Help, QrIdentity, ProfileSetup
├── docs/
│   ├── architecture/                 # Baseline, gap analysis, migration review
│   ├── implementation/               # Migration program + device test plan
│   ├── HCI_EVALUATION.md
│   ├── MESH_CONNECTION_REDESIGN.md
│   └── TEST_CHECKLIST.md
├── landing-page/                     # Static project landing page
├── PRD.md
└── PROGRESS.md
```

Dependency injection is **manual** via `CarakaApp` — Hilt is not used.

---

## Getting started

### Requirements

- **Android Studio** Ladybug (2024.2+) or newer
- **JDK 11+**
- **Physical Android devices** — emulator has no WiFi Direct / Aware
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`

### Build and run

```powershell
git clone https://github.com/Fatihmaull/cakra-mesh.git
cd cakra-mesh/app
.\gradlew.bat assembleDebug
# APK: app/app/build/outputs/apk/debug/app-debug.apk
```

Open the `app/` folder in Android Studio and deploy to a physical device.

### Permissions

| Permission | Reason |
|-----------|--------|
| `ACCESS_FINE_LOCATION` | WiFi Direct peer discovery (Android 8+) |
| `NEARBY_WIFI_DEVICES` | WiFi Direct on Android 13+ |
| `BLUETOOTH_*` | Google Nearby Connections transport |
| `CAMERA` | QR identity scan |
| `POST_NOTIFICATIONS` | SOS and chat notifications |
| `VIBRATE` | SOS haptic feedback |
| `FOREGROUND_SERVICE` + `CONNECTED_DEVICE` | Keep mesh alive in background |
| `WAKE_LOCK` | CPU active during screen-off mesh operation |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | OEM battery-kill mitigation (HiOS/MIUI/XOS) |

---

## Testing the mesh

Requires **2+ physical Android devices**.

```
Device A                    Device B (relay)              Device C
   │                              │                           │
Profile setup + mesh start   Same                         Same
   │                              │                           │
Network tab — auto discovery ────┼───────────────────────────┤
   │                              │                           │
Send SOS ──────────────────> Relay ─────────────────────> Alert
Send DM ───────────────────────────────────────────────> Decrypted
```

**Multi-hop**: Place C out of A's range but within B's range; messages relay via B.

**Post-migration gates** (from architecture baseline): 2-device verification required after Phase 1; full field test required after Phase 2 before treating DTN as production-ready.

---

## Dependencies (pinned versions)

| Library | Version | Purpose |
|---------|---------|---------|
| Android Gradle Plugin | 8.13.2 | Build |
| Kotlin | 2.0.21 | Language |
| Jetpack Compose BOM | 2024.09.00 | UI |
| Room | 2.6.1 | Local ORM |
| SQLCipher | 4.5.4 | Encrypted SQLite |
| Lazysodium Android | 5.1.0 | X25519, Ed25519, XChaCha20-Poly1305 |
| ZXing Android Embedded | 4.3.0 | QR generate/scan |
| Play Services Nearby | 19.3.0 | Nearby Connections transport |
| Gson | 2.11.0 | Mesh protocol JSON |
| Kotlinx Serialization | 1.7.3 | QR payload serialization |
| Kotlin Coroutines | 1.9.0 | Async + Flow |
| DataStore Preferences | 1.1.1 | Identity + UI prefs |

Typography: **Space Grotesk** (display), **Inter** (body), **DotGothic16** (grid/stats) via Google Fonts.

---

## Build and verification status

**Last verified:** 13 June 2026

| Check | Result |
|-------|--------|
| `./gradlew assembleDebug` | SUCCESS |
| Debug APK size | ~27 MB |
| Phase 0–1 (outbox, ACK, status UI) | Implemented |
| Phase 2–3 (DTN carry, duty cycle) | Implemented, **not yet device-tested** |

---

## Known limitations

- Authority setup uses role passwords at onboarding; production would use hardware-bound Keystore identities
- Identity keys stored in DataStore — Keystore migration noted in `IdentityManager` TODO
- No certificate revocation for authority credentials
- No forward secrecy (static keypairs per session)
- DTN carry limited to QR-verified contacts; strangers/epidemic routing deferred per baseline
- BLE backbone deferred (baseline D15)

---

## Why CARAKA for Indonesia

| Context | Detail |
|---------|--------|
| Cyber attacks 2025 | 5.5 billion (BSSN) |
| PDNS 2024 | 200+ institutions paralyzed |
| InaRISK offline | None — 100% internet-dependent |
| Backup comms today | HT radio + kentongan |
| Android penetration | ~70% of 277M population |

CARAKA bridges analog radio (no encryption, no digital relay) and the internet (unavailable in crises) with a **zero-infrastructure encrypted mesh** any Android phone can join.

---

## License

Proprietary — WRECK-IT 7.0 hackathon entry. Contact the repository owner for usage terms.

---

*CARAKA — Built for Indonesia's digital resilience.*
