package com.example.caraka.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.ui.theme.LocalStatusColors
import com.example.caraka.ui.util.rememberHaptics
import kotlinx.coroutines.delay

/**
 * "Hold-to-confirm" primary button — the user must keep pressing for [holdDurationMs] ms
 * before [onConfirm] is fired. Lifting the finger early cancels the action and resets
 * the progress ring. Designed to prevent accidental emergency broadcasts (Nielsen #5:
 * Error Prevention).
 *
 * Visual feedback:
 *  • A circular progress arc draws around the label as the user holds.
 *  • Haptic tick at start, heavy pattern on completion.
 *  • Screen-reader users hear the state ("Holding 1 of 2 seconds…").
 */
@Composable
fun HoldToConfirmButton(
    label: String,
    holdingLabel: String,
    completedLabel: String,
    enabled: Boolean = true,
    completed: Boolean = false,
    holdDurationMs: Long = 2000L,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    var pressed by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }

    val progress = (elapsed.toFloat() / holdDurationMs.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "hold_progress"
    )

    // Tick the timer while pressed.
    LaunchedEffect(pressed) {
        if (pressed && !completed) {
            haptics.tick()
            val start = System.currentTimeMillis()
            while (pressed) {
                elapsed = System.currentTimeMillis() - start
                if (elapsed >= holdDurationMs) {
                    pressed = false
                    haptics.emergency()
                    onConfirm()
                    break
                }
                delay(16L) // ~60fps
            }
            if (elapsed < holdDurationMs) elapsed = 0L
        } else if (!pressed) {
            elapsed = 0L
        }
    }

    val onlineColor = LocalStatusColors.current.online
    val containerColor = when {
        completed -> onlineColor
        !enabled  -> com.example.caraka.ui.theme.SurfaceMid
        pressed   -> MaterialTheme.colorScheme.error
        else      -> MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
    }

    val state = when {
        completed -> completedLabel
        pressed   -> holdingLabel
        else      -> label
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(
                elevation = if (enabled) 4.dp else 0.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = if (completed) onlineColor else MaterialTheme.colorScheme.error,
                spotColor = if (completed) onlineColor else MaterialTheme.colorScheme.error
            )
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = state
            }
            .pointerInput(enabled, completed) {
                if (!enabled || completed) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        pressed = event.changes.any { it.pressed }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Filling background bar that scales with progress
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.10f * animatedProgress))
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (pressed && !completed) {
                    Text(
                        "${"%.1f".format(elapsed / 1000f)} / ${"%.1f".format(holdDurationMs / 1000f)} s",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
    // Static helper text below
    if (!completed && enabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                if (pressed) holdingLabel else "Hold to send",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}
