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

    private var myId: String = ""
    private var myName: String = ""
    private var myRole: String = ""

    init {
        scope.launch {
            myId = identityManager.getPeerId()
            myName = identityManager.getDisplayName()
            myRole = identityManager.getRole()
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
                
                val myId = identityManager.getPeerId()
                // Umumkan diri kita dan siapa saja yang bisa kita capai
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
        if (myId.isEmpty()) return false // Belum siap

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

            // 3. Forward (Penerusan) pesan jika bukan untuk kita
            val nextHop = routingTable[msg.recipientId]
            if (nextHop != null) {
                Log.d(TAG, "Meneruskan pesan dari ${msg.senderId} ke ${msg.recipientId} melalui hop $nextHop")
                sendOutboundMessage(nextHop, json)
            } else {
                Log.w(TAG, "Tujuan ${msg.recipientId} tidak ditemukan di routing table. Drop pesan.")
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
        val json = gson.toJson(msg)
        if (msg.recipientId == "BROADCAST" || msg.recipientId.isBlank()) {
            broadcastOutboundMessage(json)
            return
        }

        val nextHop = routingTable[msg.recipientId]
        if (nextHop != null) {
            Log.d(TAG, "Routing pesan ke ${msg.recipientId} melalui next-hop $nextHop")
            sendOutboundMessage(nextHop, json)
        } else {
            Log.w(TAG, "Tidak ada rute ke ${msg.recipientId}, fallback ke broadcast")
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
