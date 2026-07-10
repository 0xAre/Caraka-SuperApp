package com.example.caraka.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.ui.components.EmptyStateIllustration
import com.example.caraka.ui.components.EmptyStateVariant
import com.example.caraka.ui.components.CarakaTopBarTitle
import com.example.caraka.ui.components.MeshStatusBanner
import com.example.caraka.ui.components.PeerListItem
import com.example.caraka.ui.components.CarakaSearchField
import com.example.caraka.ui.courier.CarakaTab
import com.example.caraka.ui.prefs.UiPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.example.caraka.ui.theme.*
import com.example.caraka.ui.util.SosAlertText
import com.example.caraka.viewmodel.CourierViewModel
import com.example.caraka.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MainViewModel,
    courierViewModel: CourierViewModel,
    uiPrefs: UiPreferences,
    onNavigateToChat: (String) -> Unit,
    onNavigateToNetwork: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val meshNodeCount by viewModel.meshNodeCount.collectAsStateWithLifecycle(initialValue = 1)
    val connectivity by viewModel.connectivityStatus.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle(initialValue = "IDLE")
    val carryCount by courierViewModel.activeCarryCount.collectAsStateWithLifecycle(initialValue = 0)

    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    CarakaTopBarTitle(
                        title = stringResource(R.string.messages_title),
                        subtitle = stringResource(R.string.messages_subtitle)
                    )
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
            MeshStatusBanner(
                connectivityStatus = connectivity,
                nodeCount = meshNodeCount,
                connectionState = connectionState,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_messages)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        if (carryCount > 0) {
                            BadgedBox(badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) { Text(if (carryCount > 99) "99+" else "$carryCount", style = CarakaTextStyles.badge) }
                            }) { Text(stringResource(R.string.tab_caraka)) }
                        } else {
                            Text(stringResource(R.string.tab_caraka))
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> ChatListTab(
                    viewModel = viewModel,
                    uiPrefs = uiPrefs,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToNetwork = onNavigateToNetwork
                )
                else -> CarakaTab(
                    viewModel = courierViewModel,
                    onOpenHistory = onNavigateToHistory
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListTab(
    viewModel: MainViewModel,
    uiPrefs: UiPreferences,
    onNavigateToChat: (String) -> Unit,
    onNavigateToNetwork: () -> Unit
) {
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle(initialValue = emptyList())
    val lastMessagesPerPeer by viewModel.lastMessagesPerPeer.collectAsStateWithLifecycle(initialValue = emptyMap())
    val lastReadMap by uiPrefs.observeLastReadMap().collectAsStateWithLifecycle(initialValue = emptyMap())

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

    Column(modifier = Modifier.fillMaxSize()) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 44.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                MeshEmptyStateIllustration(
                    message = stringResource(R.string.messages_empty_network_title),
                    subtitle = stringResource(R.string.messages_empty_network_subtitle),
                    actionLabel = stringResource(R.string.messages_open_network),
                    onAction = onNavigateToNetwork,
                    contentDescription = stringResource(R.string.messages_no_peers)
                )
            }
        } else if (filteredPeers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.messages_search_empty),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            com.example.caraka.ui.components.CarakaCard(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
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
                            lastMessagePreview = lastMsg?.let { msg ->
                                if (msg.type == "SOS") {
                                    SosAlertText.headline(msg.sosCategory, msg.content)
                                } else {
                                    msg.content
                                }
                            },
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
    EmptyStateIllustration(
        icon = Icons.Default.WifiTethering,
        message = message,
        subtitle = subtitle,
        actionLabel = actionLabel,
        onAction = onAction,
        contentDescription = contentDescription,
        illustrationRes = R.drawable.ill_empty_messages,
        variant = EmptyStateVariant.Compact
    )
}
