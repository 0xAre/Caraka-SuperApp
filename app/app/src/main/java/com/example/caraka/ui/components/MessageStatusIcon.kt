package com.example.caraka.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.NeonMint
import com.example.caraka.ui.theme.TextSecondary
import com.example.caraka.ui.theme.WarningCyan

/**
 * Honest delivery states (baseline D5/D6 / EU-1.5).
 *  - QUEUED:    in the outbox, not yet sent.
 *  - SENT:      handed to the transport ("sent to network") — NOT a delivery guarantee.
 *  - DELIVERED: a genuine end-to-end ACK was received. Set ONLY from a real ACK, never from
 *               overhearing/relay (D5).
 *  - FAILED:    retry budget exhausted or expired.
 * Note: there is deliberately no "delivered" state for SOS/broadcast (D6) — those are best-effort.
 */
enum class MessageDeliveryUiStatus {
    QUEUED,
    SENT,
    DELIVERED,
    FAILED
}

@Composable
fun MessageStatusIcon(
    status: MessageDeliveryUiStatus,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val (icon, tint, labelRes) = when (status) {
        MessageDeliveryUiStatus.QUEUED -> Triple(Icons.Default.Schedule, WarningCyan, R.string.msg_status_queued)
        MessageDeliveryUiStatus.SENT -> Triple(Icons.Default.Check, TextSecondary, R.string.msg_status_sent)
        MessageDeliveryUiStatus.DELIVERED -> Triple(Icons.Default.DoneAll, NeonMint, R.string.msg_status_delivered)
        MessageDeliveryUiStatus.FAILED -> Triple(Icons.Default.ErrorOutline, WarningCyan, R.string.msg_status_failed)
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
            Text(label, color = tint, style = CarakaTextStyles.badge)
        }
    }
}

/**
 * Derive the UI delivery status from the message's persisted [deliveryStatus] (EU-1.5).
 * Driven by the real outbox/ACK lifecycle (D4); "DELIVERED" originates only from a genuine
 * end-to-end ACK (D5). Incoming messages have no sender-side delivery status.
 */
fun deriveMessageDeliveryStatus(deliveryStatus: String, isIncoming: Boolean): MessageDeliveryUiStatus {
    if (isIncoming) return MessageDeliveryUiStatus.DELIVERED
    return when (deliveryStatus.uppercase()) {
        "DELIVERED" -> MessageDeliveryUiStatus.DELIVERED
        "QUEUED" -> MessageDeliveryUiStatus.QUEUED
        "FAILED", "EXPIRED" -> MessageDeliveryUiStatus.FAILED
        else -> MessageDeliveryUiStatus.SENT
    }
}
