package com.example.caraka.crypto

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.GenericHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair

/**
 * CourierCryptoHelper — operasi kriptografi khusus untuk Caraka Courier Mode.
 *
 * Dua mode yang didukung:
 *
 *  DIRECTED (A kenal Z):
 *   - Enkripsi: crypto_box(plaintext, Z_enc_pub, A_enc_sk) — X25519 + XSalsa20-Poly1305
 *   - Signature A (inner): sign(bundleId||content, A_sign_sk) — Ed25519
 *   - Z dapat verifikasi keaslian A setelah dekripsi
 *
 *  STEALTH (B & Z anonim):
 *   - A generate ephemeral key pair (EPK_priv, EPK_pub) → bagikan EPK_priv ke Z out-of-band
 *   - Claim token: RT = BLAKE2b-16(EPK_pub || nonce_rahasia) — tidak bermakna tanpa nonce
 *   - Enkripsi: crypto_secretbox(plaintext, sym_key) di mana sym_key = BLAKE2b-32(EPK_priv)
 *   - Validasi anti-scam: sign(content, A_sign_sk) disimpan DALAM ciphertext
 *     → B tidak pernah lihat signature; Z verifikasi setelah decrypt dengan A_sign_pub (dari QR)
 *   - Challenge-Response: B tantang Z buktikan kepemilikan EPK_priv tanpa mengungkap identitas
 *
 * Design decisions yang dikunci:
 *  - A & Z harus QR-exchange sebelumnya (Z punya signing_pub A, A punya enc_pub Z)
 *  - B menampilkan deadline expiry tanpa membocorkan isi/identitas
 *  - B juga anonim terhadap Z — tidak ada HANDSHAKE normal
 *  - Payload cap: 64KB (COURIER_MAX_PAYLOAD_BYTES di MeshPolicy)
 */
object CourierCryptoHelper {

    private val lazySodium: LazySodiumAndroid = LazySodiumAndroid(SodiumAndroid())

    // ── Ephemeral Key Pair (Stealth) ─────────────────────────────────────────────────────────

    /**
     * Generate ephemeral X25519 key pair untuk Stealth Mode.
     * EPK_pub → disimpan dalam bundle (publik, untuk verifikasi claim).
     * EPK_priv → dikirim ke Z via jalur out-of-band (QR atau bertemu langsung).
     */
    fun generateEphemeralKeyPair(): KeyPair = lazySodium.cryptoBoxKeypair()

    /**
     * Encode EPK_priv sebagai Base64 untuk dikirim ke Z.
     * Z menyimpannya; digunakan untuk decrypt + challenge-response.
     */
    fun encodePrivKey(key: Key): String =
        Base64.encodeToString(key.asBytes, Base64.NO_WRAP)

    fun decodeKey(b64: String): Key =
        Key.fromBytes(Base64.decode(b64, Base64.NO_WRAP))

    // ── Claim Token (Stealth) ────────────────────────────────────────────────────────────────

    /**
     * Derive rendezvous token dari EPK_pub dan nonce rahasia.
     * RT = BLAKE2b-16(EPK_pub || nonce_rahasia)
     *
     * Output: hex string 32 karakter — tidak bermakna tanpa nonce dan EPK_pub.
     * B hanya menyimpan dan broadcast RT — tidak bisa reverse-engineer EPK_pub atau isi.
     */
    fun deriveClaimToken(epkPubBytes: ByteArray, nonceSecret: ByteArray): String {
        val input = epkPubBytes + nonceSecret
        val hashBytes = ByteArray(16)
        lazySodium.cryptoGenericHash(hashBytes, hashBytes.size, input, input.size.toLong(), null, 0)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify apakah EPK_priv yang dimiliki Z menghasilkan RT yang cocok dengan bundle.
     * Digunakan oleh Z saat mencari bundle yang bisa diklaim di Caraka Mode.
     */
    fun verifyClaimToken(epkPrivB64: String, nonceSecretB64: String, expectedToken: String): Boolean {
        return try {
            val epkPriv = decodeKey(epkPrivB64)
            val epkPub = derivePublicFromPrivate(epkPriv)
            val nonceSecret = Base64.decode(nonceSecretB64, Base64.NO_WRAP)
            val computed = deriveClaimToken(epkPub.asBytes, nonceSecret)
            computed == expectedToken
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Derive EPK_pub dari EPK_priv menggunakan scalar multiplication.
     * Lazysodium mengekspos ini via crypto_scalarmult_base.
     */
    fun derivePublicFromPrivate(epkPriv: Key): Key {
        val pubBytes = ByteArray(Box.CURVE25519XSALSA20POLY1305_PUBLICKEYBYTES)
        lazySodium.cryptoScalarMultBase(pubBytes, epkPriv.asBytes)
        return Key.fromBytes(pubBytes)
    }

    // ── Symmetric Key Derivation (Stealth) ───────────────────────────────────────────────────

    /**
     * Derive 256-bit symmetric key dari EPK_priv untuk crypto_secretbox.
     * sym_key = BLAKE2b-32(EPK_priv) — deterministic, tidak reversible.
     * Hanya pemegang EPK_priv yang bisa derive key ini.
     */
    fun deriveSymKey(epkPriv: Key): ByteArray {
        val symKey = ByteArray(32)
        val privBytes = epkPriv.asBytes
        lazySodium.cryptoGenericHash(symKey, symKey.size, privBytes, privBytes.size.toLong(), null, 0)
        return symKey
    }

    // ── Encrypt / Decrypt (Stealth) ──────────────────────────────────────────────────────────

    /**
     * Enkripsi payload untuk Stealth Mode.
     * Tidak ada identitas A dalam output — B tidak bisa tahu siapa A.
     *
     * Catatan: [plaintext] sudah berisi signature A di dalamnya (lihat [buildSignedInnerPayload]).
     * Ini adalah "signed-then-encrypted" — B tidak pernah lihat signature.
     *
     * @return Pair(encPayloadB64, encNonceB64)
     */
    fun stealthEncrypt(plaintext: String, epkPriv: Key): Pair<String, String> {
        val symKey = deriveSymKey(epkPriv)
        val nonce = lazySodium.nonce(SecretBox.NONCEBYTES)
        val ciphertext = lazySodium.cryptoSecretBoxEasy(plaintext, nonce, Key.fromBytes(symKey))
        return Pair(
            Base64.encodeToString(ciphertext.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
            Base64.encodeToString(nonce, Base64.NO_WRAP)
        )
    }

    /**
     * Dekripsi Stealth payload — Z menggunakan EPK_priv yang diterima dari A (out-of-band).
     * @return plaintext yang berisi [SignedInnerPayload], atau null jika gagal.
     */
    fun stealthDecrypt(encPayloadB64: String, encNonceB64: String, epkPriv: Key): String? {
        return try {
            val symKey = deriveSymKey(epkPriv)
            val nonce = Base64.decode(encNonceB64, Base64.NO_WRAP)
            val ciphertext = String(Base64.decode(encPayloadB64, Base64.NO_WRAP), Charsets.UTF_8)
            lazySodium.cryptoSecretBoxOpenEasy(ciphertext, nonce, Key.fromBytes(symKey))
        } catch (e: Exception) {
            null
        }
    }

    // ── Inner Payload with Signature (Anti-Scam) ─────────────────────────────────────────────

    /**
     * Buat inner payload yang akan dienkripsi: konten pesan + signature A.
     * Format JSON sederhana: {"content":"...","signerPub":"...","signature":"..."}
     *
     * Signature dibuat SEBELUM enkripsi (signed-then-encrypted), sehingga:
     *  - B tidak pernah bisa melihat signature → B tidak tahu siapa A
     *  - Z bisa verifikasi setelah decrypt, menggunakan A_sign_pub yang sudah diketahui via QR
     */
    fun buildSignedInnerPayload(
        content: String,
        signerPub: String,      // Base64 A_sign_pub — Z tahu ini dari QR exchange
        signature: String       // Ed25519 sign(content, A_sign_sk)
    ): String = """{"content":${escapeJson(content)},"signerPub":${escapeJson(signerPub)},"signature":${escapeJson(signature)}}"""

    /** Parse inner payload setelah dekripsi. Returns null jika format tidak valid. */
    fun parseInnerPayload(innerJson: String): InnerPayload? {
        return try {
            // Simple regex-based parse — tidak butuh Gson/dependency baru
            val content = Regex(""""content"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(innerJson)?.groupValues?.get(1)
            val signerPub = Regex(""""signerPub"\s*:\s*"([^"]+)"""").find(innerJson)?.groupValues?.get(1)
            val signature = Regex(""""signature"\s*:\s*"([^"]+)"""").find(innerJson)?.groupValues?.get(1)
            if (content != null && signerPub != null && signature != null) {
                InnerPayload(content = content, signerPub = signerPub, signature = signature)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun escapeJson(s: String): String = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    data class InnerPayload(
        val content: String,
        val signerPub: String,
        val signature: String
    )

    // ── Challenge-Response (Stealth Delivery) ────────────────────────────────────────────────

    /**
     * Generate random challenge nonce untuk dikirim B ke Z.
     * 32 bytes → Base64 ~44 karakter.
     */
    fun generateChallenge(): String {
        val nonce = lazySodium.nonce(32)
        return Base64.encodeToString(nonce, Base64.NO_WRAP)
    }

    /**
     * Z membuat proof kepemilikan EPK_priv dengan menandatangani challenge dari B.
     * sign(challengeNonce, EPK_priv_as_signing_key)
     *
     * Catatan: kami sign dengan Ed25519 yang di-derive dari EPK_priv (X25519) via
     * crypto_sign_seed_keypair(EPK_priv) — standar NaCl.
     */
    fun signChallenge(challengeB64: String, epkPriv: Key): String {
        // Derive Ed25519 signing key dari X25519 private key seed
        val signKp = lazySodium.cryptoSignSeedKeypair(epkPriv.asBytes.take(Sign.SEEDBYTES).toByteArray())
        val signed = lazySodium.cryptoSignDetached(challengeB64, signKp.secretKey)
        return signed
    }

    /**
     * B verifikasi proof dari Z menggunakan EPK_pub yang tersimpan di bundle.
     * Jika valid → Z terbukti memiliki EPK_priv yang sesuai → kirim payload.
     */
    fun verifyChallenge(challengeB64: String, proofSignature: String, epkPub: Key): Boolean {
        return try {
            // Derive Ed25519 verify key dari X25519 public key
            val verifyKeyBytes = ByteArray(Sign.PUBLICKEYBYTES)
            lazySodium.convertPublicKeyEd25519ToCurve25519(verifyKeyBytes, epkPub.asBytes)
            // Fallback: gunakan epkPub langsung jika konversi tidak valid
            val verifyKey = try { Key.fromBytes(verifyKeyBytes) } catch (e: Exception) { epkPub }
            lazySodium.cryptoSignVerifyDetached(proofSignature, challengeB64, verifyKey)
        } catch (e: Exception) {
            false
        }
    }

    // ── Directed Mode Helpers ────────────────────────────────────────────────────────────────

    /**
     * Enkripsi untuk Directed Mode — reuse CryptoManager.encryptMessage() yang sudah ada.
     * Wrapper ini hanya untuk konsistensi API.
     */
    fun directedEncrypt(
        content: String,
        zEncPub: Key,
        aEncSk: Key
    ): Triple<String, String, String>? {
        // Format sama dengan CryptoManager: "nonce:ciphertext"
        val nonce = lazySodium.nonce(Box.NONCEBYTES)
        val keyPair = KeyPair(zEncPub, aEncSk)
        return try {
            val ciphertext = lazySodium.cryptoBoxEasy(content, nonce, keyPair)
            Triple(
                ciphertext,
                Base64.encodeToString(nonce, Base64.NO_WRAP),
                Base64.encodeToString(nonce, Base64.NO_WRAP) // nonce juga jadi encNonce
            )
        } catch (e: Exception) {
            null
        }
    }

    // ── Extra helpers for ViewModel ──────────────────────────────────────────────────────────

    /**
     * Derive shared symmetric key dari dua key halves.
     * output = BLAKE2b-32(privBytes || pubBytes)
     */
    fun deriveSymmetricKey(privBytes: ByteArray, pubBytes: ByteArray): ByteArray {
        val input = privBytes + pubBytes
        val out = ByteArray(32)
        lazySodium.cryptoGenericHash(out, out.size, input, input.size.toLong(), null, 0)
        return out
    }

    /**
     * stealthDecrypt overload yang menerima raw ByteArray symKey.
     * Dipakai oleh ViewModel yang sudah melakukan key derivation.
     */
    fun stealthDecrypt(encPayloadB64: String, encNonceB64: String, symKey: ByteArray): String? {
        return try {
            val nonce = Base64.decode(encNonceB64, Base64.NO_WRAP)
            val ciphertext = String(Base64.decode(encPayloadB64, Base64.NO_WRAP), Charsets.UTF_8)
            lazySodium.cryptoSecretBoxOpenEasy(ciphertext, nonce, Key.fromBytes(symKey))
        } catch (e: Exception) {
            null
        }
    }
}
