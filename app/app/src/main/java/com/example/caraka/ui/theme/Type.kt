package com.example.caraka.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.caraka.R

/** Display — screen titles, metric values, interactive labels. */
val DisplayFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
)

/** Body — subtitles, descriptions, chat, metadata. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Data — peer ID, coordinates, hash. */
val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

// Compatibility aliases retained while screens migrate.
val PlusJakartaSansFamily: FontFamily = DisplayFamily
val SpaceGroteskFamily: FontFamily = DisplayFamily
val DotGothicFamily: FontFamily = MonoFamily

private val display = DisplayFamily
private val body = InterFamily
private val mono = MonoFamily

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.15).sp
    ),
    titleLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

/** Semantic tokens — prefer these in shared components. */
object CarakaTextStyles {
    val screenTitle = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    )

    val screenSubtitle = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    val sectionHeader = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    )

    val listTitle = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    )

    val listSubtitle = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    val serviceLabel = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp
    )

    val profileName = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp
    )

    val statValue = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    )

    val statValueCompact = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.2).sp
    )

    val statLabel = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )

    val statusPrimary = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )

    val statusSecondary = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    val bodyDefault = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    val navLabel = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )

    val navLabelSelected = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )

    val badge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 12.sp
    )

    val monoData = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    val emptyTitle = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    )

    val chatMessage = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )

    val chatSender = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )

    val chatTimestamp = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )

    val dialogTitle = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    )

    val buttonLabel = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )

    val sosCategoryTitle = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )

    val sosButtonLabel = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )

    // ── Alias untuk CourierScreen dan komponen baru ──────────────────────────────────────────

    /** Section header di dalam screen (e.g. "Bundle Dibawa"). */
    val sectionTitle get() = listTitle

    /** Label di atas form field. */
    val fieldLabel get() = sectionHeader

    /** Caption kecil — subtitle di TopAppBar, metadata. */
    val caption get() = statusSecondary
}
