package com.example.caraka.network

import android.net.wifi.p2p.WifiP2pDevice
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport-agnostic contract for the mesh layer.
 *
 * The ViewModel and Repository depend on THIS interface rather than a concrete transport so the
 * underlying physical link (Wi-Fi Aware primary, Wi-Fi Direct fallback) can be swapped without
 * touching UI or persistence. [WifiDirectManager] implements it directly (and remains the fallback
 * + the application "brain"); [MeshManager] is the facade that layers Wi-Fi Aware on top when the
 * device supports it.
 *
 * Every member here already existed on [WifiDirectManager] except [isAwareActive]; the interface is
 * a pure extraction of the surface the rest of the app was already using.
 */
interface MeshTransport {

    // ========== STATE FLOWS ==========

    val isWifiP2pEnabled: StateFlow<Boolean>
    val availablePeers: StateFlow<List<WifiP2pDevice>>
    val connectionState: StateFlow<String>
    val relayedMessageCount: StateFlow<Int>
    val batteryLevel: StateFlow<Int>
    val incomingChatAlert: SharedFlow<ChatAlert>

    /** true when a Wi-Fi Aware (NAN) data path is the active primary transport. */
    val isAwareActive: StateFlow<Boolean>
    val localTransportStatus: StateFlow<LocalTransportStatus>

    // ========== LIFECYCLE / DISCOVERY ==========

    fun startListening()
    fun stopListening()
    fun startFallbackDiscovery()
    fun discoverPeers()
    fun updateDeviceName(name: String)

    // ========== CONNECTION ACTIONS ==========

    fun connectToPeer(device: WifiP2pDevice)
    fun connectToWifiDeviceByMac(mac: String)
    fun requestConnectionToPeer(peerId: String, autoAccept: Boolean = false)
    fun setPriorityPeerId(peerId: String)
    fun sendConnectionAcceptMessage(peerId: String, peerName: String, peerRole: String)

    // ========== MESSAGING ==========

    /** Broadcast a length-prefixed JSON payload to every reachable peer. */
    fun sendMessage(json: String)

    /** Send a JSON payload directed at a specific peerId (unicast, multi-hop aware). */
    fun sendToPeer(peerId: String, json: String)
}
