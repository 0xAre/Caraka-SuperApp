package com.example.garudamesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.garudamesh.ui.theme.*
import com.example.garudamesh.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(viewModel: MainViewModel? = null) {
    val connectedNodes by viewModel?.connectedPeerCount?.collectAsStateWithLifecycle(initialValue = 0) ?: remember { mutableStateOf(0) }
    val availablePeers by viewModel?.availablePeers?.collectAsStateWithLifecycle(initialValue = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val connectionState by viewModel?.connectionState?.collectAsStateWithLifecycle(initialValue = "IDLE") ?: remember { mutableStateOf("IDLE") }
    val connectedPeers by viewModel?.connectedPeers?.collectAsStateWithLifecycle(initialValue = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val allPeers by viewModel?.allPeers?.collectAsStateWithLifecycle(initialValue = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "NETWORK STATUS",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel?.discoverPeers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Discover", tint = AmberAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Status Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = if (connectionState.contains("CONNECTED")) ActiveGreen else WarningYellow)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("State: $connectionState", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("$connectedNodes Nodes Active", color = TextSecondary, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Connected Peers DB list
            if (connectedPeers.isNotEmpty()) {
                Text("Connected Mesh Nodes", color = AmberAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(connectedPeers.size) { index ->
                        val peer = connectedPeers[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(peer.displayName, color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text("ID: ${peer.id.take(8)}... | Role: ${peer.role}", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Available WiFi Direct Devices
            Text("Available Devices", color = AmberAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            
            if (availablePeers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No devices found. Tap refresh to discover.", color = TextSecondary)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availablePeers.size) { index ->
                        val device = availablePeers[index]
                        val knownPeer = allPeers.find { it.macAddress == device.deviceAddress }
                        val nameToShow = knownPeer?.displayName ?: device.deviceName ?: "Unknown"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .border(1.dp, AmberAccent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { viewModel?.connectToPeer(device) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(nameToShow, color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text(device.deviceAddress ?: "", color = TextSecondary, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { viewModel?.connectToPeer(device) },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)
                            ) {
                                Text("Connect", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
