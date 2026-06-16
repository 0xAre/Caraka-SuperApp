package com.example.caraka.ui.theme

import androidx.compose.ui.graphics.Color

// CARAKA Super App light surfaces.
val CanvasLight = Color(0xFFF5F7FA)
val SurfaceLow = Color(0xFFFFFFFF)
val SurfaceMid = Color(0xFFF8FAFC)
val SurfaceHigh = Color(0xFFEDF2F7)

// Brand and semantic palette.
val TelegramBlue = Color(0xFF229ED9)
val TelegramBlueStrong = Color(0xFF087DB5)
val TelegramBlueContainer = Color(0xFFE5F4FB)
val SuccessGreen = Color(0xFF168A4B)
val WarningAmber = Color(0xFFD98200)
val DangerRed = Color(0xFFD93025)
val InfoBlue = Color(0xFF3478F6)

val TextPrimary = Color(0xFF111827)
val TextSecondary = Color(0xFF64748B)
val TextTertiary = Color(0xFF8792A2)
val BorderSubtle = Color(0xFFDDE3EA)
val DividerSubtle = Color(0xFFE8ECF1)

// Compatibility aliases retained while screens migrate to semantic M3 roles.
val CanvasDark = CanvasLight
val NavyBackground = CanvasLight
val SurfaceDark = SurfaceLow
val GlassSurface = SurfaceLow
val IosSystemBlue = TelegramBlue
val IosSystemRed = DangerRed
val IosSystemGreen = SuccessGreen
val IosSystemCyan = InfoBlue
val IosLabelPrimary = TextPrimary
val IosLabelSecondary = TextSecondary
val TealPrimary = TelegramBlue
val TealMuted = TelegramBlueContainer
val CyanSecondary = InfoBlue
val EmeraldSuccess = SuccessGreen
val CoralError = DangerRed
val SkyInfo = InfoBlue
val StrokeLight = BorderSubtle
val StrokeLightAlpha = DividerSubtle
val CyanAccent = TelegramBlue
val NeonMint = SuccessGreen
val WarningCyan = WarningAmber
val DisasterBlue = InfoBlue
val OutgoingChat = TelegramBlueContainer
val IncomingChat = SurfaceLow
val TextMuted = TextSecondary

// Discovery coverage waves and location marker.
val DiscoveryRippleColor = Color(0xFF006FC8)
val DiscoveryCoreGlowColor = Color(0xFF159FE2)
val DiscoveryConnectedHaloColor = SuccessGreen

const val DiscoveryRippleDurationMs = 5_400L
const val DiscoveryRippleMinRadiusRatio = 0.09f
const val DiscoveryRippleMaxRadiusRatio = 0.49f
const val DiscoveryRippleMaxAlpha = 0.24f
const val DiscoveryStaticHaloAlpha = 0.17f

// Legacy aliases kept for any remaining references.
val RadarCoreCyan = Color(0xFF65D5FA)
val RadarBloomBlue = TelegramBlue
