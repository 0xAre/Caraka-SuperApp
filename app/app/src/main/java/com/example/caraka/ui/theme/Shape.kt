package com.example.caraka.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

@Immutable
data class CarakaShapes(
    val xs:   RoundedCornerShape = RoundedCornerShape(4.dp),
    val sm:   RoundedCornerShape = RoundedCornerShape(8.dp),
    val md:   RoundedCornerShape = RoundedCornerShape(12.dp),
    val lg:   RoundedCornerShape = RoundedCornerShape(16.dp),
    val xl:   RoundedCornerShape = RoundedCornerShape(20.dp),
    val full: RoundedCornerShape = RoundedCornerShape(50)
)

val LocalCarakaShapes = staticCompositionLocalOf { CarakaShapes() }
