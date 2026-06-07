package com.example.caraka.crypto

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * QrIdentityManager
 *
 * Handles generating and parsing QR codes that encode a CARAKA peer identity.
 *
 * QR payload (JSON):
 * {
 *   "v": 1,                       — protocol version
 *   "peerId": "abc123...",        — public key fingerprint (unique node ID)
 *   "name": "BPBD Jakarta",       — display name
 *   "role": "BPBD",               — role: BPBD | POLRI | PMI | CIVILIAN
 *   "encPub": "base64...",        — X25519 public key (encryption)
 *   "signPub": "base64..."        — Ed25519 public key (signature verification)
 * }
 *
 * Scanning workflow:
 *   1. Device A shows QR of its identity
 *   2. Device B scans it → calls [parseQrPayload]
 *   3. Device B stores Device A as a "verified peer" with their public keys
 *   4. Future messages from Device A can be signature-verified against signPub
 */
object QrIdentityManager {

    private const val QR_VERSION = 1

    @Serializable
    data class QrIdentityPayload(
        val v: Int = QR_VERSION,
        val peerId: String,
        val name: String,
        val role: String,
        val encPub: String,   // X25519 public key, Base64
        val signPub: String   // Ed25519 public key, Base64
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Build the QR code JSON payload from identity fields.
     */
    fun buildPayload(
        peerId: String,
        name: String,
        role: String,
        encPub: String,
        signPub: String
    ): String {
        val payload = QrIdentityPayload(
            peerId = peerId,
            name = name,
            role = role,
            encPub = encPub,
            signPub = signPub
        )
        return json.encodeToString(payload)
    }

    /**
     * Parse a QR code string into a [QrIdentityPayload].
     * Returns null if the string is not a valid CARAKA identity QR.
     */
    fun parseQrPayload(raw: String): QrIdentityPayload? {
        return runCatching {
            val payload = json.decodeFromString<QrIdentityPayload>(raw)
            if (payload.peerId.isBlank() || payload.signPub.isBlank()) null
            else payload
        }.getOrNull()
    }

    /**
     * Generate a QR code [Bitmap] from a payload string.
     *
     * @param content  The JSON payload string (from [buildPayload])
     * @param sizePx   Output bitmap size in pixels (default 512)
     * @param darkColor Dark module color (default black = 0xFF000000)
     * @param lightColor Light module color (default white = 0xFFFFFFFF)
     */
    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        darkColor: Int = 0xFF0A1628.toInt(),   // NavyBackground
        lightColor: Int = 0xFFF59E0B.toInt()   // AmberAccent — tactical feel
    ): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) darkColor else lightColor)
                }
            }
            bmp
        } catch (e: WriterException) {
            null
        }
    }
}
