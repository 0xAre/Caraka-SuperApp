# Roadmap — Sweep UI/UX ke Arah Enterprise

**Tanggal**: 2026-08-04
**Status**: Disetujui, siap dieksekusi bertahap

## Kenapa 2 tahap, bukan 3

Permintaan awal menyebut 3 area: onboarding, design system, copywriting.
Audit cepat (`ui/theme/Theme.kt`, `ui/theme/Dimens.kt`, dan cek
`Color(0xFF...)` hardcoded di 44 file UI) nunjukin **design system-nya
sudah matang**: skema warna light/dark/high-contrast lengkap, status
colors semantik (online/hybrid/mesh/relay/sos/authority/direct/stealth),
scale spacing (`CarakaDimens`), shape scale (`CarakaShapes`), typography
scale (`CarakaTextStyles`). Cuma 3 dari 44 file yang pakai warna
hardcoded, dan itu pun buat ilustrasi bespoke (peta di
`PeerDiscoveryExperience.kt`, radar overlay) yang memang wajar di luar
token semantik.

Jadi gak ada gap besar yang butuh tahap sendiri. Polish kecil digabung
ke Tahap 2 (copywriting pass), bukan proyek terpisah — YAGNI, jangan
bikin kerjaan yang gak perlu.

## Tahap 1 — Onboarding Wizard

Ganti `ProfileSetupScreen` (form statis 1 halaman) + `OnboardingTourOverlay`
(carousel 5 kartu yang muncul terpisah setelah setup, numpuk sama dialog
izin sistem) jadi satu alur wizard bertahap yang koheren.

Spec: [2026-08-04-onboarding-wizard-design.md](2026-08-04-onboarding-wizard-design.md)
Prompt Codex: [2026-08-04-onboarding-wizard-prompt.md](2026-08-04-onboarding-wizard-prompt.md)

## Tahap 2 — Copywriting & Konsistensi Microcopy

Jalan **setelah** Tahap 1 kelar (karena Tahap 2 juga nulis ulang copy
buat layar onboarding baru hasil Tahap 1 — kalau dibalik, copy-nya bakal
ditulis dua kali). Cakupan: perbaiki string yang bypass `strings.xml`
(hardcoded di ViewModel), tonal pass di seluruh `strings.xml` pakai voice
guide yang udah didefinisikan, plus polish kecil sisa dari temuan design
system (3 file di atas — dibiarkan apa adanya, cuma dicek gak ada
regresi).

Spec: [2026-08-04-copywriting-sweep-design.md](2026-08-04-copywriting-sweep-design.md)
Prompt Codex: [2026-08-04-copywriting-sweep-prompt.md](2026-08-04-copywriting-sweep-prompt.md)

## Di luar roadmap ini

- **Security**: `ProfileSetupScreen.kt` nyimpen password authority
  (BPBD/POLRI/PMI) polos di source (`"Presisi"`, `"Sigap"`, `"Tangguh"`)
  — gampang diekstrak dari APK lewat decompile. Ini bukan isu UX, gak
  digarap di roadmap ini. Kalau mau dibenerin, itu percakapan/spec
  terpisah (butuh keputusan soal skema autentikasi authority yang
  proper, bukan sekadar ganti string).
