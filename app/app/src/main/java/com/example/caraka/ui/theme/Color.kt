package com.example.caraka.ui.theme

import androidx.compose.ui.graphics.Color

// ── Canvas & Surfaces (GitHub dark scale) ─────────────────────────────────────
val CanvasDark  = Color(0xFF0D1117)   // GitHub dark canvas — not pure black
val SurfaceLow  = Color(0xFF161B22)
val SurfaceMid  = Color(0xFF21262D)
val SurfaceHigh = Color(0xFF262C36)

// ── Palette ───────────────────────────────────────────────────────────────────
val IosSystemBlue  = Color(0xFF2AABEE)   // Telegram blue (name kept for compat)
val IosSystemRed   = Color(0xFFE5534B)   // Muted red
val IosSystemGreen = Color(0xFF2ECC71)   // Emerald success
val IosSystemCyan  = Color(0xFF58A6FF)   // Lighter blue info

val IosLabelPrimary   = Color(0xFFE6EDF3)   // Warm white text
val IosLabelSecondary = Color(0xFF8B949E)   // Muted gray text

// ── Semantic tokens ───────────────────────────────────────────────────────────
val TealPrimary    = IosSystemBlue            // Telegram blue primary
val TealMuted      = IosSystemBlue.copy(alpha = 0.16f)
val CyanSecondary  = Color(0xFF238636)        // Gojek-tone success green
val EmeraldSuccess = IosSystemGreen
val CoralError     = IosSystemRed
val SkyInfo        = IosSystemCyan

val TextPrimary   = IosLabelPrimary
val TextSecondary = IosLabelSecondary
val TextTertiary  = Color(0xFF6E7681)

val StrokeLight      = Color.White.copy(alpha = 0.08f)
val StrokeLightAlpha = Color.White.copy(alpha = 0.04f)
val BorderSubtle     = Color(0xFFFFFFFF).copy(alpha = 0.08f)

// ── Backward Compatibility Aliases ────────────────────────────────────────────
val NavyBackground = CanvasDark
val SurfaceDark    = SurfaceLow
val GlassSurface   = SurfaceMid          // was deprecated glassmorphism; now solid surface
val CyanAccent     = TealPrimary         // → Telegram blue
val DangerRed      = CoralError
val NeonMint       = EmeraldSuccess
val WarningCyan    = Color(0xFFD29922)   // → amber warning (was cyan)
val DisasterBlue   = SkyInfo

val OutgoingChat = Color(0xFF1565C0)   // Dark blue for outgoing chat container
val IncomingChat = SurfaceMid
val TextMuted    = TextSecondary
