package com.example.caraka.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.caraka.ui.theme.CarakaTextStyles
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.caraka.ui.theme.CyanAccent
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary

@Composable
fun InfoTooltip(
    title: String,
    description: String,
    contentDescription: String = "More info",
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val popupShape = RoundedCornerShape(12.dp)

    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = contentDescription,
            tint = TextSecondary,
            modifier = Modifier
                .size(18.dp)
                .clickable { open = !open }
                .semantics { this.contentDescription = contentDescription }
        )
        if (open) {
            Popup(
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .shadow(4.dp, popupShape)
                        .clip(popupShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), popupShape),
                    color = MaterialTheme.colorScheme.surface,
                    shape = popupShape
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(title, color = TextPrimary, style = CarakaTextStyles.statLabel)
                        }
                        Spacer(Modifier.size(4.dp))
                        Text(description, color = TextSecondary, style = CarakaTextStyles.statLabel)
                    }
                }
            }
        }
    }
}
