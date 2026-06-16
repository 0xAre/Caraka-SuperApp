@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.caraka.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.network.HotspotUiState
import com.example.caraka.ui.components.CarakaBody
import com.example.caraka.ui.components.CarakaListTitle
import com.example.caraka.ui.components.CarakaTopBarTitle
import com.example.caraka.ui.components.PeerDiscoveryExperience
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.LocalStatusColors
import com.example.caraka.viewmodel.MainViewModel
import com.example.caraka.viewmodel.MeshNodeUi
import com.example.caraka.viewmodel.NetworkDiscoveryPhase
import kotlinx.coroutines.delay

@Composable
fun NetworkScreen(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit = {},
    onOpenWifiSettings: () -> Unit = {}
) {
    val uiState by viewModel.networkDiscoveryUiState.collectAsStateWithLifecycle()
    val hotspotState by viewModel.hotspotState.collectAsStateWithLifecycle()
    var selectedNode by remember { mutableStateOf<MeshNodeUi?>(null) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.startPeerScan()
    }

    LaunchedEffect(uiState.phase, uiState.scanStartedAtMillis) {
        elapsedSeconds = 0
        if (uiState.phase != NetworkDiscoveryPhase.Scanning) return@LaunchedEffect
        val startedAt = uiState.scanStartedAtMillis ?: System.currentTimeMillis()
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1_000L)
                .coerceAtLeast(0L)
                .toInt()
            delay(1_000L)
        }
    }

    selectedNode?.let { selected ->
        val currentNode = uiState.peers.firstOrNull { it.id == selected.id } ?: selected
        NodeDetailBottomSheet(
            node = currentNode,
            connectionInProgress = uiState.phase == NetworkDiscoveryPhase.Connecting,
            onConnect = viewModel::connectToNode,
            onDismiss = { selectedNode = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    CarakaTopBarTitle(
                        title = stringResource(R.string.network_title),
                        subtitle = stringResource(R.string.network_discovery_subtitle)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            EmergencyHotspotCard(
                state = hotspotState,
                onStart = viewModel::startEmergencyHotspot,
                onStop = viewModel::stopEmergencyHotspot,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            PeerDiscoveryExperience(
                uiState = uiState,
                elapsedSeconds = elapsedSeconds,
                onScanAgain = viewModel::startPeerScan,
                onRequestPermission = onRequestPermissions,
                onOpenWifiSettings = onOpenWifiSettings,
                onPeerClick = { selectedNode = it },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Emergency hotspot control — lets ANY device become a router-less AP so many peers converge onto
 * one LAN for true M-to-N multi-peer (Phase 3). Hosting is explicit; joining a neighbour's hotspot
 * happens automatically when its offer is heard.
 */
@Composable
private fun EmergencyHotspotCard(
    state: HotspotUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHost = state.role == "HOST"
    val isClient = state.role == "CLIENT"
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.hotspot_title),
                style = CarakaTextStyles.listTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.hotspot_subtitle),
                style = CarakaTextStyles.listSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.status.isNotBlank()) {
                Text(
                    state.status,
                    style = CarakaTextStyles.statusPrimary,
                    color = if (isHost || isClient) LocalStatusColors.current.online
                            else MaterialTheme.colorScheme.primary
                )
            }
            if (isHost && state.ssid != null) {
                Text(
                    stringResource(R.string.hotspot_ssid, state.ssid),
                    style = CarakaTextStyles.monoData,
                    color = MaterialTheme.colorScheme.onSurface
                )
                state.passphrase?.let { pass ->
                    Text(
                        stringResource(R.string.hotspot_pass, pass),
                        style = CarakaTextStyles.monoData,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { if (isHost) onStop() else onStart() },
                enabled = !isClient,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHost) MaterialTheme.colorScheme.error
                                     else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (isHost) stringResource(R.string.hotspot_stop)
                    else stringResource(R.string.hotspot_start)
                )
            }
        }
    }
}

@Composable
private fun NodeDetailBottomSheet(
    node: MeshNodeUi,
    connectionInProgress: Boolean,
    onConnect: (MeshNodeUi) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var requested by remember(node.id) { mutableStateOf(false) }
    val connecting = requested || connectionInProgress

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CarakaListTitle(node.name)
            CarakaBody(
                stringResource(R.string.network_peer_metadata, node.role, node.hopCount),
                muted = true
            )
            Text(
                if (node.isConnected) {
                    stringResource(R.string.network_mesh_connected)
                } else {
                    stringResource(R.string.network_device_found)
                },
                style = CarakaTextStyles.listSubtitle,
                color = if (node.isConnected) {
                    LocalStatusColors.current.online
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            if (node.isAuthority) {
                Text(
                    stringResource(R.string.network_authority_verified),
                    style = CarakaTextStyles.serviceLabel,
                    color = LocalStatusColors.current.authority
                )
            }

            if (node.id != "SELF" && !node.isConnected) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        requested = true
                        onConnect(node)
                    },
                    enabled = !connecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (connecting) {
                            stringResource(R.string.network_connecting_action)
                        } else {
                            stringResource(R.string.network_connect_action)
                        }
                    )
                }
            }
        }
    }
}
