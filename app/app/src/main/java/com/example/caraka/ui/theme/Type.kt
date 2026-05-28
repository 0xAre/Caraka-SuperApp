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

private val RajdhaniGF = GoogleFont("Rajdhani")          // Display / titles — tactical sci-fi
private val InterGF = GoogleFont("Inter")                // Body — high legibility
private val JetBrainsMonoGF = GoogleFont("JetBrains Mono") // Mono — IDs, hashes

val RajdhaniFamily = FontFamily(
    Font(googleFont = RajdhaniGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Light),
    Font(googleFont = RajdhaniGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = RajdhaniGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = RajdhaniGF, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = RajdhaniGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold)
)

val InterFamily = FontFamily(
    Font(googleFont = InterGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = InterGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = InterGF, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = InterGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = JetBrainsMonoGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = JetBrainsMonoGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium)
)

// ── Typography scale ────────────────────────────────────────────────────────
// Rajdhani for display/titles (tactical, condensed), Inter for body (readable),
// JetBrainsMono for peer IDs / hashes (monospace clarity).
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold,    fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = 1.sp),
    displayMedium = TextStyle(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold,   fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.5.sp),
    displaySmall = TextStyle(fontFamily = RajdhaniFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = 0.4.sp),
    headlineLarge = TextStyle(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold,   fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.3.sp),
    headlineMedium = TextStyle(fontFamily = RajdhaniFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.3.sp),
    titleLarge = TextStyle(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold,      fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontFamily = RajdhaniFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.4.sp),
    titleSmall = TextStyle(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium,    fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp),
    bodyLarge  = TextStyle(fontFamily = InterFamily,    fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily,    fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall  = TextStyle(fontFamily = InterFamily,    fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontFamily = RajdhaniFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.8.sp),
    labelMedium = TextStyle(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium,    fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp)
)
