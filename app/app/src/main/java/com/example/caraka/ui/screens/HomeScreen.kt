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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.caraka.ui.components.CarakaCard
import com.example.caraka.ui.components.CarakaGlassSurface
import com.example.caraka.ui.theme.SpaceGroteskFamily
import com.example.caraka.ui.components.CarakaStatTile
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
    val attackSimActive by viewModel?.attackSimActive?.collectAsStateWithLifecycle(initialValue = false)
        ?: remember { mutableStateOf(false) }

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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.app_name),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            fontFamily = SpaceGroteskFamily
                        )
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (activeAlerts.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
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
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Ambient Glowing Orbs Removed for Clean iOS Look

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = LocalCarakaDimens.current.screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LocalCarakaDimens.current.sectionGap),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    MeshStatusBanner(
                        connectivityStatus = effectiveConnectivity,
                        nodeCount = meshNodeCount,
                        connectionState = connectionState,
                        isAttackSim = attackSimActive,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    CarakaStatTile(
                    nodeCount = meshNodeCount,
                    coverageKm = (meshNodeCount * 0.1f),
                    alarmCount = activeAlerts.size,
                    forwardedCount = relayed
                )
            }

            item {
                com.example.caraka.ui.components.CarakaGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "EMERGENCY CONTROL",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        SosShortcutButton(onClick = {
                            haptics.heavy()
                            onNavigateToSos?.invoke()
                        })
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.home_active_alerts).uppercase(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            if (activeAlerts.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_no_alerts),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(activeAlerts.size) { i ->
                    EmergencyAlertCard(alert = activeAlerts[i])
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
}

@Composable
private fun SosShortcutButton(onClick: () -> Unit) {
    val haptics = rememberHaptics()
    
    val infiniteTransition = rememberInfiniteTransition(label = "sosRipple")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.98f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    val cdSos = stringResource(R.string.cd_sos_hold_emergency)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Solid iOS Red SOS Button
        Box(
            modifier = Modifier
                .size((160 * breatheScale).dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(color = Color.White),
                    onClick = {
                        haptics.tick()
                        onClick()
                    }
                )
                .semantics {
                    role = Role.Button
                    contentDescription = cdSos
                }
                .shadow(elevation = 16.dp, shape = CircleShape, spotColor = com.example.caraka.ui.theme.IosSystemRed.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(com.example.caraka.ui.theme.IosSystemRed),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_sos_btn),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Pilih kategori dan siarkan",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


@Preview
@Composable
fun PreviewHomeScreen() {
    CarakaTheme {
        HomeScreen()
    }
}
