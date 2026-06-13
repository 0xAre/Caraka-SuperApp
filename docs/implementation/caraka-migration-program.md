# CARAKA — Migration Implementation Program
> Sumber kebenaran: `docs/architecture/caraka-architecture-baseline.md` (MENGIKAT, keputusan D1–D16).
> Disusun: 13 Juni 2026 · Peran: Principal Android Engineer · TPM · Distributed Systems Engineer · DTN Systems Architect
> Lingkup: **hanya migrasi** dari codebase CARAKA yang sudah operasional ke baseline. Tidak ada arsitektur baru, tidak ada teknologi baru, tidak ada kode di dokumen ini.

Dokumen ini menerjemahkan baseline menjadi program eksekusi bertahap. Setiap keputusan baseline dirujuk dengan kode `[Dx]`. Jika ada keraguan, baseline yang berlaku.

---

# Executive Summary

CARAKA hari ini adalah aplikasi mesh "fire-and-forget" berbasis flooding yang berfungsi saat perangkat terhubung simultan. Baseline mewajibkan transformasi ke model **store-carry-forward (DTN)** dengan reliabilitas unicast yang jujur, tanpa mengorbankan keamanan yang sudah ada.

Program ini memecah migrasi menjadi **4 fase** dan **18 execution unit** yang masing-masing dapat di-deploy, di-test, dan di-rollback secara independen dalam <1 hari. Urutan dirancang agar setiap fase menghasilkan aplikasi yang tetap berfungsi (tidak ada "big bang"):

- **Fase 0 – Foundation:** memperbaiki fondasi yang retak (gerbang anti-replay berbasis ID bukan jam [D1], dedup dua-tingkat [D3], kontrak resource [Resource Model], pemisahan skema lifecycle [Message Lifecycle]). Tanpa perubahan perilaku yang terlihat pengguna.
- **Fase 1 – Reliable Unicast:** outbox persisten + ACK end-to-end sebagai satu unit [D4], status delivery jujur di UI [D5/D6].
- **Fase 2 – Limited DTN:** store-carry-forward terbatas untuk kontak verified [D7], queue-processor berbasis timer [D10], MeshRouter berhenti men-drop "no route" [D16], penegakan kuota.
- **Fase 3 – Power Optimization:** duty-cycle dua-state + matikan WiFi saat idle [D14].

Item POSTPONE baseline (carry orang asing [D8], gossip [D12], adaptive beaconing kompleks [D13], BLE [D15]) **tidak** masuk program ini; dibuka hanya oleh gerbang field test.

**Gerbang field test wajib setelah Fase 2** sebelum optimasi/penundaan dilanjutkan.

---

# Current State

Berdasarkan inspeksi codebase (lihat `caraka-gap-analysis.md`):

| Kapabilitas | Status saat ini |
|-------------|-----------------|
| Persistence | Room + SQLCipher v2; `MessageEntity` = chat-log (punya `isRead`, `isRelayed`, `ttl`; **tanpa** state delivery) |
| Dedup | `MeshSocketManager.seenIds` LRU in-memory + cek drift timestamp ±5 menit (gerbang anti-replay berbasis **jam**) |
| Relay tabel | `RelayedMessageEntity` + `RelayDao` ada tapi **mati** (hanya `deleteAll`) |
| Routing | Flooding + hop-limit (TTL) di WifiDirect; `MeshRouter` BATMAN hanya di jalur Aware, **men-drop bila no-route** |
| Unicast | `MeshRepository.sendDirectMessage` fire-and-forget; `return` bila peer tak ada di DB; **tanpa** queue/retry |
| ACK | Tipe `"ACK"` terdefinisi di `MeshProtocol` tapi **tidak pernah dikirim/ditangani** |
| Discovery | Kuat: WiFi Direct DNS-SD + LAN beacon + Aware + Nearby + PEER_LIST gossip |
| Transport abstraksi | `MeshTransport` interface + `MeshManager` facade + overlay sink (fire-and-forget) |
| Keamanan | Ed25519 signature ✅, TOFU ✅, X25519 E2E ✅, Foreground Service ✅ |
| Duty cycle | Interval tetap: beacon 3s, gossip 5s, discovery 6s, heartbeat 10s |

# Target State

Sesuai baseline `Final Architecture Baseline`:
- Unit pesan punya **lifecycle eksplisit** (CREATED→QUEUED→SENT→DELIVERED/EXPIRED/FAILED untuk unicast; CREATED→SENT untuk SOS) dengan **outbox transport terpisah** dari chat-log UI.
- **Dedup dua-tingkat**: `messageExists()` untuk pesan tersimpan + LRU untuk transit; gerbang anti-replay utama = **ID**, bukan jam [D1][D3].
- **Unicast reliabel**: outbox persisten + **ACK end-to-end**, retry berbatas; status UI **jujur** (DELIVERED hanya dari ACK nyata) [D4][D5][D6].
- **DTN terbatas**: carry-and-forward untuk **kontak verified + prioritas + kuota**; pengiriman opportunistic via **queue-processor timer**; router tidak lagi men-drop [D7][D10][D16].
- **Hemat baterai**: duty-cycle dua-state + matikan WiFi idle [D14].
- Kontrak `MeshTransport` **tidak diubah** [D9]. Tidak ada SnW, tidak ada BLE [D11][D15].

---

# Migration Strategy

Prinsip eksekusi (turunan dari baseline "sederhana > optimal", "degradasi anggun", "field test sebagai gerbang"):

1. **Aditif sebelum substitutif.** Tambah struktur baru (tabel outbox, kolom status, jalur ACK) tanpa menghapus jalur lama dulu; alihkan perilaku hanya setelah jalur baru terbukti.
2. **Setiap fase menghasilkan build yang berfungsi.** Tidak ada fase yang meninggalkan aplikasi rusak.
3. **Migrasi DB additive & teruji.** Setiap perubahan skema = migration eksplisit (bukan destructive), diuji upgrade dari versi sebelumnya.
4. **Perubahan berisiko tinggi diisolasi** ke execution unit tersendiri (MeshRouter, lifecycle transport) agar mudah di-rollback.
5. **Gerbang field test** memisahkan Fase 2 dari penundaan; data lapangan, bukan analisis, yang membuka langkah berikutnya.

---

# Phase 0 – Foundation

**Objective:** Memperbaiki fondasi yang menopang seluruh lapisan DTN — gerbang anti-replay yang tahan jam-salah, dedup yang benar, kontrak sumber daya, dan pemisahan skema lifecycle. Tanpa perubahan perilaku yang terlihat pengguna.

**Architectural rationale:** Baseline [D1] menetapkan wall-clock tidak boleh jadi gerbang anti-replay (offline = no NTP); [D3] menetapkan dedup dua-tingkat; Message Lifecycle menetapkan outbox terpisah dari chat-log; Resource Management Model harus ada **sebelum** carry (Fase 2). Membangun ini lebih dulu mencegah kerja ulang dan duplikasi/loop saat jalur DTN ditambahkan.

**Dependencies:** Tidak ada (fondasi).

**Affected modules:** `MeshSocketManager`, `WifiDirectManager` (dispatcher `onMessageReceived`), `CarakaDatabase` (migration v3), `MessageEntity`/`MessageDao`, entity+DAO baru `OutboxEntity`/`OutboxDao`, objek kebijakan/konstanta baru.

**Estimated complexity:** Medium (3–5 hari total; mayoritas unit Small).

**Technical risks:** Migration DB salah → crash/data loss di install lama (SQLCipher menambah kompleksitas); melonggarkan gerbang jam bisa mengubah perilaku anti-replay (harus dipastikan dedup-ID menutupnya).

**Acceptance criteria:**
- Anti-replay tetap mencegah duplikasi yang tampak ke pengguna tanpa bergantung pada jam tersinkron (uji dengan device ber-jam sengaja melenceng).
- Pesan tersimpan tidak pernah dobel walau diterima berkali-kali (uji `messageExists`).
- Upgrade DB v2→v3 sukses tanpa kehilangan data pada device existing.
- Tidak ada perubahan perilaku UI/pengiriman yang terlihat (fase tak terlihat).

---

# Phase 1 – Reliable Unicast

**Objective:** Mengubah unicast TEXT dari fire-and-forget menjadi reliabel: outbox persisten + ACK end-to-end + retry berbatas, dengan status delivery yang jujur di UI.

**Architectural rationale:** Baseline [D4] mewajibkan outbox dan ACK dibangun **sebagai satu unit** (outbox tanpa ACK = retry buta). [D5] melarang implicit-ACK sebagai status delivery; [D6] melarang ACK untuk SOS. Ini lompatan nilai terbesar dari "chat tak pasti" ke "pesan andal".

**Dependencies:** Fase 0 (skema outbox v3 [EU-0.4], lifecycle, dedup [EU-0.2], gerbang ID [EU-0.1]).

**Affected modules:** `MeshRepository` (`sendDirectMessage`), `WifiDirectManager` (dispatcher: case `ACK`; `handleTextMessage`), `MeshProtocol` (tipe ACK sudah ada — **tanpa** perubahan struktur), `OutboxDao`, `MessageDao` (update `deliveryStatus`), UI `MessageStatusIcon`.

**Estimated complexity:** Medium (4–6 hari).

**Technical risks:** Retry storm bila batas tidak diterapkan; ACK hilang → retry sampai TTL (diterima); kesalahan memetakan ACK ke pesan salah (perlu korelasi by message-id).

**Acceptance criteria:**
- Pesan unicast yang diterima memicu ACK; pengirim menandai DELIVERED hanya saat ACK diterima (bukan dari overhearing) [D5].
- Pesan unicast yang gagal di-retry maksimal N kali lalu FAILED; tidak ada retry tak terhingga.
- `MessageStatusIcon` menampilkan SENT vs DELIVERED vs FAILED sesuai state nyata.
- SOS tetap tampil "disiarkan", tidak pernah "sampai", dan **tidak** memicu ACK [D6].

---

# Phase 2 – Limited DTN

**Objective:** Mengaktifkan store-carry-forward terbatas: pesan untuk kontak verified yang sedang tak terjangkau disimpan dan dikirim saat tujuan muncul, dijalankan oleh queue-processor berbasis timer, dengan kuota ditegakkan dan router tidak lagi men-drop.

**Architectural rationale:** Baseline [D7] (carry terbatas: verified + prioritas + kuota), [D10] (queue-processor timer, bukan transport-hook [D9 REJECT]), [D16] (MeshRouter bukan gerbang DTN). Ini inti nilai DTN yang menjawab keluhan "fokus device-to-device".

**Dependencies:** Fase 1 (outbox + ACK + lifecycle), Resource Management policy [EU-0.3], Foreground Service (sudah ada).

**Affected modules:** `MeshForegroundService` (timer tick), `MeshRepository` (`sendDirectMessage` boleh antre untuk peer tak terjangkau), `WifiDirectManager` (`handlePeerList`/`handleHandshake` sebagai pemicu kelayakan flush), `MeshRouter` (no-drop → serahkan ke outbox), `OutboxDao` (query by recipient, eviksi kuota).

**Estimated complexity:** Large (1–2 minggu).

**Technical risks:** Thundering herd saat peer muncul (mitigasi: jitter+debounce); kuota salah → storage/battery exhaustion; perubahan MeshRouter berdampak ke jalur relay (isolasi & rollback); pesan basi menyesatkan (mitigasi: tampil timestamp asli + TTL).

**Acceptance criteria:**
- Pesan unicast ke kontak verified yang offline tersimpan, lalu terkirim & DELIVERED saat kontak kembali (uji field).
- Queue-processor mengalir secara periodik tanpa badai (jitter teramati di log).
- Outbox menghormati kuota: saat penuh, prioritas rendah/tertua/over-replika di-drop lebih dulu.
- MeshRouter tanpa rute tidak lagi men-drop pesan addressed; menyerahkannya ke carry.
- **Gerbang field test** terlampaui sebelum melanjutkan ke penundaan.

---

# Phase 3 – Power Optimization

**Objective:** Menurunkan konsumsi baterai dengan duty-cycle dua-state dan mematikan WiFi Aware/Direct saat idle, mengandalkan discovery ringan.

**Architectural rationale:** Baseline [D14] menetapkan kemenangan baterai teraman = dua-state (active/idle) + matikan radio boros saat idle, **bukan** adaptive beaconing kompleks [D13 POSTPONE].

**Dependencies:** Fase 2 stabil + data baterai field test (baseline menjadikan ini gerbang).

**Affected modules:** `WifiDirectManager` (interval beacon/gossip/discovery, lifecycle WiFi), `MeshManager` (koordinasi transport), `MeshForegroundService`.

**Estimated complexity:** Medium (3–6 hari).

**Technical risks:** Idle terlalu agresif → melewatkan kontak/fragmentasi mesh; mematikan WiFi → latensi discovery naik; siklus on/off berlebihan justru boros (mitigasi: histeresis state).

**Acceptance criteria:**
- Mesh bertahan berjam-jam layar mati dengan penurunan baterai/jam terukur lebih baik dari baseline saat ini.
- Saat aktivitas muncul, mesh kembali ke state active dan discovery pulih dalam batas waktu yang dapat diterima delay-tolerant.
- Tidak ada regresi pada pengiriman SOS/unicast saat transisi state.

---

# Execution Units

Aturan: tiap unit **independently deployable, testable, reversible, <1 hari**, regresi minimal. Kolom **Delegasi** = Opus (logika berisiko/lintas-modul/state-machine/migrasi/router/transport) vs Sonnet (aditif, terbatas, mekanis dengan kebijakan jelas).

---

### EU-0.1 — Gerbang anti-replay berbasis ID (demote jam) · [D1]
- **Goal:** Jadikan dedup-ID gerbang anti-replay utama; cek drift timestamp jadi *hint* (log/longgar), bukan satu-satunya penentu drop.
- **Scope:** Logika `isDuplicate` di `MeshSocketManager`; tidak mengubah struktur pesan.
- **Dependencies:** —
- **Files likely affected:** `MeshSocketManager.kt`.
- **Database changes:** Tidak ada.
- **Protocol changes:** Tidak ada.
- **Test plan:** Dua device, satu ber-jam sengaja melenceng >5 menit → pesan tetap diterima (tidak di-drop oleh jam); kirim pesan ID sama dua kali → drop kedua.
- **Rollback:** Revert satu fungsi; tidak ada state persisten berubah.
- **Acceptance criteria:** Pesan dari device ber-jam-salah tidak di-drop; duplikat by-ID tetap di-drop.
- **Risk level:** 🟡 Sedang (menyentuh anti-replay).
- **Delegasi:** **Opus** (subtil; keamanan anti-replay).

### EU-0.2 — Dedup persisten via `messageExists()` untuk pesan tersimpan · [D3]
- **Goal:** Sebelum memproses TEXT/SOS yang ditujukan ke kita, cek `messageExists()` agar restart tidak membuka pemrosesan ulang pesan tersimpan.
- **Scope:** Dispatcher `onMessageReceived`/handler; melengkapi LRU in-memory (tidak menggantikan).
- **Dependencies:** —
- **Files likely affected:** `WifiDirectManager.kt`, `MessageDao.kt` (method sudah ada).
- **Database changes:** Tidak ada (memakai query yang ada).
- **Protocol changes:** Tidak ada.
- **Test plan:** Simpan pesan, restart proses (kill FGS), kirim ulang pesan ID sama → tidak dobel di DB/UI.
- **Rollback:** Revert pemanggilan cek; perilaku kembali ke LRU-only.
- **Acceptance criteria:** Tidak ada pesan tersimpan dobel setelah restart.
- **Risk level:** 🟢 Rendah (aditif, query existing).
- **Delegasi:** **Sonnet** (terbatas, jelas).

### EU-0.3 — Objek kebijakan Resource Management (kontrak, tanpa perilaku) · [Resource Model]
- **Goal:** Sentralisasi konstanta: kuota outbox (jumlah & byte), TTL maksimum, urutan prioritas drop, batas retry. Belum mengubah perilaku; hanya menyediakan kontrak untuk Fase 1–2.
- **Scope:** Satu objek/konstanta baru; tidak ada call-site yang mengubah alur.
- **Dependencies:** —
- **Files likely affected:** file konstanta baru (mis. di paket `network` atau `data`).
- **Database changes:** Tidak ada.
- **Protocol changes:** Tidak ada.
- **Test plan:** Build sukses; nilai dapat diimpor; unit test pembacaan konstanta.
- **Rollback:** Hapus file; tak ada yang bergantung sampai Fase 1.
- **Acceptance criteria:** Konstanta tersedia & terdokumentasi; tidak ada perubahan perilaku.
- **Risk level:** 🟢 Rendah.
- **Delegasi:** **Sonnet**.

### EU-0.4 — Skema DB v3: tabel `OutboxEntity` + kolom `deliveryStatus` pada `messages` · [Message Lifecycle]
- **Goal:** Pemisahan unit transport (outbox) dari chat-log; tambah kolom status delivery untuk UI. Satu migration aditif.
- **Scope:** Entity+DAO baru `OutboxEntity`/`OutboxDao`; kolom `deliveryStatus` di `MessageEntity` (default mencerminkan pesan lama = SENT); bump versi DB ke 3 + `MIGRATION_2_3`.
- **Dependencies:** —
- **Files likely affected:** `CarakaDatabase.kt`, `MessageEntity.kt`, `MessageDao.kt`, entity/DAO outbox baru.
- **Database changes:** **Ya** — tabel baru `outbox`, kolom baru `deliveryStatus` (additive), migration 2→3.
- **Protocol changes:** Tidak ada.
- **Test plan:** Upgrade dari DB v2 berisi data → buka sukses, data utuh; fresh install v3 sukses; insert/query outbox.
- **Rollback:** Karena additive, build lama mengabaikan tabel/kolom; jangan pakai destructive migration. Rollback = tidak men-ship build (skema tetap kompatibel maju).
- **Acceptance criteria:** Migrasi 2→3 lulus uji tanpa data loss; outbox dapat ditulis/dibaca.
- **Risk level:** 🟡 Sedang (migrasi + SQLCipher).
- **Delegasi:** **Opus** (migrasi DB terenkripsi, risiko data loss).

---

### EU-1.1 — Tulis ke outbox saat kirim unicast (aditif) · [D4]
- **Goal:** `sendDirectMessage` menulis entri `OutboxEntity` (state QUEUED→akan SENT) selain alur kirim yang ada; chat-log diberi `deliveryStatus = SENT`.
- **Scope:** Jalur tulis saja; belum ada retry/ACK; tidak menghapus alur kirim lama.
- **Dependencies:** EU-0.4.
- **Files likely affected:** `MeshRepository.kt`, `OutboxDao`.
- **Database changes:** Memakai tabel outbox (EU-0.4).
- **Protocol changes:** Tidak ada.
- **Test plan:** Kirim pesan → entri outbox muncul dengan state benar; chat-log `deliveryStatus=SENT`.
- **Rollback:** Revert penulisan outbox; pengiriman kembali seperti sebelumnya.
- **Acceptance criteria:** Setiap unicast terkirim menghasilkan entri outbox; tidak ada regresi pengiriman.
- **Risk level:** 🟢 Rendah (aditif).
- **Delegasi:** **Sonnet**.

### EU-1.2 — Handler ACK di penerima (kirim ACK) · [D4]
- **Goal:** Saat menerima TEXT unicast yang ditujukan ke kita, kirim balik pesan `ACK` (tipe sudah ada di `MeshProtocol`) berisi message-id.
- **Scope:** `handleTextMessage` (hanya unicast untuk kita); pakai `sendToPeer` ke pengirim.
- **Dependencies:** EU-0.2 (idempotensi penerimaan).
- **Files likely affected:** `WifiDirectManager.kt`.
- **Database changes:** Tidak ada.
- **Protocol changes:** Memakai tipe `ACK` yang **sudah** ada (tanpa perubahan struktur); `content` = id pesan yang di-ACK.
- **Test plan:** Dua device, kirim unicast A→B → B mengirim ACK; lihat ACK di log A.
- **Rollback:** Revert pengiriman ACK; sistem kembali tanpa ACK.
- **Acceptance criteria:** Penerima unicast selalu mengirim ACK; SOS/broadcast **tidak** memicu ACK [D6].
- **Risk level:** 🟢 Rendah.
- **Delegasi:** **Sonnet** (terbatas; SOS-exclusion harus jelas).

### EU-1.3 — Proses ACK di pengirim → DELIVERED · [D4][D5]
- **Goal:** Tambah case `"ACK"` di dispatcher; korelasikan ke entri outbox by message-id; tandai outbox DELIVERED & update `MessageEntity.deliveryStatus`; hapus dari outbox.
- **Scope:** Dispatcher + state-machine outbox; **dilarang** menandai DELIVERED dari sumber lain (no implicit ACK) [D5].
- **Dependencies:** EU-1.1, EU-1.2.
- **Files likely affected:** `WifiDirectManager.kt`, `OutboxDao`, `MessageDao`.
- **Database changes:** Update kolom `deliveryStatus`; hapus baris outbox.
- **Protocol changes:** Konsumsi tipe `ACK`.
- **Test plan:** A→B unicast, B ACK → status A berubah DELIVERED; ACK untuk id tak dikenal diabaikan dengan aman.
- **Rollback:** Revert case ACK; status berhenti di SENT (tidak salah, hanya kurang informatif).
- **Acceptance criteria:** DELIVERED hanya muncul dari ACK nyata; korelasi id benar.
- **Risk level:** 🟡 Sedang (state-machine).
- **Delegasi:** **Opus** (state-machine, korelasi, larangan implicit-ACK).

### EU-1.4 — Retry berbatas untuk entri outbox PENDING · [D4]
- **Goal:** Kirim ulang entri yang belum di-ACK hingga N kali dengan backoff+jitter; setelah habis → FAILED.
- **Scope:** Mekanisme retry dipicu sederhana (mis. saat kirim berikutnya atau tick ringan); batas dari EU-0.3.
- **Dependencies:** EU-1.1, EU-1.3, EU-0.3.
- **Files likely affected:** `MeshRepository.kt`/komponen pengirim, `OutboxDao`.
- **Database changes:** Update `attemptCount`/`nextAttemptAt` (kolom di outbox EU-0.4).
- **Protocol changes:** Tidak ada.
- **Test plan:** Matikan penerima → pengirim retry N kali lalu FAILED; nyalakan sebelum batas → DELIVERED.
- **Rollback:** Nonaktifkan retry (kirim sekali); outbox tetap konsisten.
- **Acceptance criteria:** Tidak ada retry tak terhingga; FAILED setelah N; backoff teramati.
- **Risk level:** 🟡 Sedang (potensi retry storm bila batas salah).
- **Delegasi:** **Opus** (kontrol kongesti/backoff).

### EU-1.5 — Status delivery jujur di UI · [D5][D6]
- **Goal:** `MessageStatusIcon` digerakkan oleh `deliveryStatus` (SENT/DELIVERED/FAILED). SOS tampil "disiarkan", tak pernah "sampai".
- **Scope:** Binding UI saja; tidak ada logika jaringan.
- **Dependencies:** EU-1.3 (sumber status).
- **Files likely affected:** `MessageStatusIcon.kt`, layar chat terkait, mapping ViewModel.
- **Database changes:** Tidak ada (membaca kolom existing).
- **Protocol changes:** Tidak ada.
- **Test plan:** Kirim unicast → ikon berubah SENT→DELIVERED; matikan penerima → FAILED; SOS → label "disiarkan".
- **Rollback:** Revert binding; UI kembali ke tampilan lama.
- **Acceptance criteria:** Tidak ada label "sampai" tanpa ACK; SOS jujur.
- **Risk level:** 🟢 Rendah (UI).
- **Delegasi:** **Sonnet**.

---

### EU-2.1 — Queue-processor berbasis timer di Foreground Service · [D10]
- **Goal:** Tick periodik membaca outbox + peer reachable, flush PENDING ke tujuan terjangkau, dengan jitter/debounce.
- **Scope:** Timer di FGS; memanggil jalur kirim existing; tidak mengubah kontrak `MeshTransport` [D9].
- **Dependencies:** EU-1.1, EU-1.4.
- **Files likely affected:** `MeshForegroundService.kt`, `MeshRepository.kt`/pengirim, `OutboxDao`.
- **Database changes:** Query outbox (existing).
- **Protocol changes:** Tidak ada.
- **Test plan:** Antre beberapa pesan → tick mengirim bertahap; log menunjukkan jitter; tidak ada badai.
- **Rollback:** Matikan timer; kembali ke pengiriman saat-aksi.
- **Acceptance criteria:** Flush periodik berjalan; debounce mencegah badai; baterai tidak meningkat drastis.
- **Risk level:** 🟡 Sedang (penjadwalan di FGS/Doze).
- **Delegasi:** **Opus** (penjadwalan, debounce, interaksi Doze).

### EU-2.2 — Izinkan antre carry untuk kontak verified tak terjangkau · [D7]
- **Goal:** `sendDirectMessage` tidak lagi `return` saat peer verified ada di DB tapi sedang tak terjangkau; pesan tetap di outbox untuk carry.
- **Scope:** Hanya kontak **verified**; pesan untuk peer tak dikenal sama sekali tetap ditolak (carry orang asing = [D8 POSTPONE]).
- **Dependencies:** EU-2.1.
- **Files likely affected:** `MeshRepository.kt`.
- **Database changes:** Outbox (existing).
- **Protocol changes:** Tidak ada.
- **Test plan:** Kirim ke kontak verified yang offline → tersimpan; kontak kembali → terkirim & DELIVERED.
- **Rollback:** Kembalikan early-return; carry nonaktif.
- **Acceptance criteria:** Pesan ke kontak verified offline tidak hilang; peer tak dikenal tetap ditolak.
- **Risk level:** 🟡 Sedang.
- **Delegasi:** **Opus** (kebijakan kelayakan carry + interaksi lifecycle).

### EU-2.3 — Penegakan kuota & TTL pada outbox · [D7][Resource Model]
- **Goal:** Terapkan kebijakan EU-0.3: batasi ukuran outbox, eviksi berdasarkan prioritas/umur/replika, buang entri lewat TTL maksimum.
- **Scope:** Logika eviksi saat insert/tick; memakai konstanta EU-0.3.
- **Dependencies:** EU-2.1, EU-0.3.
- **Files likely affected:** `OutboxDao`, pengirim/processor.
- **Database changes:** Delete by policy (query baru di DAO).
- **Protocol changes:** Tidak ada.
- **Test plan:** Banjiri outbox melebihi kuota → entri prioritas rendah/tertua dibuang; entri lewat TTL dibuang; SOS dipertahankan di atas status.
- **Rollback:** Nonaktifkan eviksi (outbox tumbuh) — hanya untuk rollback darurat.
- **Acceptance criteria:** Outbox tak pernah melampaui kuota; prioritas dihormati.
- **Risk level:** 🟡 Sedang (salah eviksi bisa buang pesan penting).
- **Delegasi:** **Opus** (kebijakan drop kritis).

### EU-2.4 — MeshRouter: no-drop → serahkan ke outbox/carry · [D16]
- **Goal:** Saat tak ada rute, MeshRouter tidak men-drop pesan addressed; menyerahkannya ke outbox/carry.
- **Scope:** Hanya jalur Aware (tempat MeshRouter aktif); diisolasi sebagai unit tersendiri agar mudah rollback.
- **Dependencies:** EU-2.2.
- **Files likely affected:** `MeshRouter.kt`, jembatan ke `MeshRepository`/outbox.
- **Database changes:** Outbox (existing).
- **Protocol changes:** Tidak ada.
- **Test plan:** Tujuan tak ada di routing table → pesan masuk carry, bukan hilang; saat rute muncul → terkirim.
- **Rollback:** Kembalikan perilaku drop; isolasi memudahkan revert.
- **Acceptance criteria:** Tidak ada pesan addressed yang di-drop karena no-route; tidak ada loop (dedup menahan).
- **Risk level:** 🔴 Tinggi (mengubah jalur relay inti).
- **Delegasi:** **Opus** (perubahan router berisiko tinggi).

---

### EU-3.1 — Duty-cycle dua-state (active/idle) untuk interval beacon/gossip/discovery · [D14]
- **Goal:** Lebarkan interval saat idle (tak ada peer baru/aktivitas selama N detik), kembali rapat saat aktif; histeresis untuk cegah flapping.
- **Scope:** Penjadwalan interval di `WifiDirectManager`; tidak mematikan radio (itu EU-3.2).
- **Dependencies:** Fase 2 stabil + gerbang field test baterai.
- **Files likely affected:** `WifiDirectManager.kt`.
- **Database changes:** Tidak ada.
- **Protocol changes:** Tidak ada.
- **Test plan:** Idle → interval melebar (log); aktivitas → kembali rapat; ukur penurunan baterai.
- **Rollback:** Kembalikan interval tetap.
- **Acceptance criteria:** Baterai/jam membaik saat idle; discovery pulih saat aktif; tidak ada regresi SOS/unicast.
- **Risk level:** 🟡 Sedang.
- **Delegasi:** **Sonnet** (perubahan interval terbatas; kebijakan dari baseline).

### EU-3.2 — Matikan WiFi Aware/Direct saat idle, nyalakan saat aktif · [D14]
- **Goal:** Saat state idle berkepanjangan, hentikan transport WiFi boros; andalkan discovery ringan; nyalakan kembali saat ada aktivitas.
- **Scope:** Lifecycle transport via `MeshManager`/`WifiDirectManager`; histeresis untuk cegah on/off berlebihan.
- **Dependencies:** EU-3.1.
- **Files likely affected:** `MeshManager.kt`, `WifiDirectManager.kt`, `MeshForegroundService.kt`.
- **Database changes:** Tidak ada.
- **Protocol changes:** Tidak ada.
- **Test plan:** Idle lama → WiFi mati, mesh tetap dapat bangun saat aktivitas; ukur baterai; pastikan SOS masuk tetap memicu wake.
- **Rollback:** Jangan matikan WiFi; kembali ke EU-3.1 saja.
- **Acceptance criteria:** Penurunan baterai signifikan saat idle; pemulihan transport dalam batas delay-tolerant; tidak ada SOS hilang.
- **Risk level:** 🔴 Tinggi (lifecycle transport; risiko mesh tak pulih).
- **Delegasi:** **Opus** (lifecycle transport, histeresis, risiko tinggi).

---

### Ringkasan Execution Unit

| ID | Fase | Risiko | Delegasi | DB | Protokol |
|----|------|--------|----------|----|----------|
| EU-0.1 | 0 | 🟡 | Opus | — | — |
| EU-0.2 | 0 | 🟢 | Sonnet | — | — |
| EU-0.3 | 0 | 🟢 | Sonnet | — | — |
| EU-0.4 | 0 | 🟡 | Opus | v3 (additive) | — |
| EU-1.1 | 1 | 🟢 | Sonnet | outbox write | — |
| EU-1.2 | 1 | 🟢 | Sonnet | — | pakai ACK (ada) |
| EU-1.3 | 1 | 🟡 | Opus | update/del outbox | konsumsi ACK |
| EU-1.4 | 1 | 🟡 | Opus | attempt fields | — |
| EU-1.5 | 1 | 🟢 | Sonnet | — | — |
| EU-2.1 | 2 | 🟡 | Opus | query outbox | — |
| EU-2.2 | 2 | 🟡 | Opus | outbox | — |
| EU-2.3 | 2 | 🟡 | Opus | delete-by-policy | — |
| EU-2.4 | 2 | 🔴 | Opus | outbox | — |
| EU-3.1 | 3 | 🟡 | Sonnet | — | — |
| EU-3.2 | 3 | 🔴 | Opus | — | — |

---

# Recommended Execution Order

Urutan menghormati dependensi & menjaga setiap langkah dapat di-deploy:

```
Fase 0:  EU-0.3 → EU-0.1 → EU-0.2 → EU-0.4
Fase 1:  EU-1.1 → EU-1.2 → EU-1.3 → EU-1.4 → EU-1.5
──────── GERBANG: build stabil, unicast reliabel terverifikasi 2 device ────────
Fase 2:  EU-2.1 → EU-2.2 → EU-2.3 → EU-2.4
──────── GERBANG FIELD TEST WAJIB (baseline) sebelum Fase 3 / item POSTPONE ────────
Fase 3:  EU-3.1 → EU-3.2
```

Catatan urutan:
- **EU-0.3 lebih dulu** (kontrak konstanta) agar EU-1.4/EU-2.3 punya rujukan.
- **EU-0.4 terakhir di Fase 0** karena unit lain tak bergantung skema; migrasi diisolasi.
- **EU-1.1→1.2→1.3** membentuk loop ACK minimal sebelum retry (EU-1.4).
- **EU-2.4 (router, 🔴) terakhir di Fase 2** setelah carry & kuota stabil, agar perubahan berisiko tinggi punya jaring pengaman.
- **EU-3.2 (🔴) paling akhir**; hanya jika field test membuktikan baterai jadi masalah.

---

# Field Testing Plan

Gerbang wajib setelah Fase 2 (baseline menjadikan field test sebagai gerbang pencabutan POSTPONE).

**Setup:**
- 5–10 device fisik, beragam OEM (sertakan Infinix/Tecno/Redmi yang diketahui agresif).
- **Mode pesawat** (WiFi/BLE on, seluler off) untuk simulasi blackout total.
- Skenario mobilitas: statis (shelter), bergerak (kurir antar-cluster), terpartisi (dua grup terpisah lalu disatukan oleh satu node bergerak).

**Skenario uji (memetakan Success Criteria baseline):**
1. **Unicast offline→online:** kirim ke kontak verified yang mati; nyalakan kemudian → harus DELIVERED (membuktikan EU-2.2 + ACK).
2. **Status jujur:** verifikasi tidak ada "sampai" tanpa ACK; SOS selalu "disiarkan".
3. **SOS multi-hop:** SOS dari satu ujung menyebar ≥3 hop ke puluhan device; ukur cakupan & tanpa ACK implosion.
4. **Anti-replay/dedup:** banjiri ulang pesan → tidak ada dobel yang tampak; uji device ber-jam-salah.
5. **Daya tahan baterai:** mesh hidup berjam-jam layar mati; catat %/jam (baseline vs Fase 3).
6. **Kuota:** banjiri outbox → kuota dihormati, SOS dipertahankan.
7. **Carry via kurir:** dua grup terpisah; satu node berjalan di antara → pesan menyeberang.

**Metric yang dicatat:** delivery rate unicast, latensi end-to-end per hop, jumlah hop tercapai, %/jam baterai, jumlah duplikat tampak, jumlah pesan FAILED.

**Kriteria lulus gerbang:** Success Criteria #1–#7 baseline terpenuhi; tidak ada regresi keamanan; baterai layak untuk skenario satu hari.

---

# Regression Testing Matrix

Fitur yang **wajib tetap berfungsi** di setiap fase (uji setelah tiap fase):

| Fitur existing | Fase 0 | Fase 1 | Fase 2 | Fase 3 |
|----------------|:------:|:------:|:------:|:------:|
| Peer discovery (DNS-SD/LAN/Aware/Nearby) | ✓ | ✓ | ✓ | ✓ |
| PEER_LIST gossip | ✓ | ✓ | ✓ | ✓ |
| Handshake + TOFU key-continuity | ✓ | ✓ | ✓ | ✓ |
| Ed25519 signature verify (TEXT/SOS) | ✓ | ✓ | ✓ | ✓ |
| SOS broadcast multi-hop + relay TTL | ✓ | ✓ | ✓ | ✓ |
| E2E enkripsi unicast | ✓ | ✓ | ✓ | ✓ |
| Foreground Service survive layar mati | ✓ | ✓ | ✓ | ✓ |
| Chat UI & riwayat | ✓ | ✓ | ✓ | ✓ |
| Anti-replay (no dobel) | ✓ (gate ID) | ✓ | ✓ | ✓ |
| Secure wipe / clear identity | ✓ | ✓ | ✓ | ✓ |
| Migrasi DB upgrade tanpa data loss | — (v3) | ✓ | ✓ | ✓ |
| Unicast reliabel + status | — | ✓ | ✓ | ✓ |
| Carry-and-forward | — | — | ✓ | ✓ |
| Daya tahan baterai (tak memburuk) | ✓ | ✓ | ✓ | ✓ (membaik) |

Aturan: **tidak ada fitur ✓ yang boleh regres** akibat fase berjalan. Setiap EU berisiko 🔴 (EU-2.4, EU-3.2) wajib lulus matriks penuh sebelum merge.

---

# Definition of Done

**Per execution unit:**
- Memenuhi seluruh *acceptance criteria*-nya.
- Build sukses; tidak ada regresi pada matriks regresi yang relevan.
- Dapat di-rollback sesuai *rollback strategy* tanpa kerusakan data.
- Perubahan DB (bila ada) teruji upgrade dari versi sebelumnya tanpa data loss.
- Tidak melanggar keputusan baseline mana pun (D1–D16); khususnya: tidak ada implicit-ACK-sebagai-delivery [D5], tidak ada ACK SOS [D6], kontrak `MeshTransport` tak berubah [D9], tidak ada SnW/BLE [D11/D15].

**Per fase:**
- Seluruh EU dalam fase selesai (DoD unit).
- Acceptance criteria fase terpenuhi.
- Matriks regresi lulus penuh.
- Build dapat di-ship sebagai aplikasi yang berfungsi (tidak ada keadaan setengah jadi yang merusak).

**Program keseluruhan (akhir Fase 2 + gerbang):**
- Field Testing Plan lulus seluruh kriteria gerbang.
- Success Criteria baseline #1–#7 terpenuhi & terdokumentasi dengan metrik.
- Keputusan POSTPONE (D8/D12/D13/D15) hanya dibuka oleh data field test, melalui revisi eksplisit baseline.

---

## Out of Scope / Future Gates (tidak dikerjakan di program ini)
Sesuai baseline, **tidak** dieksekusi sampai gerbang field test membukanya:
- **D8** carry untuk orang asing / epidemic umum.
- **D12** gossip/probabilistic forwarding (cadangan skala).
- **D13** adaptive beaconing kompleks (accelerometer/contact-history).
- **D15** BLE backbone (mulai presence-only bila dibuka).
- Social-graph hardening (onion/anonim) — di luar lingkup v1 [D2].

---

*Program ini mengikuti `caraka-architecture-baseline.md` secara penuh. Tidak ada teknologi baru, tidak ada arsitektur baru, tidak ada kode. Setiap execution unit dirancang aman, kecil, dan reversibel; perubahan berisiko tinggi diisolasi dan dijadwalkan paling akhir dalam fasenya, di belakang gerbang verifikasi.*
