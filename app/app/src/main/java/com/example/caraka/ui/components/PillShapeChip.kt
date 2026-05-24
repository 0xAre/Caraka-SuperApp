package com.example.caraka.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.SurfaceDark
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary

@Composable
fun PillShapeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = AmberAccent
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) selectedColor.copy(alpha = 0.15f) else GlassSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) selectedColor.copy(alpha = 0.5f) else SurfaceDark
        )
    ) {
        Text(
            text = text,
            color = if (isSelected) selectedColor else TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
