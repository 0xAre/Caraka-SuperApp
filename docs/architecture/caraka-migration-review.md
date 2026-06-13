# CARAKA — Critical Review of the DTN Migration Path
> Disusun: 13 Juni 2026
> Peran reviewer: Principal Distributed Systems Architect · DTN Researcher · Android Networking Engineer · Security Reviewer
> Basis: `docs/RESEARCH_ARSITEKTUR_CARAKA.md` + `docs/architecture/caraka-gap-analysis.md`
> Tujuan: **menantang** rencana migrasi, bukan mendesain ulang. Fokus pada kebenaran arsitektur & pengurangan risiko.

---

# Architecture Validation

Rencana migrasi (QW1 dedup → B4 outbox table → M1 outbox → M2 ACK → B3 hook → M3 carry → M4 SnW → M6 BLE) **arahnya benar** tetapi mengandung **empat kesalahan struktural** yang harus dikoreksi sebelum eksekusi:

1. **Urutan M1→M2 terbalik secara logis.** Outbox queue (M1) yang bisa retry tidak punya makna tanpa sinyal sukses/gagal — dan sinyal itu adalah ACK (M2). Outbox tanpa ACK hanya bisa retry buta berdasarkan timer, yang justru memperburuk kongesti. **M1 dan M2 adalah satu unit, bukan dua langkah berurutan.**

2. **Dua isu fondasi tidak terdaftar sebagai blocker:** (a) **ketergantungan jam** — anti-replay & TTL bergantung pada `MAX_TIMESTAMP_DRIFT_MS ±5 menit`, padahal skenario darurat = tanpa NTP, jam antar-device pasti melenceng; (b) **privasi metadata** — `senderId`/`recipientId` di header plaintext adalah persis vektor serangan social-graph yang menjatuhkan Bridgefy (diperingatkan di riset sendiri), dan store-carry-forward **memperparahnya**.

3. **Implicit ACK disalahartikan sebagai delivery.** "Mendengar rebroadcast pesan sendiri" hanya membuktikan **kemajuan satu hop**, bukan sampai ke tujuan akhir. Menandai pesan "DELIVERED" karena tetangga me-relay-nya adalah **salah dan menyesatkan pengguna** ("pesan SOS Anda terkirim" padahal baru 1 hop).

4. **Spray-and-Wait & BLE adalah optimasi/risiko prematur.** Keduanya menyelesaikan masalah *skala* (>30 node) dan *baterai* yang belum tervalidasi di lapangan. Membangunnya sekarang = mengoptimasi sesuatu yang belum terbukti jadi bottleneck.

**Verdict ringkas:** Fondasi (dedup, reliabilitas unicast) layak dikerjakan. Lapisan DTN penuh (carry, SnW) layak secara konsep tapi butuh threat-model & batas sumber daya dulu. BLE backbone harus ditunda sampai ada data lapangan.

---

# Assumptions Review

Asumsi tersembunyi per komponen, dengan tantangannya.

### 1. Persistent Deduplication
- **Asumsi:** dedup persisten adalah quick win bernilai tinggi.
- **Tantangan:** Untuk pesan yang *disimpan* (TEXT/SOS untuk kita), `MessageDao.messageExists()` (primary key di DB terenkripsi) **sudah** dedup persisten — gratis, tinggal dipanggil. Yang benar-benar volatile hanyalah dedup untuk pesan *transit* (yang kita relay tapi bukan untuk kita). Nilai marginalnya lebih kecil dari klaim gap analysis.
- **Asumsi tersirat:** TTL pesan (menit) biasanya **lebih pendek** dari downtime restart proses. Jika benar, pesan yang sama jarang masih "in-flight" setelah restart → jendela replay nyata sempit. Foreground Service makin mengecilkannya.
- **Asumsi berbahaya:** insert DB terenkripsi (SQLCipher = AES per-page) di **hot path penerimaan saat flooding** murah. Pada laju pesan tinggi ini bisa men-stall coroutine pembaca socket.
- **Asumsi Bloom filter (dari riset):** Bloom filter punya **false positive** → bisa men-drop pesan sah secara diam-diam. Tabel DB tidak punya false positive tapi tumbuh tak terbatas. Riset menyebut Bloom, gap analysis pilih tabel — trade-off ini tidak dibahas eksplisit.

### 2. Outbox Queue
- **Asumsi:** retry dengan backoff bermakna. **Tantangan:** tanpa ACK, "gagal" tidak terdefinisi (transport fire-and-forget). Retry hanya bisa berbasis timer = retry buta.
- **Asumsi:** queue-processor di FGS bisa bangun & flush andal. **Tantangan:** Doze/OEM throttling tetap membatasi penjadwalan coroutine walau ada FGS; HiOS/MIUI/XOS terkenal agresif.
- **Asumsi:** semua pesan perlu retry. **Tantangan:** SOS/broadcast tidak punya recipient tunggal untuk di-ACK; retry-nya harus beda total dari unicast.

### 3. ACK System
- **Asumsi:** implicit ACK via overhearing berlaku universal. **Tantangan:** hanya berlaku di medium broadcast yang bisa "didengar" (BLE advertising). Di WiFi Direct (forwarding unicast via socket TCP lewat GO) dan Aware (NDP per-peer), kamu **tidak overhear** rebroadcast tetangga. Semantik implicit ACK **berbeda per transport** — asumsi yang tidak dipegang.
- **Asumsi:** overhearing = delivery. **Tantangan:** itu kemajuan hop-1, bukan end-to-end. Lihat Architecture Validation #3.
- **Asumsi:** jalur balik ACK ada. **Tantangan:** di DTN, jalur maju bisa ada saat jalur balik tidak (topologi asimetris/berubah).

### 4. Store-Carry-Forward
- **Asumsi:** node perantara mau menyimpan pesan untuk **orang asing**. **Tantangan:** ini donasi storage+baterai yang bisa diabuse (storage DoS). Briar — yang dipuji riset sebagai paling aman — sengaja **TIDAK** melakukan ini (hanya carry untuk kontak). Ada tegangan internal di riset: memuji model Briar tapi merekomendasikan carry ala epidemic.
- **Asumsi:** carrier tahu kapan "recipient muncul". **Tantangan:** untuk unicast OK (cek handshake peerId), tapi carrier memegang blob terenkripsi + **header plaintext** → ia tahu A mengirim ke B = bocoran social graph.
- **Asumsi:** pesan tertunda tetap berguna. **Tantangan:** status "aman" yang sampai 3 jam terlambat bisa **berbahaya** (mengarahkan ke lokasi yang sudah tidak aman).

### 5. Binary Spray-and-Wait
- **Asumsi:** kita bisa "menghitung kopi L" di overlay broadcast. **Tantangan:** SnW dirancang untuk **kontak pairwise diskret** (serahkan setengah kopi ke satu node, decrement). Di medium broadcast (satu TX → N tetangga) konsep "memberi setengah kopi ke satu node" tidak terpetakan bersih. Menerapkan SnW di atas transport flooding CARAKA **tidak akan berperilaku seperti teori**.
- **Asumsi:** kontak intermiten & jarang. **Tantangan:** mesh WiFi CARAKA lebih mirip "klaster terhubung" daripada "kontak pairwise jarang" — rezim di mana SnW unggul justru bukan rezim CARAKA. Di klaster padat, hop-limit flooding sudah memadai.
- **Asumsi:** node jujur soal L. **Tantangan:** node jahat bisa set L=0 (menekan) atau reset L (membanjiri).

### 6. Transport Hooks
- **Asumsi:** "peer available" adalah event diskret bersih. **Tantangan:** discovery itu **flapping** (peer muncul-hilang berulang). Flush-on-available naif → badai retransmisi.
- **Asumsi:** mengubah kontrak `MeshTransport` sepadan. **Tantangan:** ini perubahan berisiko tinggi (B3) menyentuh 3 transport. Ada alternatif non-invasif (timer tick) yang menghindari ubah interface sama sekali.

### 7. Adaptive Beaconing
- **Asumsi:** mengurangi beacon saat "stabil" aman. **Tantangan:** di bencana, perubahan topologi justru saat kamu **paling butuh** discovery cepat; back-off bisa **melewatkan kontak singkat** (kurir lewat) — padahal menangkap kontak singkat itulah inti DTN.
- **Asumsi:** gerakan (accelerometer) berkorelasi dengan peluang kontak. **Tantangan:** HP diam di shelter tetap perlu relay; gating berbasis gerakan bisa mematikan node statis yang justru jadi tulang punggung.
- **Asumsi:** adaptasi app-level efektif. **Tantangan:** OS Android sudah punya throttling scan/sensor sendiri; logika app bisa **bertengkar** dengan scheduler OS.

### 8. BLE Backbone
- **Asumsi:** BLE Android bisa jadi mesh andal. **Tantangan:** BLE Android **terkenal rapuh** — batas koneksi GATT (4-8), throttling scan, restriksi background, GATT_ERROR 133, variasi OEM ekstrem. BitChat sendiri berlabel eksperimental/belum terbukti.
- **Asumsi:** advertising cukup untuk pesan. **Tantangan:** payload 31 byte (legacy) / 255 byte (extended, tidak universal) << pesan CARAKA (terenkripsi+signature+JSON) → harus fragmentasi via GATT → connection-oriented → batas koneksi menggigit.
- **Asumsi:** device target (Infinix/Tecno/Redmi) konsisten. **Tantangan:** justru OEM ini paling agresif membunuh BLE background.

---

# Potential Design Mistakes

1. **Menandai "DELIVERED" dari implicit ACK (hop-1).** Menyesatkan pengguna di konteks nyawa. SOS yang baru 1 hop tidak boleh tampil "terkirim".
2. **Mengerjakan Outbox (M1) sebelum ACK (M2).** Menghasilkan retry buta berbasis timer = amplifikasi kongesti saat jaringan paling stres.
3. **Mengabaikan privasi metadata header.** Membangun carry-and-forward di atas header plaintext = membangun mesin pengumpul social-graph; mengulang kesalahan fatal Bridgefy yang justru jadi pelajaran utama riset.
4. **Mengabaikan ketergantungan jam.** Anti-replay & TTL berbasis wall-clock di lingkungan tanpa NTP → node ber-jam-salah di-drop total atau membuka replay. Fondasi yang retak.
5. **Memetakan Spray-and-Wait ke transport broadcast.** Mengasumsikan semantik kontak-pairwise di medium broadcast → perilaku tak terduga; kompleksitas tinggi tanpa jaminan manfaat di rezim klaster CARAKA.
6. **Mengubah kontrak `MeshTransport` (B3) padahal ada alternatif timer.** Mengambil risiko tinggi yang tidak perlu.
7. **Carry untuk orang asing tanpa kuota/threat-model.** Membuka storage/battery DoS dan relay konten tak terkendali.
8. **Menjadikan BLE backbone sebagai target arsitektur "pasti".** Mempertaruhkan upaya Major pada subsistem Android yang reliabilitasnya paling diragukan.

---

# Technical Risks

| Komponen | Risiko utama | Tingkat | Dampak Android-spesifik | Dampak baterai |
|----------|--------------|---------|-------------------------|----------------|
| Persistent dedup | DB write stall di hot path; tabel tumbuh tak terbatas saat flood | 🟡 Sedang | SQLCipher AES per-page mahal di MTK low-end | Rendah |
| Outbox queue | Retry storm; queue unbounded; Doze menahan flush | 🔴 Tinggi | OEM (HiOS/MIUI) bunuh background; FGS tak menjamin timing | Sedang (retry = TX berulang) |
| ACK | False-delivery (hop-1); ACK implosion di broadcast; ACK hilang→retry∞ | 🔴 Tinggi | Semantik overhearing tak berlaku di WiFi socket/Aware | Sedang |
| Store-carry-forward | Storage/battery DoS; bocoran social-graph; pesan basi berbahaya | 🔴 Tinggi | Storage terbatas; OEM bunuh proses → carry hilang | Tinggi (node jadi relay terus) |
| Spray-and-Wait | Semantik tak terpetakan ke broadcast; L manipulatif; tuning rapuh | 🟡 Sedang | — | Sedang |
| Transport hooks | Flapping → badai flush; thundering herd ke peer baru | 🟡 Sedang | Discovery Android noisy/flapping | Sedang |
| Adaptive beaconing | Melewatkan kontak singkat → mesh terfragmentasi | 🟡 Sedang | Bertengkar dgn throttling OS; sensor wakeup | (tujuan: turunkan, tapi bisa malah naik jika salah) |
| BLE backbone | GATT 133, batas koneksi, scan throttle, fragmentasi rapuh | 🔴 Tinggi | **Paling parah** — BLE Android rapuh, OEM variatif | Rendah (jika berhasil) — itu daya tariknya |
| **Lintas: jam** | Drift jam → drop/replay massal | 🔴 Tinggi | Tanpa NTP offline | — |
| **Lintas: metadata** | Social-graph deanonymization (ala Bridgefy) | 🔴 Tinggi | — | — |

---

# Simplifications

Alternatif yang lebih sederhana per item — sebagian besar **menunda** kompleksitas sampai terbukti perlu.

1. **Dedup:** Untuk pesan tersimpan, **panggil `messageExists()`** (sudah persisten via primary key) alih-alih tabel relay baru. Untuk transit, **perbesar LRU in-memory** + cleanup; persistensi penuh hanya jika field test menunjukkan replay nyata. *Hindari Bloom filter (false positive berbahaya).*
2. **Outbox + ACK:** Gabungkan jadi satu fitur. **End-to-end ACK hanya untuk unicast TEXT**, retry terbatas (2-3x), **tanpa ACK untuk SOS** (andalkan redundansi flooding). Jangan pakai implicit ACK untuk status delivery — paling banter untuk telemetry "relay progress".
3. **Store-carry-forward:** Versi-1 **carry hanya untuk kontak QR-verified** (model Briar) dan **hanya pesan prioritas tinggi (SOS)**, dengan **kuota storage keras + TTL**. Tunda carry unicast untuk orang asing.
4. **Spray-and-Wait:** Ganti dengan **probabilistic forwarding (gossip p<1) + hop-limit** yang sudah ada — jauh lebih sederhana di medium broadcast, menangani kongesti tanpa state replica. SnW penuh ditunda sampai data lapangan menuntut.
5. **Transport hooks:** Ganti event-driven hook (ubah kontrak, B3 risiko tinggi) dengan **queue-processor tick periodik** yang membaca peer reachable. **Tidak menyentuh interface `MeshTransport`.** Tambah debounce untuk flapping.
6. **Adaptive beaconing:** Cukup **dua state (active/idle)** dengan interval lebih panjang saat idle; jangan accelerometer/adaptif kompleks. Kemenangan baterai terbesar sebenarnya **mematikan WiFi Aware/Direct saat idle**, bukan menyetel beacon.
7. **BLE:** Jika dikejar, mulai dari **BLE advertising untuk presence/discovery saja** (bukan data); data tetap di WiFi saat klaster terbentuk. Hindari mesh BLE-GATT penuh di v1.
8. **Jam:** Pakai **TTL berbasis hop-count + monotonic uptime relatif**, bukan wall-clock absolut, untuk kedaluwarsa; longgarkan/buang cek drift timestamp sebagai satu-satunya gerbang anti-replay (gunakan ID-based dedup sebagai gerbang utama).

---

# Revised Migration Strategy

Strategi yang dikoreksi untuk kebenaran & risiko (bukan daftar tugas — ini reframing arsitektural).

**Lapisan 0 — Fondasi kebenaran (wajib lebih dulu, sering terlewat):**
- Putuskan **model TTL/kedaluwarsa yang tahan jam-salah** (hop-count + monotonic), karena ini menopang dedup, outbox, dan carry.
- Tetapkan **threat-model metadata**: apakah header sender/recipient boleh plaintext? Jika tidak, ini membatasi desain carry **sebelum** dibangun.
- Tetapkan **kebijakan sumber daya**: kuota storage queue, TTL maksimum, prioritas drop — sebagai kontrak lintas semua fitur DTN.

**Lapisan 1 — Reliabilitas unicast (gabungan, bukan terpisah):**
- Dedup yang benar (utamakan `messageExists()` + LRU; persistensi hanya jika perlu).
- **Outbox + end-to-end ACK sebagai satu unit**, terbatas pada unicast TEXT, retry berbatas.
- Drive `MessageStatusIcon` dari **status sebenarnya** (SENT vs DELIVERED end-to-end), bukan dari overhearing.

**Lapisan 2 — Opportunistic delivery (sederhana dulu):**
- **Timer-based queue-processor** (bukan transport hook) + debounce → flush ke peer reachable.
- **Carry terbatas**: kontak verified + prioritas tinggi + kuota. Validasi dengan field test sebelum melonggarkan.

**Lapisan 3 — Skala & daya tahan (hanya jika data lapangan menuntut):**
- Probabilistic/gossip forwarding sebelum mempertimbangkan SnW penuh.
- Beaconing dua-state + matikan WiFi saat idle, sebelum adaptif kompleks.

**Lapisan 4 — BLE (kandidat, bukan kepastian):**
- Hanya setelah lapisan 1-3 stabil DAN field test membuktikan baterai WiFi tak memadai. Mulai dari BLE presence-only.

---

# Recommended Implementation Order

Diurut berdasarkan **(kebenaran fondasi) → (nilai unicast) → (DTN terbatas) → (skala) → (BLE)**, dengan setiap langkah membuka langkah berikutnya:

```
1. Model kedaluwarsa tahan-jam + threat-model metadata + kebijakan kuota   (keputusan desain, bukan kode)
2. Dedup benar (messageExists + LRU; persistensi opsional)
3. Outbox + End-to-End ACK  (SATU unit; unicast TEXT; retry berbatas; SOS tanpa ACK)
4. Status delivery jujur di UI (SENT vs DELIVERED end-to-end)
5. Queue-processor timer-based + debounce  (hindari ubah kontrak transport)
6. Carry terbatas (verified contacts + prioritas + kuota)  ── GERBANG: validasi field test di sini
7. Gossip/probabilistic forwarding (jika kongesti terbukti)
8. Beaconing dua-state + matikan WiFi idle (jika baterai terbukti masalah)
9. BLE presence-only → (mungkin) BLE data  ── hanya jika 1-8 stabil & WiFi tak cukup
```

**Perbedaan kunci dari gap analysis asli:** (a) menambahkan Lapisan 0 fondasi; (b) menggabungkan M1+M2; (c) mengganti B3 transport-hook dengan timer; (d) membatasi M3 carry; (e) menurunkan M4 SnW jadi gossip sederhana & opsional; (f) menjadikan M6 BLE kandidat bersyarat, bukan target pasti; (g) memasang **gerbang field-test** setelah langkah 6.

---

# Final Go / No-Go Decision

### 🟢 GO (dengan koreksi):
- **Dedup benar (#2)** — GO. Murah, infrastruktur ada. Utamakan `messageExists()`.
- **Outbox + ACK sebagai satu unit (#3-4)** — GO. Ini lompatan nyata dari "fire-and-forget" ke "reliable unicast", dan ACK end-to-end aman secara semantik.
- **Queue-processor timer (#5)** — GO. Menggantikan transport-hook berisiko dengan pendekatan non-invasif.

### 🟡 CONDITIONAL GO (butuh prasyarat):
- **Carry-forward terbatas (#6)** — hanya setelah threat-model metadata & kebijakan kuota diputuskan (Lapisan 0). Mulai dari verified-contacts + SOS saja.
- **Gossip forwarding (#7)** & **beaconing dua-state (#8)** — hanya jika field test membuktikan kongesti/baterai jadi masalah nyata.

### 🔴 NO-GO (untuk sekarang):
- **Binary Spray-and-Wait penuh (M4)** — NO-GO sekarang. Semantik tak terpetakan ke transport broadcast CARAKA; optimasi prematur. Gunakan gossip sederhana dulu.
- **BLE backbone penuh (M6)** — NO-GO sekarang. Risiko Android tertinggi, payoff belum tervalidasi. Pertimbangkan hanya sebagai presence-only setelah segalanya stabil.
- **Implicit ACK sebagai status delivery** — NO-GO permanen. Boleh untuk telemetry, tidak boleh untuk label "terkirim".

### Keputusan keseluruhan:
**GO untuk Lapisan 0-1 dan langkah #5 segera. CONDITIONAL untuk DTN carry terbatas. NO-GO untuk SnW & BLE sampai ada validasi lapangan.** Rencana migrasi asli benar arahnya tetapi (a) melewatkan dua fondasi kritis (jam & metadata), (b) salah urut M1/M2, (c) menyalahartikan implicit ACK, dan (d) menjadwalkan optimasi berat (SnW, BLE) terlalu dini. Dengan koreksi di atas, jalur ini layak dan risikonya terkendali.

---

*Catatan: dokumen ini sengaja tidak memuat kode maupun daftar tugas implementasi, sesuai instruksi. Fokusnya kebenaran arsitektur & pengurangan risiko. Field test multi-device adalah gerbang validasi yang tidak bisa digantikan analisis.*
