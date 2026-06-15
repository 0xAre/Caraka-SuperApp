package com.example.caraka.viewmodel

import com.example.caraka.network.LocalTransportStatus

enum class NetworkDiscoveryPhase {
    Idle,
    Scanning,
    Results,
    Connecting,
    Connected,
    NoPeers,
    PermissionRequired,
    WifiDisabled,
    Failed
}

data class NetworkDiscoveryUiState(
    val phase: NetworkDiscoveryPhase = NetworkDiscoveryPhase.Idle,
    val peers: List<MeshNodeUi> = emptyList(),
    val rawConnectionState: String = "IDLE",
    val transportStatus: LocalTransportStatus = LocalTransportStatus(),
    val scanStartedAtMillis: Long? = null
)

fun mapNetworkDiscoveryPhase(
    rawConnectionState: String,
    hasPeers: Boolean,
    hasActiveMedium: Boolean,
    isScanPresentationActive: Boolean = false
): NetworkDiscoveryPhase {
    return when {
        rawConnectionState == "PERMISSION_MISSING" -> NetworkDiscoveryPhase.PermissionRequired
        rawConnectionState == "WIFI_P2P_DISABLED" ||
            (!hasActiveMedium && rawConnectionState == "IDLE") ->
            NetworkDiscoveryPhase.WifiDisabled
        rawConnectionState == "CONNECTING" -> NetworkDiscoveryPhase.Connecting
        rawConnectionState.startsWith("CONNECTED") || rawConnectionState == "MESH_ACTIVE" ->
            NetworkDiscoveryPhase.Connected
        rawConnectionState.contains("FAILED") ||
            rawConnectionState.startsWith("CONNECTION_REJECTED") ->
            NetworkDiscoveryPhase.Failed
        hasPeers || rawConnectionState == "PEERS_FOUND" -> NetworkDiscoveryPhase.Results
        rawConnectionState == "DISCOVERING" || isScanPresentationActive ->
            NetworkDiscoveryPhase.Scanning
        rawConnectionState == "NO_PEERS" || rawConnectionState == "DISCONNECTED" ->
            NetworkDiscoveryPhase.NoPeers
        else -> NetworkDiscoveryPhase.Idle
    }
}

fun canStartPeerScan(rawConnectionState: String): Boolean {
    return rawConnectionState !in setOf(
        "DISCOVERING",
        "CONNECTING",
        "CONNECTED",
        "CONNECTED_GO",
        "CONNECTED_CLIENT",
        "MESH_ACTIVE"
    )
}
