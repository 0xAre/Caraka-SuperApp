package com.example.caraka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.caraka.ui.components.CarakaCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.data.local.entity.ConnectionStatus
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.LocalStatusColors

/**
 * Displays list of peers with their connection status and action buttons.
 */
@Composable
fun PeerListView(
    peers: List<PeerEntity>,
    onConnectClick: (String) -> Unit,
    onAcceptClick: (String) -> Unit,
    onRejectClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (peers.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.peer_list_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = CarakaTextStyles.listSubtitle
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(peers) { peer ->
            PeerItemCard(
                peer = peer,
                onConnectClick = onConnectClick,
                onAcceptClick = onAcceptClick,
                onRejectClick = onRejectClick
            )
        }
    }
}

@Composable
private fun PeerItemCard(
    peer: PeerEntity,
    onConnectClick: (String) -> Unit,
    onAcceptClick: (String) -> Unit,
    onRejectClick: (String) -> Unit
) {
    CarakaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        hasSubtleBorder = true
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Peer header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peer.displayName,
                        style = CarakaTextStyles.chatSender,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = peer.role,
                        style = CarakaTextStyles.statusSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(peer.status)
            }

            // Status-based action buttons
            when (peer.status) {
                ConnectionStatus.DISCOVERED -> {
                    Button(
                        onClick = { onConnectClick(peer.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalStatusColors.current.hybrid
                        )
                    ) {
                        Text(stringResource(R.string.peer_list_connect), style = CarakaTextStyles.buttonLabel)
                    }
                }

                ConnectionStatus.PENDING_REQUEST -> {
                    Button(
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.peer_list_pending), style = CarakaTextStyles.buttonLabel)
                    }
                }

                ConnectionStatus.CONNECTED, ConnectionStatus.ACTIVE_MESH -> {
                    Text(
                        text = stringResource(R.string.connection_connected),
                        style = CarakaTextStyles.buttonLabel,
                        color = LocalStatusColors.current.online
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ConnectionStatus) {
    Row(
        modifier = Modifier
            .background(statusColor(status).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = when (status) {
                ConnectionStatus.DISCOVERED -> Icons.Default.Visibility
                ConnectionStatus.PENDING_REQUEST -> Icons.Default.Schedule
                ConnectionStatus.CONNECTED -> Icons.Default.CheckCircle
                ConnectionStatus.ACTIVE_MESH -> Icons.Default.CheckCircle
            },
            contentDescription = status.name,
            modifier = Modifier.size(12.dp),
            tint = statusColor(status)
        )
        Text(
            text = statusLabelText(status),
            style = CarakaTextStyles.badge,
            color = statusColor(status)
        )
    }
}

@Composable
private fun statusColor(status: ConnectionStatus): Color {
    val statusColors = LocalStatusColors.current
    return when (status) {
        ConnectionStatus.DISCOVERED -> statusColors.hybrid
        ConnectionStatus.PENDING_REQUEST -> MaterialTheme.colorScheme.tertiary
        ConnectionStatus.CONNECTED -> statusColors.online
        ConnectionStatus.ACTIVE_MESH -> statusColors.online
    }
}

@Composable
private fun statusLabelText(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.DISCOVERED -> stringResource(R.string.peer_status_found)
    ConnectionStatus.PENDING_REQUEST -> stringResource(R.string.peer_list_pending)
    ConnectionStatus.CONNECTED -> stringResource(R.string.connection_connected)
    ConnectionStatus.ACTIVE_MESH -> stringResource(R.string.peer_status_active)
}
