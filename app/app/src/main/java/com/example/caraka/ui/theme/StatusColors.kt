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
    val direct: Color,
    /** Aksen Courier "Stealth" (di atas surface). Per-tema. */
    val stealth: Color,
    /** Konten (teks/ikon) di atas fill [stealth]. Flip antar-tema. */
    val onStealth: Color
)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(
        online = Color.Unspecified,
        hybrid = Color.Unspecified,
        meshOnly = Color.Unspecified,
        relay = Color.Unspecified,
        sos = Color.Unspecified,
        authority = Color.Unspecified,
        direct = Color.Unspecified,
        stealth = Color.Unspecified,
        onStealth = Color.Unspecified
    )
}
