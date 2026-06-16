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
 *
 *  ── Courier Mode (Caraka Kurir) ──────────────────────────────────────────────────────────
 *  COURIER_OFFER     — A → B: tawarkan peran kurir + bundle metadata (tanpa isi)
 *  COURIER_ACCEPT    — B → A: B bersedia jadi kurir
 *  COURIER_REJECT    — B → A: B menolak
 *  COURIER_TRANSFER  — A → B: kirim bundle terenkripsi setelah B accept
 *  COURIER_BROADCAST — B → nearby: broadcast daftar claim token (Stealth Mode, TTL=1)
 *  COURIER_CLAIM     — Z → B: Z klaim token yang cocok
 *  COURIER_CHALLENGE — B → Z: challenge nonce untuk verifikasi kepemilikan kunci
 *  COURIER_PROOF     — Z → B: signature bukti kepemilikan EPK_priv
 *  COURIER_DELIVER   — B → Z: kirim encPayload setelah verifikasi
 *  COURIER_ACK       — Z → B: konfirmasi terima & berhasil decrypt
 *  COURIER_RECEIPT   — B → A: bundle berhasil disampaikan (Directed only, jika A masih reachable)
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
    val ttl: Int = MeshPolicy.DEFAULT_TTL,
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
    val targetId: String? = null,       // Specific recipient peerId (for unicast routing)
    val lanIp: String? = null,          // Sender's LAN IP (helps receiver build peerIpRegistry faster)
    val seenBy: List<String> = emptyList(),  // Peers that already relayed this message
    // PEER_LIST gossip payload
    val peers: List<PeerShare>? = null,
    // HOTSPOT_OFFER payload — emergency LocalOnlyHotspot credentials gossiped to neighbours so they
    // can converge onto ONE shared LAN for true M-to-N multi-peer (no router / Play Services / NAN).
    // Local-radio only: sent with ttl = 1 so it is never relayed beyond direct neighbours.
    val hotspotSsid: String? = null,
    val hotspotPass: String? = null,

    // ── Courier Mode (Caraka Kurir) ─────────────────────────────────────────────────────────
    // Semua field nullable dengan default null — backward compatible dengan pesan non-courier.

    /** ID unik bundle kurir. Dipakai untuk dedup dan tracking state. */
    val courierBundleId: String? = null,

    /** Mode kurir: "DIRECTED" | "STEALTH" */
    val courierMode: String? = null,

    /**
     * Claim token:
     *   Directed: Z.peerId (matching saat HANDSHAKE)
     *   Stealth:  hex hash RT_claim (matching saat COURIER_BROADCAST ↔ COURIER_CLAIM)
     */
    val courierClaimToken: String? = null,

    /** Base64 EPK_pub — untuk challenge-response verification (Stealth). */
    val courierEpkPub: String? = null,

    /** Base64 encoded encPayload (ciphertext bundle). Hanya dikirim di COURIER_DELIVER. */
    val courierEncPayload: String? = null,

    /** Base64 nonce untuk dekripsi courierEncPayload. */
    val courierEncNonce: String? = null,

    /** Base64 sender enc_pub A (Directed mode) — Z butuh ini untuk crypto_box_open. */
    val courierSenderPub: String? = null,

    /**
     * Daftar claim token yang dibawa B (untuk COURIER_BROADCAST di Stealth Mode).
     * B hanya kirim hash — tidak ada info isi/pengirim/penerima.
     */
    val courierTokenList: List<String>? = null,

    /** Nonce challenge dari B ke Z (COURIER_CHALLENGE). */
    val courierChallengeNonce: String? = null,

    /** Ed25519 signature dari Z atas challenge nonce, sebagai bukti kepemilikan EPK_priv (COURIER_PROOF). */
    val courierProofSignature: String? = null,

    /** Wall-clock expiry bundle (ms) — B tampilkan sisa waktu ke pengguna tanpa membuka isi. */
    val courierExpiry: Long? = null,

    /** Hint lokasi tujuan (opsional) — hanya tampil ke B sebagai panduan arah. */
    val courierLocationHintLat: Double? = null,
    val courierLocationHintLon: Double? = null
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
