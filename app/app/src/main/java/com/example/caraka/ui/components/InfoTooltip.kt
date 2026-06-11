package com.example.caraka.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.caraka.ui.theme.CyanAccent
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.SurfaceDark
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary

/**
 * A small "(i)" icon that toggles a glass-style popup with explanatory text on tap.
 * Used inline next to technical labels (TTL, Hop count, peer ID, mesh node…) so users
 * can self-serve definitions without leaving the screen. Improves Nielsen #10:
 * Help & Documentation and #6: Recognition over recall.
 */
@Composable
fun InfoTooltip(
    title: String,
    description: String,
    contentDescription: String = "More info",
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }

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
                        .shadow(12.dp, RoundedCornerShape(14.dp), ambientColor = CyanAccent, spotColor = SurfaceDark)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, CyanAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .background(GlassSurface),
                    color = GlassSurface
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(title, color = TextPrimary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.size(4.dp))
                        Text(description, color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
