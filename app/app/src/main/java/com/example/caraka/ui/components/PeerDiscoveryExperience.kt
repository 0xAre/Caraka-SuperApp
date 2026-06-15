@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.caraka.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.network.LocalTransportStatus
import com.example.caraka.ui.theme.CarakaTheme
import com.example.caraka.ui.theme.LocalCarakaShapes
import com.example.caraka.ui.theme.LocalStatusColors
import com.example.caraka.ui.theme.TelegramBlue
import com.example.caraka.ui.theme.TelegramBlueStrong
import com.example.caraka.viewmodel.MeshNodeUi
import com.example.caraka.viewmodel.NetworkDiscoveryPhase
import com.example.caraka.viewmodel.NetworkDiscoveryUiState
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val DiscoverySheetPeekHeight = 184.dp
private val RadarVerticalOffset = 0.dp
private const val GojekCoveragePulseDurationMs = 7_200
private const val RadarWaveCount = 3

@Composable
fun PeerDiscoveryExperience(
    uiState: NetworkDiscoveryUiState,
    elapsedSeconds: Int,
    onScanAgain: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onPeerClick: (MeshNodeUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()

    BackHandler(scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
    }

    BottomSheetScaffold(
        modifier = modifier.fillMaxSize(),
        scaffoldState = scaffoldState,
        sheetPeekHeight = DiscoverySheetPeekHeight,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShadowElevation = 8.dp,
        containerColor = MaterialTheme.colorScheme.background,
        sheetContent = {
            PeerDiscoverySheet(
                uiState = uiState,
                elapsedSeconds = elapsedSeconds,
                onScanAgain = onScanAgain,
                onRequestPermission = onRequestPermission,
                onOpenWifiSettings = onOpenWifiSettings,
                onPeerClick = onPeerClick
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            StylizedDiscoveryMap(Modifier.fillMaxSize())
            RadioRadarOverlay(
                active = uiState.phase == NetworkDiscoveryPhase.Scanning ||
                    uiState.phase == NetworkDiscoveryPhase.Connecting,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = RadarVerticalOffset)
            )
            PeerMarkerLayer(
                peers = uiState.peers,
                modifier = Modifier.fillMaxSize()
            )
            DiscoveryStatusCard(
                uiState = uiState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun StylizedDiscoveryMap(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(Color(0xFFF4F6F3))

        val road = Color.White
        val roadEdge = Color(0xFFD5DEE3)
        val minorRoad = Color(0xFFE4E9E7)
        val river = Color(0xFFCDEAF3)
        val greenArea = Color(0xFFD7EEDB)
        val building = Color(0xFFE5E8E5)
        val buildingEdge = Color(0xFFD8DEDA)

        drawRoundRect(
            color = greenArea,
            topLeft = Offset(size.width * 0.02f, size.height * 0.1f),
            size = Size(size.width * 0.42f, size.height * 0.32f),
            cornerRadius = CornerRadius(28.dp.toPx())
        )
        drawRoundRect(
            color = greenArea.copy(alpha = 0.8f),
            topLeft = Offset(size.width * 0.67f, size.height * 0.48f),
            size = Size(size.width * 0.29f, size.height * 0.2f),
            cornerRadius = CornerRadius(22.dp.toPx())
        )

        val blocks = listOf(
            floatArrayOf(0.08f, 0.46f, 0.13f, 0.06f),
            floatArrayOf(0.23f, 0.44f, 0.11f, 0.08f),
            floatArrayOf(0.37f, 0.48f, 0.15f, 0.07f),
            floatArrayOf(0.56f, 0.16f, 0.12f, 0.08f),
            floatArrayOf(0.7f, 0.2f, 0.15f, 0.07f),
            floatArrayOf(0.52f, 0.62f, 0.13f, 0.07f),
            floatArrayOf(0.14f, 0.68f, 0.17f, 0.06f),
            floatArrayOf(0.34f, 0.71f, 0.12f, 0.07f),
            floatArrayOf(0.76f, 0.72f, 0.14f, 0.06f)
        )
        blocks.forEachIndexed { index, block ->
            drawRoundRect(
                color = building,
                topLeft = Offset(size.width * block[0], size.height * block[1]),
                size = Size(size.width * block[2], size.height * block[3]),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            drawRoundRect(
                color = buildingEdge,
                topLeft = Offset(
                    size.width * block[0] + 3.dp.toPx(),
                    size.height * block[1] + 3.dp.toPx()
                ),
                size = Size(
                    size.width * block[2] - 6.dp.toPx(),
                    size.height * block[3] - 6.dp.toPx()
                ),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(width = if (index % 2 == 0) 1.dp.toPx() else 0.7.dp.toPx())
            )
        }

        val riverPath = Path().apply {
            moveTo(size.width * 0.8f, 0f)
            cubicTo(
                size.width * 0.65f,
                size.height * 0.22f,
                size.width * 0.94f,
                size.height * 0.38f,
                size.width * 0.7f,
                size.height * 0.64f
            )
        }
        drawPath(riverPath, river, style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round))

        val roads = listOf(
            Path().apply {
                moveTo(-40f, size.height * 0.32f)
                cubicTo(
                    size.width * 0.25f,
                    size.height * 0.26f,
                    size.width * 0.63f,
                    size.height * 0.4f,
                    size.width + 40f,
                    size.height * 0.3f
                )
            },
            Path().apply {
                moveTo(size.width * 0.18f, 0f)
                cubicTo(
                    size.width * 0.3f,
                    size.height * 0.23f,
                    size.width * 0.2f,
                    size.height * 0.5f,
                    size.width * 0.42f,
                    size.height
                )
            },
            Path().apply {
                moveTo(-20f, size.height * 0.56f)
                lineTo(size.width + 30f, size.height * 0.76f)
            },
            Path().apply {
                moveTo(size.width * 0.46f, 0f)
                cubicTo(
                    size.width * 0.42f,
                    size.height * 0.24f,
                    size.width * 0.66f,
                    size.height * 0.55f,
                    size.width * 0.62f,
                    size.height
                )
            }
        )
        roads.forEach { path ->
            drawPath(path, roadEdge, style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, road, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
        }

        val minorRoads = listOf(
            Path().apply {
                moveTo(0f, size.height * 0.18f)
                lineTo(size.width * 0.72f, size.height * 0.27f)
            },
            Path().apply {
                moveTo(size.width * 0.08f, size.height * 0.47f)
                lineTo(size.width * 0.92f, size.height * 0.42f)
            },
            Path().apply {
                moveTo(size.width * 0.3f, size.height * 0.1f)
                lineTo(size.width * 0.36f, size.height * 0.86f)
            },
            Path().apply {
                moveTo(size.width * 0.68f, size.height * 0.08f)
                lineTo(size.width * 0.88f, size.height * 0.86f)
            }
        )
        minorRoads.forEach { path ->
            drawPath(path, minorRoad, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            drawPath(
                path,
                Color.White.copy(alpha = 0.82f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun RadioRadarOverlay(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val diameter = minOf(maxWidth, maxHeight) * 0.82f
        if (active) {
            ActiveRadar(diameter)
        } else {
            StaticRadar(diameter)
        }
    }
}

@Composable
private fun ActiveRadar(diameter: Dp) {
    val transition = rememberInfiniteTransition(label = "network-radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(GojekCoveragePulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar-progress"
    )

    RadarCanvas(
        diameter = diameter,
        progress = progress,
        active = true,
        pinLift = pinLiftFor(progress).dp
    )
}

@Composable
private fun StaticRadar(diameter: Dp) {
    RadarCanvas(
        diameter = diameter,
        progress = 0.35f,
        active = false,
        pinLift = 0.dp
    )
}

@Composable
private fun RadarCanvas(
    diameter: Dp,
    progress: Float,
    active: Boolean,
    pinLift: Dp
) {
    Box(
        modifier = Modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f * 0.96f
            drawRadarWaves(
                center = center,
                maxRadius = maxRadius,
                progress = progress,
                active = active
            )
        }
        PeerPinMarker(pinLift)
    }
}

private fun DrawScope.drawRadarWaves(
    center: Offset,
    maxRadius: Float,
    progress: Float,
    active: Boolean
) {
    if (!active) {
        drawCircle(
            color = TelegramBlue.copy(alpha = 0.14f),
            radius = maxRadius * 0.38f,
            center = center
        )
        return
    }

    repeat(RadarWaveCount) { waveIndex ->
        val phaseOffset = waveIndex.toFloat() / RadarWaveCount
        val waveProgress = (progress + phaseOffset) % 1f
        val easedProgress = ((1f - cos(PI * waveProgress)) / 2f).toFloat()
        val visibility = sin(PI * waveProgress).toFloat()
        val alpha = 0.34f * visibility * visibility
        val radius = maxRadius * (0.01f + 0.99f * easedProgress)
        val highlightCenter = center - Offset(radius * 0.2f, radius * 0.22f)

        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFA9EAFF).copy(alpha = alpha),
                0.28f to Color(0xFF5CCAF1).copy(alpha = alpha * 0.98f),
                0.62f to TelegramBlue.copy(alpha = alpha * 0.86f),
                1f to TelegramBlueStrong.copy(alpha = alpha * 0.58f),
                center = highlightCenter,
                radius = radius * 1.18f
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = alpha * 0.24f),
                0.5f to Color(0xFFBDEFFF).copy(alpha = alpha * 0.1f),
                1f to Color.Transparent,
                center = highlightCenter,
                radius = radius * 0.54f
            ),
            radius = radius * 0.54f,
            center = highlightCenter
        )
        drawCircle(
            color = TelegramBlueStrong.copy(alpha = alpha * 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

private fun pinLiftFor(progress: Float): Float {
    val oceanSwell = (1f - cos(progress * 2f * PI).toFloat()) / 2f
    return -2.8f * oceanSwell
}

@Composable
private fun PeerPinMarker(pinLift: Dp) {
    Image(
        painter = painterResource(R.drawable.ill_peer_pin_blue),
        contentDescription = stringResource(R.string.network_self_marker),
        modifier = Modifier
            .size(width = 30.dp, height = 45.dp)
            .offset(y = (-19).dp + pinLift)
    )
}

@Composable
private fun PeerMarkerLayer(
    peers: List<MeshNodeUi>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val authority = LocalStatusColors.current.authority

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Radio peer visualization, ${peers.size} peers"
        }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f + RadarVerticalOffset.toPx())
        val baseRadius = minOf(size.width, size.height) * 0.28f

        peers.forEach { peer ->
            val hash = peer.id.hashCode() and Int.MAX_VALUE
            val angle = ((hash % 360) - 90) * PI / 180.0
            val distance = baseRadius * (0.72f + ((hash / 360) % 24) / 100f)
            val point = Offset(
                center.x + cos(angle).toFloat() * distance,
                center.y + sin(angle).toFloat() * distance
            )
            val markerColor = when {
                peer.isAuthority -> authority
                peer.isConnected -> primary
                else -> outline
            }

            drawCircle(surface, 18.dp.toPx(), point)
            if (peer.isConnected) {
                drawCircle(
                    markerColor.copy(alpha = 0.25f),
                    16.dp.toPx(),
                    point,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
            if (peer.isAuthority) {
                val half = 10.dp.toPx()
                val diamond = Path().apply {
                    moveTo(point.x, point.y - half)
                    lineTo(point.x + half, point.y)
                    lineTo(point.x, point.y + half)
                    lineTo(point.x - half, point.y)
                    close()
                }
                drawPath(diamond, markerColor)
                drawCircle(Color.White, 2.5.dp.toPx(), point)
            } else {
                drawCircle(markerColor.copy(alpha = 0.18f), 14.dp.toPx(), point)
                drawCircle(markerColor, 8.dp.toPx(), point)
                drawCircle(Color.White, 2.5.dp.toPx(), point)
            }
        }
    }
}

@Composable
private fun DiscoveryStatusCard(
    uiState: NetworkDiscoveryUiState,
    modifier: Modifier = Modifier
) {
    val title = discoveryTitle(uiState)
    val subtitle = discoverySubtitle(uiState.phase)
    val icon = when (uiState.phase) {
        NetworkDiscoveryPhase.PermissionRequired -> Icons.Default.Lock
        NetworkDiscoveryPhase.WifiDisabled -> Icons.Default.WifiOff
        NetworkDiscoveryPhase.Failed -> Icons.Default.ErrorOutline
        NetworkDiscoveryPhase.Connected -> Icons.Default.CheckCircle
        NetworkDiscoveryPhase.Results -> Icons.Default.Hub
        else -> Icons.Default.NearMe
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("network_phase_${uiState.phase.name}"),
        shape = LocalCarakaShapes.current.lg,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(23.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "${uiState.peers.size}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PeerDiscoverySheet(
    uiState: NetworkDiscoveryUiState,
    elapsedSeconds: Int,
    onScanAgain: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onPeerClick: (MeshNodeUi) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("network_sheet")
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ill_peer_pin_blue),
                contentDescription = null,
                modifier = Modifier.size(width = 34.dp, height = 48.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(discoveryTitle(uiState), style = MaterialTheme.typography.titleMedium)
                Text(
                    sheetSupportingText(uiState.phase, elapsedSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PrimaryDiscoveryAction(
                phase = uiState.phase,
                onScanAgain = onScanAgain,
                onRequestPermission = onRequestPermission,
                onOpenWifiSettings = onOpenWifiSettings
            )
        }

        TransportStatusStrip(uiState.transportStatus)

        Text(
            stringResource(R.string.network_marker_disclaimer),
            modifier = Modifier.testTag("network_marker_disclaimer"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.peers.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(uiState.peers, key = { it.id }) { peer ->
                    PeerResultRow(peer = peer, onClick = { onPeerClick(peer) })
                }
            }
        }
    }
}

@Composable
private fun PrimaryDiscoveryAction(
    phase: NetworkDiscoveryPhase,
    onScanAgain: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenWifiSettings: () -> Unit
) {
    val action = when (phase) {
        NetworkDiscoveryPhase.PermissionRequired ->
            Triple(R.string.network_allow_action, onRequestPermission, true)
        NetworkDiscoveryPhase.WifiDisabled ->
            Triple(R.string.network_open_wifi_action, onOpenWifiSettings, true)
        NetworkDiscoveryPhase.Scanning ->
            Triple(R.string.network_scanning_action, onScanAgain, false)
        NetworkDiscoveryPhase.Connecting ->
            Triple(R.string.network_connecting_action, onScanAgain, false)
        NetworkDiscoveryPhase.Connected ->
            Triple(R.string.network_mesh_active_action, onScanAgain, false)
        else -> Triple(R.string.network_scan_action, onScanAgain, true)
    }

    Button(
        onClick = action.second,
        enabled = action.third,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("network_primary_action"),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(
            when (phase) {
                NetworkDiscoveryPhase.PermissionRequired -> Icons.Default.Lock
                NetworkDiscoveryPhase.WifiDisabled -> Icons.Default.WifiOff
                NetworkDiscoveryPhase.Scanning -> Icons.Default.Search
                NetworkDiscoveryPhase.Connecting -> Icons.Default.Hub
                NetworkDiscoveryPhase.Connected -> Icons.Default.CheckCircle
                else -> Icons.Default.Refresh
            },
            contentDescription = null,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(action.first), maxLines = 1)
    }
}

@Composable
private fun TransportStatusStrip(status: LocalTransportStatus) {
    val labels = buildList {
        if (status.nearbyAvailable) add(stringResource(R.string.network_transport_nearby))
        if (status.wifiAwareAvailable) add(stringResource(R.string.network_transport_aware))
        if (status.wifiDirectEnabled) add(stringResource(R.string.network_transport_wifi_direct))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LocalCarakaShapes.current.md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Text(
            if (labels.isEmpty()) {
                stringResource(R.string.network_transport_unavailable)
            } else {
                stringResource(R.string.network_transport_ready, labels.joinToString(" · "))
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PeerResultRow(peer: MeshNodeUi, onClick: () -> Unit) {
    val statusColors = LocalStatusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("peer_row_${peer.id}")
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = if (peer.isAuthority) RoundedCornerShape(12.dp) else CircleShape,
            color = if (peer.isAuthority) {
                statusColors.authority.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ) {
            Icon(
                if (peer.isAuthority) Icons.Default.Shield else Icons.Default.Hub,
                contentDescription = null,
                tint = if (peer.isAuthority) {
                    statusColors.authority
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.padding(9.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                peer.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.network_peer_metadata, peer.role, peer.hopCount),
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
            color = if (peer.isConnected) {
                statusColors.online
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
private fun discoveryTitle(uiState: NetworkDiscoveryUiState): String {
    return when (uiState.phase) {
        NetworkDiscoveryPhase.Idle -> stringResource(R.string.network_ready_to_scan)
        NetworkDiscoveryPhase.Scanning -> stringResource(R.string.network_searching_title)
        NetworkDiscoveryPhase.Results ->
            stringResource(R.string.network_found_count, uiState.peers.size)
        NetworkDiscoveryPhase.Connecting -> stringResource(R.string.network_connecting_title)
        NetworkDiscoveryPhase.Connected ->
            stringResource(R.string.network_connected_count, uiState.peers.count { it.isConnected })
        NetworkDiscoveryPhase.NoPeers -> stringResource(R.string.network_no_peer)
        NetworkDiscoveryPhase.PermissionRequired ->
            stringResource(R.string.network_permission_title)
        NetworkDiscoveryPhase.WifiDisabled -> stringResource(R.string.network_wifi_disabled_title)
        NetworkDiscoveryPhase.Failed -> stringResource(R.string.network_failed_title)
    }
}

@Composable
private fun discoverySubtitle(phase: NetworkDiscoveryPhase): String {
    return when (phase) {
        NetworkDiscoveryPhase.PermissionRequired ->
            stringResource(R.string.network_permission_subtitle)
        NetworkDiscoveryPhase.WifiDisabled ->
            stringResource(R.string.network_wifi_disabled_subtitle)
        NetworkDiscoveryPhase.Failed -> stringResource(R.string.network_failed_subtitle)
        NetworkDiscoveryPhase.Connecting ->
            stringResource(R.string.network_connecting_subtitle)
        NetworkDiscoveryPhase.Connected ->
            stringResource(R.string.network_connected_subtitle)
        else -> stringResource(R.string.network_radio_offline)
    }
}

@Composable
private fun sheetSupportingText(
    phase: NetworkDiscoveryPhase,
    elapsedSeconds: Int
): String {
    return when (phase) {
        NetworkDiscoveryPhase.Scanning ->
            stringResource(R.string.network_scan_elapsed, formatElapsed(elapsedSeconds))
        NetworkDiscoveryPhase.Results -> stringResource(R.string.network_tap_peer_hint)
        NetworkDiscoveryPhase.Connecting ->
            stringResource(R.string.network_connecting_subtitle)
        NetworkDiscoveryPhase.Connected ->
            stringResource(R.string.network_connected_subtitle)
        NetworkDiscoveryPhase.PermissionRequired ->
            stringResource(R.string.network_permission_subtitle)
        NetworkDiscoveryPhase.WifiDisabled ->
            stringResource(R.string.network_wifi_disabled_subtitle)
        NetworkDiscoveryPhase.Failed ->
            stringResource(R.string.network_failed_subtitle)
        else -> stringResource(R.string.network_peer_hint)
    }
}

private fun formatElapsed(seconds: Int): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%02d:%02d".format(minutes, remaining)
}

private val PreviewPeers = listOf(
    MeshNodeUi("peer-a", "Tim BPBD Utara", "BPBD", true, 0, true),
    MeshNodeUi("peer-b", "Raka", "CIVILIAN", false, 1, false),
    MeshNodeUi("peer-c", "Pos PMI", "PMI", true, 2, false)
)

@Preview(name = "Network scanning", showBackground = true, widthDp = 393, heightDp = 760)
@Composable
private fun NetworkScanningPreview() {
    CarakaTheme {
        PeerDiscoveryExperience(
            uiState = NetworkDiscoveryUiState(
                phase = NetworkDiscoveryPhase.Scanning,
                transportStatus = LocalTransportStatus(
                    wifiDirectEnabled = true,
                    nearbyAvailable = true
                )
            ),
            elapsedSeconds = 8,
            onScanAgain = {},
            onRequestPermission = {},
            onOpenWifiSettings = {},
            onPeerClick = {}
        )
    }
}

@Preview(name = "Network results", showBackground = true, widthDp = 393, heightDp = 760)
@Composable
private fun NetworkResultsPreview() {
    CarakaTheme {
        PeerDiscoveryExperience(
            uiState = NetworkDiscoveryUiState(
                phase = NetworkDiscoveryPhase.Results,
                peers = PreviewPeers,
                transportStatus = LocalTransportStatus(
                    wifiDirectEnabled = true,
                    nearbyAvailable = true,
                    wifiAwareAvailable = true
                )
            ),
            elapsedSeconds = 0,
            onScanAgain = {},
            onRequestPermission = {},
            onOpenWifiSettings = {},
            onPeerClick = {}
        )
    }
}

@Preview(
    name = "Network permission large text",
    showBackground = true,
    widthDp = 393,
    heightDp = 760,
    fontScale = 1.5f
)
@Composable
private fun NetworkPermissionPreview() {
    CarakaTheme {
        PeerDiscoveryExperience(
            uiState = NetworkDiscoveryUiState(
                phase = NetworkDiscoveryPhase.PermissionRequired
            ),
            elapsedSeconds = 0,
            onScanAgain = {},
            onRequestPermission = {},
            onOpenWifiSettings = {},
            onPeerClick = {}
        )
    }
}
