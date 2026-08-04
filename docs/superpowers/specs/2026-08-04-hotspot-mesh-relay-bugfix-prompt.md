# Prompt eksekusi — Bug: peer B & C tidak saling detect via Hotspot Darurat

## Cara pakai

Copy blok kode di bawah, paste ke Opus/GPT (atau agent coding lain yang
punya akses ke repo `E:\01-Proyek\CARAKA-APP` DAN akses ke minimal 3
device fisik buat testing — bug ini gak kebukti tanpa test di
perangkat asli, WiFi Direct/Hotspot gak bisa disimulasikan di
emulator). Prompt ini punya langkah VERIFIKASI HIPOTESIS dulu sebelum
nulis kode — jangan lewati itu.

## Konteks (dari audit kode, belum di-test di device fisik)

Gua (sesi sebelumnya) udah baca kode-nya dan nemu dugaan kuat akar
masalah, tapi INI HIPOTESIS, bukan kepastian — belum ada log/test dari
device fisik yang mengonfirmasi. Skenario yang dilaporkan: Device A
(host hotspot darurat) mendeteksi B dan C (device A melihat keduanya
terhubung), tapi B dan C tidak saling mendeteksi satu sama lain.

## Prompt

```
Kamu debug bug jaringan di repo Android Kotlin "CARAKA" di
E:\01-Proyek\CARAKA-APP (root Gradle di app/, modul di app/app/).

BUG: Device A (host "Hotspot Darurat") mendeteksi device B dan C yang
terhubung ke hotspotnya. Tapi B dan C tidak saling mendeteksi/terhubung
satu sama lain, padahal keduanya sama-sama terhubung ke A.

═══════════════════════════════════════════════════════════════
LANGKAH 1 (WAJIB) — Pahami arsitektur dulu, JANGAN langsung ubah kode
═══════════════════════════════════════════════════════════════

Baca file-file ini dulu untuk paham arsitektur "Hotspot Darurat":

- app/app/src/main/java/com/example/caraka/network/LocalHotspotManager.kt
  — HOST bikin WifiManager.startLocalOnlyHotspot() (Local-Only-Hotspot,
  BUKAN hotspot biasa dari Settings), broadcast kredensial (SSID+
  passphrase) lewat mesh transport yang sudah ada. CLIENT yang denger
  offer join pakai WifiNetworkSpecifier (API 29+), lalu
  connectivityManager.bindProcessToNetwork(network) supaya semua
  traffic app terikat ke network hotspot itu. Baca komentar dokumentasi
  di baris 30-46 — asumsi desainnya: begitu semua device di satu
  subnet yang sama, backbone LAN-UDP broadcast yang sudah ada di
  WifiDirectManager (port 8890) otomatis "mengoneksikan semua orang ke
  semua orang" (M-to-N).

- app/app/src/main/java/com/example/caraka/network/WifiDirectManager.kt
  — cari `LAN_DISCOVERY_PORT = 8890`, `peerIpRegistry`
  (ConcurrentHashMap<peerId, ip>), `localBroadcastAddresses()` (baris
  ~1418, coba beberapa alamat broadcast termasuk 255.255.255.255 dan
  subnet broadcast dari DHCP info), `sendLanUnicast`/`sendLanPayload`,
  dan `onMessageReceived` (baris ~1454, ini central inbound handler
  buat semua pesan mesh yang masuk).

- app/app/src/main/java/com/example/caraka/network/MeshManager.kt —
  facade yang memiliki baik `wifiDirectManager` maupun
  `localHotspotManager`, menjembatani keduanya lewat
  `wifiDirectManager.setHotspotOfferSink { ssid, pass, fromId -> ... }`
  (baris ~96-98). Ini contoh pola bridge yang SUDAH ADA antara dua
  class ini — kalau butuh bridge baru, ikuti pola yang sama, jangan
  bikin pola baru.

═══════════════════════════════════════════════════════════════
LANGKAH 2 (WAJIB) — Verifikasi hipotesis SEBELUM menulis fix
═══════════════════════════════════════════════════════════════

HIPOTESIS (belum terbukti — hasil baca kode doang, bukan hasil test
device): `WifiManager.startLocalOnlyHotspot()` di Android secara
default mengaktifkan **client/AP isolation** pada access point yang
dibuatnya — device yang connect ke Local-Only-Hotspot itu bisa saling
kirim data ke HOST, tapi TIDAK BISA saling kirim data langsung ke
CLIENT lain di hotspot yang sama. ini bukan bug di kode Caraka,
melainkan batasan level-OS/platform Android (beda dari hotspot biasa
lewat Settings > Hotspot yang biasanya tidak isolasi klien). Kalau
hipotesis ini benar, itu menjelaskan PERSIS gejala yang dilaporkan:
A (host) lihat B dan C, tapi B dan C gak saling lihat — karena paket
UDP broadcast/unicast dari B ke arah C secara fisik diblokir oleh AP
isolation di level radio/driver WiFi Device A, sebelum sempat sampai
ke aplikasi.

Cara verifikasi (WAJIB dilakukan, jangan asumsi langsung benar):
1. Riset dulu: cari dokumentasi/issue Android resmi soal client
   isolation di `WifiManager.startLocalOnlyHotspot()` — apakah ini
   perilaku yang didokumentasikan resmi, bisakah dimatikan lewat API
   publik (kemungkinan besar TIDAK ada API publik untuk mematikannya —
   ini kebijakan platform/OEM), dan apakah perilakunya konsisten di
   semua versi Android atau bervariasi per OEM.
2. Test empiris di 3 device fisik: nyalakan logging di
   `WifiDirectManager.onMessageReceived` dan di titik kirim
   (`sendLanUnicast`/`sendLanPayload`) untuk mencatat: apakah paket
   dari B benar-benar TERKIRIM (di sisi B), dan apakah paket itu
   TERDETEKSI SAMA SEKALI di sisi C (di level socket/DatagramSocket).
   Kalau paket terkirim tapi tidak pernah sampai ke C, itu konfirmasi
   isolasi di level jaringan/OS, bukan bug logic di kode aplikasi.
   Kalau paket malah gak pernah terkirim dari B, berarti akar masalah
   beda (mungkin `peerIpRegistry` B kosong / salah `bindProcessToNetwork`)
   — investigasi arah lain, JANGAN paksa pakai fix di Langkah 3.

Laporkan hasil verifikasi ini secara eksplisit sebelum lanjut ke
Langkah 3.

═══════════════════════════════════════════════════════════════
LANGKAH 3 — Kalau hipotesis TERKONFIRMASI: implementasi host-relay
═══════════════════════════════════════════════════════════════

Karena A (host) BISA berkomunikasi dengan B dan C masing-masing
(cuma B-C yang gak bisa saling kontak langsung), solusi yang idiomatik
dengan filosofi app ini (CARAKA sudah dibangun di atas ide relay/kurir
— pesan dititipkan lewat perangkat lain yang bisa menjangkau tujuan)
adalah: **device yang berperan sebagai HOST hotspot me-relay pesan
mesh yang diterima ke semua peer lain yang dia tahu**, jadi B → A → C
dan C → A → B, meniru koneksi B-C secara aplikasi walau secara fisik
diblokir OS.

Implementasi yang disarankan (sesuaikan dengan temuan Langkah 2, ini
bukan resep kaku):

1. Tambah cara `WifiDirectManager` tahu apakah device ini SEDANG jadi
   host hotspot. `WifiDirectManager` tidak memiliki `LocalHotspotManager`
   langsung (itu dipegang `MeshManager`) — ikuti pola
   `setHotspotOfferSink` yang sudah ada: tambah semacam
   `wifiDirectManager.setHotspotHostStateProvider { localHotspotManager.state.value.role == "HOST" }`
   yang di-wire dari `MeshManager` (mirip baris ~96-98 di
   MeshManager.kt), atau pendekatan setara yang konsisten dengan pola
   dependency yang sudah dipakai di file ini — jangan bikin
   WifiDirectManager depend langsung ke LocalHotspotManager (itu balik
   arah dependency yang sudah dipilih arsitekturnya).

2. Di `onMessageReceived` (WifiDirectManager.kt, ~baris 1454): kalau
   device ini SEDANG host (dari provider di poin 1) DAN pesan yang
   diterima bukan berasal dari device ini sendiri DAN belum pernah
   di-relay (manfaatkan `socketManager.isDuplicate(protocol.id, protocol.timestamp)`
   yang SUDAH ADA sebagai anti-loop/anti-duplikat — JANGAN bikin
   mekanisme dedup baru), maka relay pesan itu ke semua entri lain di
   `peerIpRegistry` (kecuali pengirim asli) lewat `sendLanUnicast`
   yang SUDAH ADA. Ini murni penambahan di jalur yang sudah ada,
   bukan subsistem baru.

3. Uji dengan 3 device fisik lagi: A host, B dan C connect. B kirim
   pesan (mis. chat) yang recipient-nya C. Konfirmasi C menerimanya
   lewat jalur relay A, dan tidak terjadi pesan dobel/looping.

═══════════════════════════════════════════════════════════════
KALAU HIPOTESIS TIDAK TERKONFIRMASI
═══════════════════════════════════════════════════════════════
Jangan paksa implementasi Langkah 3. Laporkan temuan sebenarnya dari
Langkah 2 (log, hasil test) dan analisis akar masalah yang baru,
sebelum mengusulkan fix apa pun.

═══════════════════════════════════════════════════════════════
DI LUAR SCOPE
═══════════════════════════════════════════════════════════════
- Jangan coba matikan AP isolation lewat reflection/API tersembunyi/
  root access — gak reliable lintas OEM dan berisiko merusak stabilitas.
- Jangan ubah alur WiFi-Direct P2P yang terpisah dari Hotspot Darurat
  (itu jalur lain, gak terkait bug ini).
- Jangan ubah UI/copy — ini murni fix jaringan di layer network/.

═══════════════════════════════════════════════════════════════
VERIFIKASI AKHIR
═══════════════════════════════════════════════════════════════
cd app
./gradlew.bat :app:assembleDebug
(JAVA_HOME kalau perlu: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr")

Build harus BUILD SUCCESSFUL, DAN wajib ada bukti test 3-device fisik
(deskripsikan skenario + hasil) sebelum bug ini dianggap selesai —
tidak cukup cuma "build sukses" karena ini bug jaringan yang gak bisa
divalidasi dari compile check saja.
```
