package com.example.caraka.ui.theme

import androidx.compose.ui.graphics.Color

val CanvasDark = Color(0xFF000000) // Pitch Black (iOS/Web3 style)
val SurfaceLow = Color(0xFF0A0A0C)
val SurfaceMid = Color(0xFF141416)
val SurfaceHigh = Color(0xFF1C1C1E) // iOS Dark Mode Surface

// Apple iOS Native Colors (Dark Mode)
val IosSystemBlue = Color(0xFF0A84FF)
val IosSystemRed = Color(0xFFFF3B30)
val IosSystemGreen = Color(0xFF30D158)
val IosSystemCyan = Color(0xFF5AC8FA)
val IosLabelPrimary = Color(0xFFFFFFFF)
val IosLabelSecondary = Color(0xFFEBEBF5).copy(alpha = 0.6f)

val TealPrimary = IosSystemBlue
val TealMuted = IosSystemBlue.copy(alpha = 0.16f)
val CyanSecondary = IosSystemCyan
val EmeraldSuccess = IosSystemGreen
val CoralError = IosSystemRed
val SkyInfo = IosSystemBlue

val TextPrimary = IosLabelPrimary
val TextSecondary = IosLabelSecondary
val TextTertiary = IosLabelSecondary.copy(alpha = 0.5f)

val StrokeLight = Color.White.copy(alpha = 0.1f)
val StrokeLightAlpha = Color.White.copy(alpha = 0.05f)
val BorderSubtle = Color(0xFFFFFFFF).copy(alpha = 0.08f)

// Backward Compatibility Aliases
val NavyBackground = CanvasDark
val SurfaceDark = SurfaceLow
val GlassSurface = Color(0x3314243D) // Deprecated
val CyanAccent = CyanSecondary
val DangerRed = CoralError
val NeonMint = EmeraldSuccess
val WarningCyan = CyanSecondary
val DisasterBlue = SkyInfo

val OutgoingChat = TealPrimary.copy(alpha = 0.3f)
val IncomingChat = SurfaceLow
val TextMuted = TextSecondary