package com.example.caraka.network

import com.google.gson.Gson

/**
 * Wire protocol for mesh communication.
 * All messages sent over TCP sockets use this JSON format with length-prefixed framing.
 *
 * Types: HANDSHAKE, TEXT, SOS, SYSTEM
 */
/**
 * Message types:
 *  HANDSHAKE  — identity exchange on new socket connection
 *  TEXT       — encrypted direct or broadcast message
 *  SOS        — unencrypted emergency broadcast (signed)
 *  FLAG       — report a message as suspicious; content = target message ID
 *  ACK        — delivery acknowledgement; content = target message ID
 */
data class MeshProtocol(
    val type: String,
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val recipientId: String,
    val content: String,
    val encryptedPayload: String? = null,
    val timestamp: Long,
    val ttl: Int = 5,
    val priority: String = "NORMAL",
    val signature: String? = null,
    val sosCategory: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val flagCount: Int = 0,             // cumulative flag count propagated in mesh
    // Handshake-specific fields
    val publicKey: String? = null,      // X25519 base64
    val signingKey: String? = null      // Ed25519 base64
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: String): MeshProtocol? {
            return try {
                gson.fromJson(json, MeshProtocol::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String = gson.toJson(this)
}
