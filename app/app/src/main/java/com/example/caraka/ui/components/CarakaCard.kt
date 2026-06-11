package com.example.caraka.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.caraka.ui.theme.BorderSubtle
import com.example.caraka.ui.theme.LocalCarakaShapes

import androidx.compose.ui.graphics.Brush

@Composable
fun CarakaCard(
    modifier: Modifier = Modifier,
    shape: Shape = LocalCarakaShapes.current.md,
    containerColor: Color = com.example.caraka.ui.theme.SurfaceLow,
    hasSubtleBorder: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.02f),
            Color.White.copy(alpha = 0.05f)
        )
    )
    val border = if (hasSubtleBorder) BorderStroke(1.dp, borderBrush) else null
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        border = border,
        content = content
    )
}
