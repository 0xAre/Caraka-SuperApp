package com.example.caraka.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotRelayPolicyTest {

    @Test
    fun clientSendsBroadcastCopyToGateway() {
        val state = HotspotUiState(role = "CLIENT", gatewayIp = "192.168.43.1")

        assertEquals("192.168.43.1", HotspotRelayPolicy.clientBroadcastGateway(state))
        assertNull(HotspotRelayPolicy.clientBroadcastGateway(HotspotUiState(role = "HOST")))
        assertNull(HotspotRelayPolicy.clientBroadcastGateway(HotspotUiState(role = "CLIENT")))
    }

    @Test
    fun directedCourierTrafficCanCrossOneHotspotGateway() {
        assertEquals(2, MeshPolicy.COURIER_DIRECT_TTL)
        assertEquals(1, MeshPolicy.COURIER_BROADCAST_TTL)
    }

    @Test
    fun clientUsesGatewayWhenDestinationIsAnotherStation() {
        val state = HotspotUiState(role = "CLIENT", gatewayIp = "192.168.43.1")

        assertEquals(
            "192.168.43.1",
            HotspotRelayPolicy.clientRelayGateway(state, directIp = "192.168.43.23")
        )
    }

    @Test
    fun clientDoesNotDuplicateTrafficAlreadyAddressedToHost() {
        val state = HotspotUiState(role = "CLIENT", gatewayIp = "192.168.43.1")

        assertNull(HotspotRelayPolicy.clientRelayGateway(state, directIp = "192.168.43.1"))
    }

    @Test
    fun nonClientDoesNotUseGatewayRelay() {
        val state = HotspotUiState(role = "HOST", gatewayIp = "192.168.43.1")

        assertNull(HotspotRelayPolicy.clientRelayGateway(state, directIp = "192.168.43.23"))
    }

    @Test
    fun clientFallsBackToKnownHostIpWhenRouteHasNoGateway() {
        val state = HotspotUiState(role = "CLIENT", hostPeerId = "host-a")

        assertEquals(
            "192.168.43.1",
            HotspotRelayPolicy.clientRelayGateway(
                state = state,
                directIp = "192.168.43.23",
                knownHostIp = "192.168.43.1"
            )
        )
    }

    @Test
    fun hostRelaysDirectedTransitTraffic() {
        val state = HotspotUiState(role = "HOST")

        assertTrue(
            HotspotRelayPolicy.shouldRelayAtHost(
                state = state,
                localPeerId = "host-a",
                senderId = "client-b",
                recipientId = "client-c"
            )
        )
    }

    @Test
    fun hostDoesNotRelayBroadcastOrLocallyAddressedTraffic() {
        val state = HotspotUiState(role = "HOST")

        assertFalse(HotspotRelayPolicy.shouldRelayAtHost(state, "host-a", "client-b", "BROADCAST"))
        assertFalse(HotspotRelayPolicy.shouldRelayAtHost(state, "host-a", "client-b", "host-a"))
    }

    @Test
    fun directedToOtherExcludesBroadcastAndLocalTraffic() {
        assertTrue(HotspotRelayPolicy.isDirectedToOther("client-c", "client-b"))
        assertFalse(HotspotRelayPolicy.isDirectedToOther("BROADCAST", "client-b"))
        assertFalse(HotspotRelayPolicy.isDirectedToOther("client-b", "client-b"))
        assertFalse(HotspotRelayPolicy.isDirectedToOther("", "client-b"))
    }
}
