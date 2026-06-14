package com.example.caraka.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class CarakaDimens(
    val screenPadding: Dp = 16.dp,
    val sectionGap:    Dp = 20.dp,
    val cardPadding:   Dp = 14.dp,
    val navHeight:     Dp = 64.dp,
    val itemSpacing:   Dp = 8.dp
)

val LocalCarakaDimens = staticCompositionLocalOf { CarakaDimens() }
