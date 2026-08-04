# Prompt eksekusi — Autopilot Tahap 1 + Tahap 2 (Enterprise UX)

Versi singkat: nyuruh Codex baca & eksekusi 2 file prompt yang sudah
ada di repo secara berurutan, dengan disiplin checkpoint. Isi lengkap
tiap tahap ada di:
- [2026-08-04-onboarding-wizard-prompt.md](2026-08-04-onboarding-wizard-prompt.md)
- [2026-08-04-copywriting-sweep-prompt.md](2026-08-04-copywriting-sweep-prompt.md)

## Cara pakai

Copy blok di bawah, paste ke Codex, tinggal. Pas balik, kabarin gua —
gua review tiap commit checkpoint, verifikasi ulang, baru bump versi &
rilis.

## Prompt

```
Kerjakan repo E:\01-Proyek\CARAKA-APP secara berurutan, tanpa nunggu
konfirmasi manusia — orangnya pergi ~2 jam.

TAHAP A: Baca dan kerjakan persis semua instruksi di
docs/superpowers/specs/2026-08-04-onboarding-wizard-prompt.md
(bagian dalam blok kode di file itu).

TAHAP B: Baca dan kerjakan persis semua instruksi di
docs/superpowers/specs/2026-08-04-copywriting-sweep-prompt.md
(bagian dalam blok kode di file itu).

DISIPLIN WAJIB:
1. Jalankan gradlew dari app/, git dari root repo.
2. Verifikasi build sukses di akhir tiap tahap (perintah VERIFIKASI
   ada di masing-masing file prompt).
3. Kalau sukses: `git add -A && git commit -m "..."` (commit lokal
   sebagai checkpoint, pesan sesuai isi tahap), baru lanjut tahap
   berikutnya. Kalau gagal setelah dicoba benerin 2-3 kali: STOP
   total, jangan lanjut ke tahap berikutnya.
4. JANGAN git push, JANGAN git tag, JANGAN ubah
   versionCode/versionName — itu dikerjakan manual setelah review.
5. Di respons terakhir, laporkan: tahap mana yang selesai/berhenti,
   hasil build tiap tahap, commit apa aja yang dibuat, file yang
   berubah (git diff --stat), dan kalau ada yang gagal — apa yang
   sudah dicoba.
```
