package com.example.caraka.ui.theme

import androidx.compose.ui.graphics.Color

// ─── LIGHT SURFACES ────────────────────────────────────────────────────────
val CanvasLight    = Color(0xFFF5F7FA)
val SurfaceLow     = Color(0xFFFFFFFF)
val SurfaceMid     = Color(0xFFF8FAFC)
val SurfaceHigh    = Color(0xFFEDF2F7)

// ─── DARK SURFACES ─────────────────────────────────────────────────────────
val CanvasDarkBg       = Color(0xFF0D1117)   // deepest background
val SurfaceDarkLow     = Color(0xFF161B22)   // cards / sheets
val SurfaceDarkMid     = Color(0xFF1C2128)   // elevated surfaces
val SurfaceDarkHigh    = Color(0xFF252B34)   // tooltips / dialogs

// ─── BRAND & SEMANTIC ──────────────────────────────────────────────────────
val TelegramBlue          = Color(0xFF229ED9)
val TelegramBlueStrong    = Color(0xFF087DB5)
val TelegramBlueContainer = Color(0xFFE5F4FB)
val TelegramBlueDark      = Color(0xFF58B8E8)   // lighter for dark mode
val TelegramBlueDarkCont  = Color(0xFF0E2D3F)   // container for dark mode

val SuccessGreen      = Color(0xFF168A4B)
val SuccessGreenDark  = Color(0xFF34D47A)
val WarningAmber      = Color(0xFFD98200)
val WarningAmberDark  = Color(0xFFFFC043)
val DangerRed         = Color(0xFFD93025)
val DangerRedDark     = Color(0xFFFF6B6B)
val InfoBlue          = Color(0xFF3478F6)
val InfoBlueDark      = Color(0xFF6EA6FF)

// Courier "Stealth" accent — violet teredam (bukan neon). Pasangan on-color
// flip antar-tema agar konten di atas fill Stealth tetap kontras.
val StealthViolet     = Color(0xFF6B5E95)   // light: violet medium di atas surface terang
val OnStealthLight    = Color(0xFFFFFFFF)   // konten di atas StealthViolet (light)
val StealthVioletDark = Color(0xFFC9B7E0)   // dark: lavender muda agar terbaca di canvas gelap
val OnStealthDark     = Color(0xFF2A1A40)   // konten di atas StealthVioletDark (dark)

// ─── LIGHT TEXT & BORDERS ──────────────────────────────────────────────────
val TextPrimary    = Color(0xFF111827)
val TextSecondary  = Color(0xFF64748B)
val TextTertiary   = Color(0xFF8792A2)
val BorderSubtle   = Color(0xFFDDE3EA)
val DividerSubtle  = Color(0xFFE8ECF1)

// ─── DARK TEXT & BORDERS ───────────────────────────────────────────────────
val TextPrimaryDark   = Color(0xFFE6EDF3)
val TextSecondaryDark = Color(0xFF8B949E)
val TextTertiaryDark  = Color(0xFF6E7681)
val BorderSubtleDark  = Color(0xFF30363D)
val DividerSubtleDark = Color(0xFF21262D)

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
