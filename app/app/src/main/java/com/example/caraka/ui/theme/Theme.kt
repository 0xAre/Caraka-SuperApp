package com.example.caraka.ui.theme

import android.app.Activity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val DefaultColorScheme = darkColorScheme(
    primary = TealPrimary,
    secondary = CyanSecondary,
    tertiary = SkyInfo,
    background = CanvasDark,
    surface = SurfaceLow,
    error = CoralError,
    onPrimary = CanvasDark,
    onSecondary = CanvasDark,
    onTertiary = CanvasDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onError = TextPrimary
)

// WCAG AAA boost — pure white-on-pure-navy and stronger teal/amber for visually impaired users.
private val HighContrastScheme = DefaultColorScheme.copy(
    background = androidx.compose.ui.graphics.Color(0xFF000814),
    surface    = androidx.compose.ui.graphics.Color(0xFF0B1A33),
    onBackground = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface    = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primary    = androidx.compose.ui.graphics.Color(0xFF4DD4E8), // brighter teal
    secondary  = androidx.compose.ui.graphics.Color(0xFF5AC8FA)  // brighter cyan
)

/**
 * CARAKA theme.
 *
 * Always dark (per PRD), but reacts to:
 *  • [highContrast] — swaps to a WCAG-AAA boosted palette.
 *  • [bigText]      — bumps the system density font-scale by 25% so labels & body grow.
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
        online = EmeraldSuccess,
        hybrid = CyanSecondary,
        meshOnly = CoralError,
        relay = SkyInfo,
        sos = CoralError,
        authority = EmeraldSuccess,
        direct = TealPrimary
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
                // Make sure default Text color follows the scheme rather than parent stale colour.
                CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
                    content()
                }
            }
        )
    }
}
