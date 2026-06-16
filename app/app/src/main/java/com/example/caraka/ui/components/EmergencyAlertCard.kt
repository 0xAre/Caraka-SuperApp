package com.example.caraka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.data.local.entity.MessageEntity
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.DangerRed
import com.example.caraka.ui.theme.DisasterBlue
import com.example.caraka.ui.theme.WarningCyan
import com.example.caraka.ui.util.SosAlertText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmergencyAlertCard(
    alert: MessageEntity,
    modifier: Modifier = Modifier,
    timeLabel: String? = null
) {
    val resolvedCategory = SosAlertText.resolveCategory(alert.sosCategory, alert.content)
    val (icon, iconTint) = alertCategoryVisual(resolvedCategory)
    val categoryLabel = localizedCategoryLabel(resolvedCategory)
    val headline = SosAlertText.headline(resolvedCategory, alert.content)
    val unknownSenderLabel = stringResource(R.string.alert_unknown_sender)
    val minutesAgoTpl = stringResource(R.string.alert_minutes_ago)
    val time = timeLabel ?: formatAlertTime(alert.timestamp, stringResource(R.string.messages_just_now), minutesAgoTpl)
    val sender = formatSenderLine(alert.senderName, alert.senderRole, unknownSenderLabel)
    val hasUserDetail = headline != SosAlertText.defaultHeadline()
    val defaultSosLabel = stringResource(R.string.nav_sos)
    val alertCd = stringResource(R.string.alert_cd_template, sender, headline)

    CarakaCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = alertCd },
        hasSubtleBorder = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(iconTint)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconTint.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = categoryLabel.ifBlank { defaultSosLabel },
                        tint = iconTint,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (categoryLabel.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = iconTint.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = categoryLabel,
                                    style = CarakaTextStyles.badge,
                                    color = iconTint,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = headline,
                            style = CarakaTextStyles.listTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = time,
                            style = CarakaTextStyles.chatTimestamp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = sender,
                        style = CarakaTextStyles.listSubtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!hasUserDetail) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.alert_needs_help),
                            style = CarakaTextStyles.statusSecondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private data class AlertVisual(val icon: ImageVector, val tint: Color)

private fun alertCategoryVisual(category: String?): AlertVisual {
    return when (category?.uppercase(Locale.ROOT)) {
        "MEDICAL" -> AlertVisual(Icons.Default.LocalHospital, DangerRed)
        "FIRE" -> AlertVisual(Icons.Default.LocalFireDepartment, WarningCyan)
        "SECURITY" -> AlertVisual(Icons.Default.Security, WarningCyan)
        "DISASTER" -> AlertVisual(Icons.Default.Waves, DisasterBlue)
        else -> AlertVisual(Icons.Default.Warning, WarningCyan)
    }
}

@Composable
private fun localizedCategoryLabel(category: String?): String {
    return when (category?.uppercase(Locale.ROOT)) {
        "MEDICAL" -> stringResource(R.string.sos_cat_medical)
        "FIRE" -> stringResource(R.string.sos_cat_fire)
        "SECURITY" -> stringResource(R.string.sos_cat_security)
        "DISASTER" -> stringResource(R.string.sos_cat_disaster)
        else -> ""
    }
}

private fun formatSenderLine(name: String, role: String, unknownLabel: String): String {
    val displayName = name.ifBlank { unknownLabel }
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val roleLabel = role.ifBlank { "CIVILIAN" }
    return "$displayName · $roleLabel"
}

private fun formatAlertTime(timestamp: Long, justNowLabel: String, minutesAgoTpl: String): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> justNowLabel
        diff < 3_600_000 -> String.format(minutesAgoTpl, diff / 60_000)
        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
