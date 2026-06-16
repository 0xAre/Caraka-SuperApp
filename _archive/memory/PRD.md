# PRD — CARAKA (HCI/UX Upgrade)

## Original Problem Statement
> "this is my team project, do maximum UI/UX for this project and also must
>  eligible for Human Computer Interaction"

Project: **CARAKA** — Android Jetpack Compose app for offline crisis
communication (WiFi Direct mesh) for the WRECK-IT 7.0 hackathon.

## Architecture
- Kotlin + Jetpack Compose (Material 3), MVVM
- Downloadable Google Fonts (Rajdhani / Inter / JetBrains Mono)
- DataStore for UI prefs + Identity
- Room DB, Lazysodium crypto, WiFi Direct
- Locale switching via `createConfigurationContext`

## User Personas (HCI scope)
1. **Warga panik** — perlu interaksi sederhana, anti-mistap, multibahasa
2. **Otoritas (BPBD/Polri/PMI)** — perlu kecepatan, identitas verified, broadcast efisien
3. **Pengguna pertama kali** — perlu onboarding & help docs
4. **Pengguna dengan disabilitas** — perlu screen reader, font scaling, kontras tinggi

## What's been implemented (HCI/UX track) — Jan 2026
- [x] **i18n dual-locale** Bahasa Indonesia (default) + English, 175 strings each (`values/`, `values-en/`)
- [x] **Typography overhaul** Rajdhani + Inter + JetBrains Mono via Downloadable Fonts
- [x] **Hold-to-confirm SOS** (2 detik) — error prevention (Nielsen #5)
- [x] **Haptic feedback** util: tick/light/heavy/emergency-waveform
- [x] **Onboarding tour** 5 langkah, replay-able dari Help
- [x] **HelpScreen** dengan FAQ + ringkasan 10 heuristik + a11y
- [x] **High-Contrast mode** (WCAG AAA palette)
- [x] **Big-Text mode** (+25% font-scale)
- [x] **Snackbar feedback bus** untuk umpan balik aksi
- [x] **Floating tooltip** komponen reusable
- [x] **Accessibility semantics**: `role`, `contentDescription`, `stateDescription` di semua kontrol custom
- [x] **HCI Evaluation doc** (`docs/HCI_EVALUATION.md`) — bukti pemetaan ke Nielsen + WCAG

## Files added/modified
**New (13 files):**
- `res/values/strings.xml`, `res/values-en/strings.xml`, `res/values/font_certs.xml`
- `ui/prefs/UiPreferences.kt`, `ui/prefs/LocalUiPrefs.kt`
- `ui/util/Haptics.kt`
- `ui/components/HoldToConfirmButton.kt`, `InfoTooltip.kt`, `OnboardingTourOverlay.kt`, `SnackbarBus.kt`
- `ui/screens/HelpScreen.kt`
- `docs/HCI_EVALUATION.md`

**Modified:**
- `MainActivity.kt`, `Theme.kt`, `Type.kt`
- All screens (Home, SOS, Profile, Chat, Messages, Network, Settings)
- `BottomNavBar.kt`, `ChatInputBar.kt`
- `README.md`

## Build & verify
Since this is a native Android project, the sandbox cannot compile it.
Open `/app/app` di Android Studio (Ladybug+), sync Gradle, jalankan `app`
di device fisik (min SDK 26).

## P0/P1 Backlog
- [ ] **TalkBack QA pass** di device fisik
- [ ] User-testing report (think-aloud, 5 participants)
- [ ] Color-blind palette (deuteranopia/protanopia)
- [ ] Voice input untuk SOS (motor disability)
- [ ] Expand locales: Jawa, Sunda
