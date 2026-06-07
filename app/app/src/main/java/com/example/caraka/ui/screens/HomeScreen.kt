package com.example.caraka.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.network.ConnectivityStatus
import com.example.caraka.ui.components.AlertsBottomSheet
import com.example.caraka.ui.components.EmergencyAlertCard
import com.example.caraka.ui.components.MeshStatusBanner
import com.example.caraka.ui.theme.*
import com.example.caraka.ui.util.rememberHaptics
import com.example.caraka.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel? = null,
    onNavigateToSos: (() -> Unit)? = null,
    onNavigateToAlerts: (() -> Unit)? = null
) {
    val activeAlerts by viewModel?.activeAlerts?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val meshNodeCount by viewModel?.meshNodeCount?.collectAsStateWithLifecycle(initialValue = 1)
        ?: remember { mutableStateOf(1) }
    val connectivity by viewModel?.connectivityStatus?.collectAsStateWithLifecycle(
        initialValue = ConnectivityStatus.MESH_ONLY)
        ?: remember { mutableStateOf(ConnectivityStatus.MESH_ONLY) }
    val connectionState by viewModel?.connectionState?.collectAsStateWithLifecycle(initialValue = "IDLE")
        ?: remember { mutableStateOf("IDLE") }
    val relayed by viewModel?.relayedMessageCount?.collectAsStateWithLifecycle(initialValue = 0)
        ?: remember { mutableStateOf(0) }

    var attackSimActive by remember { mutableStateOf(false) }
    var showAlertsSheet by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    val effectiveConnectivity = if (attackSimActive) ConnectivityStatus.MESH_ONLY else connectivity

    AlertsBottomSheet(
        visible = showAlertsSheet,
        alerts = activeAlerts,
        onDismiss = { showAlertsSheet = false },
        onViewAll = onNavigateToAlerts
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = stringResource(R.string.cd_app_logo),
                            tint = AmberAccent,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.app_name),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (activeAlerts.isNotEmpty()) {
                                Badge(containerColor = DangerRed) {
                                    Text("${activeAlerts.size}", fontSize = 9.sp)
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = {
                            haptics.tick()
                            showAlertsSheet = true
                        }) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = stringResource(R.string.home_alerts_notif),
                                tint = AmberAccent
                            )
                        }
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
            item {
                MeshStatusBanner(
                    connectivityStatus = effectiveConnectivity,
                    nodeCount = meshNodeCount,
                    connectionState = connectionState,
                    isAttackSim = attackSimActive
                )
            }

            item {
                LiveStatsRow(
                    nodeCount = meshNodeCount,
                    sosCount = activeAlerts.size,
                    relayedCount = relayed
                )
            }

            item {
                AnimatedSosButton(onClick = {
                    haptics.heavy()
                    onNavigateToSos?.invoke()
                })
            }

            item {
                AttackSimulatorCard(
                    isActive = attackSimActive,
                    onToggle = {
                        haptics.tick()
                        attackSimActive = !attackSimActive
                    }
                )
            }

            item {
                Text(
                    stringResource(R.string.home_active_alerts),
                    color = AmberAccent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (activeAlerts.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_no_alerts),
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                items(activeAlerts.size) { i ->
                    EmergencyAlertCard(alert = activeAlerts[i])
                }
            }
        }
    }
}

@Composable
private fun LiveStatsRow(nodeCount: Int, sosCount: Int, relayedCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiniStatCard(Icons.Default.Hub, AmberAccent, "$nodeCount", stringResource(R.string.home_stat_nodes), Modifier.weight(1f))
        MiniStatCard(Icons.Default.SettingsInputAntenna, NeonMint, "~${nodeCount * 100}m", stringResource(R.string.home_stat_range), Modifier.weight(1f))
        MiniStatCard(Icons.Default.Warning, DangerRed, "$sosCount", stringResource(R.string.home_stat_alerts), Modifier.weight(1f))
        MiniStatCard(Icons.Default.SwapHoriz, DisasterBlue, "$relayedCount", stringResource(R.string.home_stat_relayed), Modifier.weight(1f))
    }
}

@Composable
private fun MiniStatCard(icon: ImageVector, iconTint: Color, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = AmberAccent.copy(alpha = 0.2f), spotColor = SurfaceDark)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, SurfaceDark, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .semantics { contentDescription = "$label: $value" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = AmberAccent, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AnimatedSosButton(onClick: () -> Unit) {
    val haptics = rememberHaptics()
    var isHolding by remember { mutableStateOf(false) }
    var triggered by remember { mutableStateOf(false) }

    val arcProgress by animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = if (isHolding) tween(2000, easing = LinearEasing)
                        else tween(250, easing = FastOutSlowInEasing),
        label = "hold-arc",
        finishedListener = { value ->
            if (value >= 1f && !triggered) {
                triggered = true
                isHolding = false
                onClick()
            }
        }
    )

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

    val cdSos = stringResource(R.string.cd_sos_hold_emergency)

    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(CircleShape)
            .background(SurfaceDark.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size((200 * outerPulse).dp)
                .clip(CircleShape)
                .background(DangerRed.copy(alpha = if (isHolding) 0.25f else 0.12f))
        )
        Box(
            modifier = Modifier
                .size((170 * innerPulse).dp)
                .clip(CircleShape)
                .border(3.dp, DangerRed.copy(alpha = if (isHolding) 0.9f else 0.5f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(DangerRed)
                .shadow(if (isHolding) 24.dp else 12.dp, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            triggered = false
                            isHolding = true
                            haptics.tick()
                            tryAwaitRelease()
                            if (!triggered) isHolding = false
                        }
                    )
                }
                .semantics {
                    role = Role.Button
                    contentDescription = cdSos
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    stringResource(R.string.home_sos_btn),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = if (isHolding) stringResource(R.string.home_sos_hold)
                           else stringResource(R.string.home_sos_hold_label),
                    color = Color.White.copy(0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (arcProgress > 0f) {
            Canvas(modifier = Modifier.size(164.dp)) {
                drawArc(
                    color = Color.White.copy(alpha = 0.9f),
                    startAngle = -90f,
                    sweepAngle = 360f * arcProgress,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun AttackSimulatorCard(isActive: Boolean, onToggle: () -> Unit) {
    val borderColor = if (isActive) DangerRed else SurfaceDark

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = DangerRed.copy(alpha = 0.2f), spotColor = DangerRed)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.5.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
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
                text = if (isActive) stringResource(R.string.home_attack_sim_active_title)
                       else stringResource(R.string.home_attack_sim_title),
                color = if (isActive) DangerRed else TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = if (isActive) stringResource(R.string.home_attack_sim_active_subtitle)
                       else stringResource(R.string.home_attack_sim_subtitle),
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

@Preview
@Composable
fun PreviewHomeScreen() {
    CarakaTheme {
        HomeScreen()
    }
}
