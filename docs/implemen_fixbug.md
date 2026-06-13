# CARAKA Mesh — Bug Report & Fix Suggestions
**Tanggal**: 2026-06-07  
**Status**: Menunggu eksekusi  
**Target**: Perbaikan core networking layer

---

## Konteks Sistem

CARAKA adalah aplikasi mesh networking berbasis WiFi Direct dan LAN UDP untuk komunikasi darurat offline. Arsitektur saat ini menggunakan:
- **WiFi Direct (WifiP2pManager)** sebagai transport utama
- **LAN UDP broadcast** (port 8890) sebagai discovery layer sekunder
- **TCP Socket** (port 8888) via `MeshSocketManager` untuk pengiriman pesan
- **DNS-SD / Bonjour** (`_caraka._tcp`) untuk filter discovery CARAKA-only

File inti yang relevan:
- `network/WifiDirectManager.kt` — manajer utama (±1155 baris)
- `network/MeshSocketManager.kt` — TCP socket handler
- `network/MeshProtocol.kt` — wire protocol / message format
- `network/WifiDirectReceiver.kt` — BroadcastReceiver untuk WiFi Direct events
- `viewmodel/MainViewModel.kt` — ViewModel penghubung UI-network
- `ui/screens/NetworkScreen.kt` — tampilan jaringan mesh
- `ui/screens/QrIdentityScreen.kt` — QR scan & identity verification

---

## Bug Report

---

### BUG-01: Auto-connect ke device non-CARAKA (TV, speaker, dsb.)

**Severity**: Critical  
**File**: `WifiDirectManager.kt` → `onPeersAvailable()`

**Deskripsi**:  
Ketika WiFi Direct menemukan device di sekitar, aplikasi secara otomatis mencoba terkoneksi ke semua device yang terdeteksi — termasuk Smart TV, speaker Bluetooth, laptop, dan perangkat IoT lainnya. Ini menyebabkan koneksi yang tidak diinginkan dan gangguan pada WiFi Direct state machine.

**Penyebab yang diidentifikasi**:  
Logika `onPeersAvailable()` memiliki 4 tier kandidat koneksi. Tier 4 (fallback terakhir) mencoba konek ke `anyCandidate` — yaitu device mana saja yang tidak sedang `CONNECTED`, tanpa filter apapun. Karena DNS-SD (`discoverServices()`) sering gagal di beberapa OEM, filter CARAKA-only tidak efektif dan Tier 4 terpicu.

**Dampak yang diamati**:  
- Device mencoba invite ke Smart TV di sekitar
- WiFi Direct state machine stuck di `CONNECTING` atau `CONNECT_FAILED`
- Discovery cycle terganggu dan peer CARAKA asli terlewat

**Saran arah perbaikan**:  
Pertimbangkan untuk menghilangkan mekanisme auto-connect sepenuhnya dari `onPeersAvailable()`. Koneksi sebaiknya diinisiasi secara eksplisit oleh pengguna (manual-triggered) atau oleh logika spesifik yang sudah terverifikasi identitas CARAKA-nya (misalnya via QR atau LAN beacon). Pendekatan discovery dan pendekatan connect perlu dipisahkan dengan jelas.

---

### BUG-02: QR Scan tidak memicu koneksi langsung

**Severity**: High  
**File**: `QrIdentityScreen.kt`, `WifiDirectManager.kt` → `triggerPriorityConnect()`

**Deskripsi**:  
Fitur scan QR berhasil membaca dan menyimpan identitas peer (nama, role, public key), namun tidak menghasilkan koneksi otomatis antara kedua device. User harus menunggu discovery cycle berikutnya agar device tersebut terdeteksi, dan tidak ada jaminan koneksi terjadi.

**Penyebab yang diidentifikasi**:  
QR payload saat ini berisi: `peerId`, `name`, `role`, `encPub`, `signPub`.  
WiFi Direct membutuhkan **MAC address** untuk inisiasi koneksi — bukan `peerId`. MAC address bersifat dinamis dan tidak disertakan di QR. Fungsi `triggerPriorityConnect()` hanya menyimpan `priorityPeerId` dan memulai ulang discovery, namun tidak ada mekanisme untuk memetakan `peerId` ke MAC address secara langsung.

Selain itu, LAN UDP beacon yang sudah berjalan setiap 3 detik sebenarnya sudah berisi `peerId` dan dikirim dari IP yang diketahui — namun mapping `peerId → IP` ini tidak disimpan atau dimanfaatkan untuk keperluan koneksi langsung.

**Dampak yang diamati**:  
- Setelah scan QR dan konfirmasi, tidak ada koneksi yang terbentuk
- User harus discovery manual atau menunggu lama
- Fitur QR terasa tidak fungsional untuk connect

**Saran arah perbaikan**:  
Ada dua pendekatan yang bisa dieksplorasi:

1. **Tambah informasi ke QR payload** — sertakan IP LAN atau identifier tambahan agar koneksi bisa langsung dimulai setelah scan, tanpa bergantung pada discovery WiFi Direct.

2. **Manfaatkan LAN beacon yang sudah ada** — saat LAN beacon diterima, simpan mapping `peerId → sourceIP`. Ketika QR scan selesai, cek apakah `peerId` target sudah diketahui IP-nya. Jika ya, kirim request langsung via LAN tanpa menunggu WiFi Direct discovery.

Kedua pendekatan bisa dikombinasikan.

---

### BUG-03: Full mesh tidak tercapai — B dan C tidak saling mengenal

**Severity**: High  
**File**: `WifiDirectManager.kt` → `handleHandshake()`, `sendMessage()`

**Deskripsi**:  
Ketika 3+ device terhubung, topologi yang terbentuk adalah star (bintang): A tahu B dan C, tapi B hanya tahu A dan C hanya tahu A. B dan C tidak bisa berkomunikasi langsung satu sama lain — mereka tidak mengetahui keberadaan satu sama lain.

**Penyebab yang diidentifikasi**:  
Ini adalah **hardware limitation WiFi Direct API** yang fundamental:
- WiFi Direct membentuk group dengan 1 Group Owner (GO) dan beberapa clients
- Clients tidak bisa saling terhubung langsung di layer WiFi Direct
- Relay via GO sudah bekerja (B → A → C), namun B dan C tidak memiliki informasi tentang keberadaan satu sama lain di level aplikasi

Saat ini tidak ada mekanisme *peer exchange* — GO tidak memberi tahu clients lain tentang siapa saja yang sudah terhubung.

**Dampak yang diamati**:  
- Chat dari B ke C hanya bisa dilakukan jika B mengetahui `peerId` C sebelumnya (misalnya dari QR)
- Di NetworkScreen, B tidak menampilkan C sebagai node yang dikenal
- Full mesh hanya tampak dari sudut pandang GO (A), bukan dari sudut pandang clients

**Saran arah perbaikan**:  
Perlu ada mekanisme pertukaran informasi peer (*peer exchange*). Beberapa kemungkinan:

1. **GO broadcast PEER_LIST** — setiap kali device baru handshake, GO mengirim daftar semua peer yang diketahuinya ke semua yang terhubung. Clients yang menerima list ini kemudian tahu tentang peer lain.

2. **LAN sebagai mesh backbone** — jika semua device berada dalam satu WiFi yang sama, komunikasi B↔C bisa dilakukan langsung via LAN UDP (unicast ke IP yang diketahui), tanpa melewati GO. Ini memerlukan mekanisme untuk menyimpan dan berbagi IP LAN setiap peer.

3. **Kombinasi keduanya** — peer exchange via PEER_LIST untuk discovery, LAN direct untuk delivery jika tersedia, WiFi Direct relay sebagai fallback offline.

---

### BUG-04: Kompatibilitas OEM Android — Discovery sering gagal diam-diam

**Severity**: Medium  
**File**: `WifiDirectManager.kt` → `registerCarakaService()`, `startServiceDiscovery()`, `updateDeviceName()`

**Deskripsi**:  
Pada beberapa device dengan custom ROM, proses discovery CARAKA mengalami kegagalan yang tidak terdeteksi:

| Device | OEM/ROM | Masalah |
|--------|---------|---------|
| Redmi Note 9 Pro | MIUI/HyperOS | `setDeviceName()` via reflection gagal — nama device tidak berubah, peers tidak teridentifikasi sebagai CARAKA |
| Infinix X6833B | XOS | `discoverServices()` sering return `BUSY` secara berulang tanpa recovery |
| Tecno Pova 5 | HiOS | Perilaku WiFi Direct group berbeda dari standar AOSP |

**Penyebab yang diidentifikasi**:  
- `setDeviceName()` menggunakan Java reflection yang diblokir di beberapa OEM
- Tidak ada backoff/retry logic untuk kasus `BUSY` pada `discoverServices()`
- OEM memodifikasi implementasi WiFi Direct di level firmware

**Dampak yang diamati**:  
- Device tidak terlihat oleh peer lain karena nama tidak berformat `CRK:`
- Discovery macet di beberapa device karena BUSY loop tanpa recovery
- Koneksi gagal atau tidak stabil di beberapa OEM

**Saran arah perbaikan**:  
- Identifikasi peer sebaiknya tidak bergantung pada nama device (`CRK:` prefix) karena `setDeviceName()` tidak reliable. Pendekatan berbasis peerId via LAN beacon lebih robust.
- Tambah retry logic dengan backoff untuk `discoverServices()` BUSY case.
- LAN UDP discovery sebagai primary path memberikan pengalaman lebih konsisten di semua OEM, karena tidak bergantung pada WiFi Direct API yang implementasinya bervariasi.

---

## Keputusan Desain yang Perlu Diperhitungkan

### Tentang Connect Flow
**Konteks**: Saat ini koneksi dilakukan otomatis tanpa konfirmasi pengguna.  
**Keputusan**: Koneksi langsung terjadi tanpa dialog accept/reject dari target device. QR scan berfungsi sebagai mekanisme "fast connect" yang langsung memulai koneksi.

### Tentang QR Payload
**Konteks**: QR saat ini hanya berisi identitas kriptografi.  
**Keputusan**: QR payload boleh diperluas untuk menyertakan informasi yang membantu koneksi cepat (misalnya IP LAN).

### Tentang Offline Scenario
**Konteks**: Skenario penggunaan bervariasi — kadang ada WiFi router, kadang tidak (pure offline).  
**Implikasi**: Solusi harus mendukung kedua skenario. LAN sebagai primary (saat ada WiFi), WiFi Direct relay sebagai fallback (saat offline). Saat offline dan tidak ada LAN, topologi star via GO masih akan terjadi — B↔C tetap lewat relay A. Ini adalah keterbatasan hardware yang acceptable.

---

## Prioritas Perbaikan yang Disarankan

1. **Paling mendesak**: Stop auto-connect ke device non-CARAKA (BUG-01) — ini merusak discovery cycle
2. **Paling impactful untuk UX**: Fix QR → connect langsung (BUG-02)  
3. **Untuk full mesh**: Implementasikan peer exchange / peer list mechanism (BUG-03)
4. **Untuk reliability**: Perbaikan OEM compatibility (BUG-04)

---

## Catatan Teknis Tambahan

- `MeshSocketManager.sendPayload()` sudah mengirim ke semua `clientStreams` — relay sudah bekerja di level TCP. Yang kurang adalah *awareness* antar peers di level aplikasi.
- `sendMessage()` di `WifiDirectManager` sudah LAN-first (`sendLanPayload` sebelum `socketManager.sendPayload`) — fondasi untuk LAN-as-primary sudah ada.
- LAN beacon (`sendLanHandshake()`) sudah berjalan setiap 3 detik dan berisi peerId, nama, role, public keys — hanya belum menyimpan source IP secara persisten untuk digunakan kemudian.
- `carakaLanPeerHosts` sudah menyimpan IP host LAN, tapi tidak dimap ke `peerId` secara eksplisit.
