# CARAKA — Device Test & Refinement Plan
> Disusun: 13 Juni 2026 · Setelah seluruh 4 fase program migrasi diimplementasikan (compile + assembleDebug hijau, BELUM device-test).
> Acuan: `caraka-architecture-baseline.md` (mengikat), `caraka-migration-program.md`, `caraka-migration-review.md`.
> Tujuan dokumen: (A) apa yang HARUS diuji di gawai, (B) apa yang masih perlu disempurnakan.

Status implementasi: Fase 0+1 di commit `f525b82`; Fase 2+3 di commit berikutnya. Ini adalah **gerbang field-test wajib** yang ditetapkan baseline sebelum item POSTPONE boleh dibuka.

---

# BAGIAN A — Rencana Uji Gawai

## A0. Prasyarat & Setup
- [ ] Build & install debug APK ke **minimal 3 gawai** (idealnya 5+), sertakan OEM agresif: **Infinix / Tecno (HiOS) / Redmi (MIUI)**.
- [ ] Mode **pesawat** dengan WiFi ON (simulasi blackout total tanpa seluler/internet).
- [ ] Aktifkan logcat filter tag: `WifiDirect`, `MeshRouter`, `MeshFGS`, `MeshSocket`, `CarakaDB`, `DBPassphrase`.
- [ ] Siapkan satu gawai yang **sudah punya install lama (DB v2)** untuk uji migrasi (lihat A1). Kalau tidak ada, install build lama dulu, kirim beberapa pesan, baru update.

---

## A1. Migrasi Database v2 → v3 (PALING KRITIS — risiko crash/data-loss)
Validasi skema Room terjadi **runtime** saat DB dibuka. Ini titik paling rawan.
- [ ] Update dari versi ber-DB v2 (ada pesan & peer lama) → **app tidak crash saat buka**.
- [ ] Data lama (riwayat chat, peer) **tetap utuh** setelah upgrade.
- [ ] Cek logcat: tidak ada `IllegalStateException`/`Migration didn't properly handle` dari Room.
- [ ] Tabel `outbox` & kolom `messages.deliveryStatus` ada (kirim 1 pesan → entri outbox muncul).
- [ ] Fresh install (tanpa DB lama) → DB v3 langsung, tanpa error.
- **Gagal-kriteria:** jika crash/validation error → STOP, periksa kesesuaian `MIGRATION_2_3` vs entity (kemungkinan beda tipe/DEFAULT).

## A2. Fase 1 — Reliable Unicast (EU-1.1…1.5)
- [ ] **Happy path:** Device A → unicast TEXT ke B (terjangkau). Status A berubah **SENT → DELIVERED** setelah B menerima (ikon `DoneAll`).
- [ ] **ACK nyata:** verifikasi DELIVERED muncul HANYA setelah ACK diterima (cek log `handleAck` di A), bukan langsung saat kirim.
- [ ] **Tidak ada false-delivered:** matikan B sesaat setelah A kirim sebelum ACK → status A tetap **SENT**, bukan DELIVERED (D5).
- [ ] **Retry & FAILED:** B tetap mati. A mencoba ulang (lihat backoff di log) maks 4× lalu status **FAILED**. Tidak ada retry tak-terhingga.
- [ ] **SOS tidak di-ACK:** broadcast SOS dari A → B menerima tapi **tidak** mengirim ACK; UI SOS berlabel "disiarkan", tidak pernah "sampai" (D6).
- [ ] **Multi-hop ACK:** A→(relay C)→B. ACK dari B kembali ke A lewat C. Status A jadi DELIVERED.

## A3. Fase 2 — Limited DTN (EU-2.1…2.4)
- [ ] **Carry offline→online (inti DTN):** A kirim ke kontak **verified** B yang sedang MATI. Pesan tersimpan (status SENT/carrying). Nyalakan B → pesan **terkirim & DELIVERED** otomatis (lewat flush on-handshake / timer). **Ini fitur paling penting yang dibuktikan.**
- [ ] **Carry hanya verified:** A kirim ke peer **non-verified** yang mati → setelah 4× retry jadi **FAILED** (tidak di-carry selamanya). Bandingkan dengan verified yang terus di-carry sampai 24h.
- [ ] **Timer processor:** dengan B mati lalu hidup tanpa A mengirim apa pun yang baru → pesan carried tetap terkirim oleh timer FGS (~tiap 15s), bukan hanya saat A kirim lagi.
- [ ] **Kuota outbox:** (sulit dipicu manual; opsional) banjiri outbox → entri prioritas rendah/tertua di-drop jadi FAILED, SOS/EMERGENCY dipertahankan.
- [ ] **MeshRouter no-drop TANPA storm (khusus gawai ber-Aware):** kirim unicast ke tujuan yang belum ada di routing table di jalur Aware → pesan di-broadcast fallback, **tidak** memicu badai (cek log: tiap id hanya diteruskan sekali per node berkat dedup `seenIds` MeshRouter). Pantau tidak ada lonjakan trafik/CPU.
- [ ] **FGS bertahan:** layar mati 10+ menit → kirim dari device lain → notifikasi tetap masuk (mesh hidup di background).

## A4. Fase 3 — Power Optimization (EU-3.1, 3.2)
- [ ] **Idle widening:** diamkan mesh >60s tanpa pesan → cek log interval beacon/gossip/discovery melebar ~4×.
- [ ] **Aktivitas reset:** kirim 1 pesan → interval kembali rapat (kembali ACTIVE).
- [ ] **Gossip tidak menahan active:** dua device berdekatan, TANPA pesan nyata >60s → keduanya tetap masuk idle meski saling gossip/heartbeat (karena `touchActivity` hanya pada TEXT/SOS/ACK/FLAG).
- [ ] **Deep-idle reachability (kritis):** diamkan >5 menit → active discovery berhenti (log "deep idle"). Lalu dari device lain **yang sudah satu LAN**, kirim pesan → node deep-idle **tetap menerima** (LAN listener pasif hidup) dan kembali ACTIVE.
- [ ] **Baterai:** ukur %/jam saat idle vs build sebelum Fase 3 (target: membaik). Catat per-OEM.
- [ ] **Catatan keterbatasan:** node deep-idle yang **belum** satu LAN dengan pengirim mungkin lambat ditemukan (discovery di-suspend). Ini batas yang diketahui tanpa BLE (lihat B1).

## A5. Regression (WAJIB tidak boleh rusak)
- [ ] Peer discovery (DNS-SD / LAN / Aware / Nearby) tetap jalan.
- [ ] Handshake + **TOFU** (tolak key berubah) tetap jalan.
- [ ] **Signature Ed25519** TEXT/SOS diverifikasi; pesan signature invalid di-drop.
- [ ] SOS broadcast multi-hop (≥3 hop) tetap menyebar.
- [ ] E2E enkripsi unicast tetap benar (plaintext muncul di penerima, bukan di header).
- [ ] Anti-replay: pesan ID sama tidak dobel di UI, termasuk **setelah restart app** (dedup persisten EU-0.2).
- [ ] Jam-salah (D1): set jam satu device melenceng >5 menit → pesannya **tetap diterima** (tidak di-drop oleh drift).
- [ ] Secure wipe / clear identity tetap membersihkan semua (termasuk outbox).

## A6. Kriteria Lulus Gerbang Field-Test
Lulus bila A1 (migrasi) aman, A2 + A3 (carry offline→online + status jujur) terbukti, A4 tidak membuat node tak-terjangkau di LAN, dan A5 nol regresi. Baru setelah ini item POSTPONE (Bagian B) boleh dipertimbangkan.

---

# BAGIAN B — Yang Masih Perlu Disempurnakan

Diurutkan dari yang paling berdampak. Setiap item menyebut status baseline-nya.

## B1. Channel presence hemat-daya (BLE) — membuka EU-3.2 penuh · [D15 POSTPONE]
- **Kenapa:** EU-3.2 saat ini hanya versi aman (suspend discovery, LAN listener tetap hidup). WiFi-off penuh **tidak bisa** dilakukan tanpa radio low-power untuk presence/wake — kalau WiFi dimatikan total, node tak bisa dibangunkan.
- **Aksi:** evaluasi BLE **presence-only** (advertising untuk wake + discovery ringan), data tetap di WiFi. Hanya jika field test A4 membuktikan baterai WiFi masih terlalu boros.
- **Gerbang:** data baterai dari A4.

## B2. Keaslian ACK (anti-spoof) · penyempurnaan keamanan
- **Kenapa:** ACK saat ini **tidak ditandatangani**. Penyerang bisa mengirim ACK palsu agar pesan kita tampil DELIVERED prematur. Dampak rendah (kosmetik: hanya menandai pesan kita sendiri "sampai"), tapi tidak ideal untuk app darurat.
- **Aksi:** tandatangani ACK (Ed25519) ATAU minimal verifikasi `senderId` ACK == `recipientId` pesan asli sebelum menandai DELIVERED. Sudah ada infrastruktur signature.
- **Prioritas:** Medium.

## B3. UI status "carrying" eksplisit · penyempurnaan UX
- **Kenapa:** pesan yang sedang di-carry (verified, peer offline) tampil **SENT** hingga 24 jam atau sampai DELIVERED. Pengguna tak tahu pesan "sedang menunggu peer muncul".
- **Aksi:** tambah state UI "tertunda/menunggu" (mis. ikon jam) untuk entri outbox yang sudah lewat cap retry tapi masih di-carry. Tampilkan timestamp asli pada pesan basi (cegah salah-tafsir).
- **Prioritas:** Medium.

## B4. Verifikasi outbox/ACK lintas-transport · validasi
- **Kenapa:** outbox+ACK diuji utama di WiFi Direct/LAN. Perlu pastikan jalur **Nearby Connections** & **Aware** juga membawa unicast + ACK dengan benar (mereka difunnel ke brain yang sama, tapi behaviour `sendToPeer` per-transport berbeda).
- **Aksi:** uji A2/A3 spesifik pada gawai yang aktif di Aware dan di Nearby.
- **Prioritas:** Medium (bagian dari field test, tapi layak disorot).

## B5. Persistensi dedup transit · [D3 — saat ini in-memory + router LRU]
- **Kenapa:** dedup transit (MeshSocketManager `seenIds` & MeshRouter `seenIds`) **in-memory**; hilang saat proses mati. Baseline membolehkan persistensi transit hanya jika field test membuktikan replay transit nyata.
- **Aksi:** jika A5 menunjukkan replay transit setelah restart, aktifkan tabel `RelayedMessageEntity` (sudah ada, kini idle) untuk dedup transit persisten.
- **Gerbang:** bukti dari A5.

## B6. Skalabilitas (gossip/forwarding) · [D12 POSTPONE]
- **Kenapa:** masih flooding + hop-limit. Hancur di >30 node (riset).
- **Aksi:** aktifkan gossip probabilistik HANYA jika field test skala besar menunjukkan kongesti. **Bukan** Spray-and-Wait (ditolak, D11).
- **Gerbang:** field test densitas tinggi.

## B7. Carry untuk non-kontak · [D8 POSTPONE]
- **Kenapa:** carry kini terbatas kontak verified (membatasi abuse). Memperluas ke umum menambah jangkauan tapi membuka storage/battery DoS.
- **Aksi:** hanya setelah threat-model abuse + kuota terbukti efektif di lapangan.

## B8. Kebersihan produksi · belum tersentuh program migrasi
- [ ] Package masih `com.example.caraka` → ganti ke package produksi sebelum rilis (Play Store menolak `com.example`).
- [ ] Hardening social-graph metadata (onion/anonim) — di luar lingkup v1 [D2], didokumentasikan sebagai residual risk yang diterima.
- [ ] Tinjau warning deprecation (`DhcpInfo`, `Divider`, `quadraticBezierTo`) — non-blok, kebersihan.

---

# Ringkasan Prioritas Penyempurnaan (setelah lulus field test)
1. **B1** BLE presence-only — jika baterai terbukti masalah (membuka EU-3.2 penuh).
2. **B2** ACK anti-spoof — keamanan.
3. **B3** UI "carrying" — kejujuran status (sejalan prinsip baseline).
4. **B4** validasi lintas-transport — sudah sebagian di field test.
5. **B5/B6/B7** — gated, hanya jika data lapangan menuntut.
6. **B8** — sebelum rilis publik.

---

*Catatan: jangan kerjakan item POSTPONE (B1/B5/B6/B7) sebelum gerbang field-test (A6) lulus. Itu prinsip baseline — keputusan dibuka oleh data multi-device nyata, bukan analisis.*
