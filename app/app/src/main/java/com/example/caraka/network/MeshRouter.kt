package com.example.caraka.network

import android.util.Log
import com.example.caraka.crypto.IdentityManager
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * MeshRouter bertanggung jawab atas tabel routing dan penerusan pesan (multi-hop forwarding).
 * Berjalan di atas layer transportasi (Socket/Aware) untuk mendistribusikan rute ala BATMAN/OLSR.
 */
class MeshRouter(
    private val identityManager: IdentityManager,
    private val sendOutboundMessage: (nextHopPeerId: String, payload: String) -> Unit,
    private val broadcastOutboundMessage: (payload: String) -> Unit
) {
    companion object {
        private const val TAG = "MeshRouter"
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val ROUTE_TIMEOUT_MS = 35_000L
    }

    // destinationPeerId -> nextHopPeerId
    private val routingTable = ConcurrentHashMap<String, String>()
    
    // destinationPeerId -> timestamp terakhir rute diperbarui
    private val routeLastSeen = ConcurrentHashMap<String, Long>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private val gson = Gson()

    @Volatile private var myId: String = ""
    @Volatile private var myName: String = ""
    @Volatile private var myRole: String = ""

    private suspend fun ensureIdentityLoaded() {
        if (myId.isNotEmpty()) return
        myId = identityManager.getPeerId()
        myName = identityManager.getDisplayName()
        myRole = identityManager.getRole()
    }

    init {
        scope.launch {
            ensureIdentityLoaded()
            startHeartbeat()
        }
    }

    /**
     * Memulai pengiriman Originator Message (Heartbeat) ke tetangga terdekat.
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                cleanupStaleRoutes()
                ensureIdentityLoaded()
                val reachablePeers = routingTable.keys().toList()
                val heartbeat = MeshHeartbeat(
                    originatorId = myId,
                    reachablePeers = reachablePeers,
                    timestamp = System.currentTimeMillis()
                )
                
                val protocolMsg = MeshProtocol(
                    type = "ROUTING_HEARTBEAT",
                    id = java.util.UUID.randomUUID().toString(),
                    senderId = myId,
                    senderName = myName,
                    senderRole = myRole,
                    recipientId = "BROADCAST",
                    content = gson.toJson(heartbeat),
                    timestamp = System.currentTimeMillis()
                )
                
                broadcastOutboundMessage(gson.toJson(protocolMsg))
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /**
     * Dipanggil saat menerima pesan dari Socket/Transport layer.
     * Mengembalikan true jika pesan ini adalah untuk kita (atau broadcast yang relevan).
     * Jika pesan untuk orang lain, diteruskan otomatis dan mengembalikan false.
     */
    fun onMessageReceived(json: String, senderHopId: String): Boolean {
        if (myId.isEmpty()) {
            // Identity not loaded yet — queue via coroutine so no messages are dropped (BUG-P4 fix).
            scope.launch {
                ensureIdentityLoaded()
                onMessageReceived(json, senderHopId)
            }
            return false
        }

        try {
            val msg = gson.fromJson(json, MeshProtocol::class.java)

            // 1. Update Routing Table dari pengirim terdekat (hop pengirim)
            updateRoute(msg.senderId, senderHopId)

            // Jika ini adalah pesan Heartbeat, update rute dari isi heartbeat
            if (msg.type == "ROUTING_HEARTBEAT") {
                val heartbeat = gson.fromJson(msg.content, MeshHeartbeat::class.java)
                heartbeat.reachablePeers.forEach { reachablePeerId ->
                    if (reachablePeerId != myId) {
                        // Jika pengirim bisa mencapai X, maka kita bisa mencapai X lewat pengirim
                        updateRoute(reachablePeerId, senderHopId)
                    }
                }
                return false // Jangan laporkan ke UI
            }

            // 2. Cek tujuan pesan
            if (msg.recipientId == myId || msg.recipientId == "BROADCAST" || msg.recipientId.isBlank()) {
                return true // Pesan untuk kita
            }

            // 3. Forward (Penerusan) pesan jika bukan untuk kita — decrement TTL sebelum forward
            if (msg.ttl <= 1) {
                Log.w(TAG, "TTL habis saat routing — drop ${msg.id}")
                return false
            }
            val forwardMsg = msg.copy(ttl = msg.ttl - 1)
            val fwdJson = gson.toJson(forwardMsg)
            val nextHop = routingTable[forwardMsg.recipientId]
            if (nextHop != null) {
                Log.d(TAG, "Meneruskan ${forwardMsg.senderId}→${forwardMsg.recipientId} via $nextHop (ttl=${forwardMsg.ttl})")
                sendOutboundMessage(nextHop, fwdJson)
            } else {
                Log.w(TAG, "Tujuan ${forwardMsg.recipientId} tidak di routing table. Drop.")
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Gagal parsing pesan masuk di Router", e)
            return false
        }
    }

    /**
     * Mengirim pesan menggunakan tabel routing (Multi-hop).
     */
    fun routeMessage(msg: MeshProtocol) {
        if (msg.ttl <= 1) {
            Log.w(TAG, "routeMessage: TTL habis untuk ${msg.id} — drop")
            return
        }
        val routed = msg.copy(ttl = msg.ttl - 1)
        val json = gson.toJson(routed)
        if (routed.recipientId == "BROADCAST" || routed.recipientId.isBlank()) {
            broadcastOutboundMessage(json)
            return
        }

        val nextHop = routingTable[routed.recipientId]
        if (nextHop != null) {
            Log.d(TAG, "Routing pesan ke ${routed.recipientId} via $nextHop (ttl=${routed.ttl})")
            sendOutboundMessage(nextHop, json)
        } else {
            Log.w(TAG, "Tidak ada rute ke ${routed.recipientId}, fallback ke broadcast")
            broadcastOutboundMessage(json)
        }
    }

    private fun updateRoute(targetId: String, nextHopId: String) {
        if (targetId == myId || myId.isEmpty()) return

        routingTable[targetId] = nextHopId
        routeLastSeen[targetId] = System.currentTimeMillis()
    }

    private fun cleanupStaleRoutes() {
        val now = System.currentTimeMillis()
        val stalePeers = routeLastSeen.filter { now - it.value > ROUTE_TIMEOUT_MS }.keys
        stalePeers.forEach {
            routingTable.remove(it)
            routeLastSeen.remove(it)
            Log.d(TAG, "Rute ke $it dihapus (Timeout)")
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        routingTable.clear()
        routeLastSeen.clear()
    }
}

data class MeshHeartbeat(
    val originatorId: String,
    val reachablePeers: List<String>,
    val timestamp: Long
)
