package com.example.caraka.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.ui.theme.NeonMint
import com.example.caraka.ui.theme.TextSecondary
import com.example.caraka.ui.theme.WarningYellow

enum class MessageDeliveryUiStatus {
    QUEUED,
    SENT,
    RELAYED
}

@Composable
fun MessageStatusIcon(
    status: MessageDeliveryUiStatus,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val (icon, tint, labelRes) = when (status) {
        MessageDeliveryUiStatus.QUEUED -> Triple(Icons.Default.Schedule, WarningYellow, R.string.msg_status_queued)
        MessageDeliveryUiStatus.SENT -> Triple(Icons.Default.Check, TextSecondary, R.string.msg_status_sent)
        MessageDeliveryUiStatus.RELAYED -> Triple(Icons.Default.DoneAll, NeonMint, R.string.msg_status_relayed)
    }
    val label = stringResource(labelRes)

    Row(
        modifier = modifier.semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        if (showLabel) {
            Spacer(Modifier.width(3.dp))
            Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Derive UI delivery status from message fields (placeholder until ACK protocol lands). */
fun deriveMessageDeliveryStatus(isRelayed: Boolean, isIncoming: Boolean): MessageDeliveryUiStatus {
    if (isIncoming) return MessageDeliveryUiStatus.SENT
    return when {
        isRelayed -> MessageDeliveryUiStatus.RELAYED
        else -> MessageDeliveryUiStatus.SENT
    }
}
