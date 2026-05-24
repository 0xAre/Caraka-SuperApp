# Cakra Mesh (CARAKA)

**Offline crisis communication over WiFi Direct — when the grid falls, we rise.**

CARAKA is an Android app that builds a decentralized, encrypted mesh network without internet. It targets emergency coordination during infrastructure outages (cyber warfare, disasters, grid failure) for first responders, medical teams, authorities, and civilians.

> *"When The Grid Falls, We Rise."*

Built for **WRECK-IT 7.0** (IoT Resilience) — theme: *Cyber Warfare: Silent War on The Fifth Domain*.

## Problem

When coordinated attacks take down internet, power, and cellular networks, most communication tools stop working. CARAKA keeps peer-to-peer messaging, SOS broadcasts, and relay routing alive on local WiFi Direct links.

## Features

| Feature | Status | Description |
|--------|--------|-------------|
| WiFi Direct P2P | Implemented | Device discovery and connection without internet (~100 m) |
| Encrypted messaging | Implemented | E2E encryption (X25519, Ed25519, libsodium via Lazysodium) |
| Multi-hop relay | Implemented | JSON mesh protocol with TTL-based forwarding |
| SOS broadcast | Implemented | One-tap emergency alerts with categories |
| Network map | Implemented | Real-time mesh topology UI |
| Verified roles | Demo | BPBD, Polri, PMI authority badges (demo passwords) |
| Message flagging / hybrid mode | Planned | See [PRD.md](PRD.md) |

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **WiFi Direct** for P2P connectivity
- **TCP sockets** + length-prefixed JSON (`MeshProtocol`)
- **Room** for local message/peer storage
- **DataStore** for identity keys
- **Lazysodium** (libsodium) for cryptography

## Project structure

```
cakra-mesh/
├── app/                    # Android Gradle project (open this in Android Studio)
│   ├── app/                # Application module
│   │   └── src/main/java/com/example/garudamesh/
│   │       ├── crypto/     # Encryption & identity
│   │       ├── network/    # WiFi Direct, sockets, protocol
│   │       ├── repository/ # Mesh data layer
│   │       └── ui/         # Compose screens
│   └── gradlew             # Build wrapper
├── mockups/                # UI mockups
├── proposal/               # Hackathon proposal
├── PRD.md                  # Product requirements
└── Garuda-Mesh.md          # Architecture & concept notes
```

## Getting started

### Requirements

- Android Studio (Ladybug or newer recommended)
- JDK 11+
- Physical Android devices with WiFi Direct (emulator has limited P2P support)
- **minSdk 26**, **targetSdk 36**

### Build & run

1. Clone the repository:
   ```bash
   git clone https://github.com/Fatihmaull/cakra-mesh.git
   cd cakra-mesh/app
   ```
2. Open the `app` folder in Android Studio.
3. Sync Gradle, connect a device, and run the `app` configuration.

From the command line (from `app/`):

```bash
./gradlew assembleDebug
```

Debug APK output: `app/app/build/outputs/apk/debug/`.

### Permissions

The app requests WiFi, location (required for WiFi Direct discovery on Android 8+), nearby devices (Android 13+), notifications, and vibration for SOS alerts. Grant all permissions on first launch for mesh discovery to work.

## Testing mesh

Use **two or more physical phones** on the same WiFi Direct group:

1. Complete profile setup on each device.
2. Open **Network** and start discovery.
3. Connect peers, then send messages or trigger SOS from the home screen.

## Documentation

- [PRD.md](PRD.md) — full product requirements and scope
- [Garuda-Mesh.md](Garuda-Mesh.md) — architecture and hackathon narrative
- [proposal/PROPOSAL_GARUDA_MESH.md](proposal/PROPOSAL_GARUDA_MESH.md) — competition proposal

## Security note

Authority role passwords in the demo build are hardcoded for hackathon demos only. Do not use this build as-is in production.

## License

License not yet specified. Contact the repository owner for usage terms.
