package com.example.caraka.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.caraka.R
import com.example.caraka.ui.theme.DiscoveryConnectedHaloColor
import com.example.caraka.ui.theme.DiscoveryCoreGlowColor
import com.example.caraka.ui.theme.DiscoveryRippleColor
import com.example.caraka.ui.theme.DiscoveryRippleDurationMs
import com.example.caraka.ui.theme.DiscoveryRippleMaxAlpha
import com.example.caraka.ui.theme.DiscoveryRippleMaxRadiusRatio
import com.example.caraka.ui.theme.DiscoveryRippleMinRadiusRatio
import com.example.caraka.ui.theme.DiscoveryStaticHaloAlpha
import com.example.caraka.ui.theme.TelegramBlue
import com.example.caraka.ui.theme.TelegramBlueStrong
import com.example.caraka.viewmodel.NetworkDiscoveryPhase

private enum class RadarMotionMode {
    Searching,
    Connecting,
    Settling,
    Connected,
    Static
}

private fun radarModeFor(phase: NetworkDiscoveryPhase): RadarMotionMode = when (phase) {
    NetworkDiscoveryPhase.Scanning -> RadarMotionMode.Searching
    NetworkDiscoveryPhase.Connecting -> RadarMotionMode.Connecting
    NetworkDiscoveryPhase.Results -> RadarMotionMode.Settling
    NetworkDiscoveryPhase.Connected -> RadarMotionMode.Connected
    else -> RadarMotionMode.Static
}

@Composable
internal fun RadioRadarOverlay(
    phase: NetworkDiscoveryPhase,
    diameter: Dp,
    peerCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val motionMode = radarModeFor(phase)
    var lifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleResumed = when (event) {
                Lifecycle.Event.ON_RESUME -> true
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> false
                else -> lifecycleResumed
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shouldAnimate = lifecycleResumed && ValueAnimator.areAnimatorsEnabled()
    val loopDuration = DiscoveryRippleDurationMs.toInt()
    val transition = rememberInfiniteTransition(label = "discovery-ripple")
    val loopProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(loopDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "discovery-ripple-progress"
    )
    val coreBreath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3_100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "discovery-core-breath"
    )
    val progress = if (shouldAnimate) loopProgress else 0.42f
    val breath = if (shouldAnimate) coreBreath else 0.5f
    val accessibilityDescription = pulseContentDescription(phase, peerCount)

    Box(
        modifier = modifier
            .size(diameter)
            .testTag("network_radar")
            .semantics { contentDescription = accessibilityDescription },
        contentAlignment = Alignment.Center
    ) {
        CoverageRippleField(
            progress = progress,
            coreBreath = breath,
            mode = motionMode,
            modifier = Modifier.matchParentSize()
        )
        UserLocationPuck(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun CoverageRippleField(
    progress: Float,
    coreBreath: Float,
    mode: RadarMotionMode,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.testTag(
            when (mode) {
                RadarMotionMode.Searching -> "network_radar_searching"
                RadarMotionMode.Connecting -> "network_radar_connecting"
                RadarMotionMode.Settling -> "network_radar_settling"
                RadarMotionMode.Connected -> "network_radar_connected"
                RadarMotionMode.Static -> "network_radar_static"
            }
        )
    ) {
        val anchor = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension * DiscoveryRippleMaxRadiusRatio
        val minRadius = size.minDimension * DiscoveryRippleMinRadiusRatio
        val waveCount = when (mode) {
            RadarMotionMode.Searching,
            RadarMotionMode.Connecting -> 3
            RadarMotionMode.Settling -> 2
            else -> 0
        }
        val motionStrength = when (mode) {
            RadarMotionMode.Connecting -> 1.08f
            RadarMotionMode.Settling -> 0.58f
            else -> 1f
        }

        if (waveCount > 0) {
            repeat(waveCount) { index ->
                val phaseOffset = index.toFloat() / 3f
                val waveProgress = (progress + phaseOffset) % 1f
                val radius = minRadius + (maxRadius - minRadius) * waveProgress
                val fadeIn = smoothStep(0.02f, 0.18f, waveProgress)
                val fadeOut = 1f - smoothStep(0.70f, 0.94f, waveProgress)
                val alpha = DiscoveryRippleMaxAlpha *
                    fadeIn *
                    fadeOut *
                    motionStrength

                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to DiscoveryRippleColor.copy(alpha = alpha * 0.42f),
                            0.58f to DiscoveryRippleColor.copy(alpha = alpha * 0.50f),
                            0.82f to DiscoveryRippleColor.copy(alpha = alpha * 0.68f),
                            0.94f to DiscoveryRippleColor.copy(alpha = alpha),
                            1f to DiscoveryRippleColor.copy(alpha = alpha * 0.18f)
                        ),
                        center = anchor,
                        radius = radius
                    ),
                    radius = radius,
                    center = anchor
                )
                drawCircle(
                    color = DiscoveryRippleColor.copy(alpha = alpha * 0.42f),
                    radius = radius,
                    center = anchor,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }

        val haloColor = if (mode == RadarMotionMode.Connected) {
            DiscoveryConnectedHaloColor
        } else {
            DiscoveryCoreGlowColor
        }
        val haloRadiusBase = when (mode) {
            RadarMotionMode.Searching,
            RadarMotionMode.Connecting -> 54.dp.toPx()
            RadarMotionMode.Settling -> 48.dp.toPx()
            RadarMotionMode.Connected -> 44.dp.toPx()
            RadarMotionMode.Static -> 40.dp.toPx()
        }
        val haloAlphaBase = when (mode) {
            RadarMotionMode.Searching -> 0.38f
            RadarMotionMode.Connecting -> 0.42f
            RadarMotionMode.Settling -> 0.26f
            RadarMotionMode.Connected -> 0.24f
            RadarMotionMode.Static -> DiscoveryStaticHaloAlpha
        }
        val breathAmount = if (mode == RadarMotionMode.Searching ||
            mode == RadarMotionMode.Connecting
        ) {
            coreBreath
        } else {
            0.5f
        }
        val haloRadius = haloRadiusBase * (0.94f + breathAmount * 0.08f)
        val haloAlpha = haloAlphaBase * (0.90f + breathAmount * 0.12f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to haloColor.copy(alpha = haloAlpha),
                    0.38f to haloColor.copy(alpha = haloAlpha * 0.45f),
                    1f to Color.Transparent
                ),
                center = anchor,
                radius = haloRadius
            ),
            radius = haloRadius,
            center = anchor
        )
    }
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val normalized = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return normalized * normalized * (3f - 2f * normalized)
}

@Composable
private fun UserLocationPuck(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(46.dp)
            .testTag("network_self_marker")
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = Color(0xFF0B3550).copy(alpha = 0.20f),
            radius = 23.dp.toPx(),
            center = center + Offset(0f, 3.dp.toPx())
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to TelegramBlue,
                0.72f to TelegramBlue,
                1f to TelegramBlueStrong,
                center = center - Offset(5.dp.toPx(), 6.dp.toPx()),
                radius = 25.dp.toPx()
            ),
            radius = 21.dp.toPx(),
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = 21.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.96f),
            radius = 8.dp.toPx(),
            center = center
        )
        drawCircle(
            color = TelegramBlueStrong,
            radius = 3.2.dp.toPx(),
            center = center
        )
    }
}

@Composable
private fun pulseContentDescription(phase: NetworkDiscoveryPhase, peerCount: Int): String {
    return when (phase) {
        NetworkDiscoveryPhase.Scanning ->
            stringResource(R.string.network_radar_a11y_scanning, peerCount)
        NetworkDiscoveryPhase.Connecting ->
            stringResource(R.string.network_radar_a11y_connecting, peerCount)
        NetworkDiscoveryPhase.Results ->
            stringResource(R.string.network_radar_a11y_results, peerCount)
        NetworkDiscoveryPhase.Connected ->
            stringResource(R.string.network_radar_a11y_connected, peerCount)
        else -> stringResource(R.string.network_radar_a11y_idle, peerCount)
    }
}
