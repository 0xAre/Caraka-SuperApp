package com.example.caraka

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.caraka.network.LocalTransportStatus
import com.example.caraka.ui.components.PeerDiscoveryExperience
import com.example.caraka.ui.theme.CarakaTheme
import com.example.caraka.viewmodel.MeshNodeUi
import com.example.caraka.viewmodel.NetworkDiscoveryPhase
import com.example.caraka.viewmodel.NetworkDiscoveryUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NetworkDiscoveryExperienceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scanningStateDisablesRepeatedScan() {
        setDiscoveryContent(NetworkDiscoveryPhase.Scanning)

        composeRule.onNodeWithTag("network_phase_Scanning").assertExists()
        composeRule.onNodeWithTag("network_radar_searching").assertExists()
        composeRule.onNodeWithTag("network_self_marker").assertExists()
        composeRule.onNodeWithTag("network_scan_progress").assertExists()
        composeRule.onNodeWithTag("network_primary_action").assertIsNotEnabled()
        composeRule.onNodeWithTag("network_marker_disclaimer").assertExists()
    }

    @Test
    fun scanningStatusAdvancesThroughDiscoveryStages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var elapsedSeconds by mutableStateOf(0)
        composeRule.setContent {
            CarakaTheme {
                PeerDiscoveryExperience(
                    uiState = NetworkDiscoveryUiState(
                        phase = NetworkDiscoveryPhase.Scanning,
                        transportStatus = LocalTransportStatus(wifiDirectEnabled = true)
                    ),
                    elapsedSeconds = elapsedSeconds,
                    onScanAgain = {},
                    onRequestPermission = {},
                    onOpenWifiSettings = {},
                    onPeerClick = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.network_scan_stage_preparing)).assertExists()
        composeRule.runOnIdle { elapsedSeconds = 5 }
        composeRule.onNodeWithText(context.getString(R.string.network_scan_stage_sweeping)).assertExists()
        composeRule.runOnIdle { elapsedSeconds = 12 }
        composeRule.onNodeWithText(context.getString(R.string.network_scan_stage_waiting)).assertExists()
    }

    @Test
    fun permissionStateInvokesPermissionAction() {
        var requests = 0
        composeRule.setContent {
            CarakaTheme {
                PeerDiscoveryExperience(
                    uiState = NetworkDiscoveryUiState(
                        phase = NetworkDiscoveryPhase.PermissionRequired
                    ),
                    elapsedSeconds = 0,
                    onScanAgain = {},
                    onRequestPermission = { requests++ },
                    onOpenWifiSettings = {},
                    onPeerClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("network_phase_PermissionRequired").assertExists()
        composeRule.onNodeWithTag("network_primary_action")
            .assertIsEnabled()
            .performClick()
        assertEquals(1, requests)
    }

    @Test
    fun expandedSheetShowsAllPeersAndPeerClick() {
        val peers = listOf(
            MeshNodeUi("a", "Peer A", "CIVILIAN", false, 0, false),
            MeshNodeUi("b", "Peer B", "BPBD", true, 1, true),
            MeshNodeUi("c", "Peer C", "PMI", true, 2, false),
            MeshNodeUi("d", "Peer D", "CIVILIAN", false, 1, false)
        )
        var selected: String? = null

        composeRule.setContent {
            CarakaTheme {
                PeerDiscoveryExperience(
                    uiState = NetworkDiscoveryUiState(
                        phase = NetworkDiscoveryPhase.Results,
                        peers = peers,
                        transportStatus = LocalTransportStatus(
                            wifiDirectEnabled = true,
                            nearbyAvailable = true
                        )
                    ),
                    elapsedSeconds = 0,
                    onScanAgain = {},
                    onRequestPermission = {},
                    onOpenWifiSettings = {},
                    onPeerClick = { selected = it.id }
                )
            }
        }

        composeRule.onNodeWithTag("network_sheet").performTouchInput { swipeUp() }
        peers.forEach { peer ->
            composeRule.onNodeWithTag("peer_row_${peer.id}").assertExists()
        }
        composeRule.onNodeWithTag("peer_row_d").performClick()
        assertEquals("d", selected)
    }

    @Test
    fun connectionAndRecoveryStatesExposeCorrectActions() {
        var phase by mutableStateOf(NetworkDiscoveryPhase.Connecting)
        composeRule.setContent {
            CarakaTheme {
                PeerDiscoveryExperience(
                    uiState = NetworkDiscoveryUiState(phase = phase),
                    elapsedSeconds = 0,
                    onScanAgain = {},
                    onRequestPermission = {},
                    onOpenWifiSettings = {},
                    onPeerClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("network_phase_Connecting").assertExists()
        composeRule.onNodeWithTag("network_primary_action").assertIsNotEnabled()

        composeRule.runOnIdle { phase = NetworkDiscoveryPhase.WifiDisabled }
        composeRule.onNodeWithTag("network_phase_WifiDisabled").assertExists()
        composeRule.onNodeWithTag("network_primary_action").assertIsEnabled()

        composeRule.runOnIdle { phase = NetworkDiscoveryPhase.Failed }
        composeRule.onNodeWithTag("network_phase_Failed").assertExists()
        composeRule.onNodeWithTag("network_primary_action").assertIsEnabled()

        composeRule.runOnIdle { phase = NetworkDiscoveryPhase.NoPeers }
        composeRule.onNodeWithTag("network_phase_NoPeers").assertExists()
        composeRule.onNodeWithTag("network_radar_static").assertExists()
        composeRule.onNodeWithTag("network_primary_action").assertIsEnabled()
    }

    private fun setDiscoveryContent(phase: NetworkDiscoveryPhase) {
        composeRule.setContent {
            CarakaTheme {
                PeerDiscoveryExperience(
                    uiState = NetworkDiscoveryUiState(
                        phase = phase,
                        transportStatus = LocalTransportStatus(wifiDirectEnabled = true)
                    ),
                    elapsedSeconds = 3,
                    onScanAgain = {},
                    onRequestPermission = {},
                    onOpenWifiSettings = {},
                    onPeerClick = {}
                )
            }
        }
    }
}
