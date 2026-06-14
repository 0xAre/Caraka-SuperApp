package com.example.caraka.ui.util

import androidx.annotation.StringRes
import com.example.caraka.R

enum class ConnectionActivityUi(@StringRes val labelRes: Int) {
    Ready(R.string.connection_ready),
    Searching(R.string.connection_searching),
    Connecting(R.string.connection_connecting),
    Connected(R.string.connection_connected),
    NoPeers(R.string.connection_no_peers),
    PermissionRequired(R.string.connection_permission_required),
    WifiDisabled(R.string.connection_wifi_disabled),
    Failed(R.string.connection_failed)
}

fun String.toConnectionActivityUi(): ConnectionActivityUi = when {
    startsWith("CONNECTED") || this == "MESH_ACTIVE" -> ConnectionActivityUi.Connected
    this == "DISCOVERING" -> ConnectionActivityUi.Searching
    this == "CONNECTING" -> ConnectionActivityUi.Connecting
    this == "NO_PEERS" || this == "DISCONNECTED" -> ConnectionActivityUi.NoPeers
    this == "PERMISSION_MISSING" -> ConnectionActivityUi.PermissionRequired
    this == "WIFI_P2P_DISABLED" -> ConnectionActivityUi.WifiDisabled
    contains("FAILED") || startsWith("CONNECTION_REJECTED") -> ConnectionActivityUi.Failed
    else -> ConnectionActivityUi.Ready
}
