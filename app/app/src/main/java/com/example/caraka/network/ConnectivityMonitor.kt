package com.example.caraka.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Monitors internet connectivity in real-time using NetworkCallback.
 * Emits [ConnectivityStatus] so the UI can display the correct mode badge.
 *
 * Status transitions:
 *   ONLINE    — internet available, mesh idle (standby)
 *   HYBRID    — internet available AND mesh active
 *   MESH_ONLY — no internet; mesh is the only communication channel
 */
enum class ConnectivityStatus {
    ONLINE,
    HYBRID,
    MESH_ONLY
}

class ConnectivityMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(ConnectivityStatus.MESH_ONLY)
    val status: StateFlow<ConnectivityStatus> = _status

    // Tracks whether the mesh is currently active (has ≥1 connected peer)
    private var meshActive = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        refresh()
    }

    /** Called from WifiDirectManager when peer count changes. */
    fun setMeshActive(active: Boolean) {
        meshActive = active
        refresh()
    }

    fun cleanup() {
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (_: Exception) {}
    }

    // ─── private ───────────────────────────────────────────────────────────────

    private fun refresh() {
        val hasInternet = hasValidInternet()
        _status.value = when {
            hasInternet && meshActive -> ConnectivityStatus.HYBRID
            hasInternet              -> ConnectivityStatus.ONLINE
            else                     -> ConnectivityStatus.MESH_ONLY
        }
    }

    private fun hasValidInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
