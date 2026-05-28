package com.example.caraka.ui.screens

import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MeshNodeUi
import com.example.caraka.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlin.math.*

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

    LaunchedEffect(viewModel) {
        viewModel?.discoverPeers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.network_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel?.discoverPeers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_scan_btn), tint = AmberAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Force-directed network graph
            Box(modifier = Modifier.fillMaxSize()) {
                MeshNetworkGraph(
                    peers = meshNodes,
                    hasSosActive = activeAlerts.isNotEmpty(),
                    modifier = Modifier.fillMaxSize()
                )

                // Bottom stats strip (Floating Glassmorphism)
                NetworkStatsPanel(
                    nodeCount = meshNodeCount,
                    connectionState = connectionState,
                    sosCount = activeAlerts.size,
                    relayedCount = relayed,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

// ─── Force-Directed Graph Canvas ─────────────────────────────────────────────

@Composable
private fun MeshNetworkGraph(
    peers: List<MeshNodeUi>,
    hasSosActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Continuous animations
    val infiniteTransition = rememberInfiniteTransition(label = "meshAnim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val sosPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sosPulse"
    )
    val scanAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "scan"
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

    // Text paints for node labels — created outside Canvas to avoid allocation on each frame
    val namePaint = remember {
        NativePaint().apply {
            color = android.graphics.Color.argb(220, 248, 250, 252)
            textSize = 34f
            textAlign = NativePaint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
    }
    val rolePaint = remember {
        NativePaint().apply {
            color = android.graphics.Color.argb(150, 148, 163, 184)
            textSize = 26f
            textAlign = NativePaint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = minOf(size.width, size.height) / 2f

        // ── Tactical HUD Grid ────────────────────────────────────────────────
        val gridSize = 40.dp.toPx()
        for (i in 0..(size.width / gridSize).toInt()) {
            val x = i * gridSize
            drawLine(
                color = AmberAccent.copy(alpha = 0.05f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }
        for (i in 0..(size.height / gridSize).toInt()) {
            val y = i * gridSize
            drawLine(
                color = AmberAccent.copy(alpha = 0.05f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        // ── Background radar rings ────────────────────────────────────────────
        listOf(0.38f, 0.68f, 0.95f).forEach { r ->
            drawCircle(
                color = AmberAccent.copy(alpha = 0.06f),
                radius = scale * r,
                center = Offset(cx, cy),
                style = Stroke(1.dp.toPx())
            )
        }

        // Rotating radar sweep
        drawArc(
            color = AmberAccent.copy(alpha = 0.25f),
            startAngle = scanAngle,
            sweepAngle = 55f,
            useCenter = true,
            size = Size(scale * 1.9f, scale * 1.9f),
            topLeft = Offset(cx - scale * 0.95f, cy - scale * 0.95f)
        )
        drawArc(
            color = AmberAccent.copy(alpha = 0.07f),
            startAngle = scanAngle - 20f,
            sweepAngle = 75f,
            useCenter = true,
            size = Size(scale * 1.9f, scale * 1.9f),
            topLeft = Offset(cx - scale * 0.95f, cy - scale * 0.95f)
        )

        // Helper: screen coords
        fun pos(node: GraphNode) = Offset(cx + node.x * scale, cy + node.y * scale)

        val selfNode = nodes.firstOrNull { it.id == "SELF" } ?: return@Canvas
        val selfPos = pos(selfNode)

        // ── Edges ─────────────────────────────────────────────────────────────
        nodes.filter { it.id != "SELF" }.forEach { peer ->
            val peerPos = pos(peer)
            val isDirect = peer.hopCount == 0 && peer.isConnected

            if (isDirect) {
                // Direct peer — solid amber line
                drawLine(
                    color = AmberAccent.copy(alpha = 0.45f),
                    start = selfPos,
                    end = peerPos,
                    strokeWidth = 1.8f.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Animated data-flow dot travelling along the edge
                val dotX = selfPos.x + (peerPos.x - selfPos.x) * dataFlowProgress
                val dotY = selfPos.y + (peerPos.y - selfPos.y) * dataFlowProgress
                drawCircle(color = AmberAccent, radius = 3.5f.dp.toPx(), center = Offset(dotX, dotY))
                val dotX2 = selfPos.x + (peerPos.x - selfPos.x) * ((dataFlowProgress + 0.5f) % 1f)
                val dotY2 = selfPos.y + (peerPos.y - selfPos.y) * ((dataFlowProgress + 0.5f) % 1f)
                drawCircle(color = AmberAccent.copy(alpha = 0.5f), radius = 2.5f.dp.toPx(), center = Offset(dotX2, dotY2))
            } else {
                // Relayed peer — dashed blue line via nearest direct peer
                val relay = nodes.filter { it.id != "SELF" && it.hopCount == 0 }.minByOrNull { n ->
                    val np = pos(n)
                    (np.x - peerPos.x).pow(2) + (np.y - peerPos.y).pow(2)
                } ?: selfNode
                val relayPos = pos(relay)
                val segments = 8
                for (k in 0 until segments step 2) {
                    val t0 = k / segments.toFloat()
                    val t1 = (k + 1) / segments.toFloat()
                    drawLine(
                        color = DisasterBlue.copy(alpha = 0.4f),
                        start = Offset(relayPos.x + (peerPos.x - relayPos.x) * t0, relayPos.y + (peerPos.y - relayPos.y) * t0),
                        end   = Offset(relayPos.x + (peerPos.x - relayPos.x) * t1, relayPos.y + (peerPos.y - relayPos.y) * t1),
                        strokeWidth = 1.2f.dp.toPx()
                    )
                }
            }
        }

        // ── SOS pulse overlay ─────────────────────────────────────────────────
        if (hasSosActive) {
            drawCircle(
                color = DangerRed.copy(alpha = sosPulse * 0.35f),
                radius = scale * 0.22f * (0.85f + sosPulse * 0.3f),
                center = selfPos
            )
            drawCircle(
                color = DangerRed.copy(alpha = 0.6f),
                radius = scale * 0.22f,
                center = selfPos,
                style = Stroke(2.dp.toPx())
            )
        }

        // ── Nodes ─────────────────────────────────────────────────────────────
        nodes.forEach { node ->
            val p = pos(node)
            when {
                node.id == "SELF" -> {
                    // Outer pulse ring
                    drawCircle(color = AmberAccent.copy(alpha = 0.12f * pulse), radius = 28.dp.toPx() * pulse, center = p)
                    // Mid glow
                    drawCircle(color = AmberAccent.copy(alpha = 0.3f), radius = 20.dp.toPx(), center = p)
                    // Core
                    drawCircle(color = AmberAccent, radius = 13.dp.toPx(), center = p)
                    drawCircle(color = NavyBackground, radius = 6.dp.toPx(), center = p)
                }
                node.isAuthority -> {
                    drawCircle(color = NeonMint.copy(alpha = 0.18f), radius = 22.dp.toPx(), center = p)
                    drawCircle(color = NeonMint, radius = 13.dp.toPx(), center = p)
                    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = 13.dp.toPx(), center = p, style = Stroke(1.5f.dp.toPx()))
                    // Shield cross mark
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(p.x, p.y - 6.dp.toPx()), Offset(p.x, p.y + 6.dp.toPx()), 1.5f.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(p.x - 5.dp.toPx(), p.y), Offset(p.x + 5.dp.toPx(), p.y), 1.5f.dp.toPx())
                }
                !node.isConnected -> {
                    drawCircle(color = DisasterBlue.copy(alpha = 0.12f), radius = 20.dp.toPx(), center = p)
                    drawCircle(color = SurfaceDark, radius = 13.dp.toPx(), center = p)
                    drawCircle(color = DisasterBlue.copy(alpha = 0.75f), radius = 13.dp.toPx(), center = p, style = Stroke(1.5f.dp.toPx()))
                    drawCircle(color = DisasterBlue.copy(alpha = 0.9f), radius = 5.dp.toPx(), center = p)
                }
                node.hopCount > 0 -> {
                    // Relayed (multi-hop) node
                    drawCircle(color = DisasterBlue.copy(alpha = 0.15f), radius = 18.dp.toPx(), center = p)
                    drawCircle(color = DisasterBlue.copy(alpha = 0.7f), radius = 10.dp.toPx(), center = p)
                    drawCircle(color = DisasterBlue, radius = 10.dp.toPx(), center = p, style = Stroke(1.dp.toPx()))
                }
                else -> {
                    // Direct peer
                    drawCircle(color = SurfaceDark, radius = 16.dp.toPx(), center = p)
                    drawCircle(color = AmberAccent.copy(alpha = 0.7f), radius = 9.dp.toPx(), center = p)
                    drawCircle(color = AmberAccent, radius = 9.dp.toPx(), center = p, style = Stroke(1.dp.toPx()))
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
                if (node.id != "SELF" && node.role.isNotBlank() && node.role !in listOf("DISCOVERED", "AVAILABLE", "CONNECTED")) {
                    canvas.nativeCanvas.drawText(node.role, p.x, labelY + 15.dp.toPx(), rolePaint)
                }
            }
        }
    }
}

// ─── Stats Panel ──────────────────────────────────────────────────────────────

@Composable
private fun NetworkStatsPanel(
    nodeCount: Int,
    connectionState: String,
    sosCount: Int,
    relayedCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AmberAccent.copy(alpha = 0.2f), spotColor = SurfaceDark)
            .clip(RoundedCornerShape(24.dp))
            .background(GlassSurface)
            .border(1.dp, SurfaceDark, RoundedCornerShape(24.dp))
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatChip(label = "Nodes",   value = "$nodeCount",          color = AmberAccent)
        StatChip(label = "Range",   value = "${nodeCount * 100}m", color = NeonMint)
        StatChip(label = "SOS",     value = "$sosCount",           color = DangerRed)
        StatChip(label = "Relayed", value = "$relayedCount",       color = DisasterBlue)
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
