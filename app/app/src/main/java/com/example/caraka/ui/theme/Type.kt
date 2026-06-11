package com.example.caraka.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.caraka.R

// ── Downloadable Google Fonts provider ──────────────────────────────────────
private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val SpaceGroteskGF = GoogleFont("Space Grotesk") // Headings / Numbers — Web3, geometric
private val InterGF = GoogleFont("Inter")                // Body — high legibility, modern
private val DotGothicGF = GoogleFont("DotGothic16")      // Pixel/Grid — "Bitcount Grid" alternative for hashes/stats

val SpaceGroteskFamily = FontFamily(
    Font(googleFont = SpaceGroteskGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Light),
    Font(googleFont = SpaceGroteskGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = SpaceGroteskGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = SpaceGroteskGF, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = SpaceGroteskGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold)
)

val InterFamily = FontFamily(
    Font(googleFont = InterGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = InterGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = InterGF, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = InterGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold)
)

val DotGothicFamily = FontFamily(
    Font(googleFont = DotGothicGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal)
)

// ── Typography scale ────────────────────────────────────────────────────────
// Space Grotesk for display/titles/numbers (premium Web3 feel)
// Inter for body (readable UI text)
// DotGothic16 for specific grid/pixel elements (Bitcount Grid Alternative)
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold,    fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold,   fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold,   fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold,      fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium,    fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodyLarge  = TextStyle(fontFamily = InterFamily,    fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily,    fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall  = TextStyle(fontFamily = InterFamily,    fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium,    fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.1.sp)
)
