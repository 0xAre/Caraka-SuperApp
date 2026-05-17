# PROPOSAL HACKATHON WRECK-IT 7.0

## GARUDA MESH: Zero Trust Offline Communication Network untuk Ketahanan Infrastruktur Kritis pada Skenario Cyber Warfare

**Track**: IoT Resilience — Penguatan Infrastruktur Kritis via Zero Trust

**Tim**: [NAMA TIM — TBD]

---

## DAFTAR ISI

1. BAB I — Pendahuluan
2. BAB II — Tinjauan Pustaka
3. BAB III — Metodologi
4. BAB IV — Pembahasan
5. BAB V — Penutup
6. Daftar Pustaka

---

## BAB I: PENDAHULUAN

### 1.1 Latar Belakang

Dalam era peperangan modern, domain siber telah menjadi medan pertempuran kelima (*fifth domain*) setelah darat, laut, udara, dan ruang angkasa. Serangan siber terkoordinasi terhadap infrastruktur kritis suatu negara — khususnya infrastruktur komunikasi — menjadi salah satu strategi utama dalam *cyber warfare*. Laporan dari Badan Siber dan Sandi Negara (BSSN) mencatat bahwa Indonesia mengalami lebih dari 400 juta anomali trafik siber pada tahun 2023, dengan sektor infrastruktur kritis menjadi target utama (BSSN, 2024).

Skenario serangan terkoordinasi terhadap infrastruktur komunikasi bukanlah hal yang hipotetis. Pada konflik Rusia-Ukraina (2022), serangan siber terhadap infrastruktur telekomunikasi Ukraina menjadi langkah awal sebelum serangan kinetik dimulai. Satelit KA-SAT milik Viasat lumpuh akibat serangan *wiper malware*, memutus koneksi internet ribuan pengguna di Eropa (Greenberg, 2022). Dalam skenario serupa, Indonesia dengan 17.000 pulau dan ketergantungan tinggi pada infrastruktur komunikasi terpusat sangat rentan terhadap serangan jenis ini.

Ketika terjadi serangan siber terkoordinasi yang melumpuhkan internet, jaringan seluler, dan infrastruktur listrik secara bersamaan, muncul sebuah *critical gap*: **tidak ada mekanisme komunikasi darurat yang dapat beroperasi secara independen tanpa infrastruktur apapun**. First responders — BPBD, Kepolisian, dan PMI — kehilangan kemampuan koordinasi. Rumah sakit tidak dapat meminta bantuan. Masyarakat tidak mendapat informasi terverifikasi, membuka ruang bagi disinformasi dan kepanikan massal.

Saat ini, 99% alat komunikasi yang digunakan oleh instansi pemerintah maupun masyarakat bergantung pada internet atau jaringan seluler. Ketika infrastruktur tersebut menjadi target serangan, seluruh rantai komunikasi runtuh. Kondisi ini menciptakan kebutuhan mendesak akan solusi komunikasi yang bersifat **desentralisasi, terenkripsi, dan mampu beroperasi sepenuhnya secara offline**.

### 1.2 Rumusan Masalah

Berdasarkan latar belakang di atas, rumusan masalah yang diangkat adalah:

1. Bagaimana membangun sistem komunikasi darurat yang tetap beroperasi ketika seluruh infrastruktur komunikasi (internet, seluler, listrik) lumpuh akibat serangan siber?
2. Bagaimana menerapkan prinsip *Zero Trust Architecture* pada komunikasi offline untuk menjamin keamanan dan autentisitas informasi tanpa *central authority*?
3. Bagaimana mengkoordinasikan evakuasi dan respons darurat antar instansi (BPBD, Polri, PMI) tanpa ketergantungan pada infrastruktur terpusat?

### 1.3 Tujuan

1. Mengembangkan **Garuda Mesh**, sebuah aplikasi Android yang membangun *offline mesh communication network* menggunakan WiFi Direct, memungkinkan komunikasi terenkripsi antar perangkat tanpa internet.
2. Menerapkan arsitektur **Zero Trust** pada komunikasi offline melalui *end-to-end encryption*, *Public Key Infrastructure* (PKI), dan verifikasi identitas digital.
3. Menyediakan sistem **SOS broadcast** dan koordinasi darurat yang memungkinkan first responders beroperasi secara efektif dalam skenario *cyber warfare*.

### 1.4 Manfaat

**Manfaat Strategis (Keamanan Nasional)**:
- Menyediakan *resilience layer* komunikasi ketika domain siber diserang
- Mendukung kapabilitas pertahanan siber nasional sesuai amanat Perpres No. 82 Tahun 2022 tentang Perlindungan Infrastruktur Informasi Vital (IIV)
- Memperkuat kesiapsiagaan bencana dan respons darurat

**Manfaat Teknis**:
- Implementasi Zero Trust Architecture pada komunikasi *peer-to-peer* offline
- Inovasi *mesh networking* dengan prioritas pesan darurat
- Demonstrasi PKI dan verifikasi identitas tanpa koneksi internet

**Manfaat Sosial**:
- Menyelamatkan nyawa melalui koordinasi darurat yang efektif
- Mencegah penyebaran disinformasi saat krisis
- Memberdayakan masyarakat untuk saling membantu secara terdesentralisasi

### 1.5 Ruang Lingkup

Pengembangan Garuda Mesh dalam kompetisi ini difokuskan pada:
- Platform Android (API 26+)
- Komunikasi offline via WiFi Direct
- Pesan teks terenkripsi dan SOS broadcast
- Multi-hop message relay (3+ perangkat)
- Verifikasi identitas offline untuk 3 instansi (BPBD, Polri, PMI)
- Visualisasi topologi mesh network

---

## BAB II: TINJAUAN PUSTAKA

### 2.1 Cyber Warfare dan Ancaman terhadap Infrastruktur Komunikasi

*Cyber warfare* didefinisikan sebagai penggunaan serangan siber oleh aktor negara atau non-negara untuk mengganggu, merusak, atau melumpuhkan infrastruktur kritis musuh (Clarke & Knake, 2010). Domain siber (*cyberspace*) diakui sebagai domain peperangan kelima oleh NATO sejak 2016, menegaskan bahwa serangan di ruang siber dapat memiliki dampak setara dengan serangan konvensional.

Serangan terhadap infrastruktur komunikasi merupakan langkah strategis dalam *cyber warfare* karena:
- **Melumpuhkan command and control**: Tanpa komunikasi, komando dan koordinasi militer serta sipil terganggu.
- **Menciptakan fog of war**: Ketidakpastian informasi memperlambat pengambilan keputusan.
- **Membuka ruang disinformasi**: Vakum informasi resmi diisi oleh hoax dan propaganda.

### 2.2 Zero Trust Architecture

*Zero Trust* adalah paradigma keamanan siber yang berprinsip "never trust, always verify" (NIST SP 800-207). Tidak ada entitas — baik internal maupun eksternal — yang dipercaya secara default. Setiap akses dan komunikasi harus diverifikasi, diotorisasi, dan dienkripsi.

Prinsip Zero Trust yang diterapkan dalam Garuda Mesh:

| Prinsip | Implementasi |
|---|---|
| *Never Trust, Always Verify* | Setiap pesan diverifikasi dengan tanda tangan digital Ed25519 |
| *Least Privilege* | Hanya akun authority terverifikasi yang dapat mengirim broadcast resmi |
| *Assume Breach* | End-to-end encryption memastikan node relay tidak dapat membaca isi pesan |
| *Verify Explicitly* | QR code untuk verifikasi identitas secara tatap muka |

### 2.3 Mesh Networking

*Mesh network* adalah topologi jaringan dimana setiap node (perangkat) berfungsi sebagai pengirim, penerima, sekaligus relay. Arsitektur ini bersifat desentralisasi — tidak bergantung pada server atau infrastruktur pusat (Hiertz et al., 2010).

Keunggulan mesh networking untuk skenario krisis:
- **Self-healing**: Jika satu node mati, pesan otomatis dialihkan melalui jalur lain
- **Scalable**: Semakin banyak pengguna, semakin luas jangkauan jaringan
- **No single point of failure**: Tidak ada titik kegagalan tunggal
- **Low infrastructure**: Hanya membutuhkan perangkat pengguna

### 2.4 WiFi Direct (Wi-Fi Peer-to-Peer)

WiFi Direct adalah standar Wi-Fi Alliance yang memungkinkan perangkat terhubung satu sama lain tanpa memerlukan access point atau router (Wi-Fi Alliance, 2010). Pada Android, WiFi Direct diimplementasikan melalui `WifiP2pManager` API yang tersedia sejak API level 14.

Spesifikasi teknis:
- **Jangkauan**: hingga 200 meter (line of sight)
- **Bandwidth**: hingga 250 Mbps
- **Koneksi**: mendukung group dengan multiple peers
- **Keamanan**: WPA2 encryption pada transport layer

### 2.5 Kriptografi untuk Komunikasi Aman

Garuda Mesh menggunakan kombinasi algoritma kriptografi modern:

- **X25519**: Elliptic Curve Diffie-Hellman untuk key exchange (Bernstein, 2006)
- **Ed25519**: EdDSA signature scheme untuk tanda tangan digital
- **XChaCha20-Poly1305**: Authenticated encryption untuk enkripsi pesan
- **HKDF**: Key derivation function untuk menurunkan kunci per-pesan

Kombinasi ini mengikuti best practice yang digunakan oleh Signal Protocol dan WireGuard VPN.

---

## BAB III: METODOLOGI

### 3.1 Pendekatan Pengembangan

Pengembangan Garuda Mesh menggunakan pendekatan **Agile Sprint** dengan iterasi mingguan:

| Sprint | Periode | Fokus |
|---|---|---|
| Sprint 1 | 12-18 Mei 2026 | Foundation — WiFi Direct PoC, basic UI |
| Sprint 2 | 19-25 Mei 2026 | Core Mesh — Multi-hop relay, enkripsi |
| Sprint 3 | 26 Mei-1 Jun 2026 | Features — PKI, SOS, visualisasi |
| Sprint 4 | 2-14 Jun 2026 | Polish — Testing, dokumentasi, submission |

### 3.2 Arsitektur Sistem

Garuda Mesh menggunakan arsitektur berlapis (*layered architecture*) dengan pemisahan tanggung jawab yang jelas:

```
┌──────────────────────────────────────────────────┐
│                  GARUDA MESH                      │
├──────────────────────────────────────────────────┤
│                                                  │
│  Layer 4: APPLICATION (Presentation)             │
│  ├─ Jetpack Compose UI                           │
│  ├─ MVVM ViewModels                              │
│  ├─ Navigation & Screen Management               │
│  └─ Mesh Network Visualization (Canvas)          │
│                                                  │
│  Layer 3: DOMAIN (Business Logic)                │
│  ├─ MessageService — pengiriman & penerimaan     │
│  ├─ MeshRouter — routing & relay logic           │
│  ├─ CryptoManager — enkripsi & signing           │
│  └─ IdentityManager — PKI & verifikasi           │
│                                                  │
│  Layer 2: DATA (Persistence & I/O)               │
│  ├─ Room + SQLCipher — database terenkripsi      │
│  ├─ Android Keystore — penyimpanan kunci          │
│  ├─ SharedPreferences — konfigurasi              │
│  └─ Socket I/O — komunikasi data                 │
│                                                  │
│  Layer 1: NETWORK (WiFi Direct Mesh)             │
│  ├─ WifiP2pManager — discovery & connection      │
│  ├─ DNS-SD Service Discovery — peer finding      │
│  └─ TCP Sockets — data transfer                  │
│                                                  │
└──────────────────────────────────────────────────┘
```

### 3.3 Technology Stack

| Komponen | Teknologi | Justifikasi |
|---|---|---|
| Bahasa | Kotlin | Modern, concise, coroutines untuk async |
| UI Framework | Jetpack Compose | Declarative UI, rapid development |
| Arsitektur | MVVM + Clean Architecture | Separation of concerns, testable |
| DI | Hilt (Dagger) | Standard Android DI |
| Networking | Android WiFi P2P API | Native WiFi Direct support |
| Enkripsi | Lazysodium-Android | Binding libsodium, battle-tested |
| Database | Room + SQLCipher | Encrypted local storage |
| Async | Kotlin Coroutines + Flow | Reactive data streams |
| Min SDK | API 26 (Android 8.0) | Kompatibel dengan perangkat 2020+ |

### 3.4 Protokol Komunikasi

#### Format Pesan (JSON over TCP)

```json
{
  "version": 1,
  "id": "uuid-v4",
  "type": "TEXT | SOS | SYSTEM",
  "sender": {
    "id": "fingerprint-pubkey",
    "name": "BPBD Jakarta",
    "role": "AUTHORITY",
    "signingKey": "base64-ed25519-pubkey"
  },
  "recipient": "fingerprint | BROADCAST",
  "payload": "base64-encrypted-content",
  "timestamp": 1718000000,
  "ttl": 5,
  "priority": "EMERGENCY | HIGH | NORMAL",
  "signature": "base64-ed25519-signature",
  "location": { "lat": -6.2, "lng": 106.8 },
  "sosCategory": "MEDICAL | FIRE | SECURITY | DISASTER"
}
```

#### Algoritma Relay (Simplified Flooding)

```
1. Node A membuat pesan dengan ID unik, TTL=5
2. Node A mengirim ke semua direct peers
3. Setiap node penerima:
   a. Cek message ID → jika sudah pernah diterima, DROP
   b. Dekripsi jika recipient cocok dengan self
   c. Simpan ID di relay cache (deduplication)
   d. Kurangi TTL
   e. Jika TTL > 0, forward ke semua peers KECUALI pengirim
4. Pesan EMERGENCY: skip antrian, relay segera
```

### 3.5 Desain Keamanan (Zero Trust)

#### Alur Kriptografi

**Pengiriman Pesan Langsung (Direct Message)**:
```
shared_secret = X25519(private_key_sender, public_key_recipient)
message_key   = HKDF(shared_secret, message_id)
ciphertext    = XChaCha20-Poly1305(message_key, plaintext)
signature     = Ed25519_Sign(signing_key_sender, ciphertext)
```

**Pengiriman Broadcast / SOS**:
```
plaintext (dapat dibaca semua node) + Ed25519 signature (menjamin keaslian)
```

#### Model Ancaman dan Mitigasi

| Ancaman | Mitigasi |
|---|---|
| Penyadapan pesan | XChaCha20-Poly1305 end-to-end encryption |
| Pemalsuan pesan | Ed25519 digital signature |
| Pemalsuan identitas | PKI + verifikasi QR code tatap muka |
| Replay attack | Message ID deduplication + validasi timestamp |
| Man-in-the-middle | Verifikasi kunci via QR code (out-of-band) |

### 3.6 Flowchart Sistem

```
User Membuka Aplikasi
├─ First Launch?
│   ├─ YA → Onboarding: set nama, generate keypair, pilih role
│   └─ TIDAK → Load identity dari Keystore
│
├─ Cek Koneksi Internet
│   ├─ ONLINE → Mode Hybrid (internet + mesh standby)
│   └─ OFFLINE → Aktivasi Mode Mesh
│       ├─ Scan WiFi Direct peers
│       ├─ Establish encrypted connections
│       ├─ Join/create mesh network
│       └─ Aktifkan fitur darurat
│
├─ Fitur Utama
│   ├─ SOS Broadcast → Pilih kategori → Attach lokasi → Broadcast
│   ├─ Messaging → Pilih peer → Encrypt → Kirim (direct/relay)
│   ├─ Network Map → Visualisasi topologi mesh real-time
│   └─ Verified Info → Terima broadcast dari authority + flag hoax
```

---

## BAB IV: PEMBAHASAN

### 4.1 Deskripsi Solusi

Garuda Mesh adalah **Zero Trust offline communication network** yang memungkinkan komunikasi darurat terenkripsi antar perangkat Android tanpa membutuhkan internet, jaringan seluler, atau infrastruktur apapun. Setiap perangkat yang menjalankan Garuda Mesh menjadi sebuah *node* dalam jaringan mesh, mampu mengirim, menerima, dan meneruskan (*relay*) pesan ke perangkat lain.

Solusi ini secara langsung menjawab kebutuhan penguatan infrastruktur kritis — khususnya infrastruktur komunikasi — melalui penerapan prinsip Zero Trust pada level paling fundamental: **komunikasi tetap berjalan bahkan ketika seluruh infrastruktur sudah lumpuh**.

### 4.2 Fitur Utama

#### A. Adaptive Mesh Network (P0)
- Setiap device menjadi node dalam network
- Pesan di-hop dari device ke device hingga sampai tujuan
- Semakin banyak pengguna, semakin kuat dan luas jangkauan
- Auto-reconnect dan rerouting jika node hilang

**Ilustrasi**:
```
BPBD (Node A) →100m→ Warga (Node B) →80m→ Warga (Node C) →100m→ PMI (Node D)
Total jarak: 280m komunikasi, tanpa infrastruktur apapun
```

#### B. Emergency SOS Broadcast (P0)
- One-tap SOS button dengan 4 kategori: Medical, Fire, Security, Disaster
- Auto-attach koordinat GPS (last known location)
- Prioritas tertinggi dalam relay — SOS melewati antrian pesan normal
- Visual dan audio alert pada semua device penerima
- Signed tetapi tidak encrypted — agar semua node bisa membaca

#### C. End-to-End Encryption (P0)
- Key pair generation saat first launch (X25519 + Ed25519)
- Setiap pesan langsung dienkripsi dengan XChaCha20-Poly1305
- Node relay tidak dapat membaca isi pesan yang diteruskan
- Digital signature memastikan keaslian pengirim

#### D. Verified Authority Identity (P1)
Tiga identitas authority di-hardcode untuk skenario demo:
- 🛡️ **BPBD** — Koordinasi evakuasi dan shelter
- 🚔 **Polri** — Keamanan dan area restriction
- 🏥 **PMI** — Medis dan distribusi logistik

Pesan dari authority menampilkan badge ✓ Verified yang tidak dapat dipalsukan (dijamin oleh Ed25519 signature).

#### E. Mesh Network Visualization (P1)
- Visualisasi real-time topologi jaringan mesh
- Node diberi warna sesuai status: hijau (aktif), amber (authority), merah (SOS)
- Garis penghubung menunjukkan koneksi langsung antar device
- Statistik: jumlah node, estimasi jangkauan, kekuatan mesh

#### F. Community Message Flagging (P1)
- User dapat menandai pesan sebagai "suspicious"
- 3+ flags dari user berbeda = label peringatan ⚠️
- Pesan dari verified authority tidak dapat di-flag
- Mekanisme dasar melawan disinformasi tanpa central authority

### 4.3 Inovasi

1. **Mesh networking untuk cyber warfare resilience** — Solusi pertama di Indonesia yang secara spesifik dirancang untuk menjaga komunikasi saat infrastruktur diserang.
2. **Zero Trust pada komunikasi offline** — Implementasi prinsip NIST SP 800-207 pada jaringan peer-to-peer tanpa server.
3. **PKI offline** — Verifikasi identitas authority tanpa koneksi ke certificate authority manapun.
4. **Priority-based relay** — Pesan darurat (SOS) mendapat prioritas tertinggi dalam routing mesh.
5. **Hybrid mode** — Transisi mulus antara mode online dan offline, data tersinkronisasi saat koneksi kembali.

### 4.4 Studi Kasus: Skenario Perang dan Evakuasi Korban

**Skenario**: Serangan siber terkoordinasi terhadap Jakarta

```
Timeline Serangan:
00:00 — DDoS masif melumpuhkan internet nasional
01:00 — Sabotase grid listrik, sebagian Jakarta blackout
02:00 — Jamming tower seluler, jaringan mobile down
03:00 — Disinformasi menyebar via sisa koneksi, panic massal

Respons dengan Garuda Mesh:
03:15 — First responders aktivasi Garuda Mesh
03:20 — BPBD broadcast: "Shelter evakuasi di Gedung X" [✓ Verified]
03:25 — Warga kirim SOS Medical: "Korban luka di Jl. Y"
03:30 — PMI terima SOS via relay, kirim tim medis
03:35 — Polri broadcast: "Hindari area Z, aktif konflik" [✓ Verified]
03:40 — Pesan hoax muncul, di-flag oleh community
04:00 — Koordinasi evakuasi berjalan efektif tanpa internet
```

### 4.5 Dampak

| Dimensi | Dampak |
|---|---|
| **Keamanan Nasional** | Resilience layer untuk pertahanan siber; mendukung operasional TNI/Polri dalam skenario krisis |
| **Kebencanaan** | Koordinasi evakuasi efektif saat infrastruktur lumpuh; applicable untuk gempa, tsunami, banjir |
| **Sosial** | Menyelamatkan nyawa melalui SOS broadcast; mencegah panic dengan informasi terverifikasi |
| **Teknologi** | Mendorong inovasi mesh networking dan kriptografi di Indonesia; sovereign technology |

### 4.6 Analisis

**Kelebihan**:
- Tidak bergantung pada infrastruktur apapun
- Desentralisasi penuh — tidak ada single point of failure
- Privasi terjamin melalui E2E encryption
- Scalable — makin banyak pengguna, makin kuat jaringan
- Open source — dapat diaudit dan dikembangkan komunitas

**Keterbatasan**:
- Jangkauan per-hop terbatas (~100-200m) — dimitigasi oleh multi-hop relay
- Konsumsi baterai — dimitigasi oleh adaptive beacon dan sleep mode
- Kapasitas bandwidth terbatas — fokus pada pesan teks dan SOS
- WiFi Direct behavior bervariasi antar merek perangkat

---

## BAB V: PENUTUP

### 5.1 Kesimpulan

Garuda Mesh menjawab *critical gap* dalam ketahanan infrastruktur komunikasi Indonesia terhadap skenario *cyber warfare*. Dengan membangun **Zero Trust offline mesh communication network**, Garuda Mesh memungkinkan:

1. **Komunikasi darurat tanpa infrastruktur** — pesan terenkripsi tersalurkan melalui jaringan mesh device-to-device, beroperasi penuh tanpa internet, seluler, atau listrik grid.
2. **Keamanan tanpa kompromi** — prinsip Zero Trust diterapkan pada setiap layer: enkripsi end-to-end, tanda tangan digital, dan verifikasi identitas offline.
3. **Koordinasi evakuasi efektif** — SOS broadcast dan verified authority messages memungkinkan BPBD, Polri, dan PMI berkoordinasi secara real-time meskipun seluruh infrastruktur komunikasi lumpuh.

Garuda Mesh bukan sekadar aplikasi chat offline — ini adalah **survival layer** bagi komunikasi nasional ketika *the fifth domain* diserang.

### 5.2 Saran Pengembangan

1. **Integrasi LoRa** — Modul LoRa untuk jangkauan hingga 10km per-hop, cocok untuk daerah terpencil dan kepulauan.
2. **Satellite gateway** — Koneksi ke satelit LEO (Starlink/AST SpaceMobile) untuk komunikasi ultra-long range.
3. **Adopsi pemerintah** — Integrasi dengan sistem early warning BMKG dan protokol bencana BNPB.
4. **Cross-platform** — Pengembangan ke iOS menggunakan Multipeer Connectivity framework.
5. **Deepfake detection** — Model ML on-device untuk deteksi audio dan gambar palsu.
6. **Blockchain verification** — Immutable log untuk verifikasi pesan authority saat koneksi kembali.

---

## DAFTAR PUSTAKA

1. BSSN. (2024). *Laporan Tahunan Monitoring Keamanan Siber 2023*. Badan Siber dan Sandi Negara.
2. Bernstein, D. J. (2006). Curve25519: New Diffie-Hellman speed records. *Public Key Cryptography – PKC 2006*.
3. Clarke, R. A., & Knake, R. K. (2010). *Cyber War: The Next Threat to National Security*. Ecco Press.
4. Greenberg, A. (2022). The Untold Story of the Boldest Supply-Chain Hack Ever. *Wired*.
5. Hiertz, G. R., et al. (2010). IEEE 802.11s: The WLAN Mesh Standard. *IEEE Wireless Communications*.
6. NIST. (2020). *SP 800-207: Zero Trust Architecture*. National Institute of Standards and Technology.
7. Peraturan Presiden No. 82 Tahun 2022 tentang Perlindungan Infrastruktur Informasi Vital.
8. Wi-Fi Alliance. (2010). *Wi-Fi Direct Specification*. Wi-Fi Alliance.
9. Rose, S., et al. (2020). Zero Trust Architecture. *NIST Special Publication 800-207*.

---

*Dokumen ini adalah draft proposal untuk kompetisi Hackathon WRECK-IT 7.0 — Track IoT Resilience.*
*Garuda Mesh © 2026. All rights reserved.*
