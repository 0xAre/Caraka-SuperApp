package com.example.caraka.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class CarakaDimens(
    val screenPadding: Dp = 16.dp,
    val sectionGap: Dp = 20.dp,
    val sectionHeaderTop: Dp = 24.dp,
    val sectionHeaderBottom: Dp = 10.dp,
    val metricValueLabelGap: Dp = 8.dp,
    val cardPadding: Dp = 12.dp,
    val metricValueMinHeight: Dp = 36.dp,
    val navHeight: Dp = 80.dp,
    val itemSpacing: Dp = 8.dp,
    val touchTarget: Dp = 48.dp,
    val illustrationCompact: Dp = 112.dp,
    val illustrationDefault: Dp = 156.dp
)

val LocalCarakaDimens = staticCompositionLocalOf { CarakaDimens() }

val DepthNone = 0.dp
val DepthSoft = 2.dp
