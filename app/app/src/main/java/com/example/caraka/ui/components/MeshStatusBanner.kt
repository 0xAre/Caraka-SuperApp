package com.example.caraka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.network.ConnectivityStatus
import com.example.caraka.ui.theme.LocalStatusColors

@Composable
fun MeshStatusBanner(
    connectivityStatus: ConnectivityStatus,
    nodeCount: Int,
    connectionState: String,
    modifier: Modifier = Modifier,
    isAttackSim: Boolean = false
) {
    val statusColors = LocalStatusColors.current
    val (statusColor, labelRes) = when (connectivityStatus) {
        ConnectivityStatus.ONLINE -> statusColors.online to R.string.home_status_online
        ConnectivityStatus.HYBRID -> statusColors.hybrid to R.string.home_status_hybrid
        ConnectivityStatus.MESH_ONLY -> statusColors.meshOnly to R.string.home_status_mesh_only
    }
    val statusCd = stringResource(R.string.mesh_status_cd, nodeCount, connectionState)

    CarakaCard(
        modifier = modifier.semantics { contentDescription = statusCd },
        containerColor = statusColor.copy(alpha = 0.08f),
        hasSubtleBorder = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (isAttackSim) Icons.Default.Bolt else Icons.Default.Hub,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$nodeCount node",
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
