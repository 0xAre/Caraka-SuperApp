package com.example.caraka.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class CarakaDimens(
    val screenPadding: Dp = 16.dp,
    val sectionGap: Dp = 24.dp,
    val cardPadding: Dp = 16.dp,
    val navHeight: Dp = 72.dp,
    val itemSpacing: Dp = 12.dp,
    val touchTarget: Dp = 48.dp
)

val LocalCarakaDimens = staticCompositionLocalOf { CarakaDimens() }
