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
                color = Color(0xFF999999),
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
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F8F8), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
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
                        color = Color(0xFF1A1A1A)
                    )
                    Text(
                        text = peer.role,
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
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
                            .height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0066CC)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("CONNECT", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                ConnectionStatus.PENDING_REQUEST -> {
                    Button(
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Color(0xFFE0E0E0)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Requesting...", fontSize = 12.sp)
                    }
                }

                ConnectionStatus.CONNECTED, ConnectionStatus.ACTIVE_MESH -> {
                    Text(
                        text = "✓ Connected",
                        fontSize = 12.sp,
                        color = Color(0xFF00AA00),
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
    ConnectionStatus.DISCOVERED -> Color(0xFF0066CC)
    ConnectionStatus.PENDING_REQUEST -> Color(0xFFFF9900)
    ConnectionStatus.CONNECTED -> Color(0xFF00AA00)
    ConnectionStatus.ACTIVE_MESH -> Color(0xFF00AA00)
}

private fun statusLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.DISCOVERED -> "Found"
    ConnectionStatus.PENDING_REQUEST -> "Pending"
    ConnectionStatus.CONNECTED -> "Connected"
    ConnectionStatus.ACTIVE_MESH -> "Active"
}
