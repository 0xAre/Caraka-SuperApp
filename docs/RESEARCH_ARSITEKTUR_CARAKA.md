# CARAKA — Riset Keputusan Arsitektur Komunikasi Darurat Offline
> Riset mendalam via MCP YDC (deep research) + analisis kompetitor + literatur akademik DTN
> Disusun: 13 Juni 2026 | Fokus: keputusan arsitektur, bukan kode

---

## A. Executive Summary

**Pertanyaan inti:** *"Dapatkah satu pengguna mengirim pesan darurat yang diteruskan multi-hop ke puluhan perangkat lain tanpa internet?"*

**Jawaban: REALISTIS, dengan syarat.** Target ini sesuai dengan apa yang sudah dibuktikan BitChat (~7 hop BLE), Bridgefy (jutaan pengguna di protes Hong Kong), dan literatur DTN akademik. Tetapi "puluhan perangkat" hanya tercapai jika:

1. **Pindah dari paradigma "device-to-device terhubung" ke "store-carry-forward" (DTN).** Inti masalah CARAKA saat ini — "komunikasi masih sangat terbatas, fokus device-to-device" — adalah karena Anda memperlakukan mesh seperti jaringan yang harus *terhubung penuh*. Jaringan darurat nyata **selalu terpartisi**: pesan harus bisa disimpan di node perantara, dibawa secara fisik oleh orang yang bergerak, lalu diteruskan saat bertemu node lain. Ini mengubah arsitektur secara fundamental.

2. **Routing harus controlled-replication, BUKAN pure flooding.** Riset akademik konsisten: *epidemic flooding murni hancur di atas ~20-30 node aktif* (ratusan ribu message drop karena buffer exhaustion). CARAKA saat ini pakai TTL-flooding — ini OK untuk <30 node tapi tidak akan skala ke "puluhan-ratusan".

3. **Hop count realistis adalah 3-8 hop**, bukan tak terbatas. Studi disaster-scenario (stadion sepak bola saat gempa) menunjukkan epidemic routing menghasilkan path 6-7 hop untuk pesan yang berhasil terkirim. Di atas 5-8 hop, overhead mendominasi.

4. **Densitas minimum: 10-50 smartphone aktif/km²** per "social region" (shelter, kampus, blok kota). Di bawah ini, mesh hanya berguna untuk komunikasi delay-tolerant (delay menit hingga jam), bukan real-time.

**Rekomendasi strategis untuk CARAKA:** Pertahankan WiFi Aware + WiFi Direct sebagai *high-bandwidth local cluster transport*, **tambahkan BLE sebagai DTN backbone always-on hemat-baterai**, dan **ganti flooding dengan store-and-forward + controlled replication**. Untuk coverage kilometer (SAR, daerah terpencil), tambahkan **LoRa gateway bridge** sebagai opsi hardware Fase lanjut. **Jangan** mengandalkan Bluetooth Mesh standar (Android tidak mendukungnya untuk relay) dan **jangan** kejar WiFi HaLow (belum ada smartphone yang punya radionya).

---

## B. Technology Comparison Matrix

| Teknologi | Range/hop (indoor) | Throughput | Baterai | Android support | Max hop praktis | Topologi | Cocok untuk darurat? |
|-----------|-------------------|-----------|---------|-----------------|-----------------|----------|---------------------|
| **WiFi Aware (NAN)** | 15-30 m | 10-300 Mbps | Sedang-tinggi | API 26+, hardware opsional (fragmentasi tinggi) | 2-4 (app-level) | Many-to-many discovery, link P2P; multi-hop harus dibuat sendiri | ✅ Cluster lokal padat berkapasitas tinggi |
| **WiFi Direct (P2P)** | 10-30 m (s/d 90 m LOS) | 10-300 Mbps | Tinggi (GO = boros) | API 14+, universal | 1 native (2-3 via hack) | Star per group, GO = SPOF & bottleneck | ⚠️ Grup kecil ≤6 device |
| **Bluetooth Mesh (standar)** | 10-20 m (modul) | kb/s | Sangat rendah (modul) | ❌ Android TIDAK implement mesh bearer; HP hanya GATT proxy client | 0 (HP bukan relay) | Managed flood (hanya di node khusus) | ❌ TIDAK BISA phone-only |
| **BLE Store-and-Forward (custom)** | 5-15 m | 10-300 kbps (GATT); lebih rendah via advertising | **Terendah** (paling hemat) | BLE 4.0+, universal | **3-6** | Overlay bebas (flooding/DTN) | ✅✅ DTN backbone always-on |
| **LoRa + Smartphone (Meshtastic)** | 2-15 km/hop | 0.3-5 kbps | Jam-hari (node), butuh hardware | Via BLE bridge ke node LoRa | 3 (default), 7 (max) | Managed flooding + next-hop | ✅ Coverage km, butuh hardware eksternal |
| **WiFi HaLow (802.11ah)** | ~1 km | 150 kbps-86 Mbps | Multi-tahun (IoT) | ❌ TIDAK ADA smartphone dengan radio HaLow (2024-2025) | Via 802.11s (riset) | Mesh 802.11s + relay | ❌ Tidak feasible untuk HP sekarang |
| **Hybrid BLE + LoRa** | 5-15 m (BLE) + multi-km (LoRa) | Bervariasi | BLE hemat + LoRa node | BLE native + LoRa via bridge | BLE 3-6 + LoRa 3-7 | DTN lokal + LoRa backbone | ✅✅ Terbaik untuk coverage luas |

**Catatan kritis Android:**
- **Bluetooth Mesh standar TIDAK BISA dipakai** untuk phone-to-phone relay. Android/iOS hanya menyediakan BLE GATT, bukan mesh advertisement bearer. "Bluetooth mesh antar HP" = custom overlay di atas BLE, bukan Bluetooth Mesh standar.
- **WiFi HaLow butuh radio sub-GHz terpisah** — tidak ada di smartphone manapun. Hanya modul IoT/gateway. Coret dari roadmap smartphone.
- **WiFi Direct: grup >6-10 device tidak stabil** di HP nyata, dan GO adalah single point of failure.

---

## C. Recommended Architecture untuk CARAKA

### Prinsip: "Tiered Transport + DTN Core"

```
┌─────────────────────────────────────────────────────────────┐
│  LAPISAN APLIKASI (sudah ada sebagian di CARAKA)             │
│  - SOS broadcast, chat E2E, authority verification           │
│  - Ed25519 signature ✅, TOFU ✅, Foreground Service ✅      │
├─────────────────────────────────────────────────────────────┤
│  DTN CORE — STORE-CARRY-FORWARD (INI YANG HARUS DITAMBAHKAN) │
│  - Persistent message queue (Room) dengan TTL + prioritas    │
│  - Binary Spray-and-Wait (BUKAN pure flooding)               │
│  - Implicit ACK via rebroadcast hash                         │
│  - Dedup persisten (survive restart) — Bloom filter / hash   │
├─────────────────────────────────────────────────────────────┤
│  TIERED TRANSPORT (auto-select per situasi)                  │
│  ┌──────────────┬──────────────┬───────────────────────────┐ │
│  │ BLE          │ WiFi Aware/  │ LoRa Gateway (Fase lanjut)│ │
│  │ (DTN backbone│ WiFi Direct  │ (coverage km, hardware)   │ │
│  │  always-on,  │ (burst high- │                           │ │
│  │  hemat)      │  bandwidth)  │                           │ │
│  └──────────────┴──────────────┴───────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Keputusan arsitektur kunci:

**1. BLE menjadi backbone discovery + DTN, bukan WiFi.**
- BLE adalah satu-satunya radio yang bisa always-on berhari-hari di baterai HP. Inilah yang dipakai BitChat dan Bridgefy.
- Gunakan BLE advertising untuk *presence + pesan SOS kecil*; gunakan GATT untuk transfer pesan teks.
- WiFi Aware/Direct hanya dinyalakan *opportunistically* saat ada transfer besar atau cluster padat — bukan untuk selalu-on (terlalu boros).

**2. Store-Carry-Forward menggantikan "harus terhubung".**
- Pesan disimpan di Room dengan status PENDING sampai dapat konfirmasi terkirim.
- Node perantara menyimpan pesan untuk tujuan yang sedang tak terjangkau, mengirim saat tujuan muncul (carry-and-forward).
- Ini menyelesaikan keluhan utama: "komunikasi masih sangat terbatas, fokus device-to-device".

**3. Routing: Binary Spray-and-Wait.**
- Alih-alih flood ke semua (epidemic), "spray" L kopi terbatas (misal 4-16), lalu tunggu carrier bertemu tujuan.
- Memberi delivery rate mendekati optimal dengan *transmisi jauh lebih sedikit* daripada flooding.
- Untuk SOS broadcast (memang harus menyebar luas), pertahankan controlled flooding dengan TTL + hash cache.

**4. Implicit ACK + dedup persisten.**
- Pesan ber-ID = hash(senderId, timestamp, payload).
- Saat node "mendengar" node lain me-rebroadcast pesannya → anggap terkirim (implicit ACK, trik Meshtastic).
- Seen-set disimpan persisten (survive restart) — CARAKA saat ini hanya in-memory (replay window terbuka setelah restart).

**5. Adaptive beaconing untuk baterai.**
- Beacon interval menyesuaikan: cepat saat ada kontak/gerakan, exponential back-off saat sepi.
- Gunakan accelerometer: kurangi scanning saat diam, tingkatkan saat bergerak.
- CARAKA saat ini: beacon 3s + gossip 5s + discovery 6s + heartbeat 10s konstan — fatal untuk durasi bencana.

---

## D. Tradeoff Analysis

| Keputusan | Untung | Rugi | Mitigasi |
|-----------|--------|------|----------|
| **BLE sebagai backbone** | Hemat baterai (bisa berhari-hari), universal Android | Range pendek (5-15m), throughput rendah | WiFi Aware untuk burst; multi-hop untuk extend range |
| **Spray-and-Wait vs flooding** | Skalabilitas tinggi, hemat airtime | Lebih kompleks; SOS tetap perlu flooding | Hybrid: SnW untuk unicast, controlled-flood untuk SOS |
| **Store-and-forward** | Berfungsi di topologi terpartisi (realita bencana) | Delay menit-jam; butuh storage persisten | Prioritas pesan: SOS cepat, status delay-tolerant |
| **Multi-transport (BLE+WiFi+LoRa)** | Redundansi, coverage berlapis | Kompleksitas tinggi, dedup antar-transport | Anti-replay dedup (sudah ada), abstraksi MeshTransport (sudah ada) |
| **LoRa gateway (Fase lanjut)** | Coverage km tanpa internet | Butuh hardware $30-50/unit, throughput sangat rendah | Opsional; untuk SAR/daerah terpencil saja |
| **Pertahankan WiFi Direct** | Universal, bandwidth tinggi | GO = SPOF, grup >6 tidak stabil | Demote jadi transport sekunder, bukan utama |

**Tradeoff terpenting:** *Kecepatan vs Jangkauan*. Real-time hanya mungkin dalam cluster lokal padat (1-2 hop WiFi Aware). Multi-hop ke "puluhan perangkat" pasti delay-tolerant. CARAKA harus jujur soal ini di UX — tandai pesan "Terkirim ke jaringan lokal" vs "Sedang diteruskan (mungkin tertunda)".

---

## E. Risk Analysis

| Risiko | Tingkat | Dampak | Penanganan |
|--------|---------|--------|------------|
| **Fragmentasi hardware WiFi Aware** | Tinggi | Banyak HP target (Infinix/Tecno/Redmi) tidak support Aware reliabel | BLE sebagai fallback wajib; jangan jadikan Aware satu-satunya |
| **Flooding congestion collapse** | Tinggi | Mesh hancur di >30 node (ratusan ribu drop) | Ganti ke Spray-and-Wait sebelum scale-up |
| **Disinformasi / SOS palsu** | Kritis | Korban diarahkan ke bahaya (kegagalan Bridgefy) | Signature verification ✅ sudah diimplementasi; perketat badge authority |
| **Key substitution / MITM** | Tinggi | Identitas aparat dipalsukan | TOFU ✅ sudah ada; perlu QR re-verify untuk rotasi key |
| **Baterai habis saat bencana** | Kritis | Node mati = mesh terputus, listrik padam berhari-hari | Adaptive duty cycle; mode hemat ekstrem <15% |
| **Replay setelah restart** | Sedang | Jendela replay terbuka | Dedup persisten (Bloom filter di storage) |
| **Densitas node tidak cukup** | Tinggi | Mesh tidak berguna jika partisipan <10/km² | Strategi adopsi; pre-install di komunitas rawan bencana |
| **Bluetooth Mesh disangka bisa** | Sedang | Salah arsitektur (HP bukan relay mesh standar) | Sudah diklarifikasi: pakai custom BLE overlay |
| **Ketergantungan Google Play (Nearby)** | Sedang | Tidak jalan di HP AOSP/de-Googled | Sudah ada fallback WiFi Direct + LAN |

**Pelajaran dari kompetitor:**
- **Bridgefy** (2x dibobol Royal Holloway): no authenticity, tracking, DoS via decompression bomb. CARAKA harus hindari plaintext sender/receiver ID dan validasi ukuran pesan.
- **BitChat** (Jack Dorsey): arsitektur bagus (BLE mesh 7-hop, Noise protocol, store-forward 12 jam) tapi *belum teruji adversarial* — jangan klaim "aman" tanpa audit.
- **Meshtastic**: managed flooding tidak skala >100 node/channel; key duplication bug 2025 karena entropy rendah. CARAKA harus pastikan key generation entropy cukup.
- **Briar**: paling matang (multi audit), tapi bukan broadcast mesh (hanya kontak-ke-kontak), no iOS, baterai 4x lebih boros. Trade-off keamanan vs jangkauan spontan.
- **Serval Mesh**: Rhizome DTN bundle-based solid, dipakai pasca-gempa Haiti, tapi praktis unmaintained + masalah ad-hoc WiFi di Android.

---

## F. Roadmap Implementasi 3 Bulan

### Bulan 1 — DTN Core (mengubah paradigma)
**Tujuan: dari "device-to-device" ke "store-carry-forward"**
- Persistent message queue di Room (tabel outbox): status PENDING/SENT/DELIVERED, TTL, prioritas, hop count, replica count L
- Implicit ACK: deteksi rebroadcast pesan sendiri sebagai konfirmasi terkirim; update MessageStatusIcon dari status ini
- Dedup persisten: pindahkan seen-set dari in-memory ke storage (Bloom filter / hash table), survive restart
- Carry-and-forward: simpan pesan untuk tujuan tak terjangkau, kirim saat tujuan muncul di registry
- **Milestone:** pesan ke peer offline tidak hilang; muncul saat peer kembali online

### Bulan 2 — BLE Transport + Routing Cerdas
**Tujuan: backbone hemat-baterai + skalabilitas**
- BLE transport manager: advertising untuk presence + SOS kecil, GATT untuk pesan teks
- Integrasi BLE ke abstraksi MeshTransport yang sudah ada (sejajar WiFi Aware/Direct/Nearby)
- Binary Spray-and-Wait untuk unicast (L=4-16 adaptif); pertahankan controlled-flood untuk SOS
- Adaptive beaconing: interval menyesuaikan kontak/gerakan, exponential back-off saat sepi, accelerometer untuk deteksi diam
- **Milestone:** mesh bertahan berjam-jam tanpa menguras baterai; test 10+ device tanpa congestion collapse

### Bulan 3 — Hardening + Field Test + (opsional) LoRa
**Tujuan: production-grade + validasi lapangan**
- Prioritas pesan & congestion-aware drop policy (SOS > status; drop expired/over-replicated dulu)
- Validasi ukuran pesan (cegah decompression bomb ala Bridgefy)
- Field test: 5-10 device fisik dalam mode pesawat (simulasi blackout total), ukur baterai/jam, uji 3-5 hop
- (Opsional) Proof-of-concept LoRa gateway bridge via BLE untuk coverage km — untuk skenario SAR
- Ganti package `com.example.caraka` → package produksi
- **Milestone:** demo multi-hop ke puluhan device; laporan konsumsi baterai & delivery rate

---

## G. Prioritas Fitur (Impact Tertinggi → Terendah)

| Prioritas | Fitur | Impact | Alasan |
|-----------|-------|--------|--------|
| **1** | **Store-and-forward / DTN core** | 🔴 Tertinggi | Inti masalah CARAKA. Tanpa ini, mesh hanya berfungsi saat semua terhubung simultan — tidak realistis di bencana |
| **2** | **Dedup persisten + implicit ACK** | 🔴 Tinggi | Reliabilitas pengiriman + tutup replay window; prasyarat DTN yang benar |
| **3** | **BLE transport (DTN backbone)** | 🔴 Tinggi | Satu-satunya radio always-on hemat baterai; fallback untuk HP tanpa Aware |
| **4** | **Spray-and-Wait routing** | 🟡 Sedang-tinggi | Wajib sebelum scale-up; flooding hancur >30 node |
| **5** | **Adaptive duty cycle / beaconing** | 🟡 Sedang-tinggi | Baterai = sumber daya paling langka di bencana berhari-hari |
| **6** | **Prioritas pesan + congestion control** | 🟡 Sedang | SOS harus diutamakan; cegah buffer exhaustion |
| **7** | **Validasi ukuran pesan (anti-DoS)** | 🟡 Sedang | Pelajaran Bridgefy; satu pesan jahat bisa crash mesh |
| **8** | **LoRa gateway bridge** | 🟢 Rendah-sedang | Coverage km tapi butuh hardware; untuk SAR/terpencil, bukan mayoritas pengguna |
| **9** | **WiFi HaLow** | ⚫ Jangan | Tidak ada smartphone yang punya radionya (2024-2025) |
| **10** | **Bluetooth Mesh standar** | ⚫ Jangan | Android tidak mendukung HP sebagai relay mesh; pakai custom BLE overlay |

---

## Kesimpulan

Target *"satu pengguna mengirim pesan darurat multi-hop ke puluhan perangkat tanpa internet"* **realistis dan sudah dibuktikan industri** (BitChat ~7 hop, Bridgefy jutaan pengguna, literatur DTN 3-8 hop). 

CARAKA sudah punya fondasi keamanan yang baik (Ed25519, TOFU, Foreground Service) — yang justru *lebih maju* dari Bridgefy yang dibobol berkali-kali. **Yang hilang adalah lapisan DTN store-carry-forward dan routing yang skalabel.** Tiga bulan ke depan harus fokus mengubah paradigma dari "perangkat harus terhubung" menjadi "pesan disimpan, dibawa, diteruskan" — inilah yang membedakan aplikasi chat biasa dari platform komunikasi bencana sungguhan.

Coret WiFi HaLow dan Bluetooth Mesh standar dari pertimbangan (tidak feasible di smartphone). Jadikan BLE backbone, WiFi Aware/Direct akselerator burst, dan LoRa opsi coverage-luas Fase lanjut.

---

*Sumber: MCP YDC deep research (4 query) — perbandingan transport Android 2024-2025, analisis arsitektur Briar/BitChat/Bridgefy/Meshtastic/Serval, WiFi HaLow vs LoRa, literatur DTN/opportunistic networking 2010-2025. Royal Holloway "Breaking Bridgefy" (Albrecht et al.) + "Breaking Bridgefy, again" (USENIX 2022).*
