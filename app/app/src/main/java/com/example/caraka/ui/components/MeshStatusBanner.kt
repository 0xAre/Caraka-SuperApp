package com.example.caraka.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.network.ConnectivityStatus
import com.example.caraka.ui.theme.LocalStatusColors
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.util.toConnectionActivityUi

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
        ConnectivityStatus.ONLINE    -> statusColors.online   to R.string.home_status_online
        ConnectivityStatus.HYBRID    -> statusColors.hybrid   to R.string.home_status_hybrid
        ConnectivityStatus.MESH_ONLY -> statusColors.meshOnly to R.string.home_status_mesh_only
    }
    val activityLabel = stringResource(connectionState.toConnectionActivityUi().labelRes)
    val statusCd = stringResource(R.string.mesh_status_cd, nodeCount, activityLabel)

    // Pulsing dot animation — gives a "live" feel
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue  = 1.30f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    CarakaCard(
        modifier = modifier.semantics { contentDescription = statusCd },
        containerColor = statusColor.copy(alpha = 0.08f),
        hasSubtleBorder = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing live indicator dot
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(16.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.25f))
                )
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
            Spacer(Modifier.width(10.dp))

            // Status label — AnimatedContent transitions smoothly on change
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = stringResource(labelRes),
                    transitionSpec = {
                        fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                    },
                    label = "status_label"
                ) { label ->
                    Text(
                        text  = label,
                        style = CarakaTextStyles.statusPrimary,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text  = activityLabel,
                    style = CarakaTextStyles.statusSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector       = if (isAttackSim) Icons.Default.Bolt else Icons.Default.WifiTethering,
                contentDescription = null,
                tint              = statusColor,
                modifier          = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text  = "$nodeCount node",
                style = CarakaTextStyles.statusSecondary,
                color = statusColor
            )
        }
    }
}
