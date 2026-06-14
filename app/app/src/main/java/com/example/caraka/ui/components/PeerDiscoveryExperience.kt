package com.example.caraka.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.ui.theme.LocalCarakaShapes
import com.example.caraka.ui.theme.LocalStatusColors
import com.example.caraka.viewmodel.MeshNodeUi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PeerDiscoveryExperience(
    peers: List<MeshNodeUi>,
    isSearching: Boolean,
    elapsedSeconds: Int,
    onScanAgain: () -> Unit,
    onPeerClick: (MeshNodeUi) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PeerSearchMap(
            peers = peers,
            isSearching = isSearching,
            modifier = Modifier.fillMaxSize()
        )

        SearchMetricStrip(
            peerCount = peers.size,
            isSearching = isSearching,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        PeerSearchPanel(
            peers = peers,
            isSearching = isSearching,
            elapsedSeconds = elapsedSeconds,
            onScanAgain = onScanAgain,
            onPeerClick = onPeerClick,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PeerSearchMap(
    peers: List<MeshNodeUi>,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "peer-search")
    val pulse by transition.animateFloat(
        initialValue = 0.32f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "search-pulse"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "search-rotation"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color(0xFFF2F6F8))

            val road = Color(0xFFFFFFFF)
            val roadEdge = Color(0xFFDCE5EA)
            val river = Color(0xFFD8EFF8)
            val greenArea = Color(0xFFDDEFE5)

            drawRoundRect(
                color = greenArea,
                topLeft = Offset(size.width * 0.05f, size.height * 0.18f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.38f, size.height * 0.3f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(44.dp.toPx())
            )

            val riverPath = Path().apply {
                moveTo(size.width * 0.8f, 0f)
                cubicTo(
                    size.width * 0.65f, size.height * 0.22f,
                    size.width * 0.94f, size.height * 0.38f,
                    size.width * 0.7f, size.height * 0.64f
                )
            }
            drawPath(riverPath, river, style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round))

            val roads = listOf(
                Path().apply {
                    moveTo(-40f, size.height * 0.32f)
                    cubicTo(
                        size.width * 0.25f, size.height * 0.26f,
                        size.width * 0.63f, size.height * 0.4f,
                        size.width + 40f, size.height * 0.3f
                    )
                },
                Path().apply {
                    moveTo(size.width * 0.18f, 0f)
                    cubicTo(
                        size.width * 0.3f, size.height * 0.23f,
                        size.width * 0.2f, size.height * 0.5f,
                        size.width * 0.42f, size.height
                    )
                },
                Path().apply {
                    moveTo(-20f, size.height * 0.56f)
                    lineTo(size.width + 30f, size.height * 0.76f)
                }
            )
            roads.forEach { path ->
                drawPath(path, roadEdge, style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, road, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
            }

            val center = Offset(size.width / 2f, size.height * 0.44f)
            val maxRadius = minOf(size.width, size.height) * 0.34f
            val coverageRadius = maxRadius * 0.82f
            drawCircle(primary.copy(alpha = 0.08f), coverageRadius, center)
            drawCircle(
                primary.copy(alpha = 0.2f),
                coverageRadius,
                center,
                style = Stroke(1.dp.toPx())
            )

            if (isSearching) {
                drawCircle(
                    primary.copy(alpha = (1f - pulse) * 0.26f),
                    radius = maxRadius * pulse,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                val sweepAngle = Math.toRadians(rotation.toDouble())
                drawLine(
                    color = primary.copy(alpha = 0.25f),
                    start = center,
                    end = Offset(
                        center.x + cos(sweepAngle).toFloat() * coverageRadius,
                        center.y + sin(sweepAngle).toFloat() * coverageRadius
                    ),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            peers.forEachIndexed { index, peer ->
                val angle = (2 * PI * index / peers.size.coerceAtLeast(1)) - PI / 2
                val distance = coverageRadius * (0.58f + (index % 3) * 0.1f)
                val point = Offset(
                    center.x + cos(angle).toFloat() * distance,
                    center.y + sin(angle).toFloat() * distance
                )
                val markerColor = when {
                    peer.isAuthority -> Color(0xFF168A4B)
                    peer.isConnected -> primary
                    else -> outline
                }
                drawCircle(surface, 22.dp.toPx(), point)
                drawCircle(markerColor.copy(alpha = 0.2f), 19.dp.toPx(), point)
                drawCircle(markerColor, 10.dp.toPx(), point)
                drawCircle(Color.White, 3.dp.toPx(), point)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 52.dp)
                .size(66.dp)
                .background(primary.copy(alpha = 0.14f), CircleShape)
                .border(1.dp, primary.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = primary,
                shadowElevation = 2.dp
            ) {
                Icon(
                    Icons.Default.WifiTethering,
                    contentDescription = "Posisi perangkat Anda",
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (peers.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 98.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.network_self_label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.network_signal_estimate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    }
}

@Composable
private fun SearchMetricStrip(
    peerCount: Int,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LocalCarakaShapes.current.lg,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isSearching) Icons.Default.NearMe else Icons.Default.Hub,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isSearching) {
                        stringResource(R.string.network_searching_title)
                    } else {
                        stringResource(R.string.network_found_count, peerCount)
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.network_radio_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "$peerCount",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PeerSearchPanel(
    peers: List<MeshNodeUi>,
    isSearching: Boolean,
    elapsedSeconds: Int,
    onScanAgain: () -> Unit,
    onPeerClick: (MeshNodeUi) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(42.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                        CircleShape
                    )
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.Router,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(11.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            peers.isNotEmpty() -> stringResource(
                                R.string.network_ready_count,
                                peers.size
                            )
                            isSearching -> stringResource(R.string.network_searching_devices)
                            else -> stringResource(R.string.network_no_peer)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (isSearching) {
                            stringResource(
                                R.string.network_scan_elapsed,
                                formatElapsed(elapsedSeconds)
                            )
                        } else {
                            stringResource(R.string.network_peer_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onScanAgain,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 14.dp,
                        vertical = 10.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.network_scan_action))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransportChip(Icons.Default.Bluetooth, "Bluetooth", Modifier.weight(1f))
                TransportChip(
                    Icons.Default.NearMe,
                    stringResource(R.string.network_transport_nearby),
                    Modifier.weight(1f)
                )
                TransportChip(Icons.Default.WifiTethering, "Wi-Fi", Modifier.weight(1f))
            }

            if (peers.isNotEmpty()) {
                peers.take(3).forEach { peer ->
                    PeerResultRow(peer = peer, onClick = { onPeerClick(peer) })
                }
            } else {
                Text(
                    stringResource(R.string.network_no_coordinates),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TransportChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = LocalCarakaShapes.current.md,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PeerResultRow(peer: MeshNodeUi, onClick: () -> Unit) {
    val statusColors = LocalStatusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = if (peer.isAuthority) {
                statusColors.authority.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ) {
            Icon(
                if (peer.isAuthority) Icons.Default.Shield else Icons.Default.Hub,
                contentDescription = null,
                tint = if (peer.isAuthority) statusColors.authority else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(9.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(peer.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${peer.role} · ${peer.hopCount} hop",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            if (peer.isConnected) {
                stringResource(R.string.network_peer_connected)
            } else {
                stringResource(R.string.network_peer_detected)
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (peer.isConnected) statusColors.online else MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatElapsed(seconds: Int): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%02d:%02d".format(minutes, remaining)
}
