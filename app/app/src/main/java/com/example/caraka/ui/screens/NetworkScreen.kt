@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.caraka.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import com.example.caraka.ui.components.CarakaBody
import com.example.caraka.ui.components.CarakaListTitle
import com.example.caraka.ui.components.CarakaTopBarTitle
import com.example.caraka.ui.components.PeerDiscoveryExperience
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.LocalStatusColors
import com.example.caraka.viewmodel.MainViewModel
import com.example.caraka.viewmodel.MeshNodeUi
import kotlinx.coroutines.delay

@Composable
fun NetworkScreen(viewModel: MainViewModel? = null) {
    val meshNodes by viewModel?.meshNodes?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val connectionState by viewModel?.connectionState?.collectAsStateWithLifecycle(initialValue = "IDLE")
        ?: remember { mutableStateOf("IDLE") }
    var selectedNode by remember { mutableStateOf<MeshNodeUi?>(null) }
    var scanSession by remember { mutableIntStateOf(0) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(viewModel, scanSession) {
        elapsedSeconds = 0
        viewModel?.discoverPeers()
        while (true) {
            delay(1_000)
            elapsedSeconds++
            if (elapsedSeconds % 12 == 0 && meshNodes.isEmpty()) {
                viewModel?.discoverPeers()
            }
        }
    }

    selectedNode?.let { node ->
        NodeDetailBottomSheet(
            node = node,
            onConnect = { viewModel?.connectToNode(it) },
            onDismiss = { selectedNode = null }
        )
    }

    val isSearching = connectionState == "DISCOVERING" ||
        connectionState == "IDLE" ||
        connectionState == "NO_PEERS"

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
        PeerDiscoveryExperience(
            peers = meshNodes,
            isSearching = isSearching && meshNodes.isEmpty(),
            elapsedSeconds = elapsedSeconds,
            onScanAgain = { scanSession++ },
            onPeerClick = { selectedNode = it },
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun NodeDetailBottomSheet(
    node: MeshNodeUi,
    onConnect: (MeshNodeUi) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var requested by remember(node.id) { mutableStateOf(false) }

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
            CarakaBody("${node.role} · ${node.hopCount} hop", muted = true)
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
                    enabled = !requested,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (requested) {
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
