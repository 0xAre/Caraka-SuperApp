package com.example.caraka.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * DatabasePassphraseManager
 *
 * Generates and securely stores the SQLCipher database passphrase.
 *
 * Strategy:
 * 1. On first run: generate a random 32-byte passphrase
 * 2. Encrypt it with an AES-GCM key stored in Android Keystore (hardware-backed TEE)
 * 3. Store the encrypted blob in SharedPreferences
 * 4. On subsequent runs: decrypt the blob using the Keystore key
 *
 * This means the passphrase is:
 *   - Unique per device (not hardcoded)
 *   - Protected by hardware security module (if device supports it)
 *   - Never stored in plaintext
 */
object DatabasePassphraseManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "caraka_db_master_key"
    private const val PREFS_NAME = "caraka_secure_prefs"
    private const val PREF_ENCRYPTED_PASSPHRASE = "enc_db_passphrase"
    private const val PREF_PASSPHRASE_IV = "enc_db_iv"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128 // bits

    /**
     * Returns the SQLCipher passphrase for this device.
     * Creates and persists it on first call.
     */
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedB64 = prefs.getString(PREF_ENCRYPTED_PASSPHRASE, null)
        val ivB64 = prefs.getString(PREF_PASSPHRASE_IV, null)

        return if (encryptedB64 != null && ivB64 != null) {
            // Decrypt existing passphrase
            val encryptedBytes = Base64.decode(encryptedB64, Base64.NO_WRAP)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            decryptPassphrase(encryptedBytes, iv)
        } else {
            // Generate new random passphrase (32 bytes = 256 bits)
            val newPassphrase = generateSecureRandom32Bytes()
            val (encrypted, iv) = encryptPassphrase(newPassphrase)

            // Persist encrypted passphrase
            prefs.edit {
                putString(PREF_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                putString(PREF_PASSPHRASE_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            }

            newPassphrase
        }
    }

    /**
     * Wipes the stored passphrase. Call this on "Secure Wipe" / factory reset.
     * After this, the encrypted database is permanently unreadable.
     */
    fun wipePassphrase(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            remove(PREF_ENCRYPTED_PASSPHRASE)
            remove(PREF_PASSPHRASE_IV)
        }
        // Also delete the Keystore key — makes encrypted blobs permanently undecryptable
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        // Return existing key if present
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        // Generate a new AES-256 key in the Android Keystore
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // No biometric lock for background decryption
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encryptPassphrase(passphrase: ByteArray): Pair<ByteArray, ByteArray> {
        val secretKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(passphrase)
        return Pair(encrypted, iv)
    }

    private fun decryptPassphrase(encrypted: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(encrypted)
    }

    private fun generateSecureRandom32Bytes(): ByteArray {
        return ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
    }
}
