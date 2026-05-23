package com.example.garudamesh.viewmodel

import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.garudamesh.crypto.IdentityManager
import com.example.garudamesh.data.local.entity.MessageEntity
import com.example.garudamesh.data.local.entity.PeerEntity
import com.example.garudamesh.network.ConnectivityMonitor
import com.example.garudamesh.network.ConnectivityStatus
import com.example.garudamesh.network.WifiDirectManager
import com.example.garudamesh.repository.MeshRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: MeshRepository,
    private val identityManager: IdentityManager,
    private val wifiDirectManager: WifiDirectManager,
    private val connectivityMonitor: ConnectivityMonitor
) : ViewModel() {

    // ========== STATE FLOWS (Identity) ==========

    private val _hasIdentity = kotlinx.coroutines.flow.MutableStateFlow(true)
    val hasIdentity: StateFlow<Boolean> = _hasIdentity

    init {
        viewModelScope.launch {
            _hasIdentity.value = identityManager.hasIdentity()
            if (_hasIdentity.value) {
                wifiDirectManager.updateDeviceName(identityManager.getDisplayName())
            }
        }

        // Keep ConnectivityMonitor in sync with mesh peer count
        viewModelScope.launch {
            connectedPeerCount.collect { count ->
                connectivityMonitor.setMeshActive(count > 0)
            }
        }
    }

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

    // ========== STATE FLOWS (Connectivity) ==========

    /** 🟢 ONLINE / 🟡 HYBRID / 🔴 MESH_ONLY */
    val connectivityStatus: StateFlow<ConnectivityStatus> = connectivityMonitor.status

    // ========== STATE FLOWS (Stats) ==========

    /** Messages relayed for other nodes (multi-hop stat). */
    val relayedMessageCount: StateFlow<Int> = wifiDirectManager.relayedMessageCount

    // ========== ACTIONS ==========

    fun setupIdentity(displayName: String, role: String) {
        viewModelScope.launch {
            if (role == IdentityManager.ROLE_CIVILIAN) {
                identityManager.createIdentity(displayName, role)
            } else {
                identityManager.loadAuthorityIdentity(role)
            }
            wifiDirectManager.updateDeviceName(displayName)
            _hasIdentity.value = true
        }
    }

    fun clearIdentity() {
        viewModelScope.launch {
            identityManager.clearIdentity()
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
