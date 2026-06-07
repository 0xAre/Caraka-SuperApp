package com.example.caraka.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
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
import com.example.caraka.ui.prefs.UiPreferences
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
                title = { Text(stringResource(R.string.messages_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
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
            MeshStatusBanner(
                connectivityStatus = connectivity,
                nodeCount = meshNodeCount,
                connectionState = connectionState,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.messages_search_hint), color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AmberAccent) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = GlassSurface,
                    unfocusedContainerColor = GlassSurface,
                    focusedBorderColor = AmberAccent,
                    unfocusedBorderColor = SurfaceDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            if (connectedPeers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateIllustration(
                        icon = Icons.Default.Map,
                        message = stringResource(R.string.messages_empty_title),
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
