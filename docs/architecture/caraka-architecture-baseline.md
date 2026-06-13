# CARAKA — Authoritative Architecture Baseline
> Status: **MENGIKAT** (authoritative). Semua rencana implementasi ke depan WAJIB mengikuti dokumen ini.
> Disusun: 13 Juni 2026 · Peran: Chief Architect · Technical Lead · Principal DTN Engineer
> Menyelesaikan perselisihan antara: `RESEARCH_ARSITEKTUR_CARAKA.md`, `caraka-gap-analysis.md`, `caraka-migration-review.md`
> Prinsip: jangan menambah teknologi baru kecuali benar-benar perlu.

Dokumen ini adalah keputusan final. Di mana riset, gap analysis, dan review berbeda pendapat, keputusan di sini yang berlaku. Tidak memuat kode maupun daftar tugas implementasi — itu domain dokumen perencanaan turunan.

---

# Final Architecture Decisions

Setiap rekomendasi yang diperdebatkan diberi tepat satu putusan: **ACCEPT** / **REJECT** / **POSTPONE**. Ringkasan:

| # | Komponen | Putusan | Inti keputusan |
|---|----------|---------|----------------|
| D1 | Time / expiry model | **ACCEPT** | Hop-count + monotonic uptime; wall-clock bukan gerbang anti-replay |
| D2 | Metadata privacy model | **ACCEPT** | peerId = fingerprint kunci (bukan PII); SOS publik by-design; konten selalu terenkripsi; social-graph hardening = POSTPONE |
| D3 | Deduplication | **ACCEPT** (didefinisi ulang) | Dua tingkat: `messageExists()` untuk pesan tersimpan + LRU in-memory untuk transit |
| D4 | Outbox + ACK (unicast) | **ACCEPT** (satu unit) | End-to-end ACK untuk unicast TEXT; outbox dengan retry berbatas |
| D5 | Implicit ACK sebagai delivery | **REJECT** | Hanya bukti hop-1; menyesatkan; boleh untuk telemetry, bukan status "terkirim" |
| D6 | ACK untuk SOS/broadcast | **REJECT** | Best-effort; andalkan redundansi flooding; tidak ada ACK implosion |
| D7 | Store-carry-forward (terbatas) | **ACCEPT** (lingkup dibatasi) | Carry hanya untuk kontak verified + prioritas tinggi + kuota keras |
| D8 | Carry untuk orang asing (epidemic umum) | **POSTPONE** | Butuh threat-model abuse + kuota terbukti; tunggu field test |
| D9 | Transport hooks (ubah kontrak `MeshTransport`) | **REJECT** | Ganti dengan queue-processor berbasis timer (non-invasif) |
| D10 | Queue-processor berbasis timer | **ACCEPT** | Flush opportunistic + debounce flapping |
| D11 | Binary Spray-and-Wait | **REJECT** | Tak terpetakan ke transport broadcast; optimasi prematur |
| D12 | Gossip / probabilistic forwarding | **POSTPONE** | Cadangan skala; aktifkan hanya jika kongesti terbukti |
| D13 | Adaptive beaconing (kompleks/accelerometer) | **POSTPONE** | Risiko melewatkan kontak singkat |
| D14 | Duty-cycle dua-state + matikan WiFi saat idle | **ACCEPT** | Kemenangan baterai terbesar yang aman |
| D15 | BLE backbone (mesh GATT penuh) | **POSTPONE** | Risiko Android tertinggi; payoff belum tervalidasi |
| D16 | MeshRouter (BATMAN) sebagai gerbang DTN | **REJECT** | Asumsi konektivitas kontinu; tak boleh men-drop "no route" |

Penjelasan per keputusan di bawah.

---

## D1 — Time / Expiry Model → **ACCEPT**
- **Keputusan:** Kedaluwarsa & anti-replay berbasis **hop-count (TTL) + monotonic uptime relatif**, bukan wall-clock absolut. Wall-clock TIDAK boleh jadi satu-satunya gerbang anti-replay.
- **Reasoning:** Skenario darurat = tanpa NTP. `MAX_TIMESTAMP_DRIFT_MS ±5 menit` saat ini akan men-drop total pesan dari node ber-jam-salah, atau membuka replay. Dedup berbasis ID + hop-limit tidak bergantung pada jam tersinkron.
- **Risiko:** Tanpa wall-clock, "umur" pesan lintas-device lebih sulit; mitigasi dengan TTL hop + timestamp pengirim sebagai *hint* (bukan gerbang).
- **Alternatif dipertimbangkan:** Vector clock (terlalu berat untuk skala ini); sinkronisasi jam via gossip (rapuh). Ditolak demi kesederhanaan.
- **Prasyarat:** Tidak ada; ini fondasi yang menopang D3, D4, D7.

## D2 — Metadata Privacy Model → **ACCEPT**
- **Keputusan:** `peerId` = fingerprint kunci publik (bukan nomor telepon/PII). Konten unicast **selalu** terenkripsi E2E (X25519+crypto_box) & ditandatangani (Ed25519). SOS **publik by-design** (semua harus bisa baca). Hardening social-graph (mis. onion/relay anonim) = **POSTPONE**.
- **Reasoning:** Pelajaran utama Bridgefy (Royal Holloway): header plaintext sender/recipient → deanonymization. CARAKA tidak bisa menyembunyikan recipientId sepenuhnya karena routing/carry butuh tahu tujuan — tapi peerId yang berupa fingerprint kunci jauh lebih baik dari MAC/nomor telepon. Konten tidak pernah bocor.
- **Risiko:** Residual: pengamat di jangkauan radio tetap bisa menyusun graf "fingerprint A ↔ fingerprint B". Ini **keterbatasan yang diterima untuk v1**, didokumentasikan, bukan diabaikan.
- **Alternatif dipertimbangkan:** Onion routing (kompleksitas Major, throughput hancur di mesh low-bandwidth); rotating ephemeral IDs (memecah continuity TOFU). Ditunda.
- **Prasyarat:** Tidak ada untuk baseline; hardening lanjutan butuh keputusan terpisah.

## D3 — Deduplication → **ACCEPT (didefinisi ulang)**
- **Keputusan:** Dedup **dua tingkat**: (a) pesan yang *disimpan* untuk kita → andalkan `MessageDao.messageExists()` (primary key, sudah persisten via SQLCipher); (b) pesan *transit* (di-relay, bukan untuk kita) → LRU in-memory yang sudah ada. Tabel `RelayedMessageEntity` persisten **hanya** diaktifkan jika field test membuktikan replay transit nyata.
- **Reasoning:** Gap analysis menyebut "wire up RelayDao" sebagai quick win #1; review menunjukkan `messageExists()` sudah memberi dedup persisten gratis untuk pesan tersimpan, dan TTL pesan biasanya lebih pendek dari downtime restart. Jadi nilai persistensi transit lebih kecil dari klaim awal.
- **Risiko:** LRU transit hilang saat proses mati → jendela replay sempit; dapat diterima karena hop-limit + `messageExists()` membatasi dampak.
- **Alternatif dipertimbangkan:** Bloom filter (REJECT — false positive bisa men-drop pesan sah, fatal untuk SOS); tabel relay penuh di hot-path (REJECT awal — biaya AES SQLCipher per pesan saat flooding).
- **Prasyarat:** D1 (model kedaluwarsa untuk cleanup).

## D4 — Outbox Queue + End-to-End ACK → **ACCEPT (sebagai SATU unit)**
- **Keputusan:** Outbox persisten dan ACK end-to-end dibangun **bersama**, terbatas pada **unicast TEXT**. Retry **berbatas** (mis. beberapa kali, backoff), bukan tak terhingga. Status delivery digerakkan dari ACK sebenarnya.
- **Reasoning:** Review menunjukkan outbox tanpa ACK = retry buta berbasis timer yang memperparah kongesti; keduanya saling bergantung. Gap analysis salah mengurut M1 sebelum M2.
- **Risiko:** Retry storm (mitigasi: batas retry + backoff); ACK hilang → retry sampai TTL habis (dapat diterima).
- **Alternatif dipertimbangkan:** Fire-and-forget murni (model BitChat/Bridgefy) — ditolak karena CARAKA menargetkan reliabilitas darurat; retry tak terbatas — ditolak demi kontrol kongesti.
- **Prasyarat:** D1, D3, dan model lifecycle pesan (di bawah).

## D5 — Implicit ACK sebagai status Delivery → **REJECT**
- **Keputusan:** Implicit ACK (mendengar rebroadcast) TIDAK boleh menandai pesan "DELIVERED". Boleh dipakai sebagai telemetry "relay progress" internal.
- **Reasoning:** Overhearing hanya membuktikan kemajuan hop-1, bukan sampai tujuan akhir. Di WiFi Direct (forwarding unicast via socket) dan Aware, overhearing bahkan tidak terjadi. Menampilkan "SOS terkirim" yang palsu berbahaya di konteks nyawa.
- **Risiko:** Tanpa implicit ACK, unicast bergantung pada explicit ACK (D4); SOS tidak punya konfirmasi (lihat D6) — diterima.
- **Alternatif dipertimbangkan:** Implicit ACK sebagai sinyal lemah "sedang diteruskan" di UI — boleh, asalkan TIDAK diberi label "terkirim/sampai".

## D6 — ACK untuk SOS/Broadcast → **REJECT**
- **Keputusan:** SOS & broadcast **tidak** menggunakan ACK. Best-effort dengan redundansi flooding + hop-limit.
- **Reasoning:** Broadcast ke banyak penerima → ACK implosion (badai ACK balik) yang membanjiri jaringan justru saat darurat. SOS tidak punya recipient tunggal yang bermakna untuk di-ACK.
- **Risiko:** Pengirim SOS tidak tahu pasti pesan sampai; UI harus jujur ("disiarkan ke jaringan", bukan "terkirim ke X").
- **Alternatif dipertimbangkan:** Aggregated/sampled ACK — kompleksitas tinggi, ditunda tanpa batas.

## D7 — Store-Carry-Forward (terbatas) → **ACCEPT (lingkup dibatasi)**
- **Keputusan:** Node **boleh** menyimpan & meneruskan pesan untuk tujuan tak terjangkau, TAPI v1 dibatasi pada: **(a) kontak QR-verified**, **(b) pesan prioritas tinggi (SOS) + unicast ke kontak verified**, **(c) kuota storage keras + TTL maksimum**.
- **Reasoning:** Ini inti nilai DTN yang menjawab keluhan "komunikasi masih sangat terbatas, fokus device-to-device". Tapi review benar: carry tanpa batas = storage/battery DoS + bocoran social-graph. Model Briar (carry hanya untuk kontak) membatasi abuse.
- **Risiko:** Pesan basi terkirim terlambat bisa menyesatkan (mitigasi: TTL + tampilkan timestamp asli); kuota penuh → drop prioritas rendah dulu.
- **Alternatif dipertimbangkan:** Carry epidemic untuk semua (D8, POSTPONE); tidak carry sama sekali (mengkhianati tujuan DTN). Jalan tengah dipilih.
- **Prasyarat:** D1, D2, D4, Resource Management Model (di bawah).

## D8 — Carry untuk Orang Asing (epidemic umum) → **POSTPONE**
- **Keputusan:** Tunda sampai threat-model abuse matang DAN kuota terbukti efektif di field test.
- **Reasoning:** Membawa pesan untuk peer yang tak dikenal memaksimalkan jangkauan tapi membuka DoS storage/baterai & relay konten tak terkendali. Belum ada data untuk menyetel batasnya dengan aman.
- **Prasyarat:** D7 stabil + field test densitas node.

## D9 — Transport Hooks (ubah kontrak `MeshTransport`) → **REJECT**
- **Keputusan:** Jangan ubah kontrak `MeshTransport` untuk menambah callback "peer available".
- **Reasoning:** Perubahan ini menyentuh WifiDirect, Aware, Nearby sekaligus (risiko tinggi B3), dan discovery Android flapping membuat event "peer available" tidak bersih. Ada alternatif non-invasif (D10).
- **Risiko:** Pendekatan timer sedikit kurang responsif dari event-driven; dapat diterima untuk DTN delay-tolerant.
- **Alternatif dipertimbangkan:** Hook dengan default no-op + debounce (masih mengubah kontrak; ditolak demi kesederhanaan).

## D10 — Queue-Processor Berbasis Timer → **ACCEPT**
- **Keputusan:** Pengiriman tertunda dijalankan oleh **processor periodik** (di Foreground Service) yang membaca peer reachable dan flush antrian, dengan **debounce** untuk peer yang flapping.
- **Reasoning:** Menggantikan transport-hook berisiko dengan mekanisme yang tidak menyentuh interface apa pun. Cocok dengan sifat delay-tolerant.
- **Risiko:** Thundering herd (banyak node flush ke 1 peer baru) — mitigasi dengan jitter acak + debounce.
- **Prasyarat:** D4 (outbox), Foreground Service (sudah ada).

## D11 — Binary Spray-and-Wait → **REJECT**
- **Keputusan:** Tidak mengimplementasikan Spray-and-Wait.
- **Reasoning:** SnW dirancang untuk kontak pairwise diskret; transport CARAKA adalah broadcast/cluster di mana "memberi setengah kopi ke satu node" tak terpetakan. Optimasi prematur untuk skala yang belum jadi bottleneck.
- **Risiko:** Pada skala besar, flooding bisa kongesti — ditangani oleh D12 (gossip) jika terbukti, bukan SnW.
- **Alternatif dipertimbangkan:** Gossip probabilistik + hop-limit (D12) — lebih sederhana & cocok broadcast.

## D12 — Gossip / Probabilistic Forwarding → **POSTPONE**
- **Keputusan:** Cadangan untuk mengatasi kongesti skala; aktifkan hanya jika field test menunjukkan flooding collapse.
- **Reasoning:** Riset benar bahwa flooding murni hancur >30 node, tapi CARAKA belum mendekati skala itu. Hop-limit + dedup yang ada memadai untuk sekarang.
- **Prasyarat:** Data lapangan tentang densitas & laju pesan.

## D13 — Adaptive Beaconing (kompleks) → **POSTPONE**
- **Keputusan:** Tunda adaptasi berbasis accelerometer/contact-history.
- **Reasoning:** Back-off agresif bisa melewatkan kontak singkat (kurir lewat) — justru inti DTN; logika app bisa bertengkar dengan throttling OS.
- **Prasyarat:** Pengukuran baterai field test.

## D14 — Duty-Cycle Dua-State + Matikan WiFi Idle → **ACCEPT**
- **Keputusan:** Dua state saja (active/idle): interval beacon lebih panjang saat idle, dan **matikan WiFi Aware/Direct saat idle** (andalkan discovery ringan), nyalakan saat ada aktivitas/cluster.
- **Reasoning:** Kemenangan baterai terbesar yang aman adalah mematikan radio boros saat idle, bukan menyetel interval secara rumit.
- **Risiko:** Latensi discovery naik saat idle; dapat diterima untuk delay-tolerant.
- **Prasyarat:** Tidak ada blocker; mengandalkan Foreground Service yang ada.

## D15 — BLE Backbone (mesh GATT penuh) → **POSTPONE**
- **Keputusan:** Tidak membangun mesh BLE-GATT penuh sekarang. Kandidat masa depan, dimulai dari **presence-only** jika diperlukan.
- **Reasoning:** BLE Android paling rapuh (GATT 133, batas koneksi, throttling, OEM agresif); payload kecil memaksa fragmentasi; BitChat sendiri eksperimental. Risiko Major dengan payoff belum tervalidasi.
- **Risiko menunda:** Tanpa BLE, daya tahan baterai bergantung pada D14 (duty-cycle WiFi). Jika field test membuktikan WiFi tetap terlalu boros, BLE presence-only dipertimbangkan.
- **Alternatif dipertimbangkan:** BLE advertising untuk presence saja (lebih aman dari GATT mesh) — kandidat pertama jika POSTPONE dicabut.

## D16 — MeshRouter (BATMAN) sebagai Gerbang DTN → **REJECT**
- **Keputusan:** `MeshRouter` tidak boleh menjadi gerbang yang men-drop pesan "no route". Perannya dipertahankan hanya untuk optimasi next-hop pada jalur yang **kontinu** (Aware); keputusan simpan-vs-buang dipindah ke lapisan outbox/carry.
- **Reasoning:** BATMAN/OLSR mengasumsikan konektivitas kontinu (rute timeout 35s, drop tanpa next-hop) — kebalikan asumsi DTN. "Tidak ada rute sekarang" di DTN harus berarti "simpan & bawa".
- **Prasyarat:** D7 (carry) + D10 (queue-processor) menyediakan jalur simpan.

---

# Accepted Components
1. **Time/expiry model** berbasis hop-count + monotonic uptime (D1)
2. **Metadata privacy model**: peerId=fingerprint, konten E2E, SOS publik (D2)
3. **Two-tier deduplication**: `messageExists()` + LRU transit (D3)
4. **Outbox + end-to-end ACK** sebagai satu unit, unicast TEXT, retry berbatas (D4)
5. **Store-carry-forward terbatas**: kontak verified + prioritas + kuota (D7)
6. **Queue-processor berbasis timer** dengan debounce (D10)
7. **Duty-cycle dua-state + matikan WiFi idle** (D14)

# Rejected Components
1. **Implicit ACK sebagai status delivery** (D5) — boleh untuk telemetry, tidak untuk label "terkirim"
2. **ACK untuk SOS/broadcast** (D6) — best-effort
3. **Transport hook / ubah kontrak `MeshTransport`** (D9) — diganti timer
4. **Binary Spray-and-Wait** (D11) — tak terpetakan ke broadcast
5. **MeshRouter sebagai gerbang DTN** (D16) — asumsi konektivitas kontinu

# Deferred Components
1. **Carry untuk orang asing / epidemic umum** (D8) — butuh threat-model + field test
2. **Gossip/probabilistic forwarding** (D12) — aktifkan jika kongesti terbukti
3. **Adaptive beaconing kompleks** (D13) — butuh data baterai
4. **BLE backbone** (D15) — risiko Android tertinggi; mulai presence-only jika dicabut
5. **Metadata social-graph hardening** (bagian D2) — onion/anonim, kompleksitas Major

---

# Architecture Principles

1. **Store-carry-forward, bukan "harus terhubung".** Jaringan darurat selalu terpartisi. Pesan adalah unit yang disimpan, dibawa, diteruskan — bukan stream yang butuh koneksi simultan.
2. **Jujur kepada pengguna.** Status delivery hanya boleh menyatakan apa yang benar-benar diketahui (SENT vs DELIVERED end-to-end). Tidak ada konfirmasi palsu di konteks nyawa.
3. **Terdegradasi dengan anggun.** Tiap lapisan punya fallback: Aware→Direct→LAN→(carry). Kehilangan satu transport tidak mematikan mesh.
4. **Hemat sumber daya adalah fitur keselamatan.** Baterai = sumber daya paling langka saat listrik padam. Setiap fitur dinilai terhadap biaya energinya.
5. **Keamanan tidak boleh mundur.** Tidak ada plaintext konten; signature & TOFU wajib; tidak mengulang kesalahan Bridgefy.
6. **Kesederhanaan mengalahkan optimalitas teoretis.** Pilih mekanisme paling sederhana yang memenuhi target; tunda optimasi sampai data lapangan menuntut.
7. **Jangan menambah teknologi tanpa bukti kebutuhan.** Teknologi baru (BLE, LoRa, SnW) hanya masuk setelah validasi lapangan.
8. **Field test adalah gerbang, bukan formalitas.** Keputusan POSTPONE dicabut hanya oleh data multi-device nyata.

---

# Message Lifecycle

Satu unit pesan melewati state berikut. **Catatan percakapan (UI) dipisahkan dari unit transport (outbox)** — keduanya tidak boleh dicampur dalam satu entity.

**Pesan keluar (unicast TEXT):**
```
CREATED → QUEUED → SENT → DELIVERED        (jalur sukses)
                      └──→ EXPIRED          (TTL habis sebelum ACK)
                      └──→ FAILED           (retry berbatas habis)
```
- `CREATED`: dibuat, dienkripsi, ditandatangani, dicatat di chat-log UI.
- `QUEUED`: masuk outbox persisten (state PENDING).
- `SENT`: telah dikirim ke transport minimal sekali (UI: "terkirim ke jaringan").
- `DELIVERED`: explicit end-to-end ACK diterima (UI: "sampai"). **Hanya** dari ACK nyata.
- `EXPIRED`/`FAILED`: TTL/retry habis (UI: "gagal/kedaluwarsa").

**Pesan SOS/broadcast:**
```
CREATED → SENT (best-effort, disiarkan)     — TIDAK ADA state DELIVERED
```
- UI: "disiarkan ke jaringan", tidak pernah "sampai ke X".

**Pesan transit (di-relay, bukan untuk kita):**
```
RECEIVED → (dedup check) → RELAYED          (TTL>1, belum pernah dilihat)
                        └→ DROPPED           (duplikat / TTL habis)
                        └→ CARRIED           (tujuan verified tak terjangkau; simpan)
```

# Routing Strategy

- **Broadcast/SOS:** **controlled flooding** — hop-limit (TTL) + dedup, tanpa ACK. Mekanisme yang sudah ada dipertahankan.
- **Unicast TEXT (tujuan terjangkau):** kirim langsung via `sendToPeer` (LAN unicast / next-hop), dengan outbox+ACK (D4).
- **Unicast TEXT (tujuan tak terjangkau, kontak verified):** **carry-and-forward terbatas** (D7) — simpan, kirim saat tujuan muncul (dipicu queue-processor timer D10).
- **MeshRouter (BATMAN):** dipertahankan **hanya** sebagai optimasi next-hop di jalur kontinu (Aware); **bukan** gerbang yang men-drop (D16). Tanpa rute → serahkan ke outbox/carry, bukan buang.
- **Skala besar (POSTPONE):** jika flooding collapse terbukti, aktifkan gossip probabilistik (D12). **Bukan** Spray-and-Wait (D11).

# Delivery Semantics

| Tipe | Jaminan | Konfirmasi | Label UI |
|------|---------|------------|----------|
| Unicast TEXT | At-least-once, end-to-end ACK | Explicit ACK (D4) | CREATED → terkirim ke jaringan → sampai |
| SOS / Broadcast | Best-effort | Tidak ada (D6) | disiarkan ke jaringan |
| Transit relay | Best-effort, dedup | — | (tidak ditampilkan ke pengguna) |

- "Sampai/DELIVERED" **hanya** dari explicit end-to-end ACK. Implicit ACK **dilarang** sebagai status delivery (D5).
- Duplikat ditoleransi (at-least-once); idempotensi dijamin dedup (D3).

# Metadata Privacy Model

- **Identitas:** `peerId` = fingerprint kunci publik. Bukan nomor telepon, bukan MAC. Lebih privat dari Bridgefy.
- **Konten:** unicast **selalu** E2E-encrypted (X25519+crypto_box) & signed (Ed25519). SOS tidak terenkripsi **secara sengaja** (semua harus baca) tapi tetap signed.
- **Header routing:** `senderId`/`recipientId` terlihat untuk routing & carry. Diterima sebagai **keterbatasan v1 yang didokumentasikan**.
- **Yang dilarang:** menambah metadata identifikasi baru ke header; menyimpan konten plaintext pada node carrier.
- **Residual risk yang diterima:** pengamat di jangkauan radio dapat menyusun graf relasi fingerprint. Hardening (onion/anonim) = POSTPONE (D8/D2).

# Resource Management Model

Kontrak lintas semua fitur DTN (wajib ada sebelum carry D7 diaktifkan):
- **Kuota storage outbox/carry:** batas keras per-device (mis. jumlah pesan & total byte); penuh → drop berdasar prioritas.
- **TTL maksimum:** tiap pesan punya umur maksimum (hop + waktu relatif); lewat itu → dibuang dari queue/carry.
- **Prioritas:** SOS/EMERGENCY > TEXT unicast > status. Drop policy: buang prioritas terendah & paling tua & paling banyak ter-replika lebih dulu.
- **Batas retry:** unicast retry berbatas (beberapa kali, backoff + jitter), bukan tak terhingga.
- **Batas replika/relay:** hop-limit dipertahankan; dedup mencegah loop.
- **Carry hanya untuk kontak verified** (v1) untuk membatasi abuse storage.

# Scalability Targets

Diturunkan dari riset DTN, ditetapkan sebagai target desain (bukan janji):
- **Hop efektif:** 3-8 hop untuk pesan yang berhasil terkirim. Desain untuk ini; jangan kejar hop tak terbatas.
- **Node per social region:** puluhan (shelter/kampus/blok kota) — bukan ribuan dalam satu channel.
- **Densitas berguna:** ≥10-50 device aktif/km² per region untuk DTN delay-tolerant yang bermakna.
- **Rezim operasi:** delay-tolerant (delay detik–menit, kadang jam untuk carry). Real-time hanya dalam cluster lokal 1-2 hop.
- **Ambang waspada flooding:** ~30 node aktif → titik di mana D12 (gossip) mungkin perlu diaktifkan.

# Success Criteria

Baseline dianggap berhasil jika, dalam field test multi-device (mode pesawat / blackout simulasi):
1. Pesan unicast ke peer **offline** tidak hilang — terkirim & ber-status DELIVERED saat peer kembali (D7+D4).
2. Status UI **jujur**: tidak pernah "sampai" tanpa ACK end-to-end nyata.
3. SOS menyebar multi-hop (≥3 hop) ke puluhan device tanpa ACK implosion.
4. Tidak ada replay/duplikasi yang tampak ke pengguna (dedup D3 bekerja).
5. Mesh bertahan **berjam-jam** dengan layar mati tanpa menghabiskan baterai secara tidak wajar (D14 + Foreground Service).
6. Tidak ada konten plaintext yang bocor; signature & TOFU diverifikasi.
7. Queue menghormati kuota — tidak ada storage/battery exhaustion pada node relay.

# Non-Goals

CARAKA **tidak** berusaha (untuk baseline ini):
1. **Real-time voice/video over multi-hop** — bandwidth & latensi mesh tidak mendukung (pelajaran Serval).
2. **Jaminan pengiriman city-wide** — DTN bersifat best-effort/delay-tolerant, bukan carrier-grade.
3. **Mesh BLE-GATT penuh** (D15) — risiko Android terlalu tinggi untuk v1.
4. **Spray-and-Wait / routing canggih** (D11) — kesederhanaan diutamakan.
5. **Anonimitas penuh / perlindungan social-graph** (D2) — di luar lingkup v1.
6. **WiFi HaLow & Bluetooth Mesh standar** — tidak feasible di smartphone (riset).
7. **Carry pesan untuk orang asing tanpa batas** (D8) — risiko abuse.
8. **Sinkronisasi jam terdistribusi** (D1) — dihindari demi ketahanan offline.

# Final Architecture Baseline

```
┌──────────────────────────────────────────────────────────────────┐
│  UI / APLIKASI                                                     │
│  Chat, SOS, authority badge · status JUJUR (SENT vs DELIVERED)     │
├──────────────────────────────────────────────────────────────────┤
│  KEAMANAN (dipertahankan, tidak mundur)                            │
│  E2E X25519+crypto_box · Ed25519 signature · TOFU · FGS            │
├──────────────────────────────────────────────────────────────────┤
│  LAPISAN DTN (baru — inti baseline ini)                            │
│  • Outbox persisten + End-to-End ACK (unicast TEXT)         [D4]   │
│  • Store-carry-forward TERBATAS (verified + prioritas + kuota)[D7] │
│  • Queue-processor berbasis TIMER + debounce                [D10]  │
│  • Two-tier dedup (messageExists + LRU)                     [D3]   │
│  • Model kedaluwarsa hop+monotonic (bukan wall-clock)       [D1]   │
│  • Resource Management (kuota, TTL, prioritas, batas retry)        │
├──────────────────────────────────────────────────────────────────┤
│  ROUTING                                                           │
│  • Broadcast/SOS: controlled flooding + hop-limit (best-effort)    │
│  • Unicast: direct + carry terbatas (BUKAN gerbang BATMAN)  [D16]  │
│  • (POSTPONE) gossip probabilistik jika kongesti terbukti   [D12]  │
├──────────────────────────────────────────────────────────────────┤
│  TRANSPORT (kontrak TIDAK diubah — D9)                             │
│  WiFi Aware (burst) · WiFi Direct (fallback/brain) · Nearby · LAN  │
│  Duty-cycle dua-state + matikan WiFi saat idle              [D14]  │
│  (POSTPONE) BLE presence-only → mungkin data                [D15]  │
└──────────────────────────────────────────────────────────────────┘

PRINSIP PENGIKAT: store-carry-forward · jujur ke pengguna · degradasi anggun ·
hemat baterai = keselamatan · keamanan tak mundur · sederhana > optimal ·
tanpa teknologi baru tanpa bukti · field test sebagai gerbang.
```

**Status dokumen:** Otoritatif & mengikat. Setiap rencana implementasi turunan WAJIB merujuk dan mematuhi keputusan D1-D16 di sini. Perubahan pada baseline ini hanya melalui revisi eksplisit dokumen ini, didukung data field test untuk item POSTPONE.

---

*Menyelesaikan perselisihan: di mana riset merekomendasikan SnW & BLE agresif, dan gap analysis mengurut M1 sebelum M2 serta mengusulkan transport-hook, baseline ini mengikuti review kritis — dengan koreksi fondasi (jam, metadata) dan penundaan optimasi prematur. Tidak ada teknologi baru yang diperkenalkan di luar yang sudah dianalisis.*
