@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.caraka.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
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
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.CarakaTheme
import com.example.caraka.ui.theme.LocalCarakaShapes
import com.example.caraka.ui.theme.LocalStatusColors
import com.example.caraka.viewmodel.MeshNodeUi
import com.example.caraka.viewmodel.NetworkDiscoveryPhase
import com.example.caraka.viewmodel.NetworkDiscoveryUiState
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val DiscoverySheetPeekHeight = 184.dp
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            val focalOffsetY = (maxHeight * 0.02f).coerceIn(4.dp, 20.dp)
            val radarDiameter = minOf(maxWidth, maxHeight) * 0.95f
            val mapEmphasis = mapEmphasisFor(uiState.phase)

            StylizedDiscoveryMap(
                emphasis = mapEmphasis,
                modifier = Modifier.fillMaxSize()
            )
            MapCenterVignette(
                centerYOffset = focalOffsetY,
                scanningActive = uiState.phase == NetworkDiscoveryPhase.Scanning ||
                    uiState.phase == NetworkDiscoveryPhase.Connecting,
                modifier = Modifier.fillMaxSize()
            )
            RadioRadarOverlay(
                phase = uiState.phase,
                peerCount = uiState.peers.size,
                diameter = radarDiameter,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = focalOffsetY)
            )
            PeerMarkerLayer(
                peers = uiState.peers,
                centerYOffset = focalOffsetY,
                scanningActive = uiState.phase == NetworkDiscoveryPhase.Scanning ||
                    uiState.phase == NetworkDiscoveryPhase.Connecting,
                modifier = Modifier.fillMaxSize()
            )
            val showTelemetry = uiState.phase != NetworkDiscoveryPhase.Scanning &&
                uiState.phase != NetworkDiscoveryPhase.Connecting
            if (showTelemetry) {
                RadarTelemetryStrip(
                    uiState = uiState,
                    elapsedSeconds = elapsedSeconds,
                    centerYOffset = focalOffsetY,
                    radarDiameter = radarDiameter,
                    modifier = Modifier.fillMaxSize()
                )
            }
            DiscoveryStatusCard(
                uiState = uiState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            )
        }
    }
}

private fun mapEmphasisFor(phase: NetworkDiscoveryPhase): Float = when (phase) {
    NetworkDiscoveryPhase.Scanning,
    NetworkDiscoveryPhase.Connecting -> 0.88f
    NetworkDiscoveryPhase.Results,
    NetworkDiscoveryPhase.Connected -> 1f
    else -> 0.75f
}

private fun blendTowardMuted(color: Color, emphasis: Float): Color {
    val muted = Color(0xFFE8ECF0)
    return lerp(muted, color, emphasis)
}

@Composable
private fun StylizedDiscoveryMap(emphasis: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val baseCanvas = blendTowardMuted(Color(0xFFF4F6F3), emphasis)
        drawRect(baseCanvas)

        val road = blendTowardMuted(Color.White, emphasis)
        val roadEdge = blendTowardMuted(Color(0xFFD5DEE3), emphasis)
        val minorRoad = blendTowardMuted(Color(0xFFE4E9E7), emphasis)
        val river = blendTowardMuted(Color(0xFFCDEAF3), emphasis)
        val greenArea = blendTowardMuted(Color(0xFFD7EEDB), emphasis * 0.85f + 0.15f)
        val building = blendTowardMuted(Color(0xFFE5E8E5), emphasis * 0.5f + 0.5f)
        val buildingEdge = blendTowardMuted(Color(0xFFD8DEDA), emphasis * 0.5f + 0.5f)

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
                Color.White.copy(alpha = 0.82f * emphasis),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun MapCenterVignette(
    centerYOffset: Dp,
    scanningActive: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f + centerYOffset.toPx())
        val radius = minOf(size.width, size.height) * 0.52f
        val innerStop = if (scanningActive) 0.72f else 0.78f
        val midAlpha = if (scanningActive) 0.10f else 0.12f
        val outerAlpha = if (scanningActive) 0.18f else 0.22f
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    innerStop to Color.Transparent,
                    0.82f to Color.White.copy(alpha = midAlpha),
                    1f to Color.White.copy(alpha = outerAlpha)
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

@Composable
private fun RadarTelemetryStrip(
    uiState: NetworkDiscoveryUiState,
    elapsedSeconds: Int,
    centerYOffset: Dp,
    radarDiameter: Dp,
    modifier: Modifier = Modifier
) {
    val mediumLabel = telemetryMediumLabel(uiState.transportStatus)
    val elapsedLabel = if (uiState.phase == NetworkDiscoveryPhase.Scanning) {
        formatElapsed(elapsedSeconds)
    } else {
        "—"
    }
    val telemetry = stringResource(
        R.string.network_telemetry_strip,
        elapsedLabel,
        mediumLabel,
        uiState.peers.size
    )

    Box(modifier = modifier) {
        Text(
            telemetry,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = centerYOffset + radarDiameter * 0.34f)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            style = CarakaTextStyles.monoData,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
        )
    }
}

@Composable
private fun telemetryMediumLabel(status: LocalTransportStatus): String {
    val labels = buildList {
        if (status.nearbyAvailable) add(stringResource(R.string.network_transport_nearby))
        if (status.wifiAwareAvailable) add(stringResource(R.string.network_transport_aware))
        if (status.wifiDirectEnabled) add(stringResource(R.string.network_transport_wifi_direct))
    }
    return labels.firstOrNull()
        ?: stringResource(R.string.network_transport_unavailable)
}

@Composable
private fun PeerMarkerLayer(
    peers: List<MeshNodeUi>,
    centerYOffset: Dp,
    scanningActive: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val authority = LocalStatusColors.current.authority
    val visualizationDescription = stringResource(
        R.string.network_peer_visualization,
        peers.size
    )

    Box(
        modifier = modifier.semantics {
            contentDescription = visualizationDescription
        }
    ) {
        peers.forEach { peer ->
            AnimatedPeerMarker(
                peer = peer,
                centerYOffset = centerYOffset,
                scanningActive = scanningActive,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AnimatedPeerMarker(
    peer: MeshNodeUi,
    centerYOffset: Dp,
    scanningActive: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val authority = LocalStatusColors.current.authority
    val entrance = remember(peer.id) { Animatable(0f) }
    LaunchedEffect(peer.id) {
        entrance.snapTo(0f)
        entrance.animateTo(1f, tween(durationMillis = 420))
    }
    val scale = entrance.value

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f + centerYOffset.toPx())
        val baseRadius = minOf(size.width, size.height) * 0.28f
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

        if (scanningActive) {
            drawCircle(
                color = markerColor.copy(alpha = 0.10f * scale),
                radius = 22.dp.toPx() * scale,
                center = point
            )
            drawCircle(
                color = markerColor.copy(alpha = 0.16f * scale),
                radius = 16.dp.toPx() * scale,
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        val markerRadius = 18.dp.toPx() * scale
        drawCircle(surface, markerRadius, point)
        if (peer.isConnected) {
            drawCircle(
                markerColor.copy(alpha = 0.25f),
                16.dp.toPx() * scale,
                point,
                style = Stroke(width = 4.dp.toPx())
            )
        }
        if (peer.isAuthority) {
            val half = 10.dp.toPx() * scale
            val diamond = Path().apply {
                moveTo(point.x, point.y - half)
                lineTo(point.x + half, point.y)
                lineTo(point.x, point.y + half)
                lineTo(point.x - half, point.y)
                close()
            }
            drawPath(diamond, markerColor)
            drawCircle(
                markerColor.copy(alpha = 0.35f),
                12.dp.toPx() * scale,
                point,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(Color.White, 2.5.dp.toPx() * scale, point)
        } else {
            drawCircle(markerColor.copy(alpha = 0.18f), 14.dp.toPx() * scale, point)
            drawCircle(markerColor, 8.dp.toPx() * scale, point)
            drawCircle(Color.White, 2.5.dp.toPx() * scale, point)
        }
    }
}

@Composable
private fun DiscoveryStatusCard(
    uiState: NetworkDiscoveryUiState,
    modifier: Modifier = Modifier
) {
    val title = discoveryTitle(uiState)
    val icon = when (uiState.phase) {
        NetworkDiscoveryPhase.PermissionRequired -> Icons.Default.Lock
        NetworkDiscoveryPhase.WifiDisabled -> Icons.Default.WifiOff
        NetworkDiscoveryPhase.Failed -> Icons.Default.ErrorOutline
        NetworkDiscoveryPhase.Connected -> Icons.Default.CheckCircle
        NetworkDiscoveryPhase.Results -> Icons.Default.Hub
        else -> Icons.Default.NearMe
    }
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)

    Surface(
        modifier = modifier
            .border(1.dp, borderColor, LocalCarakaShapes.current.lg)
            .testTag("network_phase_${uiState.phase.name}"),
        shape = LocalCarakaShapes.current.lg,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "${uiState.peers.size}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
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
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(9.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sheetTitle(uiState), style = MaterialTheme.typography.titleMedium)
                Text(
                    discoverySubtitle(uiState, elapsedSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

    val showProgress = phase == NetworkDiscoveryPhase.Scanning ||
        phase == NetworkDiscoveryPhase.Connecting

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
            disabledContainerColor = if (showProgress) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            disabledContentColor = if (showProgress) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(17.dp)
                    .testTag("network_scan_progress"),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                when (phase) {
                    NetworkDiscoveryPhase.PermissionRequired -> Icons.Default.Lock
                    NetworkDiscoveryPhase.WifiDisabled -> Icons.Default.WifiOff
                    NetworkDiscoveryPhase.Connected -> Icons.Default.CheckCircle
                    else -> Icons.Default.Refresh
                },
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
        }
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
private fun sheetTitle(uiState: NetworkDiscoveryUiState): String {
    return when (uiState.phase) {
        NetworkDiscoveryPhase.Scanning -> stringResource(R.string.network_searching_devices)
        NetworkDiscoveryPhase.Connecting -> stringResource(R.string.network_sheet_connecting_title)
        NetworkDiscoveryPhase.Results -> stringResource(R.string.network_sheet_results_title)
        NetworkDiscoveryPhase.Connected -> stringResource(R.string.network_sheet_connected_title)
        NetworkDiscoveryPhase.NoPeers -> stringResource(R.string.network_sheet_no_peers_title)
        NetworkDiscoveryPhase.PermissionRequired ->
            stringResource(R.string.network_sheet_permission_title)
        NetworkDiscoveryPhase.WifiDisabled -> stringResource(R.string.network_sheet_wifi_title)
        NetworkDiscoveryPhase.Failed -> stringResource(R.string.network_sheet_failed_title)
        NetworkDiscoveryPhase.Idle -> stringResource(R.string.network_sheet_idle_title)
    }
}

@Composable
private fun discoverySubtitle(
    uiState: NetworkDiscoveryUiState,
    elapsedSeconds: Int
): String {
    return when (uiState.phase) {
        NetworkDiscoveryPhase.Scanning -> when {
            elapsedSeconds < 3 -> stringResource(R.string.network_scan_stage_preparing)
            elapsedSeconds < 10 -> stringResource(R.string.network_scan_stage_sweeping)
            else -> stringResource(R.string.network_scan_stage_waiting)
        }
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
