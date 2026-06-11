package com.example.caraka.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

@Immutable
data class CarakaShapes(
    val sm: RoundedCornerShape = RoundedCornerShape(12.dp),
    val md: RoundedCornerShape = RoundedCornerShape(16.dp),
    val lg: RoundedCornerShape = RoundedCornerShape(20.dp),
    val xl: RoundedCornerShape = RoundedCornerShape(28.dp),
    val full: RoundedCornerShape = RoundedCornerShape(50)
)

val LocalCarakaShapes = staticCompositionLocalOf { CarakaShapes() }
