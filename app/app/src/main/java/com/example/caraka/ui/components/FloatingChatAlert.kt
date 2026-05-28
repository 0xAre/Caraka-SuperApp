package com.example.caraka.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.network.ChatAlert
import com.example.caraka.ui.theme.*

/**
 * A floating heads-up alert that slides down from the top when a chat message arrives.
 * Tappable to open the conversation; auto-dismiss is handled by the caller.
 */
@Composable
fun FloatingChatAlert(
    alert: ChatAlert?,
    onClick: (ChatAlert) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = alert != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val current = alert ?: return@AnimatedVisibility
        val isAuthority = current.senderRole in listOf("BPBD", "POLRI", "PMI")
        val accent = if (isAuthority) NeonMint else AmberAccent

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .shadow(20.dp, RoundedCornerShape(20.dp), ambientColor = accent, spotColor = SurfaceDark)
                .clip(RoundedCornerShape(20.dp))
                .background(GlassSurface)
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .clickable { onClick(current) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f))
                    .border(1.5.dp, accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isAuthority) Icons.Default.Verified else Icons.AutoMirrored.Filled.Message,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        current.senderName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("· new message", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    current.content,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Tap",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    }
}
