package com.example.caraka.network

import com.google.gson.Gson

/**
 * A single peer's shareable identity, gossiped in PEER_LIST messages so that every
 * device learns about every other device (full-mesh awareness), even peers it has
 * never directly heard from — e.g. client B learning about client C through GO A.
 */
data class PeerShare(
    val peerId: String,
    val name: String,
    val role: String,
    val encPub: String,
    val signPub: String,
    val ip: String? = null   // last-known LAN IP (for direct unicast); null if unknown
)

/**
 * Wire protocol for mesh communication.
 * All messages sent over TCP sockets use this JSON format with length-prefixed framing.
 */
/**
 * Message types:
 *  HANDSHAKE  — identity exchange on new socket connection
 *  TEXT       — encrypted direct or broadcast message
 *  SOS        — unencrypted emergency broadcast (signed)
 *  FLAG       — report a message as suspicious; content = target message ID
 *  ACK        — delivery acknowledgement; content = target message ID
 *  CONNECTION_REQUEST  — request to establish connection
 *  CONNECTION_ACCEPT   — accept incoming connection request
 *  CONNECTION_REJECT   — reject incoming connection request
 *  PEER_LIST  — gossip of known peers for full-mesh awareness
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
    val signingKey: String? = null,     // Ed25519 base64
    // Connection request fields
    val autoAccept: Boolean = false,    // For QR: auto-accept without dialog
    val seenBy: List<String> = emptyList(),  // Peers that already relayed this message
    // PEER_LIST gossip payload
    val peers: List<PeerShare>? = null
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
