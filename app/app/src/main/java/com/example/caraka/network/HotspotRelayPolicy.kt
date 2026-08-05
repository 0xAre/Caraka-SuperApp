package com.example.caraka.network

/** Pure routing decisions for the LocalOnlyHotspot application-level relay path. */
internal object HotspotRelayPolicy {

    /**
     * Soft-AP implementations commonly isolate broadcast traffic from client stations. A client
     * therefore sends broadcast-style mesh packets (most importantly LAN_DISCOVERY) to the gateway
     * as an additional unicast copy so the host can learn and fan them out to the other stations.
     */
    fun clientBroadcastGateway(state: HotspotUiState?): String? {
        if (state?.role != "CLIENT") return null
        return state.gatewayIp?.takeIf { it.isNotBlank() }
    }

    /**
     * A hotspot client sends a second copy of directed traffic to the AP/host. This copy is only
     * needed when the real destination is another client; traffic addressed to the host already
     * reaches the gateway directly.
     */
    fun clientRelayGateway(
        state: HotspotUiState?,
        directIp: String?,
        knownHostIp: String? = null
    ): String? {
        if (state?.role != "CLIENT") return null
        val gateway = state.gatewayIp?.takeIf { it.isNotBlank() }
            ?: knownHostIp?.takeIf { it.isNotBlank() }
            ?: return null
        return gateway.takeUnless { it == directIp }
    }

    /** The hotspot host relays only directed transit traffic, never broadcasts or its own traffic. */
    fun shouldRelayAtHost(
        state: HotspotUiState?,
        localPeerId: String,
        senderId: String,
        recipientId: String
    ): Boolean {
        if (state?.role != "HOST") return false
        if (senderId.isBlank() || senderId == localPeerId) return false
        if (recipientId.isBlank() || recipientId == "BROADCAST") return false
        return recipientId != localPeerId
    }

    fun isDirectedToOther(recipientId: String, localPeerId: String): Boolean {
        return recipientId.isNotBlank() && recipientId != "BROADCAST" && recipientId != localPeerId
    }
}
