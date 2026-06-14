@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.caraka.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.network.ConnectivityStatus
import com.example.caraka.ui.components.AlertsBottomSheet
import com.example.caraka.ui.components.CarakaCard
import com.example.caraka.ui.components.CarakaStatTile
import com.example.caraka.ui.components.CarakaBody
import com.example.caraka.ui.components.CarakaListTitle
import com.example.caraka.ui.components.CarakaTopBarTitle
import com.example.caraka.ui.components.EmergencyAlertCard
import com.example.caraka.ui.components.MeshStatusBanner
import com.example.caraka.ui.components.SectionTitle
import com.example.caraka.ui.components.ServiceTile
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.CarakaTheme
import com.example.caraka.ui.theme.LocalCarakaDimens
import com.example.caraka.ui.theme.LocalStatusColors
import com.example.caraka.ui.util.rememberHaptics
import com.example.caraka.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel? = null,
    onNavigateToSos: (() -> Unit)? = null,
    onNavigateToAlerts: (() -> Unit)? = null,
    onNavigateToMessages: (() -> Unit)? = null,
    onNavigateToNetwork: (() -> Unit)? = null,
    onNavigateToProfile: (() -> Unit)? = null,
    onNavigateToQr: (() -> Unit)? = null,
    onNavigateToHelp: (() -> Unit)? = null
) {
    val activeAlerts by viewModel?.activeAlerts?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val meshNodeCount by viewModel?.meshNodeCount?.collectAsStateWithLifecycle(initialValue = 1)
        ?: remember { mutableStateOf(1) }
    val connectivity by viewModel?.connectivityStatus?.collectAsStateWithLifecycle(initialValue = ConnectivityStatus.MESH_ONLY)
        ?: remember { mutableStateOf(ConnectivityStatus.MESH_ONLY) }
    val connectionState by viewModel?.connectionState?.collectAsStateWithLifecycle(initialValue = "IDLE")
        ?: remember { mutableStateOf("IDLE") }
    val relayed by viewModel?.relayedMessageCount?.collectAsStateWithLifecycle(initialValue = 0)
        ?: remember { mutableStateOf(0) }
    val attackSimActive by viewModel?.attackSimActive?.collectAsStateWithLifecycle(initialValue = false)
        ?: remember { mutableStateOf(false) }

    val effectiveConnectivity = if (attackSimActive) ConnectivityStatus.MESH_ONLY else connectivity
    val haptics = rememberHaptics()
    val statusColors = LocalStatusColors.current
    val dimens = LocalCarakaDimens.current
    var showAlertsSheet by remember { mutableStateOf(false) }

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
                        Image(
                            painter = painterResource(R.drawable.ic_caraka_logo),
                            contentDescription = stringResource(R.string.cd_app_logo),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        CarakaTopBarTitle(
                            title = stringResource(R.string.app_name),
                            subtitle = "Pusat komunikasi darurat"
                        )
                    }
                },
                actions = {
                    BadgedBox(
                        modifier = Modifier.padding(end = dimens.screenPadding),
                        badge = {
                            if (activeAlerts.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("${activeAlerts.size}", style = CarakaTextStyles.badge)
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
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                top = 8.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    coverageKm = meshNodeCount * 0.1f,
                    alarmCount = activeAlerts.size,
                    forwardedCount = relayed
                )
            }

            item { SectionTitle("Layanan cepat") }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ServiceTile(Icons.Default.Warning, "SOS", MaterialTheme.colorScheme.error, { onNavigateToSos?.invoke() }, Modifier.weight(1f), R.drawable.ill_service_sos)
                        ServiceTile(Icons.AutoMirrored.Filled.Message, "Pesan", MaterialTheme.colorScheme.primary, { onNavigateToMessages?.invoke() }, Modifier.weight(1f), R.drawable.ill_service_message)
                        ServiceTile(Icons.Default.WifiTethering, "Jaringan", MaterialTheme.colorScheme.primary, { onNavigateToNetwork?.invoke() }, Modifier.weight(1f), R.drawable.ill_service_network)
                        ServiceTile(Icons.Default.QrCodeScanner, "Scan QR", MaterialTheme.colorScheme.primary, { onNavigateToQr?.invoke() }, Modifier.weight(1f), R.drawable.ill_service_qr)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ServiceTile(Icons.Default.Notifications, "Alarm", MaterialTheme.colorScheme.tertiary, { onNavigateToAlerts?.invoke() }, Modifier.weight(1f), R.drawable.ill_service_alarm)
                        ServiceTile(Icons.Default.Shield, "Identitas", statusColors.authority, { onNavigateToProfile?.invoke() }, Modifier.weight(1f), R.drawable.ill_service_identity)
                        ServiceTile(Icons.Default.HelpOutline, "Bantuan", MaterialTheme.colorScheme.primary, { onNavigateToHelp?.invoke() }, Modifier.weight(1f), R.drawable.ill_service_help)
                        ServiceTile(Icons.Default.Bolt, "Simulator", MaterialTheme.colorScheme.primary, { onNavigateToProfile?.invoke() }, Modifier.weight(1f), R.drawable.ill_service_simulator)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Alarm aktif", modifier = Modifier.weight(1f))
                    if (activeAlerts.isNotEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                "${activeAlerts.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = CarakaTextStyles.badge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Lihat semua",
                            modifier = Modifier.clickable { onNavigateToAlerts?.invoke() },
                            style = CarakaTextStyles.buttonLabel,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (activeAlerts.isEmpty()) {
                item {
                    CarakaCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = statusColors.online.copy(alpha = 0.12f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Default.Hub,
                                    null,
                                    tint = statusColors.online,
                                    modifier = Modifier.padding(11.dp)
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Column {
                                CarakaListTitle("Tidak ada alarm aktif")
                                CarakaBody(
                                    "Jaringan akan menampilkan siaran darurat di sini.",
                                    muted = true
                                )
                            }
                        }
                    }
                }
            } else {
                items(activeAlerts.take(3), key = { it.id }) { alert ->
                    EmergencyAlertCard(alert)
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewHomeScreen() {
    CarakaTheme { HomeScreen() }
}
