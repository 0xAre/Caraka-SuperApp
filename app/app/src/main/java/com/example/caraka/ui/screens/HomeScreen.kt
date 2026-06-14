@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.caraka.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.network.ConnectivityStatus
import com.example.caraka.ui.components.AlertsBottomSheet
import com.example.caraka.ui.components.CarakaCard
import com.example.caraka.ui.components.CarakaStatTile
import com.example.caraka.ui.components.EmergencyAlertCard
import com.example.caraka.ui.components.MeshStatusBanner
import com.example.caraka.ui.components.SectionTitle
import com.example.caraka.ui.components.ServiceTile
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
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = stringResource(R.string.cd_app_logo),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Pusat komunikasi darurat",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (activeAlerts.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("${activeAlerts.size}")
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
                CarakaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            ServiceTile(Icons.Default.Warning, "SOS", MaterialTheme.colorScheme.error, { onNavigateToSos?.invoke() }, Modifier.weight(1f))
                            ServiceTile(Icons.AutoMirrored.Filled.Message, "Pesan", MaterialTheme.colorScheme.primary, { onNavigateToMessages?.invoke() }, Modifier.weight(1f))
                            ServiceTile(Icons.Default.Map, "Jaringan", MaterialTheme.colorScheme.secondary, { onNavigateToNetwork?.invoke() }, Modifier.weight(1f))
                            ServiceTile(Icons.Default.QrCodeScanner, "Scan QR", statusColors.online, { onNavigateToQr?.invoke() }, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            ServiceTile(Icons.Default.Notifications, "Alarm", MaterialTheme.colorScheme.tertiary, { onNavigateToAlerts?.invoke() }, Modifier.weight(1f))
                            ServiceTile(Icons.Default.Shield, "Identitas", statusColors.authority, { onNavigateToProfile?.invoke() }, Modifier.weight(1f))
                            ServiceTile(Icons.Default.HelpOutline, "Bantuan", MaterialTheme.colorScheme.primary, { onNavigateToHelp?.invoke() }, Modifier.weight(1f))
                            ServiceTile(Icons.Default.Bolt, "Simulator", MaterialTheme.colorScheme.error, { onNavigateToProfile?.invoke() }, Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                EmergencyActionCard(onClick = {
                    haptics.heavy()
                    onNavigateToSos?.invoke()
                })
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Alarm aktif", modifier = Modifier.weight(1f))
                    if (activeAlerts.isNotEmpty()) {
                        Text(
                            "Lihat semua",
                            modifier = Modifier.clickable { onNavigateToAlerts?.invoke() },
                            style = MaterialTheme.typography.labelLarge,
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
                                Text(
                                    "Tidak ada alarm aktif",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Jaringan akan menampilkan siaran darurat di sini.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun EmergencyActionCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(13.dp)
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Kirim SOS darurat",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Pilih kategori, sertakan lokasi, lalu tahan untuk menyiarkan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f)
                )
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Buka")
            }
        }
    }
}

@Preview
@Composable
private fun PreviewHomeScreen() {
    CarakaTheme { HomeScreen() }
}
