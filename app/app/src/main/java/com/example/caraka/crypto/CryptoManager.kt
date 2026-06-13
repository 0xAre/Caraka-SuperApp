package com.example.caraka.crypto

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair

/**
 * CryptoManager — handles all cryptographic operations for CARAKA.
 *
 * Algorithms used (per PRD):
 * - X25519: Key exchange (Diffie-Hellman) for deriving shared secrets
 * - Ed25519: Digital signatures for message authenticity
 * - XSalsa20-Poly1305 (via crypto_box): Authenticated encryption
 *
 * Note: Lazysodium's crypto_box uses X25519 + XSalsa20-Poly1305 internally,
 * which provides the same security guarantees as XChaCha20-Poly1305.
 */
class CryptoManager {

    private val lazySodium: LazySodiumAndroid = LazySodiumAndroid(SodiumAndroid())

    // ========== KEY GENERATION ==========

    /**
     * Generate a new X25519 keypair for encryption.
     */
    fun generateEncryptionKeyPair(): KeyPair {
        return lazySodium.cryptoBoxKeypair()
    }

    /**
     * Generate a new X25519 keypair from a seed.
     */
    fun generateEncryptionKeyPairFromSeed(seed: ByteArray): KeyPair {
        return lazySodium.cryptoBoxSeedKeypair(seed)
    }

    /**
     * Generate a new Ed25519 keypair for digital signatures.
     */
    fun generateSigningKeyPair(): KeyPair {
        return lazySodium.cryptoSignKeypair()
    }

    /**
     * Generate a new Ed25519 keypair from a seed.
     */
    fun generateSigningKeyPairFromSeed(seed: ByteArray): KeyPair {
        return lazySodium.cryptoSignSeedKeypair(seed)
    }

    // ========== ENCRYPTION / DECRYPTION ==========

    /**
     * Encrypt a message for a specific recipient using crypto_box.
     * Uses sender's secret key + recipient's public key (X25519 DH).
     *
     * @param plaintext The message to encrypt
     * @param recipientPublicKey Recipient's X25519 public key
     * @param senderSecretKey Sender's X25519 secret key
     * @return Base64-encoded "nonce:ciphertext", or null on failure
     */
    fun encryptMessage(
        plaintext: String,
        recipientPublicKey: Key,
        senderSecretKey: Key
    ): String? {
        return try {
            val nonce = lazySodium.nonce(Box.NONCEBYTES)
            val keyPair = KeyPair(recipientPublicKey, senderSecretKey)

            val ciphertext = lazySodium.cryptoBoxEasy(
                plaintext,
                nonce,
                keyPair
            )

            val nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
            "$nonceB64:$ciphertext"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypt a message from a specific sender.
     *
     * @param encryptedPayload "nonce:ciphertext" format
     * @param senderPublicKey Sender's X25519 public key
     * @param recipientSecretKey Recipient's X25519 secret key
     * @return Decrypted plaintext, or null on failure
     */
    fun decryptMessage(
        encryptedPayload: String,
        senderPublicKey: Key,
        recipientSecretKey: Key
    ): String? {
        return try {
            val parts = encryptedPayload.split(":", limit = 2)
            if (parts.size != 2) return null

            val nonce = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = parts[1]
            val keyPair = KeyPair(senderPublicKey, recipientSecretKey)

            lazySodium.cryptoBoxOpenEasy(
                ciphertext,
                nonce,
                keyPair
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ========== DIGITAL SIGNATURES ==========

    /**
     * Sign a message with Ed25519.
     * Used to prove message authenticity (especially for authority broadcasts).
     *
     * @param message The message to sign
     * @param signingSecretKey Sender's Ed25519 secret key
     * @return Hex-encoded signature, or null on failure
     */
    fun signMessage(message: String, signingSecretKey: Key): String? {
        return try {
            lazySodium.cryptoSignDetached(message, signingSecretKey)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Verify a message signature with Ed25519.
     *
     * @param message The original message
     * @param signatureHex Hex-encoded signature
     * @param signingPublicKey Sender's Ed25519 public key
     * @return true if signature is valid
     */
    fun verifySignature(
        message: String,
        signatureHex: String,
        signingPublicKey: Key
    ): Boolean {
        return try {
            lazySodium.cryptoSignVerifyDetached(
                signatureHex,
                message,
                signingPublicKey
            )
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ========== UTILITY ==========

    /**
     * Generate a fingerprint (short hash) of a public key.
     * Used as peer ID throughout the app.
     */
    fun fingerprint(publicKey: Key): String {
        val hash = lazySodium.cryptoGenericHash(publicKey.asHexString)
        return hash.take(16)
    }

    /**
     * Convert a Base64 string to a Lazysodium Key object.
     */
    fun keyFromBase64(base64: String): Key {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return Key.fromBytes(bytes)
    }

    /**
     * Convert a Key to Base64 string (for storage/transmission).
     */
    fun keyToBase64(key: Key): String {
        return Base64.encodeToString(key.asBytes, Base64.NO_WRAP)
    }
}
