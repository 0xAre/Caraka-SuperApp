package com.example.garudamesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks message IDs that have been relayed by this node.
 * Used for deduplication — prevents relaying the same message twice.
 * PRD: "Message ID dedup (no duplicate relay)"
 */
@Entity(tableName = "relayed_messages")
data class RelayedMessageEntity(
    @PrimaryKey
    val messageId: String,             // Same as MessageEntity.id

    val relayedAt: Long = System.currentTimeMillis(),
    val relayedTo: String? = null      // Comma-separated peer IDs this was forwarded to
)
