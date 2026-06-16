package com.example.caraka.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiscoveryUiStateTest {

    @Test
    fun rawConnectionStatesMapToExpectedUiPhases() {
        val cases = mapOf(
            "IDLE" to NetworkDiscoveryPhase.Idle,
            "DISCOVERING" to NetworkDiscoveryPhase.Scanning,
            "PEERS_FOUND" to NetworkDiscoveryPhase.Results,
            "CONNECTING" to NetworkDiscoveryPhase.Connecting,
            "CONNECTED" to NetworkDiscoveryPhase.Connected,
            "CONNECTED_GO" to NetworkDiscoveryPhase.Connected,
            "MESH_ACTIVE" to NetworkDiscoveryPhase.Connected,
            "NO_PEERS" to NetworkDiscoveryPhase.NoPeers,
            "PERMISSION_MISSING" to NetworkDiscoveryPhase.PermissionRequired,
            "WIFI_P2P_DISABLED" to NetworkDiscoveryPhase.WifiDisabled,
            "DISCOVERY_FAILED" to NetworkDiscoveryPhase.Failed,
            "CONNECTION_REJECTED:peer-a" to NetworkDiscoveryPhase.Failed
        )

        cases.forEach { (rawState, expected) ->
            assertEquals(
                rawState,
                expected,
                mapNetworkDiscoveryPhase(
                    rawConnectionState = rawState,
                    hasPeers = rawState == "PEERS_FOUND",
                    hasActiveMedium = rawState != "WIFI_P2P_DISABLED",
                    isDiscoverySessionActive = rawState == "DISCOVERING"
                )
            )
        }
    }

    @Test
    fun visiblePeersWinOverIdleState() {
        assertEquals(
            NetworkDiscoveryPhase.Results,
            mapNetworkDiscoveryPhase(
                rawConnectionState = "IDLE",
                hasPeers = true,
                hasActiveMedium = true
            )
        )
    }

    @Test
    fun minimumScanPresentationOnlyDelaysEmptyResult() {
        assertEquals(
            NetworkDiscoveryPhase.Scanning,
            mapNetworkDiscoveryPhase(
                rawConnectionState = "NO_PEERS",
                hasPeers = false,
                hasActiveMedium = true,
                isDiscoverySessionActive = true
            )
        )
        assertEquals(
            NetworkDiscoveryPhase.Results,
            mapNetworkDiscoveryPhase(
                rawConnectionState = "PEERS_FOUND",
                hasPeers = true,
                hasActiveMedium = true,
                isDiscoverySessionActive = true
            )
        )
        assertEquals(
            NetworkDiscoveryPhase.Failed,
            mapNetworkDiscoveryPhase(
                rawConnectionState = "DISCOVERY_FAILED",
                hasPeers = false,
                hasActiveMedium = true,
                isDiscoverySessionActive = true
            )
        )
    }

    @Test
    fun scanCannotRestartDuringBusyOrActiveStates() {
        listOf(
            "CONNECTING",
            "CONNECTED",
            "CONNECTED_GO",
            "CONNECTED_CLIENT",
            "MESH_ACTIVE"
        ).forEach { state ->
            assertFalse(state, canStartPeerScan(state))
        }

        assertTrue(canStartPeerScan("IDLE"))
        assertTrue(canStartPeerScan("DISCOVERING"))
        assertTrue(canStartPeerScan("NO_PEERS"))
        assertTrue(canStartPeerScan("DISCOVERY_FAILED"))
    }
}
