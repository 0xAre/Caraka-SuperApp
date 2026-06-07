package com.example.caraka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.data.local.entity.MessageEntity
import com.example.caraka.ui.theme.DangerRed
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary
import com.example.caraka.ui.theme.WarningYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmergencyAlertCard(
    alert: MessageEntity,
    modifier: Modifier = Modifier,
    timeLabel: String? = null
) {
    val (icon, iconTint) = alertCategoryVisual(alert.sosCategory)
    val time = timeLabel ?: formatAlertTime(alert.timestamp)
    val sender = "${alert.senderName} (${alert.senderRole})"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, iconTint.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .semantics { contentDescription = "Alert from $sender: ${alert.content}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(alert.content, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sender, color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(time, color = TextSecondary, fontSize = 12.sp)
            }
            alert.sosCategory?.let { cat ->
                Spacer(Modifier.height(4.dp))
                Text(
                    cat,
                    color = iconTint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun alertCategoryVisual(category: String?): Pair<ImageVector, Color> {
    return when (category?.uppercase()) {
        "MEDICAL" -> Icons.Default.LocalHospital to DangerRed
        "FIRE" -> Icons.Default.Warning to WarningYellow
        "SECURITY" -> Icons.Default.Warning to WarningYellow
        "DISASTER" -> Icons.Default.Warning to com.example.caraka.ui.theme.DisasterBlue
        else -> Icons.Default.Warning to WarningYellow
    }
}

private fun formatAlertTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
