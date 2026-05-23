package com.example.garudamesh.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.garudamesh.network.ConnectivityStatus
import com.example.garudamesh.ui.theme.*
import com.example.garudamesh.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel? = null, onNavigateToSos: (() -> Unit)? = null) {
    val activeAlerts by viewModel?.activeAlerts?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val connectedNodes by viewModel?.connectedPeerCount?.collectAsStateWithLifecycle(initialValue = 0)
        ?: remember { mutableStateOf(0) }
    val connectivity by viewModel?.connectivityStatus?.collectAsStateWithLifecycle(
        initialValue = ConnectivityStatus.MESH_ONLY)
        ?: remember { mutableStateOf(ConnectivityStatus.MESH_ONLY) }
    val relayed by viewModel?.relayedMessageCount?.collectAsStateWithLifecycle(initialValue = 0)
        ?: remember { mutableStateOf(0) }

    // Attack Simulator state — when toggled, we pretend the grid is down (UI only)
    var attackSimActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("GARUDA MESH", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = AmberAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ── Connectivity status banner ─────────────────────────────────
            item {
                ConnectivityBanner(
                    status = if (attackSimActive) ConnectivityStatus.MESH_ONLY else connectivity,
                    nodeCount = connectedNodes,
                    isAttackSim = attackSimActive
                )
            }

            // ── Live stats row ─────────────────────────────────────────────
            item {
                LiveStatsRow(
                    nodeCount = connectedNodes + 1,
                    sosCount = activeAlerts.size,
                    relayedCount = relayed
                )
            }

            // ── SOS Button (animated pulsing) ─────────────────────────────
            item {
                AnimatedSosButton(onClick = onNavigateToSos ?: {})
            }

            // ── Attack Simulator card ──────────────────────────────────────
            item {
                AttackSimulatorCard(
                    isActive = attackSimActive,
                    onToggle = { attackSimActive = !attackSimActive }
                )
            }

            // ── Active Alerts ─────────────────────────────────────────────
            item {
                Text("Active Alerts", color = AmberAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth())
            }

            if (activeAlerts.isEmpty()) {
                item {
                    Text("No active alerts nearby.", color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                items(activeAlerts.size) { i ->
                    val alert = activeAlerts[i]
                    AlertCard(
                        title = alert.content,
                        sender = "${alert.senderName} (${alert.senderRole})",
                        time = "Just now",
                        icon = if (alert.sosCategory == "MEDICAL") Icons.Default.LocalHospital else Icons.Default.Warning,
                        iconTint = if (alert.sosCategory == "MEDICAL") DangerRed else WarningYellow
                    )
                }
            }
        }
    }
}

// ─── Connectivity Banner ─────────────────────────────────────────────────────

@Composable
private fun ConnectivityBanner(
    status: ConnectivityStatus,
    nodeCount: Int,
    isAttackSim: Boolean
) {
    val (dot, label, color) = when (status) {
        ConnectivityStatus.ONLINE    -> Triple(ActiveGreen,   "🟢 ONLINE — Mesh Standby",               ActiveGreen)
        ConnectivityStatus.HYBRID    -> Triple(WarningYellow, "🟡 HYBRID — Internet + Mesh Active",     WarningYellow)
        ConnectivityStatus.MESH_ONLY -> Triple(DangerRed,     "🔴 MESH ONLY — Grid Down, We Rise",      DangerRed)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "banner")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dot.copy(alpha = if (status == ConnectivityStatus.MESH_ONLY) alpha else 1f))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (isAttackSim) {
                Text("⚠️ ATTACK SIMULATION ACTIVE", color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("$nodeCount nodes", color = TextSecondary, fontSize = 12.sp)
    }
}

// ─── Live Stats Row ───────────────────────────────────────────────────────────

@Composable
private fun LiveStatsRow(nodeCount: Int, sosCount: Int, relayedCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiniStatCard("🌐", "$nodeCount", "Nodes", Modifier.weight(1f))
        MiniStatCard("📡", "${nodeCount * 100}m", "Range", Modifier.weight(1f))
        MiniStatCard("🆘", "$sosCount", "Alerts", Modifier.weight(1f))
        MiniStatCard("🔀", "$relayedCount", "Relayed", Modifier.weight(1f))
    }
}

@Composable
private fun MiniStatCard(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(value, color = AmberAccent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

// ─── Animated Pulsing SOS Button ─────────────────────────────────────────────

@Composable
private fun AnimatedSosButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos")
    val outerPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "outer"
    )
    val innerPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "inner"
    )

    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(CircleShape)
            .background(SurfaceDark.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring (pulsing)
        Box(
            modifier = Modifier
                .size((200 * outerPulse).dp)
                .clip(CircleShape)
                .background(DangerRed.copy(alpha = 0.12f))
        )
        // Mid ring
        Box(
            modifier = Modifier
                .size((170 * innerPulse).dp)
                .clip(CircleShape)
                .border(3.dp, DangerRed.copy(alpha = 0.5f), CircleShape)
        )
        // Inner button
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(DangerRed)
                .shadow(12.dp, CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(32.dp))
                Text("SOS", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("EMERGENCY", color = Color.White.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Attack Simulator Card ────────────────────────────────────────────────────

@Composable
private fun AttackSimulatorCard(isActive: Boolean, onToggle: () -> Unit) {
    val bgColor = if (isActive) DangerRed.copy(alpha = 0.15f) else SurfaceDark
    val borderColor = if (isActive) DangerRed else SurfaceDark

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onToggle() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.FlashOff else Icons.Default.FlashOn,
            contentDescription = null,
            tint = if (isActive) DangerRed else AmberAccent,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isActive) "⚡ GRID DOWN — MESH ACTIVE" else "ATTACK SIMULATOR",
                color = if (isActive) DangerRed else TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = if (isActive) "Demonstrating offline mesh resilience" else "Simulate infrastructure attack for demo",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = isActive,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DangerRed
            )
        )
    }
}

// ─── Alert Card ───────────────────────────────────────────────────────────────

@Composable
fun AlertCard(
    title: String,
    sender: String,
    time: String,
    icon: ImageVector,
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, iconTint.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sender, color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(time, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

// ─── Old Composables kept for compatibility ───────────────────────────────────

@Composable
fun ConnectionBanner(connectedNodes: Int) {
    ConnectivityBanner(ConnectivityStatus.MESH_ONLY, connectedNodes, false)
}

@Composable
fun SosButtonBig(onClick: () -> Unit) {
    AnimatedSosButton(onClick = onClick)
}

@Composable
fun ActiveAlertsSection(alerts: List<com.example.garudamesh.data.local.entity.MessageEntity>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Active Alerts", color = AmberAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp))
        alerts.forEach { alert ->
            AlertCard(
                title = alert.content,
                sender = "${alert.senderName} (${alert.senderRole})",
                time = "Just now",
                icon = if (alert.sosCategory == "MEDICAL") Icons.Default.LocalHospital else Icons.Default.Warning,
                iconTint = if (alert.sosCategory == "MEDICAL") DangerRed else WarningYellow
            )
        }
    }
}

@Preview
@Composable
fun PreviewHomeScreen() {
    GarudaMeshTheme {
        HomeScreen()
    }
}
