# CARAKA — Gap Analysis: Current Implementation → Recommended DTN Architecture
> Disusun: 13 Juni 2026
> Basis: `docs/RESEARCH_ARSITEKTUR_CARAKA.md` + inspeksi kode aktual
> Lingkup: hanya teknologi yang SUDAH ada (tidak mengusulkan teknologi baru)

Dokumen ini memetakan jarak antara apa yang sudah dibangun CARAKA dan arsitektur DTN (store-carry-forward) yang direkomendasikan riset. Setiap temuan dirujuk ke file sumber agar bisa diverifikasi.

---

## Ringkasan Status (Scorecard)

| Komponen | Status | Kompleksitas migrasi |
|----------|--------|---------------------|
| Message persistence | ✅ Ada (tapi sebagai chat-log, bukan queue) | — |
| Peer discovery | ✅ Ada (kuat, multi-transport) | — |
| Transport abstraction | ✅ Ada (MeshTransport + MeshManager) | — |
| Relay logic (flooding + TTL) | ✅ Ada | — |
| Encryption / signature / TOFU | ✅ Ada | — |
| Deduplication | ⚠️ Sebagian (in-memory saja) | Small |
| Routing | ⚠️ Sebagian (BATMAN, hanya di Aware; bukan DTN) | Large |
| Message lifecycle / delivery status | ❌ Hilang | Medium |
| Acknowledgment (ACK / implicit ACK) | ❌ Hilang (tipe ada, logika tidak) | Medium |
| Persistent outbox queue | ❌ Hilang | Medium |
| Store-carry-forward (carry untuk offline) | ❌ Hilang | Large |
| Controlled replication (Spray-and-Wait) | ❌ Hilang | Large |
| Adaptive beaconing / duty cycle | ❌ Hilang | Medium |
| BLE transport (DTN backbone) | ❌ Hilang | Major |

---

# Existing Capabilities

Hal-hal yang **sudah diimplementasi dengan baik** dan menjadi fondasi yang dapat dipertahankan.

### 1. Message Persistence — ✅ ADA (dengan caveat)
- `CarakaDatabase` (Room + SQLCipher AES-256, schema v2) dengan `MessageEntity`, `PeerEntity`, `RelayedMessageEntity`.
- `MessageDao` lengkap: insert (OnConflict IGNORE → dedup by primary key), query per-peer, SOS, broadcast, recent alerts, unread count.
- Enkripsi at-rest + secure wipe + Keystore passphrase sudah benar.
- **Caveat:** `MessageEntity` adalah *catatan percakapan*, bukan *unit transport*. Tidak ada state lifecycle (PENDING/SENT/DELIVERED), `replicaCount`, atau `nextAttempt`. Lihat blocker B4.

### 2. Peer Discovery — ✅ ADA (kuat)
- Multi-transport: WiFi Direct DNS-SD (`_caraka._tcp`) + LAN UDP beacon (port 8890), WiFi Aware publish/subscribe, Nearby Connections P2P_CLUSTER.
- `PEER_LIST` gossip (5s) untuk full-mesh awareness — node belajar peer yang tak terdengar langsung.
- `peerIpRegistry` memetakan peerId → LAN IP untuk unicast.
- Ini adalah area terkuat CARAKA dan tidak perlu diubah untuk DTN.

### 3. Transport Abstraction — ✅ ADA
- `MeshTransport` interface memisahkan UI/repository dari link fisik.
- `MeshManager` facade melapisi WiFi Aware + Nearby di atas `WifiDirectManager` (brain) via `overlayBroadcastSinks` / `overlayUnicastSinks`.
- Penambahan transport baru (mis. BLE) bisa mengikuti pola sink yang sama — abstraksi ini siap untuk diperluas.
- **Caveat:** API-nya fire-and-forget (lihat blocker B3).

### 4. Relay Logic (Flooding + TTL) — ✅ ADA
- `handleTextMessage` / `handleSosMessage` me-relay dengan decrement TTL; broadcast relay (BUG-P1 sudah difix).
- `MeshRouter` (BATMAN/OLSR-style) sekarang juga decrement TTL saat forward (baru difix).
- `markSent()` mencegah pengirim memproses ulang relay-nya sendiri.
- Cocok untuk SOS broadcast; **tidak cocok** sebagai mekanisme unicast skalabel (lihat Missing).

### 5. Keamanan — ✅ ADA (lebih maju dari Bridgefy)
- Ed25519 signature verification di `handleTextMessage`/`handleSosMessage` (baru diimplementasi).
- TOFU key-continuity di `handleHandshake`/`handlePeerList`.
- X25519 + crypto_box untuk E2E unicast. Foreground Service untuk daya tahan background.

---

# Missing Components

Diurutkan berdasarkan dampak terhadap kemampuan DTN.

### M1. Persistent Outbox Queue — ❌ HILANG · **Medium (3-7 hari)**
- `sendDirectMessage()` (`MeshRepository.kt:170`) adalah **fire-and-forget**: encrypt → save chat-log → `transport.sendToPeer()` → selesai. Tidak ada antrian, tidak ada retry.
- Tidak ada tabel outbox dengan status, percobaan, atau jadwal kirim ulang.
- **Dampak:** jika pengiriman pertama gagal (peer sedang tak terjangkau), pesan hilang dari jalur transport selamanya — hanya tersisa sebagai catatan chat "terkirim" yang menyesatkan.

### M2. Acknowledgment Mechanism — ❌ HILANG · **Medium (3-7 hari)**
- Tipe `"ACK"` terdefinisi di `MeshProtocol.kt:29` tapi **tidak pernah dikirim maupun ditangani** — tidak ada `case "ACK"` di dispatcher (`WifiDirectManager.kt:1350`).
- Tidak ada implicit ACK (mendeteksi rebroadcast pesan sendiri sebagai bukti terkirim — pola Meshtastic).
- `MessageStatusIcon` (komponen UI) ada tapi tidak pernah di-update dari event jaringan.
- **Dampak:** tidak ada cara tahu apakah pesan benar-benar sampai; tidak bisa hapus pesan dari queue; tidak bisa retry cerdas.

### M3. Store-Carry-Forward (carry untuk peer offline) — ❌ HILANG · **Large (1-3 minggu)**
- `sendDirectMessage()` **`return` lebih awal jika `peerDao.getPeerById(recipientId) == null`** (`MeshRepository.kt:171`). Tidak bisa mengantre pesan untuk peer yang belum/sedang tak dikenal.
- Tidak ada logika "simpan pesan untuk tujuan tak terjangkau, kirim saat tujuan muncul".
- **Dampak:** inti dari DTN — yang menyelesaikan keluhan "komunikasi masih sangat terbatas, fokus device-to-device" — belum ada sama sekali. Ini gap konseptual terbesar.

### M4. Controlled Replication (Binary Spray-and-Wait) — ❌ HILANG · **Large (1-3 minggu)**
- Unicast saat ini: `sendToPeer` → LAN unicast langsung, atau fallback flooding via overlay sink.
- Tidak ada konsep "L kopi terbatas" atau utility-based forwarding.
- **Dampak:** flooding murni hancur di >30 node (riset: ratusan ribu message drop). Tanpa ini, target "puluhan perangkat" tidak akan skala.

### M5. Adaptive Beaconing / Duty Cycle — ❌ HILANG · **Medium (3-7 hari)**
- Interval tetap: LAN beacon 3s, gossip 5s, discovery 6s, heartbeat router 10s — semua konstan, berjalan simultan.
- Tidak ada penyesuaian berdasarkan densitas/gerakan/kontak, tidak ada exponential back-off, tidak ada accelerometer gating.
- **Dampak:** boros baterai — fatal untuk bencana berhari-hari saat listrik padam.

### M6. BLE Transport (DTN backbone) — ❌ HILANG · **Major (1+ bulan)**
- Tidak ada `BleTransportManager`. Backbone always-on hemat baterai (yang dipakai BitChat/Bridgefy) belum ada.
- **Dampak:** WiFi Aware/Direct terlalu boros untuk always-on, dan Aware terfragmentasi di HP target (Infinix/Tecno/Redmi). Tanpa BLE, tidak ada radio yang bisa bertahan berjam-jam.
- *Catatan: ini teknologi "baru" relatif terhadap kode saat ini, tapi sudah direkomendasikan riset — dimasukkan sebagai target Fase akhir, bukan untuk sekarang.*

---

# Partially Implemented (Sebagian)

### P1. Deduplication — ⚠️ SEBAGIAN · perbaikan **Small (1-2 hari)**
- **Ada:** `MeshSocketManager.isDuplicate()` (`:81`) — `seenIds` LinkedHashSet (cap 2000) + cek timestamp drift ±5 menit. `markSent()` mencegah loop pengirim.
- **Hilang:** persistensi. `RelayedMessageEntity` + `RelayDao` (`markAsRelayed`, `hasBeenRelayed`, `cleanOldRelays`) **terdefinisi tapi tidak pernah dipanggil** — hanya `deleteAll()` di `clearAllData()`. `MessageDao.messageExists()` juga tidak terpakai.
- **Dampak:** setelah restart proses, `seenIds` kosong → jendela replay terbuka kembali. Infrastruktur DB untuk fix ini **sudah ada**, tinggal dikabelkan.

### P2. Routing — ⚠️ SEBAGIAN · migrasi ke DTN **Large (1-3 minggu)**
- **Ada:** `MeshRouter` (BATMAN/OLSR-style) dengan routing table, heartbeat 10s, route timeout 35s, next-hop unicast.
- **Keterbatasan:** hanya dikabelkan pada jalur **Aware** (`MeshManager.setupAwareLayer()`); WiFi Direct & Nearby pakai flooding. Lebih penting: router ini **mengasumsikan konektivitas kontinu** (rute kedaluwarsa 35s, drop jika tak ada next-hop) — kebalikan dari asumsi DTN (jaringan terpartisi). Lihat blocker B1.

---

# Architectural Blockers

Asumsi/keterbatasan struktural yang **harus dibereskan sebelum** fitur DTN bisa dipasang dengan benar.

### B1. Routing layer mengasumsikan konektivitas kontinu (online MANET, bukan DTN)
- `MeshRouter.onMessageReceived()` mem-forward jika ada next-hop, **drop jika tidak ada** (`MeshRouter.kt`). `cleanupStaleRoutes()` menghapus rute setelah 35s.
- DTN justru dirancang untuk topologi terpartisi: "tidak ada rute sekarang" harus berarti "simpan dan bawa", bukan "drop".
- **Konsekuensi:** menambahkan store-carry-forward berarti router tidak boleh lagi jadi gerbang yang men-drop; perlu lapisan queue di atas/sebelum routing yang menyimpan pesan tanpa rute.

### B2. `sendToPeer` / `sendDirectMessage` mengasumsikan peer terjangkau SEKARANG
- `sendDirectMessage()` `return` jika peer tidak ada di DB lokal (`MeshRepository.kt:171`).
- `transport.sendToPeer()` adalah operasi sekali-tembak tanpa nilai balik.
- **Konsekuensi:** tidak ada titik masuk untuk "antre pesan ke peer yang belum dikenal / sedang offline". Lifecycle pengiriman harus dipindah dari Repository fire-and-forget ke sebuah queue-processor.

### B3. Transport abstraction fire-and-forget (tanpa delivery feedback / flush hook)
- `MeshTransport.sendMessage()` dan `sendToPeer()` me-return `void`; tidak ada handle pesan, callback delivery, atau status.
- Tidak ada event "peer baru muncul → flush antrian untuk peer itu". Penemuan peer (`handleHandshake`, `handlePeerList`) tidak terhubung ke mekanisme pengiriman tertunda.
- **Konsekuensi:** interface perlu ditambah hook (mis. callback "peerAvailable") agar queue-processor bisa dipicu secara opportunistic. Ini perubahan kontrak yang menyentuh semua implementasi transport.

### B4. Database: `MessageEntity` mencampur "chat-log" dengan "unit transport"
- `MessageEntity` punya `isRead`, `isRelayed`, `ttl` (snapshot saat kirim) — tapi **tidak ada** state delivery (PENDING/SENT/DELIVERED/FAILED), `replicaCount` (untuk Spray-and-Wait), `nextAttemptAt`, atau `attemptCount`.
- Insert pakai `OnConflictStrategy.IGNORE` → pesan tak pernah di-update lifecycle-nya.
- `RelayedMessageEntity` ada tapi mati (tak terpakai).
- **Konsekuensi:** perlu **tabel baru** (mis. `OutboxEntity` / `BundleEntity`) yang memisahkan unit transport DTN dari catatan percakapan UI. Memaksa state lifecycle ke `MessageEntity` akan mencemari layer UI. Butuh Room migration (schema v3).

### B5. Dedup volatile (hilang saat proses mati)
- `seenIds` murni in-memory. Foreground Service mengurangi (bukan menghilangkan) risiko proses dibunuh.
- **Konsekuensi:** prasyarat DTN yang benar adalah dedup persisten; ini blocker kecil tapi harus diberesi sebelum scale-up (untungnya infrastruktur DB-nya sudah ada — lihat P1).

---

# Migration Strategy

Strategi bertahap dari **arsitektur saat ini (online relay/flooding)** menuju **DTN store-carry-forward**, mengikuti roadmap riset tapi dipetakan ke realitas kode.

### Fase 0 — Bereskan blocker dedup & lifecycle (fondasi)
1. **Kabelkan dedup persisten** (P1, B5): pakai `RelayDao.markAsRelayed`/`hasBeenRelayed`/`cleanOldRelays` yang sudah ada; load seen-set dari DB saat `MeshSocketManager` init. → menutup replay window.
2. **Tambah state lifecycle** (B4): buat `OutboxEntity` (id, payload, recipientId, state, attemptCount, replicaCount, nextAttemptAt, ttlExpiry, priority) via Room migration v3. Pisahkan dari `MessageEntity`. Driving `MessageStatusIcon` dari state ini.

### Fase 1 — Outbox + ACK (reliabilitas)
3. **Persistent outbox queue** (M1): `sendDirectMessage` menulis ke outbox dengan state PENDING, bukan langsung kirim. Queue-processor (di Foreground Service) yang mengirim & retry.
4. **Acknowledgment** (M2): implementasikan handler `"ACK"` + implicit ACK (deteksi rebroadcast sendiri). Update outbox state PENDING→DELIVERED; hapus dari queue saat ACK.

### Fase 2 — Store-Carry-Forward + transport hook (inti DTN)
5. **Hook "peer available"** (B3): tambah callback di `MeshTransport` dipicu dari `handleHandshake`/`handlePeerList`; queue-processor flush antrian untuk peer yang baru muncul.
6. **Carry-and-forward** (M3, B1, B2): izinkan antre pesan ke peer yang belum dikenal; node perantara menyimpan & meneruskan saat tujuan muncul. Router tidak lagi men-drop "no route" — lempar ke outbox.

### Fase 3 — Skalabilitas + daya tahan
7. **Binary Spray-and-Wait** (M4): tambah `replicaCount L` ke unit transport; ganti unicast flooding dengan controlled replication. SOS tetap controlled-flood.
8. **Adaptive beaconing** (M5): interval menyesuaikan kontak/gerakan, exponential back-off, accelerometer gating.

### Fase 4 — BLE backbone (target akhir)
9. **BLE transport** (M6): tambah `BleTransportManager` mengikuti pola overlay-sink yang sudah ada; jadikan backbone DTN always-on.

---

# Quick Wins

Dampak tinggi, usaha rendah, risiko rendah — bisa dikerjakan lebih dulu.

| # | Aksi | Kompleksitas | Kenapa quick win |
|---|------|--------------|------------------|
| QW1 | **Kabelkan dedup persisten** via `RelayDao` yang sudah ada | Small (1-2 hari) | Infrastruktur DB sudah ada; tinggal panggil `markAsRelayed`/`hasBeenRelayed` + load saat init. Langsung tutup replay window. |
| QW2 | **Tambah `cleanOldRelays()` ke jadwal** (mis. di FGS atau saat startup) | Small (<1 hari) | Mencegah tabel relay membengkak; method sudah ada. |
| QW3 | **Drive `MessageStatusIcon` dari `isRelayed`/implicit signal sederhana** | Small (1-2 hari) | UI komponen sudah ada; beri feedback visual awal walau ACK penuh belum ada. |
| QW4 | **Adaptive duty cycle sederhana**: lebarkan interval saat topologi stabil (tak ada peer baru N detik) | Small-Medium | Tidak butuh perubahan arsitektur; langsung hemat baterai. |

---

# High Risk Changes

Perubahan yang menyentuh banyak komponen / mengubah kontrak — perlu desain hati-hati & test menyeluruh.

| Perubahan | Risiko | Mengapa berisiko | Mitigasi |
|-----------|--------|------------------|----------|
| **Migrasi routing online → DTN** (B1) | 🔴 Tinggi | `MeshRouter` diasumsikan kontinu; mengubah perilaku drop→store berdampak ke semua jalur relay & bisa menyebabkan duplikasi/loop jika dedup belum persisten | Kerjakan SETELAH QW1 (dedup persisten); tambahkan lapisan outbox di atas router, jangan ubah router secara destruktif |
| **Ubah kontrak `MeshTransport`** (B3) | 🔴 Tinggi | Menambah callback/hook menyentuh WifiDirect, Aware, Nearby, dan facade sekaligus | Tambah method opsional dengan default no-op; rollout per transport |
| **Room migration v3 + tabel Outbox** (B4) | 🟡 Sedang | Migration salah = crash/data loss di install lama; SQLCipher menambah kompleksitas | Tulis migration eksplisit + test upgrade dari v2; jangan pakai fallbackToDestructive |
| **Spray-and-Wait menggantikan flooding** (M4) | 🟡 Sedang | Mengubah semantik forwarding; salah tuning L → delivery turun | Jalankan paralel dgn flooding di belakang flag; ukur delivery rate di field test |
| **BLE transport** (M6) | 🟡 Sedang | Permission BLE, manajemen GATT, perilaku OEM agresif | Fase terpisah; pakai pola overlay-sink yang sudah terbukti |

---

# Recommended Next Feature

**Mulai dari: Dedup Persisten (QW1) → lalu Persistent Outbox + ACK (M1 + M2).**

**Alasan:**
1. **QW1 adalah prasyarat keras** untuk semua kerja DTN berikutnya. Tanpa dedup persisten, mengubah routing ke store-carry-forward (yang menambah jalur & retry) akan memperparah duplikasi dan loop. Infrastrukturnya (`RelayDao`, `RelayedMessageEntity`) **sudah ada di kode tapi mati** — ini usaha paling kecil dengan unblock terbesar.
2. **Outbox + ACK (M1+M2)** adalah langkah pertama yang secara nyata mengubah CARAKA dari "fire-and-forget chat" menjadi "reliable messaging" — dan keduanya adalah fondasi yang dibutuhkan store-carry-forward (M3). Tipe `ACK` bahkan sudah terdefinisi di protokol; tinggal logikanya.

**Yang JANGAN dikerjakan dulu:** store-carry-forward penuh (M3) dan Spray-and-Wait (M4) — keduanya bergantung pada outbox + ACK + dedup persisten yang stabil. BLE (M6) adalah target akhir, bukan sekarang.

**Urutan konkret yang direkomendasikan:**
```
QW1 (dedup persisten)  →  B4 (OutboxEntity + migration v3)  →  M1 (outbox queue + retry)
   →  M2 (ACK + implicit ACK)  →  B3 (transport hook)  →  M3 (carry-forward)  →  M4 (Spray-and-Wait)
```

---

*Referensi kode: `MeshRepository.kt`, `WifiDirectManager.kt`, `MeshRouter.kt`, `MeshSocketManager.kt`, `MeshTransport.kt`, `MeshManager.kt`, `CarakaDatabase.kt`, `MessageDao.kt`, `RelayDao.kt`, `MessageEntity.kt`, `RelayedMessageEntity.kt`, `MeshProtocol.kt`. Referensi arsitektur: `docs/RESEARCH_ARSITEKTUR_CARAKA.md`.*
