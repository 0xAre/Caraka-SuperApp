# Riset: Komunikasi Offline Multi-Device di Android
## Alternatif Transport & Analisis Kompetitor

**Tanggal Riset**: 7 Juni 2026  
**Konteks**: Evaluasi teknologi untuk CARAKA Mesh — komunikasi darurat offline antar >2 perangkat dengan relay

---

## 1. Lanskap Transport Technology untuk Offline Mesh

Setiap smartphone modern memiliki beberapa radio hardware yang bisa dimanfaatkan untuk komunikasi tanpa internet:

```
┌─────────────────────────────────────────────────────────────────┐
│              RADIO HARDWARE DI SMARTPHONE MODERN               │
│                                                                 │
│  WiFi Radio ────── WiFi Direct (P2P)          ~100m, high BW  │
│              ────── Hotspot / AP Mode          ~100m, high BW  │
│              ────── WiFi Aware (NAN)           ~10m, low power │
│              ────── LAN UDP (jika ada router)  ~100m unlimited │
│                                                                 │
│  Bluetooth ─────── BLE Mesh (Advertising)     ~10-100m, low   │
│              ────── Classic BT (RFCOMM)        ~10-30m, medium │
│                                                                 │
│  + Hardware eksternal:                                          │
│  LoRa Radio ───── via Bluetooth/USB           ~1-10km, low BW │
│  Satellite ─────── via hardware dongle        global, $$$      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Analisis Per-Teknologi

---

### 2.1 WiFi Direct (P2P)

**Yang sudah digunakan CARAKA saat ini.**

**Cara Kerja:**
- Satu device menjadi Group Owner (GO), sisanya menjadi clients
- Koneksi dibentuk melalui negotiation WPA2-Enterprise
- Range: ~100-200 meter (theoretically), praktik ~50-100m

**Kelebihan:**
- Bandwidth tinggi: 50-250 Mbps
- Range terbaik di antara opsi smartphone-only
- Tidak butuh WiFi router eksternal
- Sudah ada di semua Android 4.0+

**Kekurangan (yang CARAKA alami):**
- **Star topology** — clients tidak bisa direct ke sesama client
- **Satu GO per group** — tidak bisa chain antar groups secara native
- **OEM fragmentation** — MIUI, XOS, HiOS mengimplementasi berbeda
- `discoverServices()` BUSY di beberapa OEM (Infinix, Tecno)
- `setDeviceName()` reflection gagal di MIUI/HyperOS
- Auto-invite ke non-CARAKA device (Smart TV, dll.)
- Butuh GPS permission aktif di Android 10+

**Status untuk Full Mesh:**  
❌ Tidak bisa full mesh secara native. Butuh relay di layer aplikasi.

---

### 2.2 LAN UDP / WiFi LAN

**Transport sekunder yang sudah ada di CARAKA.**

**Cara Kerja:**
- Semua device di WiFi router yang sama → satu subnet
- UDP broadcast/unicast ke port tertentu (CARAKA: 8890)
- Device teridentifikasi via IP address + peerId

**Kelebihan:**
- **Full mesh secara native** — A ke B, B ke C, A ke C langsung
- Tidak ada topologi pembatasan (bukan star)
- Sangat reliable di semua OEM (tidak bergantung WiFi Direct API)
- Bandwidth unlimited, latency rendah
- Mudah implementasi

**Kekurangan:**
- **Butuh WiFi router** — tidak bekerja saat offline total (tidak ada router)
- Tidak bekerja di 4G/5G hotspot pada beberapa device (isolated client mode)
- DHCP dapat berubah (IP dinamis)
- `dhcpInfo` API deprecated di Android 12+ (ada warning tapi masih bekerja)

**Status untuk Full Mesh:**  
✅ **Solusi terbaik jika ada WiFi router.** Tidak memerlukan workaround apapun.

---

### 2.3 Bluetooth Low Energy (BLE) Mesh

**Alternatif yang belum dipakai CARAKA.**

**Cara Kerja:**
- BLE Advertising Mode — device broadcast packet ke semua yang mendengarkan
- **Managed Flooding** — setiap device relay pesan yang belum pernah dilihat
- TTL (Time-to-Live) mencegah loop tak terbatas
- Tidak butuh "pairing" — node langsung relay tanpa koneksi permanen

**Kelebihan:**
- **True multi-hop mesh** — setiap device relay ke semua yang dalam jangkauan
- **Tidak ada star topology** — B dan C bisa relay tanpa melewati A
- Power efficiency lebih baik dari WiFi Direct (duty cycle)
- Bekerja 100% offline tanpa router
- BitChat menggunakan ini dengan sukses

**Kekurangan:**
- Range lebih pendek: **10-100 meter** (vs WiFi Direct ~100-200m)
- Bandwidth rendah: ~250 Kbps (vs WiFi Direct 50+ Mbps)
- Latency lebih tinggi karena flooding
- BLE background scanning dibatasi Android 8+ (Doze mode)
- Beberapa OEM agresif membatasi BLE background di MIUI, XOS
- Hanya cocok untuk teks/pesan kecil, bukan file transfer

**Range Extension via Multi-Hop:**
```
Device A ──(50m)── Device B ──(50m)── Device C ──(50m)── Device D
Total jarak efektif: ~150m dengan relay 
(makin banyak device, makin jauh jangkauan)
```

**Status untuk Full Mesh:**  
✅ **Ideal untuk teks mesh offline.** Murni peer-to-peer, tidak butuh router.

---

### 2.4 WiFi Aware / NAN (Neighbor Awareness Networking)

**Teknologi baru yang belum dievaluasi CARAKA.**

**Cara Kerja:**
- Device membentuk "cluster" secara otomatis menggunakan scheduled discovery windows
- Publish/Subscribe model untuk discovery tanpa access point
- Setelah discovery, bisa buka data path (NDP) untuk transfer data
- Tidak butuh WiFi router, tidak butuh WiFi Direct group negotiation

**Kelebihan:**
- Discovery lebih efficient dari WiFi Direct (tidak ada GO election)
- Power efficiency lebih baik dari WiFi Direct
- Discovery tanpa router, data transfer tanpa router
- Bandwidth cukup tinggi setelah NDP terbentuk

**Kekurangan:**
- **Fragmentasi hardware parah** — butuh specific WiFi chipset + HAL support
- **Tidak ada multi-hop native** — tetap point-to-point seperti WiFi Direct
- Banyak device tidak support (tidak ada cara tahu tanpa tes langsung)
- API complex dan debugging sulit ("unknown errors" sangat umum)
- OEM tertentu tidak implement meski spec support
- Tidak ada di Android < 8.0 (API 26)
- Cross-platform: hanya Android, iOS tidak support

**Dukungan Device (estimasi 2024-2026):**
- Pixel 3+ : ✅ Support
- Samsung Galaxy S10+ : ✅ Support  
- Redmi Note series: ⚠️ Tidak pasti (MIUI HAL)
- Infinix/Tecno: ⚠️ Sangat tidak pasti

**Status untuk Full Mesh:**  
⚠️ **Menarik tapi terlalu berisiko.** Fragmentasi terlalu besar untuk deployment luas.

---

### 2.5 Hotspot / SoftAP Mode

**Alternatif yang belum dipakai CARAKA.**

**Cara Kerja:**
- Satu device aktifkan WiFi Hotspot (SoftAP) menggunakan `WifiManager.startLocalOnlyHotspot()`
- Device lain connect ke hotspot tersebut seperti WiFi biasa
- Semua yang connect dapat IP di subnet yang sama
- Traffic bisa relay via aplikasi di device yang jadi hotspot

**Kelebihan:**
- Sangat reliable — tidak ada OEM quirks seperti WiFi Direct
- Semua device client dapat saling bicara via IP (full mesh di subnet)
- Tidak butuh GPS permission (berbeda dari WiFi Direct)
- Android 8+ support `startLocalOnlyHotspot()` tanpa butuh jadi carrier
- Bandwidth tinggi sama seperti WiFi LAN

**Kekurangan:**
- **Satu device harus jadi hotspot** — mirip GO issue di WiFi Direct
- Hotspot mematikan koneksi WiFi device yang jadi host
- Device lain harus disconnect dari WiFi mereka untuk join hotspot
- Hanya 1 layer hop — yang tidak dalam jangkauan hotspot tidak bisa join
- Android 13+ membutuhkan permission tambahan untuk custom SSID/config

**Hybrid Hotspot + WiFi Direct:**
Secara teori bisa: Device A jadi hotspot → B dan C connect ke A → B dan C bisa saling ping via LAN subnet A. Ini adalah **WiFi Direct Star yang lebih reliable** karena menggunakan standard WiFi stack bukan P2P stack.

**Status untuk Full Mesh:**  
⚠️ **Lebih reliable dari WiFi Direct untuk star topology.** Bukan solusi full mesh.

---

### 2.6 Classic Bluetooth (RFCOMM/SPP)

**Tidak direkomendasikan untuk mesh.**

**Cara Kerja:**
- Point-to-point connection setelah pairing
- Serial Port Profile (SPP) atau RFCOMM untuk data transfer
- Maximum 7 simultaneous piconet connections per device

**Kekurangan untuk Mesh:**
- Butuh pairing untuk setiap koneksi
- 7 device limit per piconet
- Tidak ada routing/relay native
- Deprecated di banyak usecase oleh BLE

**Status:**  
❌ **Tidak cocok untuk mesh networking.**

---

## 3. Analisis Kompetitor

---

### 3.1 Briar
**Status**: Aktif, open-source  
**Transport**: Bluetooth + WiFi Direct + Tor (opsional)  
**Protocol**: Bramble (custom delay-tolerant networking)

**Arsitektur:**
- **Bukan real-time mesh** — store-and-forward (DTN model)
- Sync database antar device saat dalam jangkauan
- "Social mesh" — hanya relay ke kontak terpercaya (1-hop privacy)
- Sedang meneliti multi-hop publik (belum release)

**Kelebihan:**
- Security model terbaik — enkripsi penuh, relay tidak bisa baca
- Bisa relay pesan via "human carrier" (orang membawa data secara fisik)
- Open-source, diaudit

**Kekurangan:**
- Bukan instant delivery — "delay tolerant" artinya bisa menit/jam
- Belum ada true multi-hop publik
- Heavy untuk use-case emergency real-time

**Pelajaran untuk CARAKA:**
> Briar membuktikan Bluetooth + WiFi Direct bisa bekerja, tapi fokusnya pada ketahanan jangka panjang bukan real-time. Untuk CARAKA (emergency SOS), delivery delay yang lebih cepat lebih penting.

---

### 3.2 BitChat
**Status**: Aktif, open-source (GitHub)  
**Transport**: **BLE (Bluetooth Low Energy) mesh**  
**Protocol**: Custom BLE advertising + managed flooding

**Arsitektur:**
- Pure BLE — tidak menggunakan WiFi Direct sama sekali
- Setiap device broadcast pesan via BLE advertising
- Device lain relay jika belum pernah lihat message ID tersebut (anti-duplikat via seenIds)
- TTL limit untuk mencegah loop
- Store-and-forward untuk device yang tidak online

**Kelebihan:**
- **True peer-to-peer mesh** — tidak ada "group owner"
- **B dan C bisa saling kirim** tanpa lewat A
- Tidak perlu pairing, tidak perlu invite
- Cross-platform (Android + iOS) via BLE

**Kekurangan:**
- Range pendek: ~100m per hop (BLE)
- Bandwidth terbatas (hanya cocok teks)
- BLE background dibatasi di beberapa Android OEM

**Pelajaran untuk CARAKA:**
> BitChat membuktikan BLE mesh bekerja untuk teks di smartphone. Model flooding + TTL + seenIds mirip dengan apa yang CARAKA butuhkan. **BLE sebagai transport mesh alternatif bisa menyelesaikan masalah star topology WiFi Direct.**

---

### 3.3 Bridgefy
**Status**: Aktif, closed-source SDK  
**Transport**: BLE Mesh  
**Protocol**: Proprietary (sebelumnya bermasalah keamanan, sekarang pakai Signal Protocol)

**Arsitektur:**
- BLE advertising mesh dengan propagation profiles
- Mode: Direct, Mesh, Broadcast
- Configurable: hop limit, TTL, message persistence

**Kelebihan:**
- Terbukti di lapangan: demo dan protes besar (Hong Kong, Myanmar)
- Configurable propagation sesuai density area
- SDK tersedia untuk integrasi

**Kekurangan:**
- Closed-source — tidak bisa audit penuh
- Pernah ada vulnerabilitas serius (ETH Zurich report 2020)
- Komersial — SDK tidak gratis untuk skala besar

**Pelajaran untuk CARAKA:**
> Pendekatan BLE mesh dengan configurable TTL dan propagation profiles adalah pattern yang tepat untuk disaster scenario. Density rendah = butuh TTL lebih tinggi.

---

### 3.4 Berty (Wesh Network)
**Status**: Aktif, open-source  
**Transport**: **BLE + mDNS (LAN) + IPFS (internet)**  
**Protocol**: libp2p + Wesh Network (Go)

**Arsitektur:**
- Multi-transport otomatis — pilih transport terbaik yang tersedia
- BLE saat offline, mDNS saat ada LAN, IPFS saat ada internet
- Store-and-forward async
- Serverless, cryptographic identity

**Kelebihan:**
- **Multi-transport paling sophisticated** di antara kompetitor
- Otomatis switch transport sesuai ketersediaan
- Libp2p yang battle-tested
- Open-source penuh, diaudit

**Kekurangan:**
- Complex — menggunakan Go + gomobile bridge ke React Native
- Overkill untuk aplikasi native Kotlin
- BLE bagian yang sama keterbatasannya
- IPFS dependency untuk internet mode

**Pelajaran untuk CARAKA:**
> **Arsitektur multi-transport Berty adalah yang paling relevan sebagai model.** CARAKA sudah punya fondasi ini (LAN + WiFi Direct). Yang kurang: BLE sebagai transport ketiga untuk full offline mesh.

---

### 3.5 Meshtastic
**Status**: Aktif, open-source  
**Transport**: **LoRa Radio** (via hardware eksternal)  
**Hardware**: LILYGO T-Beam, RAK WisBlock, Heltec, dll.

**Arsitektur:**
- LoRa node terhubung ke HP via Bluetooth
- Setiap node = relay otomatis
- Managed flooding dengan heard-before suppression
- TTL per packet, hop counter

**Range:**
- 1 node: **1-10 km** tergantung terrain
- Multi-hop: bisa puluhan km dengan chain relay

**Kelebihan:**
- Range jauh superior (10x-100x dibanding BLE/WiFi)
- Bekerja di kondisi paling ekstrem (gunung, bencana)
- Konsumsi daya sangat rendah (node bisa berbulan-bulan dengan baterai)
- Komunitas aktif, protocol matang

**Kekurangan:**
- **Butuh hardware eksternal** (~$15-50 per node)
- Bandwidth sangat rendah (250 bps - 5.5 Kbps)
- Hanya untuk teks pendek, tidak untuk file/media
- Latency tinggi (beberapa detik per hop)
- Tidak semua user mau beli hardware

**Relevansi untuk CARAKA:**
> Untuk skenario bencana nyata dengan jarak jauh, integrasi Meshtastic sebagai **transport LoRa opsional** adalah fitur differentiator yang kuat. Jika ada LoRa node di lapangan, CARAKA bisa relay pesan via LoRa → HP via BT → WiFi Direct ke HP lain.

---

## 4. Matrix Perbandingan

| Teknologi | Range | Bandwidth | Multi-Hop Native | Butuh Infra | OEM Compat | Power | Implementasi |
|-----------|-------|-----------|-----------------|-------------|------------|-------|--------------|
| WiFi Direct | ★★★★ | ★★★★★ | ❌ (star) | ❌ | ⚠️ (fragmented) | ★★★ | Sudah ada |
| LAN UDP | ★★★★ | ★★★★★ | ✅ | ✅ (router) | ✅ | ★★★★ | Sudah ada |
| BLE Mesh | ★★ | ★ | ✅ | ❌ | ★★★ | ★★★★★ | Belum |
| WiFi Aware | ★★★ | ★★★★ | ❌ | ❌ | ⚠️ (terlalu varied) | ★★★★ | Berisiko |
| Hotspot SoftAP | ★★★★ | ★★★★★ | ❌ (star+) | ❌ | ✅ | ★★ | Possible |
| Classic BT | ★★ | ★★★ | ❌ | ❌ | ✅ | ★★★ | Tidak cocok |
| LoRa (Meshtastic) | ★★★★★ | ★ | ✅ | ❌ | ✅ | ★★★★★ | Butuh HW |

---

## 5. Temuan Kunci

### 5.1 Tidak ada satu transport yang sempurna
Semua app terkemuka (Briar, Berty, BitChat) menggunakan **multi-transport approach**:
- **BLE untuk discovery dan offline mesh** (selalu tersedia, tidak butuh router)
- **WiFi/LAN untuk high-bandwidth transfer** (saat router tersedia)
- **Internet untuk long-range** (saat tersedia)

### 5.2 BLE adalah kunci untuk full offline mesh
WiFi Direct secara native tidak bisa full mesh karena hardware star topology. BLE mesh (via advertising + flooding) adalah satu-satunya cara mendapat true peer-to-peer mesh murni dari hardware smartphone tanpa hardware eksternal.

### 5.3 Star topology adalah trade-off, bukan kegagalan
Briar, BitChat, bahkan Bridgefy semua mengakui star topology muncul di beberapa skenario. Yang membedakan mereka adalah **apakah relay berjalan di layer aplikasi** secara transparan. CARAKA sudah punya relay via `sendMessage()` + flooding — yang kurang adalah peer awareness antar clients.

### 5.4 Kompetitor tidak solve QR connect problem
Tidak ada kompetitor yang menggunakan QR untuk connect langsung. Briar menggunakan QR hanya untuk key exchange (identity), bukan untuk memicu koneksi WiFi. Ini berarti **CARAKA bisa menjadi pioneer** untuk fitur QR-triggered instant connect jika menggunakan LAN IP sebagai jembatan.

### 5.5 OEM compatibility = prioritas utama
Semua app besar memprioritaskan BLE daripada WiFi Direct untuk compatibility karena BLE lebih konsisten di semua OEM. WiFi Direct adalah secondary/opportunistic transport.

---

## 6. Rekomendasi Strategis untuk CARAKA

### Transport Priority Stack (dari riset)

```
Priority 1: LAN UDP unicast/broadcast
  → Paling reliable, full mesh, tidak ada star topology
  → Syarat: ada WiFi router (tidak selalu tersedia)

Priority 2: BLE Mesh (tambahan baru)  
  → Full offline mesh tanpa router
  → Range lebih pendek, bandwidth lebih rendah
  → Cocok untuk teks SOS saat total offline
  → Solusi untuk B↔C tanpa lewat A

Priority 3: WiFi Direct (star + relay)
  → Sudah ada, bandwidth tinggi
  → Star topology tapi relay di layer app sudah bekerja
  → Untuk file/gambar yang tidak bisa lewat BLE

Priority 4: LoRa via Meshtastic (masa depan)
  → Integrasi opsional untuk range sangat jauh
  → Butuh hardware eksternal
```

### Apa yang Perlu Ditambahkan ke CARAKA

**Jangka Pendek (Fix Bug saat ini):**
- Perbaiki auto-connect non-CARAKA (BUG-01)
- Fix QR connect via LAN IP (BUG-02)
- Peer exchange / PEER_LIST protocol (BUG-03)
- DNS-SD backoff untuk OEM compat (BUG-04)

**Jangka Menengah (Arsitektur):**
- Tambah **BLE Mesh** sebagai transport ketiga
- Mode: BLE untuk discovery + teks, WiFi Direct untuk file
- Ini menyelesaikan B↔C permanently, di semua kondisi

**Jangka Panjang (Differentiator):**
- **LoRa integration** via Meshtastic node (hardware opsional)
- Hybrid BLE + LoRa mesh untuk bencana large-scale
- QR code yang berisi IP LAN (pioneer fitur)

---

## Referensi & Sumber

- Briar Project: https://briarproject.org (Bramble protocol)
- BitChat GitHub: https://github.com/jackjack-jack/bitchat (BLE mesh)
- Bridgefy: https://bridgefy.me (BLE SDK)
- Berty/Wesh: https://berty.tech (multi-transport libp2p)
- Meshtastic: https://meshtastic.org (LoRa mesh)
- Android WiFi Aware Docs: https://developer.android.com/develop/connectivity/wifi/wifi-aware
- "Security Analysis of Bridgefy" - ETH Zurich 2020
- Meshrabiya (WiFi Direct multi-group): https://github.com/UstadMobile/Meshrabiya
