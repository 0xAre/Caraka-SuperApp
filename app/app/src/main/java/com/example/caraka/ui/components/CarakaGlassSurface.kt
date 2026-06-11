package com.example.caraka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.example.caraka.ui.theme.LocalCarakaShapes

import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun CarakaGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = LocalCarakaShapes.current.md,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(com.example.caraka.ui.theme.SurfaceHigh) // Solid iOS 1C1C1E
    ) {
        content()
    }
}
