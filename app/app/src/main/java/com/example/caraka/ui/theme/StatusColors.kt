package com.example.caraka.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class StatusColors(
    val online: Color,
    val hybrid: Color,
    val meshOnly: Color,
    val relay: Color,
    val sos: Color,
    val authority: Color,
    val direct: Color
)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(
        online = Color.Unspecified,
        hybrid = Color.Unspecified,
        meshOnly = Color.Unspecified,
        relay = Color.Unspecified,
        sos = Color.Unspecified,
        authority = Color.Unspecified,
        direct = Color.Unspecified
    )
}
