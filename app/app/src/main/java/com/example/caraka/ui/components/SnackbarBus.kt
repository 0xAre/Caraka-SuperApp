package com.example.caraka.ui.components

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Lightweight global event bus for in-app toast/snackbar feedback.
 *
 * Any composable can call `LocalSnackbar.current.tryEmit("Message sent")` to surface
 * transient feedback, satisfying Nielsen heuristic #1 (Visibility of System Status).
 *
 * Consumed by the root composable in [com.example.caraka.MainActivity] which wires a
 * `SnackbarHost` to render the events.
 */
val LocalSnackbar = compositionLocalOf<MutableSharedFlow<String>> {
    MutableSharedFlow(extraBufferCapacity = 8)
}
