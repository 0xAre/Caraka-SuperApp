package com.example.caraka.network

import android.content.Context
import android.net.Network
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import android.util.Log
import com.example.caraka.crypto.CryptoManager
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.repository.MeshRepository
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.Inet6Address
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Facade that selects the active mesh transport and exposes a single [MeshTransport] surface to the
 * ViewModel and Repository.
 *
 * - [WifiDirectManager] is always constructed: it remains the application "brain" (message handlers,
 *   crypto, Room persistence, notifications), the LAN-UDP backbone, AND the automatic fallback when
 *   Wi-Fi Aware hardware is absent.
 * - When the device supports Wi-Fi Aware (NAN), a [WifiAwareManager] + [MeshRouter] + a dedicated
 *   [MeshSocketManager] are layered on top as the PRIMARY any-to-any multi-hop transport. Inbound
 *   Aware messages are funnelled into the very same brain via [WifiDirectManager.onMessageReceived];
 *   outbound sends piggyback through the brain's [WifiDirectManager.awareBroadcastHook] /
 *   [WifiDirectManager.awareUnicastHook] seams so relays/handshakes/gossip also cross the Aware path.
 *
 * Double-delivery across LAN + Aware is safe: [MeshSocketManager.isDuplicate] + anti-replay dedupe.
 */
class MeshManager(
    private val context: Context,
    repository: MeshRepository,
    private val identityManager: IdentityManager,
    cryptoManager: CryptoManager
) : MeshTransport {

    companion object {
        private const val TAG = "MeshManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** The brain + fallback. Always present. */
    private val wifiDirectManager = WifiDirectManager(context, repository, identityManager, cryptoManager)

    private val awareSupported: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI_AWARE)

    /**
     * Nearby Connections is the primary reliable overlay on phones without NAN. It needs Google Play
     * Services; when absent (AOSP/de-Googled) we silently skip it and rely on Wi-Fi Direct + LAN.
     */
    private val nearbySupported: Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    // ── Aware layer (null when unsupported) ──────────────────────────────────────────────────────
    private var awareSocketManager: MeshSocketManager? = null
    private var meshRouter: MeshRouter? = null
    private var wifiAwareManager: WifiAwareManager? = null

    // ── Nearby Connections layer (null when no Play Services) ─────────────────────────────────────
    private var nearby: NearbyTransport? = null

    /** neighbour peerId -> connId (for next-hop unicast) and the reverse, learned on HANDSHAKE. */
    private val peerToConnId = ConcurrentHashMap<String, String>()
    private val connIdToPeer = ConcurrentHashMap<String, String>()

    private val awareActiveFallback = MutableStateFlow(false)

    init {
        if (awareSupported) setupAwareLayer()
        if (nearbySupported) setupNearbyLayer()
        Log.d(TAG, "Transports — aware=$awareSupported nearby=$nearbySupported (WiFiDirect/LAN always on)")
    }

    private fun setupNearbyLayer() {
        val transport = NearbyTransport(
            context = context,
            // Inbound: feed the existing brain. Its TTL re-broadcast floods back out through the
            // overlay sinks below → multi-hop any-to-any. Anti-replay dedupe prevents loops.
            onMessage = { json, endpointId ->
                MeshProtocol.fromJson(json)?.let { protocol ->
                    wifiDirectManager.onMessageReceived(protocol, "nearby:$endpointId", endpointId)
                }
            },
            // A new neighbour connected → send our HANDSHAKE so it saves us (keys) and we exchange ids.
            onEndpointConnected = { sendNearbyHandshake() }
        )
        nearby = transport
        wifiDirectManager.addOverlayBroadcastSink { json -> transport.broadcast(json) }
        wifiDirectManager.addOverlayUnicastSink { peerId, json -> transport.sendToPeer(peerId, json) }
        Log.d(TAG, "Nearby Connections layer constructed (primary overlay)")
    }

    private fun sendNearbyHandshake() {
        val transport = nearby ?: return
        scope.launch {
            val handshake = MeshProtocol(
                type = "HANDSHAKE",
                id = UUID.randomUUID().toString(),
                senderId = identityManager.getPeerId(),
                senderName = identityManager.getDisplayName(),
                senderRole = identityManager.getRole(),
                recipientId = "BROADCAST",
                content = "HANDSHAKE",
                timestamp = System.currentTimeMillis(),
                publicKey = identityManager.getEncryptionPublicKeyBase64(),
                signingKey = identityManager.getSigningPublicKeyBase64()
            )
            transport.broadcast(handshake.toJson())
        }
    }

    private fun setupAwareLayer() {
        val socket = MeshSocketManager(AwareSocketListener())
        awareSocketManager = socket

        val router = MeshRouter(
            identityManager = identityManager,
            // next-hop unicast over the Aware data path
            sendOutboundMessage = { nextHopPeerId, payload ->
                peerToConnId[nextHopPeerId]?.let { connId -> socket.sendToConnection(connId, payload) }
            },
            // flood to every directly-connected Aware neighbour
            broadcastOutboundMessage = { payload -> socket.sendPayload(payload) }
        )
        meshRouter = router

        wifiAwareManager = WifiAwareManager(
            context = context,
            onPeerDiscovered = { peerHandle, _ -> Log.d(TAG, "Aware peer discovered: $peerHandle") },
            onClientDataPathReady = ::onAwareClientDataPathReady
        )

        // Route the brain's outbound traffic onto the Aware path too.
        wifiDirectManager.addOverlayBroadcastSink { json -> socket.sendPayload(json) }
        wifiDirectManager.addOverlayUnicastSink { _, json ->
            MeshProtocol.fromJson(json)?.let { router.routeMessage(it) }
        }
        Log.d(TAG, "Wi-Fi Aware layer constructed (primary transport)")
    }

    /** Initiator side data path is up → open a TCP client socket bound to that Aware network. */
    private fun onAwareClientDataPathReady(network: Network, peerIpv6: Inet6Address, port: Int) {
        awareSocketManager?.connectOverNetwork(network, peerIpv6, port)
    }

    /**
     * Listener for the Aware [MeshSocketManager]. Routing decisions go through [MeshRouter]; anything
     * destined for us is handed to the existing brain for full reuse.
     */
    private inner class AwareSocketListener : MeshMessageListener {
        override fun onMessageReceived(protocol: MeshProtocol, fromAddress: String, connId: String) {
            // Learn the neighbour <-> connection mapping from the direct HANDSHAKE.
            if (protocol.type == "HANDSHAKE" && connId.isNotBlank() && protocol.senderId.isNotBlank()) {
                peerToConnId[protocol.senderId] = connId
                connIdToPeer[connId] = protocol.senderId
            }
            val router = meshRouter ?: return
            val json = protocol.toJson()
            // The immediate hop is the peer on the other end of THIS socket (else the sender itself).
            val hopId = connIdToPeer[connId] ?: protocol.senderId
            val forMe = router.onMessageReceived(json, hopId)
            if (forMe) {
                // Reuse every handler, crypto path, DB write and notification in WifiDirectManager.
                wifiDirectManager.onMessageReceived(protocol, "aware:${protocol.senderId}", connId)
            }
        }

        override fun onSocketConnected(address: String, isServer: Boolean) {
            Log.d(TAG, "Aware socket up ($address, server=$isServer) — sending HANDSHAKE")
            sendAwareHandshake()
        }

        override fun onPeerConnected(address: String, isServer: Boolean) {
            Log.d(TAG, "Aware CARAKA peer validated: $address")
        }

        override fun onPeerDisconnected(address: String) {
            Log.d(TAG, "Aware socket disconnected: $address")
        }
    }

    private fun sendAwareHandshake() {
        val socket = awareSocketManager ?: return
        scope.launch {
            val handshake = MeshProtocol(
                type = "HANDSHAKE",
                id = UUID.randomUUID().toString(),
                senderId = identityManager.getPeerId(),
                senderName = identityManager.getDisplayName(),
                senderRole = identityManager.getRole(),
                recipientId = "BROADCAST",
                content = "HANDSHAKE",
                timestamp = System.currentTimeMillis(),
                publicKey = identityManager.getEncryptionPublicKeyBase64(),
                signingKey = identityManager.getSigningPublicKeyBase64()
            )
            socket.sendPayload(handshake.toJson())
        }
    }

    // ========== MeshTransport: delegated state ==========

    override val isWifiP2pEnabled: StateFlow<Boolean> get() = wifiDirectManager.isWifiP2pEnabled
    override val availablePeers: StateFlow<List<WifiP2pDevice>> get() = wifiDirectManager.availablePeers
    override val connectionState: StateFlow<String> get() = wifiDirectManager.connectionState
    override val relayedMessageCount: StateFlow<Int> get() = wifiDirectManager.relayedMessageCount
    override val batteryLevel: StateFlow<Int> get() = wifiDirectManager.batteryLevel
    override val incomingChatAlert: SharedFlow<ChatAlert> get() = wifiDirectManager.incomingChatAlert
    override val peerDiscoverySession: StateFlow<PeerDiscoverySession>
        get() = wifiDirectManager.peerDiscoverySession
    override val isAwareActive: StateFlow<Boolean>
        get() = wifiAwareManager?.isAwareAvailable ?: awareActiveFallback
    override val localTransportStatus: StateFlow<LocalTransportStatus> =
        combine(isWifiP2pEnabled, isAwareActive) { wifiDirectEnabled, awareAvailable ->
            LocalTransportStatus(
                wifiDirectEnabled = wifiDirectEnabled,
                nearbyAvailable = nearbySupported,
                wifiAwareAvailable = awareAvailable
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = LocalTransportStatus(
                wifiDirectEnabled = isWifiP2pEnabled.value,
                nearbyAvailable = nearbySupported,
                wifiAwareAvailable = isAwareActive.value
            )
        )

    // ========== MeshTransport: lifecycle / actions (brain delegated, Aware layered) ==========

    override fun startListening() {
        wifiDirectManager.startListening()
        if (awareSupported) {
            // Publisher (server) listens on the agreed Aware port; subscribers dial in.
            awareSocketManager?.startServer(WifiAwareManager.AWARE_PORT)
            // Pass localPeerId so WifiAwareManager can derive per-pair PSK (replaces hardcoded PSK).
            scope.launch { wifiAwareManager?.startAwareSession(identityManager.getPeerId()) }
        }
        nearby?.let { transport ->
            // Advertise/discover under our peerId so neighbours learn it on connect.
            scope.launch { transport.start(identityManager.getPeerId()) }
        }
    }

    override fun stopListening() {
        wifiDirectManager.stopListening()
        wifiAwareManager?.stop()
        awareSocketManager?.stopAll()
        meshRouter?.stop()
        nearby?.stop()
        peerToConnId.clear()
        connIdToPeer.clear()
    }

    override fun startFallbackDiscovery() = wifiDirectManager.startFallbackDiscovery()
    override fun discoverPeers() = wifiDirectManager.discoverPeers()
    override fun startPeerDiscoverySession() = wifiDirectManager.startPeerDiscoverySession()
    override fun updateDeviceName(name: String) = wifiDirectManager.updateDeviceName(name)
    override fun connectToPeer(device: WifiP2pDevice) = wifiDirectManager.connectToPeer(device)
    override fun connectToWifiDeviceByMac(mac: String) = wifiDirectManager.connectToWifiDeviceByMac(mac)
    override fun requestConnectionToPeer(peerId: String, autoAccept: Boolean) =
        wifiDirectManager.requestConnectionToPeer(peerId, autoAccept)
    override fun setPriorityPeerId(peerId: String) = wifiDirectManager.setPriorityPeerId(peerId)
    override fun sendConnectionAcceptMessage(peerId: String, peerName: String, peerRole: String) =
        wifiDirectManager.sendConnectionAcceptMessage(peerId, peerName, peerRole)

    override fun sendMessage(json: String) = wifiDirectManager.sendMessage(json)
    override fun sendToPeer(peerId: String, json: String) = wifiDirectManager.sendToPeer(peerId, json)
}
