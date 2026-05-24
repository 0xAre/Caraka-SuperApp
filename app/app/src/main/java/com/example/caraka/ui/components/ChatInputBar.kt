package com.example.caraka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.SurfaceDark
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary
import com.example.caraka.ui.theme.Typography

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ketik pesan darurat..."
) {
    Surface(
        modifier = modifier
            .padding(16.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = AmberAccent, spotColor = SurfaceDark),
        shape = RoundedCornerShape(24.dp),
        color = GlassSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = TextSecondary,
                        style = Typography.bodyLarge
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = Typography.bodyLarge.copy(color = TextPrimary),
                    cursorBrush = SolidColor(AmberAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .background(if (value.isNotBlank()) AmberAccent.copy(alpha = 0.2f) else SurfaceDark, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (value.isNotBlank()) AmberAccent else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
