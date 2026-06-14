package com.example.caraka.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.ui.components.EmptyStateIllustration
import com.example.caraka.ui.components.MeshStatusBanner
import com.example.caraka.ui.components.PeerListItem
import com.example.caraka.ui.components.CarakaSearchField
import com.example.caraka.ui.prefs.UiPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MainViewModel,
    uiPrefs: UiPreferences,
    onNavigateToChat: (String) -> Unit,
    onNavigateToNetwork: () -> Unit = {}
) {
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle(initialValue = emptyList())
    val lastMessagesPerPeer by viewModel.lastMessagesPerPeer.collectAsStateWithLifecycle(initialValue = emptyMap())
    val lastReadMap by uiPrefs.observeLastReadMap().collectAsStateWithLifecycle(initialValue = emptyMap())
    val meshNodeCount by viewModel.meshNodeCount.collectAsStateWithLifecycle(initialValue = 1)
    val connectivity by viewModel.connectivityStatus.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle(initialValue = "IDLE")

    var searchQuery by remember { mutableStateOf("") }

    val filteredPeers = remember(connectedPeers, searchQuery) {
        if (searchQuery.isBlank()) connectedPeers
        else connectedPeers.filter { peer ->
            peer.displayName.contains(searchQuery, ignoreCase = true) ||
                peer.role.contains(searchQuery, ignoreCase = true)
        }
    }

    val justNow = stringResource(R.string.messages_just_now)
    val minAgo = stringResource(R.string.messages_min_ago)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_title), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
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
            MeshStatusBanner(
                connectivityStatus = connectivity,
                nodeCount = meshNodeCount,
                connectionState = connectionState,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            AnimatedVisibility(
                visible = connectedPeers.isNotEmpty(),
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300))
            ) {
                CarakaSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.messages_search_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (connectedPeers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    MeshEmptyStateIllustration(
                        message = "Belum ada pesan di jaringan",
                        subtitle = "Hubungkan ke peer di sekitar untuk mulai berkomunikasi",
                        actionLabel = stringResource(R.string.messages_open_network),
                        onAction = onNavigateToNetwork,
                        contentDescription = stringResource(R.string.messages_no_peers)
                    )
                }
            } else if (filteredPeers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.messages_search_empty),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredPeers, key = { it.id }) { peer ->
                        val lastMsg = lastMessagesPerPeer[peer.id]
                        val lastRead = lastReadMap[peer.id] ?: 0L
                        val unreadCount = if (lastMsg != null && lastMsg.isIncoming && lastMsg.timestamp > lastRead) 1 else 0
                        val timeStr = lastMsg?.let { formatMessageTime(it.timestamp, justNow, minAgo) }

                        PeerListItem(
                            displayName = peer.displayName,
                            role = peer.role,
                            isAuthority = peer.isAuthority,
                            isConnected = true,
                            lastMessagePreview = lastMsg?.content,
                            isOutgoingPreview = lastMsg?.isIncoming == false,
                            timeLabel = timeStr,
                            unreadCount = unreadCount,
                            onClick = { onNavigateToChat(peer.id) },
                            showDivider = peer.id != filteredPeers.lastOrNull()?.id
                        )
                    }
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long, justNow: String, minAgo: String): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> justNow
        diff < 3_600_000 -> "${diff / 60_000}$minAgo"
        diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
fun MeshEmptyStateIllustration(
    message: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    contentDescription: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "meshPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Canvas(modifier = Modifier.size(120.dp, 80.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Define 3 surrounding nodes
            val nodes = listOf(
                Offset(cx - 35.dp.toPx(), cy - 25.dp.toPx()),
                Offset(cx + 40.dp.toPx(), cy - 10.dp.toPx()),
                Offset(cx - 15.dp.toPx(), cy + 30.dp.toPx())
            )

            val lineColor = primaryColor.copy(alpha = pulseAlpha)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

            // Draw connections
            nodes.forEach { node ->
                drawLine(
                    color = lineColor,
                    start = Offset(cx, cy),
                    end = node,
                    strokeWidth = 3f,
                    pathEffect = dashEffect
                )
            }
            // Draw connection between nodes
            drawLine(color = lineColor, start = nodes[0], end = nodes[1], strokeWidth = 2f, pathEffect = dashEffect)
            drawLine(color = lineColor, start = nodes[0], end = nodes[2], strokeWidth = 2f, pathEffect = dashEffect)

            // Draw nodes
            val nodeColor = primaryColor
            nodes.forEach { node ->
                drawCircle(color = nodeColor, radius = 4.dp.toPx(), center = node)
            }
            
            // Draw Center Node (YOU)
            drawCircle(color = nodeColor, radius = 6.dp.toPx(), center = Offset(cx, cy))
            // Center node pulse
            drawCircle(color = nodeColor.copy(alpha = 0.3f), radius = 6.dp.toPx() + (4.dp.toPx() * pulseAlpha), center = Offset(cx, cy))
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = onAction,
            shape = LocalCarakaShapes.current.md,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(actionLabel, fontWeight = FontWeight.Bold)
        }
    }
}
