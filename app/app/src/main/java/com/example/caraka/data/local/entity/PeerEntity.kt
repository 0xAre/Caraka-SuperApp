package com.example.caraka.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a peer (node) discovered in the mesh network.
 * Tracks connection state, identity, and public keys for E2E encryption.
 */
@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey
    val id: String,                    // Fingerprint of peer's public key

    val deviceName: String,            // WiFi Direct device name
    val displayName: String,           // User-set display name
    val role: String,                  // "CIVILIAN", "BPBD", "POLRI", "PMI"

    val publicKey: String,             // X25519 public key (base64) for encryption
    val signingKey: String,            // Ed25519 public key (base64) for signature verification

    val isVerified: Boolean = false,   // Verified via QR code (in-person)
    val isAuthority: Boolean = false,  // Pre-registered authority identity

    val macAddress: String?,           // WiFi Direct MAC address
    val lastSeen: Long,                // Last time this peer was active (unix millis)
    val isConnected: Boolean = false,  // Currently connected in mesh
    val hopCount: Int = 0,             // How many hops away (0 = direct peer)

    val createdAt: Long = System.currentTimeMillis()
)
