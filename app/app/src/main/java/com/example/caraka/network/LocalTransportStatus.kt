package com.example.caraka.network

data class LocalTransportStatus(
    val wifiDirectEnabled: Boolean = false,
    val nearbyAvailable: Boolean = false,
    val wifiAwareAvailable: Boolean = false
) {
    val hasActiveMedium: Boolean
        get() = wifiDirectEnabled || nearbyAvailable || wifiAwareAvailable
}
