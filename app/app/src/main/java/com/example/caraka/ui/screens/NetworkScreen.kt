@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.caraka.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.ui.components.CarakaCard
import com.example.caraka.ui.components.EnterpriseMenuRow
import com.example.caraka.ui.components.SectionTitle
import com.example.caraka.ui.theme.LocalStatusColors
import com.example.caraka.viewmodel.MainViewModel
import com.example.caraka.viewmodel.MeshNodeUi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NetworkScreen(viewModel: MainViewModel? = null) {
    val meshNodes by viewModel?.meshNodes?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val connectionState by viewModel?.connectionState?.collectAsStateWithLifecycle(initialValue = "IDLE")
        ?: remember { mutableStateOf("IDLE") }
    val meshNodeCount by viewModel?.meshNodeCount?.collectAsStateWithLifecycle(initialValue = 1)
        ?: remember { mutableStateOf(1) }
    val relayed by viewModel?.relayedMessageCount?.collectAsStateWithLifecycle(initialValue = 0)
        ?: remember { mutableStateOf(0) }
    var selectedNode by remember { mutableStateOf<MeshNodeUi?>(null) }

    LaunchedEffect(viewModel) { viewModel?.discoverPeers() }

    selectedNode?.let { node ->
        NodeDetailBottomSheet(
            node = node,
            onConnect = { viewModel?.connectToNode(it) },
            onDismiss = { selectedNode = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.network_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Pantau koneksi dan perangkat di sekitar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel?.discoverPeers() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cd_scan_btn),
                            tint = MaterialTheme.colorScheme.primary
                        )
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                NetworkSummary(
                    peerCount = (meshNodeCount - 1).coerceAtLeast(0),
                    relayed = relayed,
                    connectionState = connectionState
                )
            }

            item { SectionTitle("Topologi jaringan") }

            item {
                CarakaCard(modifier = Modifier.fillMaxWidth()) {
                    CleanTopologyGraph(
                        peers = meshNodes,
                        isScanning = connectionState == "DISCOVERING",
                        onNodeTap = { selectedNode = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .padding(12.dp)
                    )
                }
            }

            if (meshNodes.isEmpty()) {
                item {
                    CarakaCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(11.dp)
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text("Mencari perangkat", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Buka tab Jaringan pada perangkat CARAKA lain dan dekatkan perangkat.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                item { SectionTitle("Perangkat terdeteksi") }
                item {
                    CarakaCard(modifier = Modifier.fillMaxWidth()) {
                        meshNodes.forEachIndexed { index, node ->
                            EnterpriseMenuRow(
                                icon = if (node.isAuthority) Icons.Default.Shield else Icons.Default.Hub,
                                title = node.name,
                                subtitle = "${node.role} · ${if (node.isConnected) "Terhubung" else "Terdeteksi"} · ${node.hopCount} hop",
                                iconColor = when {
                                    node.isAuthority -> LocalStatusColors.current.authority
                                    node.hopCount > 0 -> LocalStatusColors.current.relay
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                onClick = { selectedNode = node },
                                showDivider = index != meshNodes.lastIndex
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkSummary(peerCount: Int, relayed: Int, connectionState: String) {
    CarakaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryValue("$peerCount", "Peer", MaterialTheme.colorScheme.primary)
            SummaryValue(if (peerCount > 0) "Baik" else "Siaga", "Kesehatan", LocalStatusColors.current.online)
            SummaryValue("$relayed", "Relay", MaterialTheme.colorScheme.secondary)
            SummaryValue(connectionState.lowercase().replaceFirstChar { it.uppercase() }, "Aktivitas", MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun SummaryValue(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CleanTopologyGraph(
    peers: List<MeshNodeUi>,
    isScanning: Boolean,
    onNodeTap: (MeshNodeUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val status = LocalStatusColors.current
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(52, 64, 84)
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val positions = remember(peers) {
        peers.mapIndexed { index, node ->
            val angle = (2 * PI * index / peers.size.coerceAtLeast(1)) - PI / 2
            node to Offset(cos(angle).toFloat(), sin(angle).toFloat())
        }
    }

    Box(modifier = modifier.background(surface), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(positions) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = minOf(size.width, size.height) * 0.34f
                        positions.forEach { (node, unit) ->
                            val point = Offset(center.x + unit.x * radius, center.y + unit.y * radius)
                            if ((tap - point).getDistance() <= 32.dp.toPx()) onNodeTap(node)
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(size.width, size.height) * 0.34f

            positions.forEach { (_, unit) ->
                val point = Offset(center.x + unit.x * radius, center.y + unit.y * radius)
                drawLine(outline.copy(alpha = 0.65f), center, point, 2.dp.toPx())
            }

            drawCircle(primary.copy(alpha = 0.12f), 34.dp.toPx(), center)
            drawCircle(primary, 18.dp.toPx(), center)
            drawCircle(Color.White, 6.dp.toPx(), center)
            drawIntoCanvas {
                it.nativeCanvas.drawText("Anda", center.x, center.y + 48.dp.toPx(), labelPaint)
            }

            positions.forEach { (node, unit) ->
                val point = Offset(center.x + unit.x * radius, center.y + unit.y * radius)
                val color = when {
                    node.isAuthority -> status.authority
                    node.hopCount > 0 -> status.relay
                    !node.isConnected -> outline
                    else -> primary
                }
                drawCircle(color.copy(alpha = 0.12f), 28.dp.toPx(), point)
                drawCircle(color, 14.dp.toPx(), point)
                drawIntoCanvas {
                    it.nativeCanvas.drawText(node.name.take(12), point.x, point.y + 40.dp.toPx(), labelPaint)
                }
            }
        }

        if (peers.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Hub, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isScanning) "Memindai jaringan..." else "Belum ada peer",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
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
            Text(node.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${node.role} · ${node.hopCount} hop",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (node.isConnected) "Terhubung ke mesh" else "Perangkat ditemukan",
                style = MaterialTheme.typography.bodyMedium,
                color = if (node.isConnected) LocalStatusColors.current.online else MaterialTheme.colorScheme.tertiary
            )
            if (node.isAuthority) {
                Text(
                    "Identitas otoritas terverifikasi",
                    style = MaterialTheme.typography.labelLarge,
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (requested) "Menghubungkan..." else "Hubungkan")
                }
            }
        }
    }
}
