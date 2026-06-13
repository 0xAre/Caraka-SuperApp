package com.example.caraka.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A transport unit in the DTN outbox (architecture baseline: Message Lifecycle, D4/D7).
 *
 * This is deliberately SEPARATE from [MessageEntity]: [MessageEntity] is the user-facing chat log,
 * while this row is the transport-layer unit that the queue-processor sends, retries, and carries.
 * Mixing the two was identified as a blocker (B4) in the gap analysis.
 *
 * Lifecycle of [state]: QUEUED → SENT → DELIVERED, or → EXPIRED / FAILED.
 * Created by EU-0.4 (schema only). The fields are written/read starting in Phase 1 (EU-1.x) and
 * Phase 2 (EU-2.x); this entity introduces no behaviour on its own.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey
    val id: String,                 // same UUID as the MeshProtocol/MessageEntity id (correlation)

    val recipientId: String,        // target peerId for this unicast unit
    val payloadJson: String,        // the full MeshProtocol JSON to (re)transmit

    val state: String = "QUEUED",   // QUEUED | SENT | DELIVERED | EXPIRED | FAILED
    val priority: String = "NORMAL",// EMERGENCY | HIGH | NORMAL | (status) — drives drop policy

    val attemptCount: Int = 0,      // number of send attempts so far (bounded by MeshPolicy)
    val nextAttemptAt: Long = 0,    // earliest time (ms) the next retry may run
    val createdAt: Long = System.currentTimeMillis(),
    val ttlExpiry: Long = 0,        // coarse wall-clock expiry bound (hop TTL lives in payload)
    val replicaCount: Int = 0       // reserved for future controlled-replication; unused in v1
)
