package com.example.caraka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.ui.theme.CyanAccent
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.SurfaceDark
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary
import com.example.caraka.ui.theme.Typography
import com.example.caraka.ui.util.rememberHaptics

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.chat_input_placeholder)
) {
    val haptics = rememberHaptics()
    val sendCd = stringResource(R.string.cd_send_btn)

    Surface(
        modifier = modifier
            .padding(16.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = CyanAccent, spotColor = SurfaceDark),
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
            Box(modifier = Modifier.padding(end = 8.dp).fillMaxWidth(0.85f)) {
                if (value.isEmpty()) {
                    Text(text = placeholder, color = TextSecondary, style = Typography.bodyLarge)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = Typography.bodyLarge.copy(color = TextPrimary),
                    cursorBrush = SolidColor(CyanAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            IconButton(
                onClick = {
                    haptics.light()
                    onSend()
                },
                enabled = value.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .background(if (value.isNotBlank()) CyanAccent.copy(alpha = 0.2f) else SurfaceDark, CircleShape)
                    .semantics { contentDescription = sendCd }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if (value.isNotBlank()) CyanAccent else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
