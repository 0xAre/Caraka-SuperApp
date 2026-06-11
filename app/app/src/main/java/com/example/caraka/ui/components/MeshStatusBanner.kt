package com.example.caraka.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.network.ConnectivityStatus
import com.example.caraka.ui.theme.DotGothicFamily
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
    val (dotColor, labelRes) = when (connectivityStatus) {
        ConnectivityStatus.ONLINE -> statusColors.online to R.string.home_status_online
        ConnectivityStatus.HYBRID -> statusColors.hybrid to R.string.home_status_hybrid
        ConnectivityStatus.MESH_ONLY -> statusColors.meshOnly to R.string.home_status_mesh_only
    }



    val infiniteTransition = rememberInfiniteTransition(label = "meshBanner")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    val statusCd = stringResource(R.string.mesh_status_cd, nodeCount, connectionState)

    CarakaGlassSurface(
        modifier = modifier
            .semantics { contentDescription = statusCd },
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = if (connectivityStatus == ConnectivityStatus.MESH_ONLY) alpha else 1f))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "$nodeCount",
                    fontFamily = DotGothicFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
