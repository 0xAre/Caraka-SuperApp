package com.example.garudamesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a message in the Garuda Mesh network.
 * Covers all message types: TEXT (direct/group), SOS (broadcast), SYSTEM (join/leave).
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,                    // UUID v4, unique across entire mesh

    val type: String,                  // "TEXT", "SOS", "SYSTEM"
    val senderId: String,              // Fingerprint of sender's public key
    val senderName: String,            // Display name of sender
    val senderRole: String,            // "CIVILIAN", "BPBD", "POLRI", "PMI"

    val recipientId: String,           // Fingerprint of recipient OR "BROADCAST"

    val content: String,               // Plaintext content (decrypted locally)
    val encryptedPayload: String?,     // Original encrypted payload (base64), null for SOS/SYSTEM

    val timestamp: Long,               // Unix timestamp (millis)
    val ttl: Int,                      // Time-to-live (hop count remaining)
    val priority: String,              // "EMERGENCY", "HIGH", "NORMAL"

    val signature: String?,            // Ed25519 signature (base64)

    // SOS-specific fields
    val sosCategory: String?,          // "MEDICAL", "FIRE", "SECURITY", "DISASTER" (null if not SOS)
    val latitude: Double?,             // GPS latitude (null if unavailable)
    val longitude: Double?,            // GPS longitude (null if unavailable)

    // Status tracking
    val isIncoming: Boolean,           // true = received, false = sent by this device
    val isRead: Boolean = false,       // Read receipt (local only)
    val flagCount: Int = 0,            // Number of "suspicious" flags from other users
    val isRelayed: Boolean = false,    // Whether this message has been relayed to other peers
    val createdAt: Long = System.currentTimeMillis()
)
