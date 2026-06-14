package com.example.caraka.ui.theme

import android.app.Activity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val DefaultColorScheme = darkColorScheme(
    primary              = TealPrimary,           // #2AABEE Telegram blue
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFF1565C0),      // dark blue — outgoing chat bubbles
    onPrimaryContainer   = Color(0xFFE3F2FD),
    secondary            = CyanSecondary,          // #238636 Gojek-tone green
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFF1A3A20),
    onSecondaryContainer = Color(0xFF4CAF50),
    tertiary             = Color(0xFFD29922),       // amber warnings
    onTertiary           = Color(0xFF000000),
    tertiaryContainer    = Color(0xFF3D2B00),
    onTertiaryContainer  = Color(0xFFFFD60A),
    background           = CanvasDark,             // #0D1117
    onBackground         = TextPrimary,            // #E6EDF3
    surface              = SurfaceLow,             // #161B22
    onSurface            = TextPrimary,
    surfaceVariant       = SurfaceMid,             // #21262D
    onSurfaceVariant     = TextSecondary,          // #8B949E
    error                = CoralError,             // #E5534B
    onError              = Color(0xFFFFFFFF),
    errorContainer       = Color(0xFF8C1D18),
    onErrorContainer     = Color(0xFFF9DEDC),
    outline              = Color(0xFF30363D),
    outlineVariant       = SurfaceMid,
)

// WCAG AAA boost — higher contrast palette for visually impaired users
private val HighContrastScheme = DefaultColorScheme.copy(
    background   = Color(0xFF000000),
    surface      = Color(0xFF0D1117),
    onBackground = Color(0xFFFFFFFF),
    onSurface    = Color(0xFFFFFFFF),
    primary      = Color(0xFF5CC8F8),    // brighter Telegram blue for AAA
    secondary    = Color(0xFF3FBA6A),    // brighter green for AAA
    error        = Color(0xFFFF6B6B),    // brighter error for AAA
)

/**
 * Always dark (per PRD); reacts to [highContrast] (WCAG AAA) and [bigText] (+25% font scale).
 */
@Composable
fun CarakaTheme(
    highContrast: Boolean = false,
    bigText: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (highContrast) HighContrastScheme else DefaultColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    val baseDensity = LocalDensity.current
    val density = if (bigText) {
        Density(density = baseDensity.density, fontScale = baseDensity.fontScale * 1.25f)
    } else baseDensity

    val statusColors = StatusColors(
        online    = EmeraldSuccess,   // #2ECC71 emerald
        hybrid    = SkyInfo,          // #58A6FF lighter blue
        meshOnly  = CoralError,       // #E5534B muted red
        relay     = TealPrimary,      // #2AABEE Telegram blue
        sos       = CoralError,
        authority = EmeraldSuccess,
        direct    = TealPrimary
    )

    CompositionLocalProvider(
        LocalDensity provides density,
        LocalStatusColors provides statusColors,
        LocalCarakaShapes provides CarakaShapes(),
        LocalCarakaDimens provides CarakaDimens()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = {
                CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
                    content()
                }
            }
        )
    }
}
