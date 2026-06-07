# CARAKA Mesh Connection Redesign
**Date:** June 7, 2026  
**Status:** Design Finalized  
**Devices:** Android 10, 12, 14 (4 devices tested)

---

## Problem Statement

### Current Issues
1. **Partial Discovery** — 4 devices not all detected at same time (random)
2. **Inconsistent Auto-Connect** — Sometimes auto-connect, sometimes manual click needed
3. **Asymmetric Mesh** — Device A becomes hub; B, C only connect to A (not to each other)
4. **QR Scan No Auto-Connect** — Can identify peer but must manually connect afterward
5. **Android Version Fragmentation** — Android 10, 12, 14 have different WiFi Direct behavior

### Root Causes
- WiFi P2P DNS-SD race conditions (timing-dependent)
- Group Owner (GO) election creates hub-and-spoke topology
- Auto-connect logic unreliable across Android versions
- QR scan triggers identity verification but not connection

---

## Solution Overview

### Three Core Changes

#### 1. **Explicit Manual Connection Request System**
- Discovery finds peers → shows list
- User clicks `[CONNECT]` button
- Recipient gets dialog → can `[ACCEPT]` or `[REJECT]`
- Reliable on all Android versions (no timing race)

#### 2. **QR Auto-Connect**
- Scanning QR code = in-person consent
- Automatically sends connection request with `autoAccept=true`
- Recipient auto-accepts (no dialog needed)
- Instant mesh connection after QR scan

#### 3. **Fully Connected Mesh Topology**
- Remove hub-and-spoke (GO election)
- Every device connects to every other discovered device
- Multiple simultaneous connections per device (all sockets are equal)
- Result: A↔B, B↔C, A↔C, D↔A, D↔B, D↔C (complete graph)

---

## Architecture Design

### Connection State Machine

```
DISCOVERED
  ↓ (user clicks CONNECT or QR scanned)
PENDING_REQUEST (waiting for response from peer)
  ├─ Recipient ACCEPT → CONNECTED
  └─ Recipient REJECT → DISCOVERED (can retry after 2 min)

CONNECTED
  ├─ HANDSHAKE successful → ACTIVE_MESH ✅
  └─ Socket closes → DISCOVERED (auto-retry after 5 sec)

ACTIVE_MESH
  ├─ Ready for messaging (encrypted relay)
  └─ Heartbeat every 3s (keep-alive)
```

### Updated Data Models

**PeerEntity (Room database):**

```kotlin
data class PeerEntity(
    @PrimaryKey
    val peerId: String,              // Unique peer identifier
    val name: String,                // Display name
    val role: String,                // BPBD, Polri, PMI, Civilian
    val encPub: String,              // X25519 encryption public key
    val signPub: String,             // Ed25519 signature public key
    
    // NEW: Connection state tracking
    val connectionId: String,        // "peerId_A||peerId_B" (deterministic)
    val status: ConnectionStatus,    // DISCOVERED, PENDING_REQUEST, CONNECTED, ACTIVE_MESH
    val direction: String,           // "OUTBOUND" (we requested), "INBOUND" (they requested)
    val lastAttempt: Long,           // When we last tried to connect
    val rejectionCount: Int,         // How many times they rejected
    val isVerified: Boolean,         // QR scan verified
    
    val lastHeartbeat: Long,
    val hopDistance: Int             // 0=direct, 1=via 1 relay, etc
)

enum class ConnectionStatus {
    DISCOVERED,      // Found via discovery, not yet requested
    PENDING_REQUEST, // Request sent, waiting for response
    CONNECTED,       // TCP socket established, HANDSHAKE pending
    ACTIVE_MESH      // HANDSHAKE verified, ready for messages
}
```

**MeshProtocol (wire format):**

```kotlin
data class MeshProtocol(
    val type: String,        // HANDSHAKE, TEXT, SOS, CONNECTION_REQUEST, etc
    val id: String,          // Unique message ID (for dedup)
    val timestamp: Long,
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val recipientId: String?,  // For DM; null for broadcast
    val content: String,
    val publicKey: String?,
    val signature: String?,
    val ttl: Int,
    val seenBy: List<String>, // Anti-loop: which peers already relayed
    
    // NEW: Connection request fields
    val autoAccept: Boolean = false  // For QR: auto-accept without dialog
)
```

---

## Implementation Plan

### Phase 1: Connection Request System (3-4 hours)

**Files to modify:**

1. **data/local/entity/PeerEntity.kt**
   - Add `connectionId`, `status`, `direction`, `lastAttempt`, `rejectionCount` fields
   - Add `ConnectionStatus` enum

2. **network/MeshProtocol.kt**
   - Add `CONNECTION_REQUEST`, `CONNECTION_ACCEPT`, `CONNECTION_REJECT` message types
   - Add `autoAccept` field

3. **network/WifiDirectManager.kt**
   - Remove auto-connect logic from `discoverPeers()`
   - Add `requestConnectionToPeer(peerId)` function
   - Stop immediately connecting to discovered peers
   - Let user click button to initiate connection

4. **network/MeshSocketManager.kt**
   - Add handler for `CONNECTION_REQUEST` messages
   - Add handler for `CONNECTION_ACCEPT` / `CONNECTION_REJECT` messages
   - Store connection state in `handshakeCompleted` map by `connectionId`

5. **repository/MeshRepository.kt**
   - Add `saveConnectionRequest(peerId)` — creates PENDING_REQUEST state
   - Add `acceptConnectionRequest(peerId)` — sends CONNECTION_ACCEPT
   - Add `rejectConnectionRequest(peerId)` — sends CONNECTION_REJECT
   - Query: get all peers by status

6. **ui/screens/NetworkScreen.kt**
   - For each discovered peer, show `[CONNECT]` button
   - For pending requests, show "Requesting..." spinner
   - For accepted, show "Connected" checkmark
   - For rejected peers, show "Rejected — retry in 2 min" (grayed out)

7. **Add new Dialog: ConnectionRequestDialog.kt**
   - Shows when peer receives CONNECTION_REQUEST
   - Displays: peer name, role, icon
   - Buttons: `[ACCEPT]` `[REJECT]`
   - Binds to ViewModel to handle user choice

### Phase 2: Fully Connected Mesh (1 hour)

**Files to modify:**

1. **network/WifiDirectManager.kt**
   - Change `discoverPeers()` to loop through all discovered peers:
     ```kotlin
     fun autoConnectToPeers() {
         for (peer in discoveredPeers) {
             if (peer.status == DISCOVERED && 
                 peer.lastAttempt < now - 2.minutes) {
                 requestConnectionToPeer(peer.peerId)
             }
         }
     }
     ```
   - Call this every 10 seconds instead of selective GO election

2. **network/MeshSocketManager.kt**
   - Support multiple outbound connections per peer
   - Use `connectionId` as key in `clientStreams` map

### Phase 3: QR Auto-Connect (1 hour)

**Files to modify:**

1. **crypto/QrIdentityManager.kt**
   - When QR scan successful, extract peerId
   - Trigger `meshRepository.requestConnectionToPeer(peerId, autoAccept=true)`

2. **network/MeshSocketManager.kt**
   - When receive `CONNECTION_REQUEST` with `autoAccept=true`:
     ```kotlin
     if (msg.autoAccept) {
         // Auto-accept without showing dialog
         sendConnectionAccept(msg.senderId)
     } else {
         // Show dialog to user
         showConnectionRequestDialog(msg)
     }
     ```

3. **ui/screens/QrIdentityScreen.kt**
   - After scan success, show toast "Connecting to [PeerName]..."
   - No additional dialog needed (auto-accept handles it)

### Phase 4: Android 10-14 Compatibility (30 min)

**Files to modify:**

1. **network/WifiDirectManager.kt**
   ```kotlin
   fun discoverPeers() {
       // Always run: LAN broadcast discovery (works on all versions)
       startLanBroadcastDiscovery()
       
       // Conditional: WiFi P2P DNS-SD (Android 12+ only)
       if (Build.VERSION.SDK_INT >= 31 && hasLocationPermission()) {
           startWifiP2pDnsDiscovery()
       }
       // Android 10 relies on LAN broadcast (reliable enough)
   }
   ```

2. **Verify permissions in AndroidManifest.xml:**
   - `ACCESS_FINE_LOCATION` (for WiFi P2P on Android 12+)
   - `NEARBY_WIFI_DEVICES` (Android 13+)
   - `CHANGE_WIFI_STATE` (all versions)
   - LAN broadcast needs `CHANGE_NETWORK_STATE`

### Phase 5: Multi-hop Relay Update (1 hour)

**Files to modify:**

1. **network/MeshSocketManager.kt**
   - Update relay logic:
     ```kotlin
     // Instead of relaying only to GO:
     fun relayMessage(msg: MeshProtocol) {
         // Relay to ALL connected peers except sender
         for ((connectionId, socket) in clientStreams) {
             if (!msg.seenBy.contains(connectionId)) {
                 msg.ttl--
                 if (msg.ttl > 0) {
                     socket.write(msg.toBytes())
                 }
             }
         }
     }
     ```

2. **Add loop detection:**
   ```kotlin
   val msg = receive()
   if (msg.seenBy.contains(myPeerId)) {
       return  // Loop detected, drop
   }
   msg.seenBy.add(myPeerId)
   relayMessage(msg)
   ```

3. **SOS priority:**
   ```kotlin
   if (msg.type == "SOS") {
       msg.ttl = 10  // Farther range
       relayImmediately(msg)
   } else {
       msg.ttl = 5
       batchRelay(msg)  // Delay 100ms to avoid spam
   }
   ```

---

## Testing Strategy

### Unit Tests
- Connection ID ordering (A||B == B||A)
- State transitions (DISCOVERED → PENDING_REQUEST → CONNECTED → ACTIVE_MESH)
- Message deduplication (anti-replay)
- TTL decrement logic

### Integration Tests (4 real devices)

**Test 1: Manual Connection Request**
- A discovers B → clicks [CONNECT]
- B shows dialog → clicks [ACCEPT]
- Verify: ACTIVE_MESH status on both

**Test 2: Fully Connected (4 devices)**
- Start all 4 devices simultaneously
- Wait 10 seconds
- Each device's NetworkScreen should show 3 others as ACTIVE_MESH

**Test 3: QR Auto-Connect**
- A scans B's QR
- No dialog on B
- Both show ACTIVE_MESH within 2 seconds

**Test 4: Android 10 Discovery**
- Start Infinix (Android 10) last
- LAN broadcast detects it within 5 seconds

**Test 5: Multi-hop Relay**
- A-B-C chain (A not directly connected to C)
- A sends message
- C receives via B relay

**Test 6: SOS Multi-hop**
- A sends SOS (TTL=10)
- All 3 others receive within 2 seconds

---

## Success Criteria

✅ All 4 devices discover each other within 10 seconds (with manual connect)  
✅ User clicks [CONNECT] → recipient gets dialog  
✅ Fully connected mesh (A↔B, B↔C, A↔C, D↔all)  
✅ QR scan auto-connects without dialog  
✅ Messages relay correctly via multiple hops  
✅ SOS broadcasts reach all devices even 3+ hops away  
✅ Works on Android 10, 12, 14 with same UX  

---

## Risk & Mitigation

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Multiple sockets per peer = high memory | Device slowdown | Limit to 4 simultaneous peers max; implement socket pooling |
| Connection requests lost in flight | User confusion | Add retry logic: if no response in 10s, resend REQUEST |
| Android 10 LAN broadcast unreliable | Some devices not found | Add WiFi Direct persistent group mode fallback |
| State corruption (peer stuck in PENDING) | Hang state | Add 30-min timeout: auto-transition PENDING→DISCOVERED |

---

## Files Summary

**Total files to create/modify: 12**

Create:
- `ui/dialogs/ConnectionRequestDialog.kt`
- `data/local/dao/ConnectionStateDao.kt` (queries by status)

Modify:
- `data/local/entity/PeerEntity.kt`
- `network/MeshProtocol.kt`
- `network/WifiDirectManager.kt`
- `network/MeshSocketManager.kt`
- `repository/MeshRepository.kt`
- `ui/screens/NetworkScreen.kt`
- `ui/screens/QrIdentityScreen.kt`
- `crypto/QrIdentityManager.kt`
- `viewmodel/MainViewModel.kt` (add handlers)
- `AndroidManifest.xml` (verify permissions)

**Estimated total time: 6-7 hours**

---

## Next Steps

1. ✅ Design finalized (this document)
2. → Start Phase 1: Connection Request System
3. → Build & test on 4 devices
4. → Phase 2-5 sequence
5. → Full integration test
6. → Deploy to production

---

*Design by Claude Code + User (Darkside0908)*  
*CARAKA Mesh Resilience Redesign*
