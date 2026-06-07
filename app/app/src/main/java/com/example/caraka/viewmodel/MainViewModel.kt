package com.example.caraka.viewmodel

import android.net.wifi.p2p.WifiP2pDevice
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.data.local.entity.MessageEntity
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.network.ConnectivityMonitor
import com.example.caraka.network.ConnectivityStatus
import com.example.caraka.network.MeshTransport
import com.example.caraka.repository.MeshRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeshNodeUi(
    val id: String,
    val name: String,
    val role: String,
    val isAuthority: Boolean,
    val hopCount: Int,
    val isConnected: Boolean
)

class MainViewModel(
    private val repository: MeshRepository,
    private val identityManager: IdentityManager,
    private val transport: MeshTransport,
    private val connectivityMonitor: ConnectivityMonitor
) : ViewModel() {

    // ========== STATE FLOWS (Identity) ==========

    private val _hasIdentity = MutableStateFlow(true)
    val hasIdentity: StateFlow<Boolean> = _hasIdentity

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName

    private val _myRole = MutableStateFlow("")
    val myRole: StateFlow<String> = _myRole

    private val _myPeerId = MutableStateFlow("")
    val myPeerId: StateFlow<String> = _myPeerId

    // ========== STATE FLOWS (DB) ==========

    val connectedPeerCount: StateFlow<Int> = repository.getConnectedPeerCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activeAlerts: StateFlow<List<MessageEntity>> = repository.getRecentAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectedPeers: StateFlow<List<PeerEntity>> = repository.getConnectedPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPeers: StateFlow<List<PeerEntity>> = repository.getAllPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ========== STATE FLOWS (WiFi Direct) ==========

    val isWifiP2pEnabled: StateFlow<Boolean> = transport.isWifiP2pEnabled

    val availablePeers: StateFlow<List<WifiP2pDevice>> = transport.availablePeers

    val connectionState: StateFlow<String> = transport.connectionState

    // NEW: Connection request dialog state
    private val _incomingConnectionRequest = MutableStateFlow<String?>(null)
    val incomingConnectionRequest: StateFlow<String?> = _incomingConnectionRequest

    val meshNodes: StateFlow<List<MeshNodeUi>> = combine(connectedPeers, availablePeers) { connected, available ->
        val connectedMacs = connected.mapNotNull { it.macAddress }.toSet()
        val connectedNodes = connected.map { peer ->
            MeshNodeUi(
                id = peer.id,
                name = peer.displayName,
                role = peer.role,
                isAuthority = peer.isAuthority,
                hopCount = peer.hopCount,
                isConnected = true
            )
        }
        // Only surface CARAKA devices as discovered nodes. Non-CARAKA WiFi-Direct devices
        // (TVs, speakers, other phones' P2P interfaces) are filtered out so they neither
        // clutter the map nor inflate the node count.
        val discoveredNodes = available
            .filterNot { device -> device.deviceAddress in connectedMacs }
            .filter { device ->
                val n = device.deviceName
                n.startsWith("CRK", ignoreCase = true) || n.contains("CARAKA", ignoreCase = true)
            }
            .map { device ->
                MeshNodeUi(
                    id = "wifi:${device.deviceAddress}",
                    name = device.deviceName.ifBlank { "Nearby device" },
                    role = device.statusLabel(),
                    isAuthority = false,
                    hopCount = 0,
                    isConnected = device.status == WifiP2pDevice.CONNECTED
                )
            }

        (connectedNodes + discoveredNodes).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val meshNodeCount: StateFlow<Int> = meshNodes
        .map { nodes -> nodes.size + 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    // ========== STATE FLOWS (Connectivity) ==========

    /** 🟢 ONLINE / 🟡 HYBRID / 🔴 MESH_ONLY */
    val connectivityStatus: StateFlow<ConnectivityStatus> = connectivityMonitor.status

    // ========== STATE FLOWS (Stats) ==========

    /** Messages relayed for other nodes (multi-hop stat). */
    val relayedMessageCount: StateFlow<Int> = transport.relayedMessageCount

    /** Current device battery level (0-100). Updated on each connect attempt. */
    val batteryLevel: StateFlow<Int> = transport.batteryLevel

    /** Fires when a direct chat arrives — drives the floating in-app alert. */
    val incomingChatAlert = transport.incomingChatAlert

    /** Last direct message per peer ID — used for Messages screen preview. */
    val lastMessagesPerPeer: StateFlow<Map<String, com.example.caraka.data.local.entity.MessageEntity>> =
        repository.getAllDirectMessages()
            .map { messages ->
                val perPeer = mutableMapOf<String, com.example.caraka.data.local.entity.MessageEntity>()
                messages.forEach { msg ->
                    val peerId = if (msg.isIncoming) msg.senderId else msg.recipientId
                    perPeer[peerId] = msg
                }
                perPeer
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            // Reset connection state on launch; live peers re-mark themselves ACTIVE_MESH within
            // seconds via beacons/handshake. We intentionally do NOT delete peers here — the
            // gossip-live filter and CARAKA-name filter already keep the node count accurate,
            // and deleting on every restart would cold-start the mesh during testing.
            repository.disconnectAllPeers()
            _hasIdentity.value = identityManager.hasIdentity()
            if (_hasIdentity.value) {
                _displayName.value = identityManager.getDisplayName()
                _myRole.value = identityManager.getRole()
                _myPeerId.value = identityManager.getPeerId()
                transport.updateDeviceName(_displayName.value)
                transport.startFallbackDiscovery()
            }
        }

        // Keep ConnectivityMonitor in sync with visible mesh node count.
        viewModelScope.launch {
            meshNodeCount.collect { count ->
                connectivityMonitor.setMeshActive(count > 1)
            }
        }
    }

    // ========== ACTIONS ==========

    fun setupIdentity(displayName: String, role: String) {
        viewModelScope.launch {
            if (role == IdentityManager.ROLE_CIVILIAN) {
                identityManager.createIdentity(displayName, role)
            } else {
                identityManager.loadAuthorityIdentity(role)
            }
            _displayName.value = identityManager.getDisplayName()
            _myRole.value = identityManager.getRole()
            _myPeerId.value = identityManager.getPeerId()
            transport.updateDeviceName(_displayName.value)
            transport.startFallbackDiscovery()
            _hasIdentity.value = true
        }
    }

    fun clearIdentity() {
        viewModelScope.launch {
            transport.stopListening()
            repository.clearAllData()
            identityManager.clearIdentity()
            chatFlowCache.clear()
            _displayName.value = ""
            _myRole.value = ""
            _myPeerId.value = ""
            _hasIdentity.value = false
        }
    }

    fun startWifiDirect() {
        transport.startListening()
    }

    fun stopWifiDirect() {
        transport.stopListening()
    }

    fun discoverPeers() {
        transport.discoverPeers()
    }

    fun connectToPeer(device: WifiP2pDevice) {
        transport.connectToPeer(device)
    }

    fun broadcastSos(category: String, description: String, lat: Double?, lng: Double?) {
        viewModelScope.launch {
            repository.broadcastSos(category, description, lat, lng)
        }
    }

    /**
     * Flag a message as suspicious.
     * Increments local DB flag count AND broadcasts FLAG packet to mesh.
     */
    fun flagMessage(messageId: String) {
        viewModelScope.launch {
            repository.flagAndBroadcast(messageId)
        }
    }

    private val chatFlowCache = mutableMapOf<String, StateFlow<List<MessageEntity>>>()

    fun getChatMessages(peerId: String): StateFlow<List<MessageEntity>> {
        return chatFlowCache.getOrPut(peerId) {
            repository.getMessagesByPeer(peerId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
    }

    fun sendDirectMessage(recipientId: String, content: String) {
        viewModelScope.launch {
            repository.sendDirectMessage(recipientId, content)
        }
    }

    // ========== QR IDENTITY METHODS ==========

    /**
     * Returns the X25519 encryption public key as Base64 (for QR generation).
     */
    suspend fun getEncPubBase64(): String = identityManager.getEncryptionPublicKeyBase64()

    /**
     * Returns the Ed25519 signing public key as Base64 (for QR generation).
     */
    suspend fun getSignPubBase64(): String = identityManager.getSigningPublicKeyBase64()

    /**
     * Saves a peer scanned via QR as a verified contact in the local database.
     * Marks the peer as verified = true and stores their public keys.
     */
    suspend fun saveVerifiedPeer(payload: com.example.caraka.crypto.QrIdentityManager.QrIdentityPayload) {
        repository.saveVerifiedPeer(
            peerId = payload.peerId,
            displayName = payload.name,
            role = payload.role,
            encPubKey = payload.encPub,
            signPubKey = payload.signPub
        )
    }

    /**
     * After QR scan is confirmed, set this peer as priority connection target.
     * Resets the auto-connect cooldown and triggers an immediate WiFi Direct discovery
     * cycle so the app connects as soon as the peer is discovered.
     */
    fun triggerPriorityConnect(peerId: String) {
        transport.setPriorityPeerId(peerId)
        Log.d("MainViewModel", "Priority connect triggered for peerId: $peerId")
    }

    // ========== NEW: CONNECTION REQUEST METHODS ==========

    /**
     * Show incoming connection request dialog.
     * Called from WifiDirectManager when CONNECTION_REQUEST is received.
     */
    fun showConnectionRequestDialog(peerId: String) {
        _incomingConnectionRequest.value = peerId
    }

    /**
     * Dismiss the connection request dialog.
     */
    fun dismissConnectionRequestDialog() {
        _incomingConnectionRequest.value = null
    }

    /**
     * Accept an incoming connection request.
     * Sends CONNECTION_ACCEPT back to peer and attempts WiFi P2P connection.
     */
    fun acceptConnectionRequest(peerId: String) {
        viewModelScope.launch {
            Log.d("MainViewModel", "Accepting connection request from $peerId")
            repository.updatePeerConnectionState(
                peerId,
                com.example.caraka.data.local.entity.ConnectionStatus.CONNECTED
            )
            // Send accept message via mesh
            val peer = repository.getPeerById(peerId) ?: return@launch
            transport.sendConnectionAcceptMessage(peerId, peer.displayName, peer.role)
        }
        _incomingConnectionRequest.value = null
    }

    /**
     * Reject an incoming connection request.
     * Sends CONNECTION_REJECT back to peer.
     */
    fun rejectConnectionRequest(peerId: String) {
        viewModelScope.launch {
            Log.d("MainViewModel", "Rejecting connection request from $peerId")
            repository.incrementRejectionCount(peerId)
            repository.updatePeerConnectionState(
                peerId,
                com.example.caraka.data.local.entity.ConnectionStatus.DISCOVERED
            )
        }
        _incomingConnectionRequest.value = null
    }

    /**
     * NEW: Request connection to a peer (user clicks [CONNECT] button).
     * Sends CONNECTION_REQUEST message and marks peer as PENDING_REQUEST.
     */
    fun requestConnectionToPeer(peerId: String, autoAccept: Boolean = false) {
        transport.requestConnectionToPeer(peerId, autoAccept)
    }

    /**
     * Manual connect from the network map. A node id of "wifi:<MAC>" is a raw WiFi-Direct
     * device → initiate a P2P connection directly. Any other id is a CARAKA peerId → go
     * through the LAN/registry path (instant connect if reachable, else broadcast request).
     */
    fun connectToNode(node: MeshNodeUi) {
        if (node.id.startsWith("wifi:")) {
            transport.connectToWifiDeviceByMac(node.id.removePrefix("wifi:"))
        } else {
            transport.requestConnectionToPeer(node.id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityMonitor.cleanup()
    }
}

class MainViewModelFactory(
    private val repository: MeshRepository,
    private val identityManager: IdentityManager,
    private val transport: MeshTransport,
    private val connectivityMonitor: ConnectivityMonitor
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, identityManager, transport, connectivityMonitor) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private fun WifiP2pDevice.statusLabel(): String {
    return when (status) {
        WifiP2pDevice.AVAILABLE -> "AVAILABLE"
        WifiP2pDevice.INVITED -> "INVITED"
        WifiP2pDevice.CONNECTED -> "CONNECTED"
        WifiP2pDevice.FAILED -> "FAILED"
        WifiP2pDevice.UNAVAILABLE -> "UNAVAILABLE"
        else -> "DISCOVERED"
    }
}
