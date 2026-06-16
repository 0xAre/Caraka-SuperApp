package com.example.caraka.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tabel penyimpanan kurir — berisi paket terenkripsi yang dibawa oleh node B untuk
 * diteruskan ke penerima Z (Caraka Courier Mode, Phase A+B).
 *
 * Berbeda dari [OutboxEntity] yang bersifat epidemik (flood ke siapapun), courier bundle
 * bersifat *directed* atau *stealth*: hanya diserahkan ke pemegang klaim yang sah.
 *
 * Lifecycle [state]:
 *   PENDING_ACCEPT → CARRYING → DELIVERED | EXPIRED | REJECTED
 *
 * Mode:
 *   DIRECTED — A kenal Z (recipientId = Z.peerId, enkripsi X25519 normal).
 *   STEALTH   — A tidak kenal Z; klaim via rendezvous token + challenge-response.
 */
@Entity(tableName = "courier_bundle")
data class CourierBundleEntity(

    @PrimaryKey
    val bundleId: String,               // UUID unik bundle ini

    val mode: String,                   // "DIRECTED" | "STEALTH"
    val state: String = "PENDING_ACCEPT", // PENDING_ACCEPT | CARRYING | DELIVERED | EXPIRED | REJECTED

    // ── Claim & Recipient ────────────────────────────────────────────────────────────────────
    /** Directed: Z.peerId. Stealth: RT_claim (hex hash dari EPK_pub || nonce). */
    val claimToken: String,

    /** Base64 kunci publik untuk verifikasi claim:
     *  Directed: Z enc_pub (X25519, untuk Z verifikasi). Stealth: EPK_pub (X25519 ephemeral). */
    val epkPub: String,

    // ── Payload ──────────────────────────────────────────────────────────────────────────────
    /** Base64 ciphertext. Directed: crypto_box. Stealth: crypto_secretbox. */
    val encPayload: String,

    /** Base64 nonce yang digunakan saat enkripsi. */
    val encNonce: String,

    // ── Sender info (Directed only, null di Stealth untuk anonimitas A) ──────────────────────
    /** Base64 X25519 enc_pub A — dibutuhkan Z untuk bisa decrypt (Directed mode). */
    val senderPub: String? = null,

    /** Ed25519 signature dari A atas bundleId — Z verifikasi keaslian A (Directed mode). */
    val signatureA: String? = null,

    // ── Metadata ─────────────────────────────────────────────────────────────────────────────
    val priority: String = "NORMAL",    // NORMAL | EMERGENCY
    val createdAt: Long,
    val expiry: Long,                   // wall-clock expiry — bundle dihapus setelah ini

    /** Opsional: hint lokasi tujuan, hanya ditampilkan ke B sebagai navigasi, tidak diekspos ke Z. */
    val locationHintLat: Double? = null,
    val locationHintLon: Double? = null,

    val deliveredAt: Long? = null       // diisi saat state = DELIVERED
)
