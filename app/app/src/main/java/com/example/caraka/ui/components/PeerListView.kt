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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.data.local.entity.ConnectionStatus
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.DangerRed
import com.example.caraka.ui.theme.DisasterBlue
import com.example.caraka.ui.theme.NeonMint
import com.example.caraka.ui.theme.NavyBackground
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary

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
                "No peers discovered yet",
                color = TextSecondary,
                fontSize = 13.sp
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
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
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = peer.role,
                        fontSize = 12.sp,
                        color = TextSecondary
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
                            containerColor = DisasterBlue
                        )
                    ) {
                        Text("CONNECT", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                            disabledContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContentColor = TextSecondary
                        )
                    ) {
                        Text("Menunggu...", fontSize = 13.sp)
                    }
                }

                ConnectionStatus.CONNECTED, ConnectionStatus.ACTIVE_MESH -> {
                    Text(
                        text = "✓ Terhubung",
                        fontSize = 13.sp,
                        color = NeonMint,
                        fontWeight = FontWeight.SemiBold
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
            text = statusLabel(status),
            fontSize = 10.sp,
            color = statusColor(status),
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun statusColor(status: ConnectionStatus): Color = when (status) {
    ConnectionStatus.DISCOVERED -> DisasterBlue
    ConnectionStatus.PENDING_REQUEST -> AmberAccent
    ConnectionStatus.CONNECTED -> NeonMint
    ConnectionStatus.ACTIVE_MESH -> NeonMint
}

private fun statusLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.DISCOVERED -> "Found"
    ConnectionStatus.PENDING_REQUEST -> "Pending"
    ConnectionStatus.CONNECTED -> "Connected"
    ConnectionStatus.ACTIVE_MESH -> "Active"
}
