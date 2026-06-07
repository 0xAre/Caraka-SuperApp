package com.example.caraka.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.network.ConnectivityStatus
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.DangerRed
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.NeonMint
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary
import com.example.caraka.ui.theme.WarningYellow

@Composable
fun MeshStatusBanner(
    connectivityStatus: ConnectivityStatus,
    nodeCount: Int,
    connectionState: String,
    modifier: Modifier = Modifier,
    isAttackSim: Boolean = false
) {
    val (dotColor, labelRes) = when (connectivityStatus) {
        ConnectivityStatus.ONLINE -> NeonMint to R.string.home_status_online
        ConnectivityStatus.HYBRID -> WarningYellow to R.string.home_status_hybrid
        ConnectivityStatus.MESH_ONLY -> DangerRed to R.string.home_status_mesh_only
    }

    val connectionLabel = when {
        isAttackSim -> stringResource(R.string.home_attack_active)
        connectionState.contains("CONNECT", ignoreCase = true) ->
            stringResource(R.string.mesh_status_connected, nodeCount)
        connectionState.contains("SEARCH", ignoreCase = true) ||
            connectionState.contains("DISCOVER", ignoreCase = true) ->
            stringResource(R.string.mesh_status_searching)
        connectionState.contains("RECONNECT", ignoreCase = true) ->
            stringResource(R.string.mesh_status_reconnecting)
        connectionState == "IDLE" -> stringResource(R.string.mesh_status_searching)
        else -> stringResource(R.string.mesh_status_connected, nodeCount)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "meshBanner")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    val statusCd = stringResource(R.string.mesh_status_cd, nodeCount, connectionLabel)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = dotColor.copy(alpha = 0.5f), spotColor = dotColor)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, dotColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .semantics { contentDescription = statusCd },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = if (connectivityStatus == ConnectivityStatus.MESH_ONLY) alpha else 1f))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(labelRes),
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                connectionLabel,
                color = if (isAttackSim) DangerRed else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isAttackSim) FontWeight.Bold else FontWeight.Normal
            )
        }
        Text(
            "$nodeCount ${stringResource(R.string.home_nodes_label)}",
            color = AmberAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
