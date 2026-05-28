package com.example.caraka.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel? = null) {
    val displayName by viewModel?.displayName?.collectAsStateWithLifecycle(initialValue = "") ?: remember { mutableStateOf("") }
    val myRole by viewModel?.myRole?.collectAsStateWithLifecycle(initialValue = "") ?: remember { mutableStateOf("") }
    val myPeerId by viewModel?.myPeerId?.collectAsStateWithLifecycle(initialValue = "") ?: remember { mutableStateOf("") }
    val meshNodeCount by viewModel?.meshNodeCount?.collectAsStateWithLifecycle(initialValue = 1) ?: remember { mutableStateOf(1) }
    val relayed by viewModel?.relayedMessageCount?.collectAsStateWithLifecycle(initialValue = 0) ?: remember { mutableStateOf(0) }
    val connectedPeerCount by viewModel?.connectedPeerCount?.collectAsStateWithLifecycle(initialValue = 0) ?: remember { mutableStateOf(0) }

    var showResetDialog by remember { mutableStateOf(false) }

    val isAuthority = myRole in listOf("BPBD", "POLRI", "PMI")
    val roleColor = when (myRole) {
        "BPBD" -> DisasterBlue
        "POLRI" -> AmberAccent
        "PMI" -> DangerRed
        else -> NeonMint
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Identity Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = roleColor.copy(alpha = 0.3f), spotColor = SurfaceDark)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassSurface)
                    .border(1.dp, roleColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(roleColor.copy(alpha = 0.15f))
                            .border(2.dp, roleColor.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAuthority) Icons.Default.Shield else Icons.Default.Person,
                            contentDescription = null,
                            tint = roleColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = displayName.ifBlank { "—" },
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(roleColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(myRole.ifBlank { "CIVILIAN" }, color = roleColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (isAuthority) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Verified, contentDescription = "Verified", tint = roleColor, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                if (myPeerId.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(12.dp))
                    Text("Peer ID", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = myPeerId.take(32) + if (myPeerId.length > 32) "…" else "",
                        color = TextPrimary.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            // Network Stats Card
            Text("Network Stats", color = AmberAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsStatChip("Nodes", "$meshNodeCount", Icons.Default.Hub, AmberAccent, Modifier.weight(1f))
                SettingsStatChip("Peers", "$connectedPeerCount", Icons.Default.People, NeonMint, Modifier.weight(1f))
                SettingsStatChip("Relayed", "$relayed", Icons.Default.SwapHoriz, DisasterBlue, Modifier.weight(1f))
            }

            // App Info Card
            Text("About", color = AmberAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassSurface)
                    .border(1.dp, SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsInfoRow(Icons.Default.Info, "App", "CARAKA Mesh v1.0")
                SettingsInfoRow(Icons.Default.WifiTethering, "Protocol", "WiFi Direct + LAN Broadcast")
                SettingsInfoRow(Icons.Default.Lock, "Encryption", "X25519 + Ed25519 (Lazysodium)")
                SettingsInfoRow(Icons.Default.Router, "Relay", "Multi-hop TTL mesh routing")
            }

            // Danger Zone
            Text("Danger Zone", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = DangerRed)
                Spacer(Modifier.width(8.dp))
                Text("Reset Identity & Logout", color = DangerRed, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed) },
            title = { Text("Reset Identity?", color = TextPrimary) },
            text = {
                Text(
                    "This will delete your cryptographic identity and all local data. You will be logged out.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.clearIdentity()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("Reset", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun SettingsStatChip(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurface)
            .border(1.dp, SurfaceDark, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun SettingsInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = TextSecondary, fontSize = 11.sp)
            Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
