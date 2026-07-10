# Caraka Mode — Relokasi ke Layar Pesan (2 Tab) + Alur Directed Terstruktur

> Plan executable. Status fondasi: courier backend (Directed+Stealth) sudah jalan; UI refinement sebelumnya selesai (lihat `ui_refinement_plan_caraka.md`). Plan ini me-restrukturisasi UX-nya.

## Konteks & Tujuan

Rebrand "Kurir/Courier" → **"Caraka"** (ID+EN); pindahkan ke **layar Pesan** sebagai **2 tab** (Pesan | Caraka); alur kirim **Directed** yang jelas: pilih **kurir B** (terhubung) → **tujuan Z** (kontak) → **pesan untuk Z** (terenkripsi) → **catatan untuk kurir B** (plaintext). Kontak auto-tersimpan (QR + pernah-terhubung) + tambah manual via PeerID.

**Keputusan user:** Directed-only (Stealth keluar dari UI) · pindah penuh (hapus tile Home + rute `Screen.Courier`) · kontak auto + manual ditandai "perlu terhubung".

**Fakta terverifikasi:** peer pernah-terhubung menyimpan `publicKey`/`signingKey` (`WifiDirectManager.kt:1213,1597,1608`) → Directed-capable & persisten tanpa migrasi DB. Belum ada `TabRow`. `MainViewModel` & `courierViewModel` activity-scoped.

## Work Items

1. **Layar Pesan 2 tab** — `MessagesScreen.kt`: `PrimaryTabRow` (Pesan|Caraka), ekstrak isi sekarang → `ChatListTab`, tab Caraka → `CarakaTab`. Terima `courierViewModel`. Badge carry di label tab.
2. **CarakaTab + send Directed** — section kurir terhubung (B) + "sedang dibawa"; send flow: kurir B → tujuan Z (kontak) → pesan Z → catatan B → `sendCourierRequest(...,mode=DIRECTED,note=...)`.
3. **Catatan kurir (additive)** — `MeshProtocol.courierNote`; `CourierRepository.buildOfferMessage` bawa note; `CourierManager.handleOffer` → `CourierEvent.OfferReceived.note`; VM state + `CourierOfferDialog` menampilkan note (+ hint plaintext).
4. **Kontak** — `PeerDao` cleanup hanya hapus `isVerified=0 AND publicKey=''`; `CourierViewModel.contacts` dari `getAllPeers()` filter `publicKey` non-blank; manual via DataStore (`UiPreferences.manual_contacts`) + `addManualContact`, ditandai "perlu terhubung".
5. **Rebrand** — string value Kurir/Courier → Caraka (ID+EN) + string baru (`tab_*`, kontak, catatan, manual).
6. **Cleanup nav + dialog host** — hapus `Screen.Courier` (BottomNavBar) + composable + tile Home + `courierCarryCount`; host dialog courier app-wide di `MainActivity`; pertahankan `CourierHistory`; pensiunkan `CourierScreen.kt`; hapus stealth UI (`StealthBroadcastDialog`/`StealthChallengeDialog`/`StealthCredentialShareSheet`/ModeChip stealth).
7. **i18n & a11y** — parity ID==EN; contentDescription tombol ikon baru.

## Verifikasi
- `cd app && ./gradlew :app:installDebug`; force-stop + relaunch.
- Grep: tak ada `Screen.Courier` (selain History); tak ada teks "Kurir"/"Courier" di string values; parity ID==EN.
- Device: 2 tab; send B→Z (pesan Z + catatan B) → OFFER B menampilkan catatan → accept → Z decrypt; kontak persisten setelah restart; manual ditandai "perlu terhubung"; gelap/terang OK.

## Risiko
- Catatan-kurir plaintext (terbaca B) — beri hint UI. Dialog app-wide jangan dobel-host. Stealth backend dorman. Peer ber-kunci awet (pertimbangkan pruning by lastSeen nanti).
