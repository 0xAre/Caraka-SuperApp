# CARAKA — Implementation Plan Phase 0
## Stabilisasi & Bug Fix (dari CARAKA_BIGPLAN_V2)

**Target**: App stabil dan siap demo 4 device fisik
**Estimasi Total**: 6–10 jam coding
**Basis Kode**: Post git pull `b85fb2d`

---

## ℹ️ Tentang Device

> **Tidak perlu device terhubung untuk fase coding ini.**
> Device hanya dibutuhkan saat tahap **build & install** untuk testing.
> Siapkan device saat kita sudah masuk ke bagian Test Plan.

---

## 🔍 Kondisi Kode Saat Ini (Hasil Analisis)

### Sudah Ada — Tidak Perlu Diimplementasikan Ulang

| Komponen | File | Status |
|----------|------|--------|
| `peerIpRegistry` (ConcurrentHashMap peerId→IP) | `WifiDirectManager.kt:153` | ✅ Ada |
| SQLCipher via `SupportFactory` | `CarakaDatabase.kt` | ✅ Ada |
| `DatabasePassphraseManager` (hardware-backed) | `crypto/` | ✅ Ada |
| `PeerShare` data class | `MeshProtocol.kt:10-17` | ✅ Ada |
| `PEER_LIST` message type (comment) | `MeshProtocol.kt:33` | ✅ Terdefinisi |
| `peers: List<PeerShare>?` field | `MeshProtocol.kt:59` | ✅ Ada |
| `ConnectionStatus` enum | `PeerEntity.kt:6-11` | ✅ Ada |
| `autoAccept` field | `MeshProtocol.kt:56` | ✅ Ada |
| `carakaLanPeerHosts` set | `WifiDirectManager.kt:146` | ✅ Ada |
| `ConnectionRequestDialog.kt` | `ui/dialogs/` | ✅ Dibuat |
| `PeerListView.kt` | `ui/components/` | ✅ Dibuat |
| `autoConnectLoopJob` placeholder | `WifiDirectManager.kt:109` | ✅ Ada |

### Masih Harus Diimplementasikan

| # | Item | File Target | Severity |
|---|------|-------------|----------|
| WG1 | DB Migration (version 1→2, schema baru) | `CarakaDatabase.kt` | **KRITIS** |
| WG2 | Handler + broadcast `PEER_LIST` (full mesh B↔C) | `WifiDirectManager.kt` | HIGH |
| WG3 | `triggerPriorityConnect()` pakai `peerIpRegistry` | `WifiDirectManager.kt` | HIGH |
| WG4 | Integrasi `ConnectionRequestDialog` ke UI | `MainActivity.kt` | HIGH |
| WG5 | Theme migration `PeerListView` & `Dialog` | `PeerListView.kt`, `ConnectionRequestDialog.kt` | MEDIUM |
| WG6 | DNS-SD exponential backoff (OEM Infinix/Tecno) | `WifiDirectManager.kt` | MEDIUM |
| WG7 | DAO helper methods jika belum ada | `PeerDao.kt`, `MeshRepository.kt` | MEDIUM |

---

## 🛠️ Work Group 1: Database Migration
**File**: [`CarakaDatabase.kt`](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/data/local/CarakaDatabase.kt)
**Estimasi**: 30 menit | **Severity**: KRITIS

### Masalah
`CarakaDatabase.kt` baris 39: `version = 1`
`PeerEntity.kt` punya 5 kolom baru: `connectionId`, `status`, `direction`, `lastAttempt`, `rejectionCount`

Saat ini ada `fallbackToDestructiveMigration()` — artinya update app akan **hapus semua data peers**. Perlu diganti dengan migration proper.

### Perubahan di `CarakaDatabase.kt`

```kotlin
// 1. Naikkan versi:
@Database(
    // ...
    version = 2,   // ← dari 1 ke 2
    // ...
)

// 2. Tambah migration object (di dalam companion object):
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE peers ADD COLUMN connectionId TEXT NOT NULL DEFAULT ''"
        )
        database.execSQL(
            "ALTER TABLE peers ADD COLUMN status TEXT NOT NULL DEFAULT 'DISCOVERED'"
        )
        database.execSQL(
            "ALTER TABLE peers ADD COLUMN direction TEXT NOT NULL DEFAULT ''"
        )
        database.execSQL(
            "ALTER TABLE peers ADD COLUMN lastAttempt INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE peers ADD COLUMN rejectionCount INTEGER NOT NULL DEFAULT 0"
        )
    }
}

// 3. Ganti di getDatabase():
.fallbackToDestructiveMigration()   // ← HAPUS INI
.addMigrations(MIGRATION_1_2)       // ← GANTI DENGAN INI
```

---

## 🛠️ Work Group 2: PEER_LIST Gossip — Full Mesh B↔C
**File**: [`WifiDirectManager.kt`](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/network/WifiDirectManager.kt)
**Estimasi**: 2 jam | **Severity**: HIGH

### Masalah
`peerIpRegistry` sudah menyimpan `peerId→IP` dari setiap LAN beacon, tapi informasi ini **tidak pernah dibagikan** ke peer lain. Device B hanya tahu peer yang kirim LAN beacon langsung ke B. C yang masuk via WiFi Direct tidak akan diketahui B.

`MeshProtocol.peers: List<PeerShare>?` sudah ada tapi tidak pernah diisi/diproses.

### 2a. Tambah `broadcastPeerList()` di `WifiDirectManager.kt`

```kotlin
private fun broadcastPeerList() {
    scope.launch {
        val myId = identityManager.getPeerId()
        if (myId.isBlank()) return@launch

        val peerShares = peerIpRegistry.entries.mapNotNull { (peerId, ip) ->
            if (peerId == myId) return@mapNotNull null
            val peer = repository.getPeerById(peerId) ?: return@mapNotNull null
            PeerShare(
                peerId = peerId,
                name = peer.displayName,
                role = peer.role,
                encPub = peer.publicKey,
                signPub = peer.signingKey,
                ip = ip
            )
        }
        if (peerShares.isEmpty()) return@launch

        val protocol = MeshProtocol(
            type = "PEER_LIST",
            id = java.util.UUID.randomUUID().toString(),
            senderId = myId,
            senderName = identityManager.getDisplayName(),
            senderRole = identityManager.getRole(),
            recipientId = "BROADCAST",
            content = "peer-gossip",
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            peers = peerShares
        )
        sendMessage(protocol.toJson())
        Log.d(TAG, "PEER_LIST broadcast: ${peerShares.size} peers")
    }
}
```

### 2b. Tambah `handlePeerList()` di `WifiDirectManager.kt`

```kotlin
private suspend fun handlePeerList(protocol: MeshProtocol) {
    val myId = identityManager.getPeerId()
    val shares = protocol.peers ?: return
    var newPeers = 0

    shares.forEach { share ->
        if (share.peerId == myId) return@forEach
        share.ip?.let { ip ->
            peerIpRegistry[share.peerId] = ip
            carakaLanPeerHosts.add(ip)
            peerLastLanSeen[share.peerId] = System.currentTimeMillis()
        }
        if (repository.getPeerById(share.peerId) == null) {
            repository.savePeer(
                PeerEntity(
                    id = share.peerId,
                    deviceName = share.name,
                    displayName = share.name,
                    role = share.role,
                    publicKey = share.encPub,
                    signingKey = share.signPub,
                    isVerified = false,
                    isAuthority = false,
                    macAddress = null,
                    lastSeen = System.currentTimeMillis(),
                    status = com.example.caraka.data.local.entity.ConnectionStatus.DISCOVERED
                )
            )
            newPeers++
        }
    }
    Log.d(TAG, "PEER_LIST: $newPeers new peers discovered from ${protocol.senderName}")
}
```

### 2c. Panggil `broadcastPeerList()` setelah setiap LAN peer baru dideteksi

Cari bagian di `WifiDirectManager.kt` tempat `peerIpRegistry[senderId] = hostAddress` diset (saat proses LAN HANDSHAKE). Setelah baris itu tambahkan:

```kotlin
peerIpRegistry[protocol.senderId] = hostAddress
peerLastLanSeen[protocol.senderId] = System.currentTimeMillis()
broadcastPeerList()   // ← Tambah ini
```

### 2d. Tambah case `"PEER_LIST"` di message handler

Cari switch/when di `WifiDirectManager.kt` yang menangani `protocol.type`. Tambah:

```kotlin
"PEER_LIST" -> scope.launch { handlePeerList(protocol) }
```

---

## 🛠️ Work Group 3: QR Auto-Connect via `peerIpRegistry`
**File**: [`WifiDirectManager.kt`](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/network/WifiDirectManager.kt), [`QrIdentityScreen.kt`](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/ui/screens/QrIdentityScreen.kt)
**Estimasi**: 1 jam | **Severity**: HIGH

### Masalah
`triggerPriorityConnect()` (atau `setPriorityPeerId()`) hanya set priority flag dan restart WiFi Direct discovery. Tidak cek apakah peer sudah ada di `peerIpRegistry`. Jika peer sudah tahu via LAN, kita bisa langsung kirim `CONNECTION_REQUEST` via LAN unicast.

### 3a. Tambah `sendLanUnicast()` helper

```kotlin
private suspend fun sendLanUnicast(json: String, targetIp: String) {
    val bytes = json.toByteArray(Charsets.UTF_8)
    try {
        java.net.DatagramSocket().use { socket ->
            val packet = java.net.DatagramPacket(
                bytes, bytes.size,
                InetAddress.getByName(targetIp),
                LAN_DISCOVERY_PORT
            )
            socket.send(packet)
            Log.d(TAG, "LAN unicast → $targetIp: ${bytes.size}B")
        }
    } catch (e: Exception) {
        Log.w(TAG, "sendLanUnicast failed to $targetIp: ${e.message}")
    }
}
```

### 3b. Update `triggerPriorityConnect()` / buat fungsi baru

Cari fungsi yang dipanggil setelah QR scan (kemungkinan `setPriorityPeerId` atau `triggerPriorityConnect` di ViewModel). Ubah/tambahkan:

```kotlin
fun triggerPriorityConnect(peerId: String) {
    scope.launch {
        val lanIp = peerIpRegistry[peerId]
        if (lanIp != null) {
            // Peer sudah dikenal via LAN — kirim langsung
            Log.d(TAG, "QR connect: $peerId known at $lanIp — sending direct REQUEST")
            val myId = identityManager.getPeerId()
            val request = MeshProtocol(
                type = "CONNECTION_REQUEST",
                id = java.util.UUID.randomUUID().toString(),
                senderId = myId,
                senderName = identityManager.getDisplayName(),
                senderRole = identityManager.getRole(),
                recipientId = peerId,
                content = "REQUEST",
                timestamp = System.currentTimeMillis(),
                ttl = 1,
                publicKey = identityManager.getEncryptionPublicKeyBase64(),
                signingKey = identityManager.getSigningPublicKeyBase64(),
                autoAccept = true   // QR = in-person consent, auto-accept di sisi penerima
            )
            sendLanUnicast(request.toJson(), lanIp)
        } else {
            // Fallback ke WiFi Direct priority discovery
            Log.d(TAG, "QR connect: $peerId not in LAN registry — waiting for beacon")
            setPriorityPeerId(peerId)
            discoverPeers()
        }
    }
}
```

### 3c. Handler `CONNECTION_REQUEST` — `autoAccept` path

Di message handler, pastikan ada logika:

```kotlin
"CONNECTION_REQUEST" -> {
    val myId = identityManager.getPeerId()
    if (protocol.recipientId != myId) return@launch  // bukan untuk saya
    if (protocol.autoAccept) {
        // QR path: langsung accept, tidak perlu tampilkan dialog
        repository.updatePeerStatus(protocol.senderId, ConnectionStatus.ACTIVE_MESH)
        Log.d(TAG, "AUTO-ACCEPT connection from ${protocol.senderName} (QR path)")
    } else {
        // Manual path: emit ke UI
        _incomingConnectionRequest.emit(
            IncomingRequest(protocol.senderId, protocol.senderName, protocol.senderRole)
        )
    }
}
```

---

## 🛠️ Work Group 4: Integrasi ConnectionRequestDialog ke UI
**File**: [`MainActivity.kt`](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/MainActivity.kt), [`MainViewModel.kt`](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/viewmodel/MainViewModel.kt)
**Estimasi**: 1 jam | **Severity**: HIGH

### Masalah
`ConnectionRequestDialog.kt` sudah ada tapi tidak pernah dipanggil dari mana pun.

### 4a. Cek apakah `_incomingConnectionRequest` SharedFlow sudah ada di `WifiDirectManager.kt`

Cari: `MutableSharedFlow<IncomingRequest>` atau serupa. Jika belum ada, tambahkan:

```kotlin
data class IncomingRequest(
    val fromPeerId: String,
    val fromName: String,
    val fromRole: String
)

private val _incomingConnectionRequest = MutableSharedFlow<IncomingRequest>(
    extraBufferCapacity = 4
)
val incomingConnectionRequest: SharedFlow<IncomingRequest> = _incomingConnectionRequest
```

### 4b. Expose ke `MainViewModel.kt`

```kotlin
val incomingConnectionRequest: SharedFlow<IncomingRequest> =
    wifiDirectManager.incomingConnectionRequest

fun acceptConnectionRequest(peerId: String) {
    wifiDirectManager.respondToConnectionRequest(peerId, accept = true)
}

fun rejectConnectionRequest(peerId: String) {
    wifiDirectManager.respondToConnectionRequest(peerId, accept = false)
}
```

### 4c. Tambah `respondToConnectionRequest()` di `WifiDirectManager.kt`

```kotlin
fun respondToConnectionRequest(peerId: String, accept: Boolean) {
    scope.launch {
        repository.updatePeerStatus(
            peerId,
            if (accept) ConnectionStatus.ACTIVE_MESH else ConnectionStatus.DISCOVERED
        )
        Log.d(TAG, "Connection ${if(accept) "ACCEPTED" else "REJECTED"} for $peerId")
    }
}
```

### 4d. Tampilkan dialog di `MainActivity.kt` (atau root Composable)

Di tempat utama app Composable (cari `setContent { }` di `MainActivity.kt`):

```kotlin
// Tambah di dalam setContent atau NavHost
val incomingRequest by viewModel.incomingConnectionRequest
    .collectAsStateWithLifecycle(initialValue = null)

incomingRequest?.let { request ->
    ConnectionRequestDialog(
        peerId = request.fromPeerId,
        peerName = request.fromName,
        peerRole = request.fromRole,
        onAccept = { viewModel.acceptConnectionRequest(it) },
        onReject = { viewModel.rejectConnectionRequest(it) },
        onDismiss = { /* dismiss handled by accept/reject */ }
    )
}
```

---

## 🛠️ Work Group 5: Theme Migration
**Files**: [`PeerListView.kt`](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/ui/components/PeerListView.kt), [`ConnectionRequestDialog.kt`](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/ui/dialogs/ConnectionRequestDialog.kt)
**Estimasi**: 45 menit | **Severity**: MEDIUM

### Mapping Warna Lama → CARAKA Theme

| Hardcoded | CARAKA Theme |
|-----------|-------------|
| `Color.White` (background card) | `GlassSurface` |
| `Color(0xFF1A1A1A)` | `TextPrimary` |
| `Color(0xFF666666)` | `TextSecondary` |
| `Color(0xFF0066CC)` (CONNECT button) | `AmberAccent` |
| `Color(0xFF00AA00)` (connected) | `NeonMint` |
| `Color(0xFFCC0000)` (reject) | `DangerRed` |
| `Color(0xFF1A1A1A)` dialog background | `NavyBackground` |
| `Color(0xFFEEEEEE)` divider | `SurfaceDark` |

### Tambah imports di kedua file:

```kotlin
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.NavyBackground
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.NeonMint
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary
import com.example.caraka.ui.theme.DangerRed
import com.example.caraka.ui.theme.SurfaceDark
```

---

## 🛠️ Work Group 6: DNS-SD Exponential Backoff
**File**: [`WifiDirectManager.kt`](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/network/WifiDirectManager.kt)
**Estimasi**: 30 menit | **Severity**: MEDIUM (untuk Infinix XOS)

### Tindakan

Cari `discoverServices` di `WifiDirectManager.kt`. Tambah property `dnssdBusyCount` dan update `onFailure`:

```kotlin
private var dnssdBusyCount = 0

// Di onFailure callback discoverServices():
override fun onFailure(reason: Int) {
    if (reason == WifiP2pManager.BUSY) {
        dnssdBusyCount++
        val backoffMs = minOf(3_000L * (1 shl dnssdBusyCount.coerceAtMost(4)), 48_000L)
        Log.w(TAG, "DNS-SD BUSY (attempt $dnssdBusyCount) — retry in ${backoffMs}ms")
        scope.launch {
            delay(backoffMs)
            if (dnssdBusyCount >= 3) {
                Log.w(TAG, "DNS-SD 3x BUSY — LAN-only mode, reset counter")
                dnssdBusyCount = 0
                // LAN discovery sudah berjalan, tidak perlu restart
            } else {
                startServiceDiscovery()
            }
        }
    } else {
        Log.w(TAG, "DNS-SD failed: reason=$reason")
        dnssdBusyCount = 0
    }
}
```

Juga, reset counter saat discovery sukses:

```kotlin
// Di onSuccess callback discoverServices():
override fun onSuccess() {
    dnssdBusyCount = 0
    Log.d(TAG, "DNS-SD discovery started")
}
```

---

## 🛠️ Work Group 7: DAO & Repository Helpers
**Files**: [`PeerDao.kt`](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/data/local/dao/PeerDao.kt), [`MeshRepository.kt`](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/repository/MeshRepository.kt)
**Estimasi**: 30 menit | **Severity**: MEDIUM (diperlukan WG2 & WG4)

### Cek dan tambah di `PeerDao.kt` jika belum ada:

```kotlin
@Query("SELECT * FROM peers WHERE id = :peerId LIMIT 1")
suspend fun getPeerById(peerId: String): PeerEntity?

@Query("UPDATE peers SET lastSeen = :timestamp WHERE id = :peerId")
suspend fun updateLastSeen(peerId: String, timestamp: Long)

@Query("UPDATE peers SET status = :status WHERE id = :peerId")
suspend fun updateConnectionStatus(peerId: String, status: String)
```

### Cek dan tambah di `MeshRepository.kt` jika belum ada:

```kotlin
suspend fun getPeerById(peerId: String): PeerEntity? {
    return peerDao.getPeerById(peerId)
}

suspend fun updatePeerStatus(peerId: String, status: ConnectionStatus) {
    peerDao.updateConnectionStatus(peerId, status.name)
}
```

---

## 📅 Urutan Eksekusi

```
Hari 1 (~5 jam):
  WG7  Cek & tambah DAO helpers          [30 min]  dibutuhkan oleh WG2
  WG1  DB Migration (version 1→2)        [30 min]  KRITIS, lakukan sebelum build
  WG2  PEER_LIST broadcast & handler     [2 jam]   inti full mesh B↔C
  WG3  QR auto-connect via peerIpRegistry[1 jam]   fix QR connect
  WG6  DNS-SD backoff                    [30 min]  OEM compat

Hari 2 (~3-4 jam):
  WG4  Integrasi dialog ke MainActivity  [1 jam]   agar dialog muncul
  WG5  Theme migration                   [45 min]  visual polish
  Build & Test di device                 [1+ jam]  verifikasi semua
```

---

## 📱 Test Plan (Perlu Device)

### Setup
- 4 device fisik terhubung ke **WiFi router yang sama** (atau offline WiFi Direct only)
- Install via ADB: `adb install -r app-debug.apk`

### Test Case 1 — Discovery & No Auto-Connect ke TV
```
□ 4 device buka CARAKA
□ Semua saling detect dalam 15 detik (via LAN beacon)
□ Smart TV / Laptop TIDAK muncul di peer list
```

### Test Case 2 — Manual Connect Flow
```
□ A tap [CONNECT] ke B dari peer list
□ B terima dialog "Incoming Connection Request" dengan nama + role A
□ B tap ACCEPT → keduanya masuk ACTIVE_MESH
□ Pesan A→B berhasil terkirim
```

### Test Case 3 — Full Mesh B↔C
```
□ A, B, C di WiFi yang sama
□ A connect ke B (keduanya ACTIVE_MESH)
□ A connect ke C (keduanya ACTIVE_MESH)
□ Setelah PEER_LIST terkirim, B melihat C di peer list
□ B connect ke C tanpa melewati A
□ Pesan B→C via LAN langsung (cek logcat: "LAN unicast")
```

### Test Case 4 — QR Auto-Connect
```
□ A tampilkan QR di QrIdentityScreen
□ B scan QR A → dialog konfirmasi muncul
□ B konfirmasi → koneksi dibuat dalam 5 detik
□ TIDAK perlu tap CONNECT di NetworkScreen
```

### Test Case 5 — OEM Devices
```
□ Infinix (XOS): DNS-SD BUSY backoff aktif, LAN beacon tetap bekerja
□ Redmi (MIUI): peer teridentifikasi via peerId, bukan device name
```

### Test Case 6 — SOS Relay (Multi-Hop)
```
□ A tidak direct ke D (A—B—C—D chain)
□ A kirim SOS (TTL=10)
□ D terima SOS via relay
□ Logcat B & C: "Relaying SOS"
```

---

## ⚠️ Catatan Penting

### Tidak Perlu Diimplementasikan
- SQLCipher: sudah ada di `CarakaDatabase.kt` ✅
- Auto-connect ke TV sudah dihapus dari `onPeersAvailable()` ✅ (git pull terbaru)
- `PeerShare`/`PEER_LIST` type sudah ada di protocol ✅

### Yang Perlu Dicek Sebelum Eksekusi
1. Apakah `MeshRepository.getPeerById()` sudah ada? → Cek `PeerDao.kt`
2. Di mana tepatnya `onMessageReceived()` di `WifiDirectManager.kt`? → Untuk WG2d
3. Apakah sudah ada `_incomingConnectionRequest` SharedFlow? → Untuk WG4a

### Phase 1 (Setelah Phase 0 Selesai)
- BLE Transport Manager (solusi permanent full mesh offline)
- Real PKI (ganti hardcoded demo passwords)
- Voice PTT (Opus codec)
- Offline resource map (MapLibre + OSM)

---

## 🔬 Root Cause Analysis

### Masalah 1 — Auto-connect ke TV / device non-CARAKA
```
Penyebab: onPeersAvailable() → anyCandidate fallback (Tier 4)
          mencoba connect ke SEMUA device yang terlihat, 
          termasuk Smart TV, speaker, laptop.
          
DNS-SD filter belum cukup karena:
- Beberapa OEM (MIUI, XOS) blokir discoverServices()
- Tier 4 fallback akhirnya konek ke semua device
```

### Masalah 2 — QR Scan tidak langsung connect
```
WiFi Direct TIDAK bisa konek hanya dari peerId (fingerprint).
Yang dibutuhkan: WiFi Direct MAC address.

QR payload berisi: peerId, name, role, encPub, signPub
QR payload TIDAK berisi: WiFi Direct MAC (karena dinamis & berubah)

priorityPeerId sudah di-set, tapi koneksi tetap bergantung pada 
device tersebut muncul di scan WiFi Direct terlebih dulu.
```

### Masalah 3 — Star Topology (B-C tidak bisa langsung)
```
FUNDAMENTAL LIMITATION WiFi Direct API:
- Hanya ada 1 Group Owner (GO) per group
- Client (B, C) tidak bisa saling terhubung langsung via WiFi Direct
- B tahu A (GO), C tahu A (GO), tapi B tidak tahu C
- Solusi WiFi Direct murni: TIDAK MUNGKIN (hardware limitation)
- Solusi: LAN UDP peer exchange
```

### Masalah 4 — Android OEM Compatibility
```
Redmi MIUI/HyperOS: setDeviceName reflection gagal diam-diam
XOS (Infinix):      discoverServices() → BUSY intermittent
HiOS (Tecno):       WiFi Direct group behavior non-standard
Dampak: device tidak terlihat satu sama lain
```

---

## Arsitektur Solusi

```
┌─────────────────────────────────────────────────────┐
│               TRANSPORT HIERARCHY                   │
│                                                     │
│  Primary:  LAN UDP (port 8890)                      │
│            → All Android versions, all OEMs         │
│            → Any-to-any, no star topology limit     │
│            → Works when on same WiFi network        │
│                                                     │
│  Fallback: WiFi Direct TCP Socket (port 8888)       │
│            → Offline/disaster (no WiFi router)      │
│            → Star via relay (A relays B→C)          │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│              CONNECT FLOW (NEW)                     │
│                                                     │
│  AUTO DISCOVERY  → peers terdeteksi via LAN beacon  │
│  USER TAPS peer  → CONNECT_REQUEST dikirim          │
│  TARGET DEVICE   → tampilkan Accept/Reject dialog   │
│  On accept       → save peer, mark connected        │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│              FULL MESH (NEW)                        │
│                                                     │
│  B connects to A (GO) via WiFi Direct               │
│  A broadcasts PEER_LIST to all clients              │
│  B learns about C (C's LAN IP from list)            │
│  B sends directly to C via LAN UDP                  │
│  (no relay needed when on same WiFi)                │
└─────────────────────────────────────────────────────┘
```

---

## Proposed Changes

---

### Component 1 — Protocol

#### [MODIFY] [MeshProtocol.kt](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/network/MeshProtocol.kt)

Tambah field dan tipe pesan baru:

```kotlin
data class MeshProtocol(
    // ... existing fields ...
    val targetId: String? = null,        // [NEW] peerId tujuan spesifik
    val lanIp: String? = null,           // [NEW] IP LAN sender (untuk PEER_LIST)
)
```

Tipe pesan baru:
- `CONNECT_REQUEST` — A request connect ke B; content = "REQ"
- `CONNECT_ACCEPT` — B terima; content = "ACC"
- `CONNECT_REJECT` — B tolak; content = "REJ"
- `PEER_LIST` — GO broadcast daftar semua clients; content = JSON array

---

### Component 2 — WifiDirectManager (Core Changes)

#### [MODIFY] [WifiDirectManager.kt](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/network/WifiDirectManager.kt)

**2a. Tambah `DiscoveredPeer` data class + StateFlow**
```kotlin
data class DiscoveredPeer(
    val peerId: String,
    val displayName: String,
    val role: String,
    val transport: String,      // "LAN" | "WIFI_DIRECT"
    val address: String,        // IP address (LAN) atau MAC (WiFi Direct)
    val publicKey: String,
    val signingKey: String,
    val lastSeen: Long,
    val isVerified: Boolean = false
)

private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers

// Map peerId → IP untuk LAN direct messaging
private val lanPeerIpMap = ConcurrentHashMap<String, String>()
```

**2b. Tambah `_incomingConnectRequest` SharedFlow**
```kotlin
data class ConnectRequest(
    val fromPeerId: String,
    val fromName: String,
    val fromRole: String
)

private val _incomingConnectRequest = MutableSharedFlow<ConnectRequest>(extraBufferCapacity = 4)
val incomingConnectRequest: SharedFlow<ConnectRequest> = _incomingConnectRequest
```

**2c. Hapus auto-connect dari `onPeersAvailable()`**
```kotlin
// SEBELUM: semua Tier 1-4 auto-connect logic
// SESUDAH: hanya update state + list
internal fun onPeersAvailable(peers: List<WifiP2pDevice>) {
    _availablePeers.value = peers
    if (_connectionState.value !in busyStates) {
        _connectionState.value = if (peers.isEmpty()) "NO_PEERS" else "PEERS_FOUND"
    }
    Log.d(TAG, "Peers available: ${peers.size} — ${peers.map { it.deviceName }}")
    // Tidak ada auto-connect
}
```

**2d. Hapus auto-connect dari `setupServiceDiscoveryListeners()`**
```kotlin
// DNS-SD listener: HANYA add ke carakaServicePeerMacs dan _discoveredPeers
// JANGAN connectToPeer() otomatis
val serviceListener = WifiP2pManager.DnsSdServiceResponseListener { _, registrationType, device ->
    if (registrationType.contains(CARAKA_SERVICE_TYPE, ignoreCase = true)) {
        carakaServicePeerMacs.add(device.deviceAddress)
        // Tidak ada connectToPeer() di sini
    }
}
```

**2e. Modifikasi `saveLanPeer()` → update `_discoveredPeers` dan `lanPeerIpMap`**
```kotlin
private fun saveLanPeer(protocol: MeshProtocol, hostAddress: String) {
    val existing = _discoveredPeers.value.firstOrNull { it.peerId == protocol.senderId }
    if (existing == null) {
        val newPeer = DiscoveredPeer(
            peerId = protocol.senderId,
            displayName = protocol.senderName,
            role = protocol.senderRole,
            transport = "LAN",
            address = hostAddress,
            publicKey = protocol.publicKey ?: "",
            signingKey = protocol.signingKey ?: "",
            lastSeen = System.currentTimeMillis()
        )
        _discoveredPeers.value = _discoveredPeers.value + newPeer
    } else {
        _discoveredPeers.value = _discoveredPeers.value.map {
            if (it.peerId == protocol.senderId) 
                it.copy(address = hostAddress, lastSeen = System.currentTimeMillis())
            else it
        }
    }
    lanPeerIpMap[protocol.senderId] = hostAddress
    carakaLanPeerHosts.add(hostAddress)
    Log.d(TAG, "LAN peer discovered: ${protocol.senderName} at $hostAddress")
}
```

**2f. Tambah `requestConnect(peerId)` — user-initiated**
```kotlin
fun requestConnect(peerId: String) {
    scope.launch {
        val myId = identityManager.getPeerId()
        val request = MeshProtocol(
            type = "CONNECT_REQUEST",
            id = UUID.randomUUID().toString(),
            senderId = myId,
            senderName = identityManager.getDisplayName(),
            senderRole = identityManager.getRole(),
            targetId = peerId,
            recipientId = peerId,
            content = "REQ",
            timestamp = System.currentTimeMillis(),
            publicKey = identityManager.getEncryptionPublicKeyBase64(),
            signingKey = identityManager.getSigningPublicKeyBase64(),
            lanIp = getMyLanIp()
        )
        val lanIp = lanPeerIpMap[peerId]
        if (lanIp != null) {
            sendLanDirectPayload(request.toJson(), lanIp)
            Log.d(TAG, "CONNECT_REQUEST sent via LAN to $peerId at $lanIp")
        } else {
            // Fallback: broadcast via LAN + WiFi Direct
            sendLanPayload(request.toJson())
            socketManager.sendPayload(request.toJson())
            Log.d(TAG, "CONNECT_REQUEST broadcast (no direct IP for $peerId)")
        }
    }
}
```

**2g. Tambah `respondToConnectRequest(peerId, accept)`**
```kotlin
fun respondToConnectRequest(peerId: String, accept: Boolean) {
    scope.launch {
        val response = MeshProtocol(
            type = if (accept) "CONNECT_ACCEPT" else "CONNECT_REJECT",
            id = UUID.randomUUID().toString(),
            senderId = identityManager.getPeerId(),
            senderName = identityManager.getDisplayName(),
            senderRole = identityManager.getRole(),
            targetId = peerId,
            recipientId = peerId,
            content = if (accept) "ACC" else "REJ",
            timestamp = System.currentTimeMillis(),
            publicKey = identityManager.getEncryptionPublicKeyBase64(),
            signingKey = identityManager.getSigningPublicKeyBase64(),
            lanIp = getMyLanIp()
        )
        val lanIp = lanPeerIpMap[peerId]
        if (lanIp != null) sendLanDirectPayload(response.toJson(), lanIp)
        else sendLanPayload(response.toJson())

        if (accept) {
            // Simpan peer ke DB sebagai connected
            savePeerToDb(peerId)
            // Jika target ada di WiFi Direct, initiate connect
            val wdDevice = _availablePeers.value.firstOrNull {
                _discoveredPeers.value.firstOrNull { p -> 
                    p.peerId == peerId && p.transport == "WIFI_DIRECT" 
                }?.address == it.deviceAddress
            }
            wdDevice?.let { connectToPeer(it) }
        }
        Log.d(TAG, "CONNECT_${if(accept) "ACCEPT" else "REJECT"} sent to $peerId")
    }
}
```

**2h. Tambah handler baru di `onMessageReceived()`**
```kotlin
when (protocol.type) {
    "HANDSHAKE"       -> handleHandshake(protocol, fromAddress)
    "TEXT"            -> handleTextMessage(protocol)
    "SOS"             -> handleSosMessage(protocol)
    "FLAG"            -> handleFlagMessage(protocol)
    "CONNECT_REQUEST" -> handleConnectRequest(protocol)
    "CONNECT_ACCEPT"  -> handleConnectAccept(protocol)
    "CONNECT_REJECT"  -> handleConnectReject(protocol)
    "PEER_LIST"       -> handlePeerList(protocol)
    else -> Log.w(TAG, "Unknown type: ${protocol.type}")
}
```

**2i. Implementasi handler-handler baru**
```kotlin
private suspend fun handleConnectRequest(protocol: MeshProtocol) {
    val myId = identityManager.getPeerId()
    if (protocol.targetId != myId) return  // bukan untuk saya
    
    // Update IP map dari request
    protocol.lanIp?.let { lanPeerIpMap[protocol.senderId] = it }
    
    _incomingConnectRequest.emit(ConnectRequest(
        fromPeerId = protocol.senderId,
        fromName = protocol.senderName,
        fromRole = protocol.senderRole
    ))
    Log.d(TAG, "CONNECT_REQUEST from ${protocol.senderName}")
}

private suspend fun handleConnectAccept(protocol: MeshProtocol) {
    val myId = identityManager.getPeerId()
    if (protocol.targetId != myId) return
    
    protocol.lanIp?.let { lanPeerIpMap[protocol.senderId] = it }
    
    // Simpan peer ke DB sebagai connected
    savePeerToDb(protocol.senderId, protocol)
    Log.d(TAG, "CONNECT_ACCEPT from ${protocol.senderName} — peer saved")
}

private suspend fun handleConnectReject(protocol: MeshProtocol) {
    val myId = identityManager.getPeerId()
    if (protocol.targetId != myId) return
    Log.d(TAG, "CONNECT_REJECT from ${protocol.senderName}")
    // Optionally emit event to UI
}

private suspend fun handlePeerList(protocol: MeshProtocol) {
    // Parse daftar peer yang dikirim GO
    try {
        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
        val peers: List<Map<String, String>> = gson.fromJson(protocol.content, type)
        peers.forEach { peerMap ->
            val peerId = peerMap["peerId"] ?: return@forEach
            val ip = peerMap["address"] ?: return@forEach
            val name = peerMap["name"] ?: ""
            val transport = peerMap["transport"] ?: "LAN"
            
            // Tambah ke discovered jika belum ada
            if (_discoveredPeers.value.none { it.peerId == peerId }) {
                _discoveredPeers.value = _discoveredPeers.value + DiscoveredPeer(
                    peerId = peerId, displayName = name, role = "",
                    transport = transport, address = ip,
                    publicKey = "", signingKey = "",
                    lastSeen = System.currentTimeMillis()
                )
                lanPeerIpMap[peerId] = ip
                Log.d(TAG, "PEER_LIST: discovered $name at $ip via relay")
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "handlePeerList parse error", e)
    }
}
```

**2j. Modifikasi `handleHandshake()` → GO broadcast PEER_LIST**
```kotlin
// Tambah di akhir handleHandshake(), setelah savePeer():
if (_connectionState.value == "CONNECTED_GO") {
    broadcastPeerList()
}
```

**2k. Tambah `broadcastPeerList()`**
```kotlin
private suspend fun broadcastPeerList() {
    val myId = identityManager.getPeerId()
    val myIp = getMyLanIp() ?: return
    
    // Semua discovered peers (termasuk self untuk LAN IP info)
    val peers = _discoveredPeers.value.filter { it.peerId != myId }
    val peerListData = peers.map { p ->
        mapOf("peerId" to p.peerId, "name" to p.displayName,
              "address" to p.address, "transport" to p.transport)
    }
    
    val protocol = MeshProtocol(
        type = "PEER_LIST",
        id = UUID.randomUUID().toString(),
        senderId = myId,
        senderName = identityManager.getDisplayName(),
        senderRole = identityManager.getRole(),
        recipientId = "BROADCAST",
        content = gson.toJson(peerListData),
        timestamp = System.currentTimeMillis(),
        lanIp = myIp
    )
    sendMessage(protocol.toJson())
    Log.d(TAG, "GO broadcast PEER_LIST: ${peers.size} peers")
}
```

**2l. Tambah `sendLanDirectPayload(json, targetIp)` — unicast LAN**
```kotlin
private suspend fun sendLanDirectPayload(json: String, targetIp: String) {
    val bytes = json.toByteArray(Charsets.UTF_8)
    try {
        DatagramSocket().use { socket ->
            val packet = DatagramPacket(
                bytes, bytes.size,
                InetAddress.getByName(targetIp), LAN_DISCOVERY_PORT
            )
            socket.send(packet)
            Log.d(TAG, "LAN direct → $targetIp: ${bytes.size}B")
        }
    } catch (e: Exception) {
        Log.w(TAG, "sendLanDirectPayload failed to $targetIp", e)
    }
}
```

**2m. Tambah `getMyLanIp()` helper**
```kotlin
private fun getMyLanIp(): String? {
    val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
    val dhcp = wifiManager.dhcpInfo ?: return null
    if (dhcp.ipAddress == 0) return null
    val ip = dhcp.ipAddress
    return "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"
}
```

**2n. Fix `triggerPriorityConnect()` — LAN-first**
```kotlin
fun triggerPriorityConnect(peerId: String) {
    val lanIp = lanPeerIpMap[peerId]
    if (lanIp != null) {
        // Peer sudah terdeteksi via LAN → langsung request connect
        requestConnect(peerId)
        Log.d(TAG, "QR connect via LAN direct to $peerId at $lanIp")
    } else {
        // Belum terdeteksi → set priority + tunggu LAN beacon
        setPriorityPeerId(peerId)
        discoverPeers()
        Log.d(TAG, "QR connect: waiting for LAN beacon from $peerId")
    }
}
```

**2o. OEM fix: DNS-SD exponential backoff**
```kotlin
private var dnssdBusyCount = 0

// Di discoverServices() onFailure:
override fun onFailure(reason: Int) {
    if (reason == WifiP2pManager.BUSY) {
        dnssdBusyCount++
        val backoffMs = minOf(3_000L * (1 shl dnssdBusyCount.coerceAtMost(4)), 48_000L)
        Log.w(TAG, "DNS-SD BUSY ($dnssdBusyCount) — retry in ${backoffMs}ms")
        scope.launch {
            delay(backoffMs)
            if (dnssdBusyCount >= 3) {
                Log.w(TAG, "DNS-SD failed 3x — falling back to peer discovery only")
                discoverPeers()
            } else {
                startServiceDiscovery()
            }
        }
    } else {
        discoverPeers()
    }
}
```

**2p. Hapus `setDeviceName` reflection** (tidak reliable di MIUI/XOS)
- Device diidentifikasi via peerId dari LAN beacon, bukan dari device name
- Tetap ada sebagai best-effort, tapi tidak kritis

---

### Component 3 — LAN Beacon Update

#### [MODIFY] `WifiDirectManager.sendLanHandshake()` — tambah `lanIp`

```kotlin
private suspend fun sendLanHandshake() {
    val myId = identityManager.getPeerId()
    if (myId.isBlank()) return
    val handshake = MeshProtocol(
        // ... existing fields ...
        lanIp = getMyLanIp()  // [NEW] include LAN IP in beacon
    )
    sendLanPayloadInternal(handshake.toJson())
}
```

---

### Component 4 — MainViewModel

#### [MODIFY] [MainViewModel.kt](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/viewmodel/MainViewModel.kt)

```kotlin
// Expose new flows
val discoveredPeers: StateFlow<List<DiscoveredPeer>> = wifiDirectManager.discoveredPeers
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

val incomingConnectRequest = wifiDirectManager.incomingConnectRequest

// New actions
fun requestConnect(peerId: String) {
    wifiDirectManager.requestConnect(peerId)
}

fun respondToConnectRequest(peerId: String, accept: Boolean) {
    wifiDirectManager.respondToConnectRequest(peerId, accept)
}
```

---

### Component 5 — NetworkScreen UI

#### [MODIFY] [NetworkScreen.kt](file:///e:/Project%\20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/ui/screens/NetworkScreen.kt)

**Tambah dua section baru di bawah graph:**

**Section "Nearby Peers"** — dari `discoveredPeers` yang belum connected:
```
┌──────────────────────────────────────────┐
│  NEARBY PEERS (3)                        │
│                                          │
│  ● Budi Rahman    [BPBD]   [LAN] Connect │
│  ● Sari Dewi     [PMI]    [LAN] Connect │
│  ○ Unknown       [?]   [P2P]  Connect   │
└──────────────────────────────────────────┘
```

**Section "Connected"** — dari `connectedPeers` DB:
```
┌──────────────────────────────────────────┐
│  CONNECTED (2)                           │
│                                          │
│  ✓ Ahmad Rizal   [POLRI]  hop:0  Chat   │
│  ✓ Maya Putri    [SIPIL]  hop:1  Chat   │
└──────────────────────────────────────────┘
```

**Incoming Connect Request Dialog:**
```kotlin
@Composable
fun ConnectRequestDialog(
    request: ConnectRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
)
```

---

### Component 6 — QrIdentityScreen

#### [MODIFY] [QrIdentityScreen.kt](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/ui/screens/QrIdentityScreen.kt)

Setelah `triggerPriorityConnect()` dipanggil, tampilkan status:
- "Mengirim permintaan koneksi..." (saat LAN peer ditemukan)
- "Menunggu device terdeteksi..." (saat peer belum ada di LAN)
- "Terhubung! ✓" (saat `CONNECT_ACCEPT` diterima)
- "Ditolak" (saat `CONNECT_REJECT` diterima)

---

### Component 7 — MeshSocketManager

#### [MODIFY] [MeshSocketManager.kt](file:///e:/Project%20APP/CARAKA-APP/app/app/src/main/java/com/example/caraka/network/MeshSocketManager.kt)

```kotlin
// Tambah TCP keepalive
socket.setKeepAlive(true)
socket.soTimeout = 0  // No read timeout — keep alive indefinitely

// Tambah reconnect delay setelah disconnect
// (di finally block handleIncomingData)
```

---

## Priority Implementation Order

| # | Task | Impact | Complexity |
|---|------|--------|------------|
| 1 | Hapus auto-connect dari `onPeersAvailable()` | Critical | Very Low |
| 2 | Hapus auto-connect dari DNS-SD listener | Critical | Very Low |
| 3 | Tambah `DiscoveredPeer` + `_discoveredPeers` | High | Low |
| 4 | Tambah `_incomingConnectRequest` SharedFlow | High | Low |
| 5 | Modifikasi `saveLanPeer()` → update DiscoveredPeers | High | Low |
| 6 | Tambah `lanIp` ke `MeshProtocol` | High | Very Low |
| 7 | Tambah `lanIp` ke LAN beacon (sendLanHandshake) | High | Very Low |
| 8 | Tambah `getMyLanIp()` + `sendLanDirectPayload()` | High | Low |
| 9 | `requestConnect()` + `respondToConnectRequest()` | High | Medium |
| 10 | Handler: `CONNECT_REQUEST/ACCEPT/REJECT/PEER_LIST` | High | Medium |
| 11 | `broadcastPeerList()` dari GO setelah HANDSHAKE | High | Medium |
| 12 | Fix `triggerPriorityConnect()` — LAN-first | High | Low |
| 13 | OEM fix: DNS-SD exponential backoff | Medium | Low |
| 14 | MainViewModel: expose `discoveredPeers` + actions | Medium | Low |
| 15 | NetworkScreen: Nearby Peers list + Connect button | Medium | Medium |
| 16 | Incoming connect request dialog | Medium | Medium |
| 17 | QrIdentityScreen: live connection status | Low | Low |
| 18 | MeshSocketManager: TCP keepalive | Low | Very Low |

---

## Open Questions

> [!IMPORTANT]
> **Q1: Apakah LAN (WiFi router) tersedia saat field testing?**
> Jika tidak (offline scenario), B↔C tetap harus lewat relay A via WiFi Direct.
> Apakah ini acceptable untuk usecase CARAKA?

> [!WARNING]
> **Q2: Accept/Reject dialog — apakah wajib?**
> - Opsi A (sesuai request): target device harus buka app dan tap "Terima"
> - Opsi B (simpler): langsung connect tanpa konfirmasi dari target
>
> Opsi A lebih aman seperti Bluetooth pairing, tapi keduanya harus buka app bersamaan.
> Opsi B lebih praktis untuk disaster scenario.

> [!IMPORTANT]
> **Q3: Apakah QR payload boleh ditambah `lanIp`?**
> Jika ya → QR connect bisa instant karena IP sudah diketahui dari QR.
> Ini paling efektif untuk connect antar device yang sudah di WiFi yang sama.

> [!NOTE]
> **Q4: Peer yang "Nearby" (discovered) vs "Trusted" (QR verified)**
> Apakah user bisa connect ke peer yang belum pernah di-QR?
> Atau connect hanya boleh ke peer yang sudah di-QR scan sebelumnya?

---

## Verification Plan

### Test 1: Tidak ada auto-connect ke TV
1. Nyalakan Smart TV di sekitar device
2. Buka CARAKA → NetworkScreen
3. TV TIDAK boleh muncul di "Nearby Peers" (karena tidak kirim LAN beacon CARAKA)
4. CARAKA TIDAK boleh mencoba konek ke TV

### Test 2: Manual Connect Flow
1. 4 device buka CARAKA, semua di WiFi yang sama
2. Buka NetworkScreen → "Nearby Peers" tampil device lain
3. Tap "Connect" → device target tampilkan dialog
4. Tap "Terima" → kedua device masuk "Connected"

### Test 3: QR → Instant Connect
1. Device A tampilkan QR
2. Device B scan QR → Confirm
3. Status: "Mengirim permintaan..." → "Terhubung!" (< 5 detik via LAN)

### Test 4: Full Mesh B↔C
1. A, B, C di WiFi yang sama
2. A connect ke B dan C (atau B&C connect ke A)
3. A broadcast PEER_LIST → B tahu IP C, C tahu IP B
4. B dan C connect langsung satu sama lain via LAN
5. Kirim pesan B→C: langsung, bukan via relay

### Test 5: Offline Relay
1. Matikan WiFi router
2. A = GO, B dan C = clients via WiFi Direct
3. B kirim ke C → relay via A
4. Logcat A: "Relaying message to C"

### Test 6: OEM Compatibility
- Redmi: DNS-SD BUSY → backoff aktif, LAN beacon bekerja
- Infinix: sama dengan Redmi
- Tecno: WiFi Direct group + LAN hybrid bekerja
