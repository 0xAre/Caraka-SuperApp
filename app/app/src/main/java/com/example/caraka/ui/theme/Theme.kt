package com.example.caraka.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = TelegramBlueStrong,
    onPrimary = Color.White,
    primaryContainer = TelegramBlueContainer,
    onPrimaryContainer = Color(0xFF073B53),
    secondary = InfoBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F0FF),
    onSecondaryContainer = Color(0xFF143E82),
    tertiary = WarningAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF1D6),
    onTertiaryContainer = Color(0xFF5C3700),
    background = CanvasLight,
    onBackground = TextPrimary,
    surface = SurfaceLow,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFE8E5),
    onErrorContainer = Color(0xFF7D1711),
    outline = Color(0xFFD0D5DD),
    outlineVariant = BorderSubtle,
)

private val DarkColorScheme = darkColorScheme(
    primary = TelegramBlueDark,
    onPrimary = Color(0xFF003A57),
    primaryContainer = TelegramBlueDarkCont,
    onPrimaryContainer = Color(0xFFB3E5FF),
    secondary = InfoBlueDark,
    onSecondary = Color(0xFF00225A),
    secondaryContainer = Color(0xFF163172),
    onSecondaryContainer = Color(0xFFD5E3FF),
    tertiary = WarningAmberDark,
    onTertiary = Color(0xFF3E2700),
    tertiaryContainer = Color(0xFF593A00),
    onTertiaryContainer = Color(0xFFFFDDB4),
    background = CanvasDarkBg,
    onBackground = TextPrimaryDark,
    surface = SurfaceDarkLow,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDarkMid,
    onSurfaceVariant = TextSecondaryDark,
    error = DangerRedDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = BorderSubtleDark,
    outlineVariant = DividerSubtleDark,
)

private val HighContrastScheme = LightColorScheme.copy(
    background = Color.White,
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF333333),
    primary = Color(0xFF005F8D),
    secondary = Color(0xFF174EA6),
    error = Color(0xFFB3261E),
    outline = Color(0xFF5F6368),
)

@Composable
fun CarakaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    bigText: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        highContrast -> HighContrastScheme
        darkTheme    -> DarkColorScheme
        else         -> LightColorScheme
    }
    val isLight = !darkTheme && !highContrast
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = isLight
                isAppearanceLightNavigationBars = isLight
            }
        }
    }

    val baseDensity = LocalDensity.current
    val density = if (bigText) {
        Density(baseDensity.density, baseDensity.fontScale * 1.25f)
    } else {
        baseDensity
    }

    val shapes = CarakaShapes()
    val statusColors = if (darkTheme) {
        StatusColors(
            online    = SuccessGreenDark,
            hybrid    = TelegramBlueDark,
            meshOnly  = WarningAmberDark,
            relay     = InfoBlueDark,
            sos       = DangerRedDark,
            authority = SuccessGreenDark,
            direct    = TelegramBlueDark,
            stealth   = StealthVioletDark,
            onStealth = OnStealthDark
        )
    } else {
        StatusColors(
            online    = SuccessGreen,
            hybrid    = TelegramBlueStrong,
            meshOnly  = WarningAmber,
            relay     = InfoBlue,
            sos       = DangerRed,
            authority = SuccessGreen,
            direct    = TelegramBlueStrong,
            stealth   = StealthViolet,
            onStealth = OnStealthLight
        )
    }

    CompositionLocalProvider(
        LocalDensity provides density,
        LocalStatusColors provides statusColors,
        LocalCarakaShapes provides shapes,
        LocalCarakaDimens provides CarakaDimens()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = MaterialTheme.shapes.copy(
                extraSmall = shapes.xs,
                small = shapes.sm,
                medium = shapes.md,
                large = shapes.lg,
                extraLarge = shapes.xl
            )
        ) {
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
                content()
            }
        }
    }
}
