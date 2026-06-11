package com.example.caraka.ui.screens

import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MeshNodeUi
import com.example.caraka.viewmodel.MainViewModel
import com.example.caraka.ui.components.CarakaCard
import com.example.caraka.ui.components.CarakaGlassSurface
import com.example.caraka.ui.theme.SpaceGroteskFamily
import kotlinx.coroutines.delay
import kotlin.math.*

// ─── Network State ───────────────────────────────────────────────────────────

enum class NetworkActivity { IDLE, SCANNING, CONNECTED }

sealed class NetworkState(val activity: NetworkActivity) {
    object Idle : NetworkState(NetworkActivity.IDLE)
    object Scanning : NetworkState(NetworkActivity.SCANNING)
    data class Connected(val peers: List<MeshNodeUi>) : NetworkState(NetworkActivity.CONNECTED)
}

// ─── Data model for a graph node ─────────────────────────────────────────────

private data class GraphNode(
    val id: String,
    val name: String,
    val role: String,
    val isAuthority: Boolean,
    val hopCount: Int,
    val isConnected: Boolean,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(viewModel: MainViewModel? = null) {
    val meshNodes by viewModel?.meshNodes?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val connectionState by viewModel?.connectionState?.collectAsStateWithLifecycle(initialValue = "IDLE")
        ?: remember { mutableStateOf("IDLE") }
    val meshNodeCount by viewModel?.meshNodeCount?.collectAsStateWithLifecycle(initialValue = 1)
        ?: remember { mutableStateOf(1) }
    val activeAlerts by viewModel?.activeAlerts?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val relayed by viewModel?.relayedMessageCount?.collectAsStateWithLifecycle(initialValue = 0)
        ?: remember { mutableStateOf(0) }

    val networkState = remember(connectionState, meshNodes) {
        when {
            meshNodes.isNotEmpty() -> NetworkState.Connected(meshNodes)
            connectionState == "DISCOVERING" -> NetworkState.Scanning
            else -> NetworkState.Idle
        }
    }

    var selectedNode by remember { mutableStateOf<MeshNodeUi?>(null) }

    LaunchedEffect(viewModel) {
        viewModel?.discoverPeers()
    }

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
                title = { Text(stringResource(R.string.network_title), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel?.discoverPeers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_scan_btn), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Force-directed network graph
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                MeshNetworkGraph(
                    networkState = networkState,
                    onNodeTap = { node -> selectedNode = node },
                    modifier = Modifier.fillMaxSize()
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = meshNodeCount == 1,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    CarakaCard(
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Minta perangkat lain membuka tab Jaringan",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            val hasWeakSignal = meshNodes.any { it.hopCount > 0 }
            NetworkStatsPanel(
                nodeCount = meshNodeCount,
                hasWeakSignal = hasWeakSignal,
                networkState = networkState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )
        }
    }
}

// ─── Force-Directed Graph Canvas ─────────────────────────────────────────────


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDetailBottomSheet(
    node: MeshNodeUi,
    onConnect: (MeshNodeUi) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var requested by remember(node.id) { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.example.caraka.ui.theme.SurfaceLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(node.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.network_node_role, node.role), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 14.sp)
            Text(
                stringResource(
                    if (node.isConnected) R.string.network_node_connected else R.string.network_node_discovered
                ),
                color = if (node.isConnected) LocalStatusColors.current.online else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            if (node.hopCount > 0) {
                Text(stringResource(R.string.network_node_hops, node.hopCount), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            if (node.isAuthority) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.network_node_authority), color = LocalStatusColors.current.online, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            // Manual connect action — only for peers that are not the local node and not yet connected.
            if (node.id != "SELF" && !node.isConnected) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        requested = true
                        onConnect(node)
                    },
                    enabled = !requested,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(
                        if (requested) "Menghubungkan…" else "Hubungkan",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MeshNetworkGraph(
    networkState: NetworkState,
    onNodeTap: (MeshNodeUi) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val peers = when (networkState) {
        is NetworkState.Connected -> networkState.peers
        else -> emptyList()
    }
    val isScanning = networkState.activity == NetworkActivity.SCANNING

    // Continuous animations
    val infiniteTransition = rememberInfiniteTransition(label = "meshAnim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    
    val scanningAlpha by animateFloatAsState(
        targetValue = if (isScanning) 1f else 0f,
        animationSpec = tween(500),
        label = "scanningAlpha"
    )
    
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "ripple"
    )
    val dataFlowProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "dataFlow"
    )

    // Graph node state — rebuilt only when peer list changes
    val nodes = remember(peers.map { it.id }.toSet()) {
        mutableStateListOf<GraphNode>().also { list ->
            // Self node at center
            list.add(GraphNode("SELF", "YOU", "SELF", isAuthority = false, hopCount = -1, isConnected = true, x = 0f, y = 0f))
            peers.forEachIndexed { i, peer ->
                val angle = (i.toFloat() / peers.size.coerceAtLeast(1)) * 2 * PI.toFloat()
                val r = if (peer.hopCount == 0) 0.35f else 0.62f
                list.add(
                    GraphNode(
                        id = peer.id,
                        name = peer.name,
                        role = peer.role,
                        isAuthority = peer.isAuthority,
                        hopCount = peer.hopCount,
                        isConnected = peer.isConnected,
                        x = cos(angle) * r,
                        y = sin(angle) * r
                    )
                )
            }
        }
    }

    // Physics loop — spring/repulsion force simulation at ~30fps
    LaunchedEffect(nodes.size) {
        while (true) {
            for (i in nodes.indices) {
                if (nodes[i].id == "SELF") continue // Self is always centered
                var fx = 0f; var fy = 0f

                // Repulsion from every other node
                for (j in nodes.indices) {
                    if (i == j) continue
                    val dx = nodes[i].x - nodes[j].x
                    val dy = nodes[i].y - nodes[j].y
                    val dist2 = (dx * dx + dy * dy).coerceAtLeast(0.0001f)
                    val repF = 0.003f / dist2
                    fx += dx * repF; fy += dy * repF
                }

                // Orbital attraction — pull toward the target ring radius
                val targetR = if (nodes[i].hopCount == 0) 0.35f else 0.62f
                val dist = sqrt(nodes[i].x * nodes[i].x + nodes[i].y * nodes[i].y).coerceAtLeast(0.001f)
                val spring = (dist - targetR) * 0.04f
                fx -= (nodes[i].x / dist) * spring
                fy -= (nodes[i].y / dist) * spring

                val newVx = (nodes[i].vx + fx) * 0.88f
                val newVy = (nodes[i].vy + fy) * 0.88f
                val newX = (nodes[i].x + newVx).coerceIn(-0.88f, 0.88f)
                val newY = (nodes[i].y + newVy).coerceIn(-0.88f, 0.88f)
                nodes[i] = nodes[i].copy(x = newX, y = newY, vx = newVx, vy = newVy)
            }
            delay(33L) // ~30fps
        }
    }

    val context = LocalContext.current
    val interTypeface = remember {
        androidx.core.content.res.ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.DEFAULT
    }

    // Text paints for node labels — created outside Canvas to avoid allocation on each frame
    val namePaint = remember(interTypeface) {
        NativePaint().apply {
            color = android.graphics.Color.argb(220, 248, 250, 252)
            textSize = 34f
            textAlign = NativePaint.Align.CENTER
            typeface = Typeface.create(interTypeface, Typeface.BOLD)
            isAntiAlias = true
        }
    }
    val rolePaint = remember(interTypeface) {
        NativePaint().apply {
            color = android.graphics.Color.argb(150, 148, 163, 184)
            textSize = 26f
            textAlign = NativePaint.Align.CENTER
            typeface = Typeface.create(interTypeface, Typeface.NORMAL)
            isAntiAlias = true
        }
    }


    val peerById = remember(peers) { peers.associateBy { it.id } }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(peers, nodes.size) {
            detectTapGestures { offset ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val scale = size.height / 2f
                val hitRadius = 40.dp.toPx()
                val nodePadding = 40.dp.toPx()
                nodes.filter { it.id != "SELF" }.forEach { node ->
                    val rawX = cx + node.x * scale
                    val rawY = cy + node.y * scale
                    val px = rawX.coerceIn(nodePadding, size.width - nodePadding)
                    val py = rawY.coerceIn(nodePadding, size.height - nodePadding)
                    val dx = offset.x - px
                    val dy = offset.y - py
                    if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                        peerById[node.id]?.let { onNodeTap(it) }
                    }
                }
            }
        }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = size.height / 2f

        // ── Tactical HUD Grid ────────────────────────────────────────────────
        val gridSize = 40.dp.toPx()
        for (i in 0..(size.width / gridSize).toInt()) {
            val x = i * gridSize
            drawLine(
                color = primaryColor.copy(alpha = 0.03f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }
        for (i in 0..(size.height / gridSize).toInt()) {
            val y = i * gridSize
            drawLine(
                color = primaryColor.copy(alpha = 0.03f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        // ── Environmental Layer (Volumetric Ripples) ──────────────────────────────
        for (i in 0..3) {
            val progress = (rippleProgress + (i / 4f)) % 1f
            val radius = (scale * 0.75f) * progress
            val alpha = (1f - progress).coerceIn(0f, 1f)
            
            val baseAlpha = if (networkState is NetworkState.Idle) 0.2f else 0.6f
            
            drawCircle(
                brush = Brush.radialGradient(
                    0.6f to primaryColor.copy(alpha = 0f),
                    0.9f to primaryColor.copy(alpha = alpha * baseAlpha),
                    0.96f to primaryColor.copy(alpha = alpha * (baseAlpha + 0.3f)),
                    1.0f to primaryColor.copy(alpha = 0f),
                    center = Offset(cx, cy),
                    radius = radius.coerceAtLeast(0.1f)
                ),
                radius = radius,
                center = Offset(cx, cy)
            )
        }


        // Show scanning text if only self is present
        if (nodes.size == 1) {
            val showScanText = scanningAlpha > 0.5f
            val titleText = if (showScanText) "MESH DISCOVERY ACTIVE" else "MESH STANDBY"
            val subText = if (showScanText) "Scanning Channel 4..." else "Ready to connect"
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(titleText, cx, cy + 90.dp.toPx(), namePaint)
                canvas.nativeCanvas.drawText(subText, cx, cy + 115.dp.toPx(), rolePaint)
            }
        }

        // Helper: screen coords
        fun pos(node: GraphNode): Offset {
            val nodePadding = 40.dp.toPx()
            val rawX = cx + node.x * scale
            val rawY = cy + node.y * scale
            return Offset(
                x = rawX.coerceIn(nodePadding, size.width - nodePadding),
                y = rawY.coerceIn(nodePadding, size.height - nodePadding)
            )
        }

        val selfNode = nodes.firstOrNull { it.id == "SELF" } ?: return@Canvas
        val selfPos = pos(selfNode)

        // ── Organic Edges ─────────────────────────────────────────────────────────────
        nodes.filter { it.id != "SELF" }.forEach { peer ->
            val peerPos = pos(peer)
            val isDirect = peer.hopCount == 0 && peer.isConnected

            val midX = (selfPos.x + peerPos.x) / 2
            val midY = (selfPos.y + peerPos.y) / 2
            val dx = peerPos.x - selfPos.x
            val dy = peerPos.y - selfPos.y
            val dist = sqrt(dx*dx + dy*dy).coerceAtLeast(0.1f)
            val perpX = -dy / dist * (dist * 0.15f)
            val perpY = dx / dist * (dist * 0.15f)
            val cpX = midX + perpX
            val cpY = midY + perpY

            val path = Path().apply {
                moveTo(selfPos.x, selfPos.y)
                quadraticBezierTo(cpX, cpY, peerPos.x, peerPos.y)
            }

            if (isDirect) {
                // Direct peer — flowing glowing path
                drawPath(
                    path = path,
                    color = primaryColor.copy(alpha = 0.4f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                
                // Animated data-flow particle along Bezier
                val t = dataFlowProgress
                val invT = 1f - t
                val particleX = invT * invT * selfPos.x + 2 * invT * t * cpX + t * t * peerPos.x
                val particleY = invT * invT * selfPos.y + 2 * invT * t * cpY + t * t * peerPos.y
                
                drawCircle(color = Color.White, radius = 4f.dp.toPx(), center = Offset(particleX, particleY))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.6f), primaryColor.copy(alpha=0f)),
                        center = Offset(particleX, particleY),
                        radius = 14.dp.toPx()
                    ),
                    radius = 14.dp.toPx(),
                    center = Offset(particleX, particleY)
                )
            } else {
                // Relayed peer
                val relay = nodes.filter { it.id != "SELF" && it.hopCount == 0 }.minByOrNull { n ->
                    val np = pos(n)
                    (np.x - peerPos.x).pow(2) + (np.y - peerPos.y).pow(2)
                } ?: selfNode
                val relayPos = pos(relay)
                
                val rmX = (relayPos.x + peerPos.x) / 2
                val rmY = (relayPos.y + peerPos.y) / 2
                val rdx = peerPos.x - relayPos.x
                val rdy = peerPos.y - relayPos.y
                val rdist = sqrt(rdx*rdx + rdy*rdy).coerceAtLeast(0.1f)
                val rcpX = rmX - rdy / rdist * (rdist * 0.1f)
                val rcpY = rmY + rdx / rdist * (rdist * 0.1f)
                
                val relayPath = Path().apply {
                    moveTo(relayPos.x, relayPos.y)
                    quadraticBezierTo(rcpX, rcpY, peerPos.x, peerPos.y)
                }
                
                drawPath(
                    path = relayPath,
                    color = secondaryColor.copy(alpha = 0.25f),
                    style = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }



        // ── Nodes (Volumetric Glowing Orbs) ──────────────────────────────────────────
        nodes.forEach { node ->
            val p = pos(node)

            when {
                node.id == "SELF" -> {
                    // Outer volumetric pulse ring
                    if (scanningAlpha > 0f) {
                        val pR = 40.dp.toPx() * pulse
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.4f * pulse * scanningAlpha), primaryColor.copy(alpha = 0f)),
                                center = p,
                                radius = pR.coerceAtLeast(0.1f)
                            ),
                            radius = pR,
                            center = p
                        )
                    }
                    // Core Glow
                    drawCircle(color = primaryColor, radius = 16.dp.toPx(), center = p)
                    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 6.dp.toPx(), center = p)
                }
                node.isAuthority -> {
                    // Hexagon with inner glow
                    val radius = 16.dp.toPx()
                    val path = Path().apply {
                        for (i in 0 until 6) {
                            val angle_deg = 60 * i - 30
                            val angle_rad = Math.PI / 180 * angle_deg
                            val x = p.x + radius * cos(angle_rad).toFloat()
                            val y = p.y + radius * sin(angle_rad).toFloat()
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(secondaryColor.copy(alpha = 0.6f), secondaryColor.copy(alpha = 0f)),
                            center = p, radius = radius * 2.5f
                        ),
                        radius = radius * 2.5f, center = p
                    )
                    drawPath(path = path, color = secondaryColor)
                    drawPath(path = path, color = Color.White, style = Stroke(1.5f.dp.toPx()))
                }
                !node.isConnected -> {
                    drawCircle(color = Color.DarkGray, radius = 13.dp.toPx(), center = p)
                }
                node.hopCount > 0 -> {
                    // Relayed node - Cyan glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(secondaryColor.copy(alpha = 0.5f), secondaryColor.copy(alpha = 0f)),
                            center = p, radius = 20.dp.toPx()
                        ),
                        radius = 20.dp.toPx(), center = p
                    )
                    drawCircle(color = secondaryColor.copy(alpha = 0.8f), radius = 11.dp.toPx(), center = p)
                }
                else -> {
                    // Direct peer - Blue glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.5f), primaryColor.copy(alpha = 0f)),
                            center = p, radius = 24.dp.toPx()
                        ),
                        radius = 24.dp.toPx(), center = p
                    )
                    drawCircle(color = primaryColor, radius = 12.dp.toPx(), center = p)
                    drawCircle(color = Color.White.copy(alpha=0.6f), radius = 4.dp.toPx(), center = p)
                }
            }
        }

        // ── Node Labels ────────────────────────────────────────────────────────
        drawIntoCanvas { canvas ->
            nodes.forEach { node ->
                val p = pos(node)
                val nodeRadius = when {
                    node.id == "SELF" -> 28.dp.toPx()
                    node.isAuthority -> 22.dp.toPx()
                    else -> 18.dp.toPx()
                }
                val labelY = p.y + nodeRadius + 14.dp.toPx()
                val name = if (node.id == "SELF") "YOU" else node.name.take(11)
                canvas.nativeCanvas.drawText(name, p.x, labelY, namePaint)
                
                // Add tactical telemetry stats
                if (node.id != "SELF") {
                    val mockSnr = "${12 - (node.hopCount * 4)}dB"
                    val mockBat = "${85 - (node.hopCount * 10)}%"
                    canvas.nativeCanvas.drawText("SNR: $mockSnr | Bat: $mockBat", p.x, labelY + 18.dp.toPx(), rolePaint)
                }
            }
        }
    }
}
}

// ─── Stats Panel ──────────────────────────────────────────────────────────────

@Composable
private fun NetworkStatsPanel(
    nodeCount: Int,
    hasWeakSignal: Boolean,
    networkState: NetworkState,
    modifier: Modifier = Modifier
) {
    CarakaGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val peerCount = maxOf(0, nodeCount - 1)
            
            val healthValue: String
            val healthColor: Color
            when {
                peerCount == 0 -> {
                    healthValue = "NO PEER"
                    healthColor = Color.Gray
                }
                hasWeakSignal -> {
                    healthValue = "LEMAH"
                    healthColor = Color(0xFF5AC8FA)
                }
                else -> {
                    healthValue = "BAIK"
                    healthColor = LocalStatusColors.current.online
                }
            }

            StatChip(label = "PEERS",     value = "$peerCount", color = MaterialTheme.colorScheme.primary)
            Divider(Modifier.width(1.dp).height(32.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            StatChip(label = "HEALTH",    value = healthValue, color = healthColor)
            Divider(Modifier.width(1.dp).height(32.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            StatChip(label = "CHANNEL",   value = "CH-04", color = MaterialTheme.colorScheme.primary)
            Divider(Modifier.width(1.dp).height(32.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            
            val isActivityActive = networkState.activity != NetworkActivity.IDLE
            StatChip(label = "ACTIVITY",  value = networkState.activity.name, color = if(isActivityActive) LocalStatusColors.current.hybrid else Color.Gray)
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = value, 
            fontFamily = SpaceGroteskFamily, 
            color = color, 
            fontWeight = FontWeight.Bold, 
            fontSize = 20.sp,
            style = LocalTextStyle.current.copy(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = color.copy(alpha = 0.4f),
                    blurRadius = 12f
                )
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label, 
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Bold, 
            letterSpacing = 1.2.sp
        )
    }
}
