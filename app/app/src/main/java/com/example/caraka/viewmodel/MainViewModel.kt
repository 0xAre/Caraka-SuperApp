package com.example.caraka.viewmodel

import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.data.local.entity.MessageEntity
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.network.ConnectivityMonitor
import com.example.caraka.network.ConnectivityStatus
import com.example.caraka.network.WifiDirectManager
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
    private val wifiDirectManager: WifiDirectManager,
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

    val isWifiP2pEnabled: StateFlow<Boolean> = wifiDirectManager.isWifiP2pEnabled

    val availablePeers: StateFlow<List<WifiP2pDevice>> = wifiDirectManager.availablePeers

    val connectionState: StateFlow<String> = wifiDirectManager.connectionState

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
        val discoveredNodes = available
            .filterNot { device -> device.deviceAddress in connectedMacs }
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
    val relayedMessageCount: StateFlow<Int> = wifiDirectManager.relayedMessageCount

    /** Fires when a direct chat arrives — drives the floating in-app alert. */
    val incomingChatAlert = wifiDirectManager.incomingChatAlert

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
            repository.disconnectAllPeers()
            _hasIdentity.value = identityManager.hasIdentity()
            if (_hasIdentity.value) {
                _displayName.value = identityManager.getDisplayName()
                _myRole.value = identityManager.getRole()
                _myPeerId.value = identityManager.getPeerId()
                wifiDirectManager.updateDeviceName(_displayName.value)
                wifiDirectManager.startFallbackDiscovery()
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
            wifiDirectManager.updateDeviceName(_displayName.value)
            wifiDirectManager.startFallbackDiscovery()
            _hasIdentity.value = true
        }
    }

    fun clearIdentity() {
        viewModelScope.launch {
            wifiDirectManager.stopListening()
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
        wifiDirectManager.startListening()
    }

    fun stopWifiDirect() {
        wifiDirectManager.stopListening()
    }

    fun discoverPeers() {
        wifiDirectManager.discoverPeers()
    }

    fun connectToPeer(device: WifiP2pDevice) {
        wifiDirectManager.connectToPeer(device)
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

    override fun onCleared() {
        super.onCleared()
        connectivityMonitor.cleanup()
    }
}

class MainViewModelFactory(
    private val repository: MeshRepository,
    private val identityManager: IdentityManager,
    private val wifiDirectManager: WifiDirectManager,
    private val connectivityMonitor: ConnectivityMonitor
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, identityManager, wifiDirectManager, connectivityMonitor) as T
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
