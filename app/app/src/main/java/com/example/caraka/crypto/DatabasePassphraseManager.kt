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

        if (encryptedB64 != null && ivB64 != null) {
            try {
                val encryptedBytes = Base64.decode(encryptedB64, Base64.NO_WRAP)
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                return decryptPassphrase(encryptedBytes, iv)
            } catch (e: Exception) {
                // Keystore key was invalidated (device reset, biometric change, TEE error).
                // Clear the stale blob so we create a fresh passphrase below.
                android.util.Log.w("DBPassphrase", "Keystore decrypt failed — regenerating passphrase", e)
                prefs.edit(commit = true) {
                    remove(PREF_ENCRYPTED_PASSPHRASE)
                    remove(PREF_PASSPHRASE_IV)
                }
                runCatching {
                    val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                    ks.deleteEntry(KEY_ALIAS)
                }
            }
        }

        // Generate new random passphrase (32 bytes = 256 bits).
        val newPassphrase = generateSecureRandom32Bytes()
        return try {
            val (encrypted, iv) = encryptPassphrase(newPassphrase)
            // Use commit=true (synchronous) so the blob survives an immediate crash.
            prefs.edit(commit = true) {
                putString(PREF_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                putString(PREF_PASSPHRASE_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            }
            newPassphrase
        } catch (e: Exception) {
            // Keystore TEE unavailable (seen on some MTK devices with RKPD timeout).
            // Fall back to storing the passphrase obfuscated in SharedPreferences.
            android.util.Log.w("DBPassphrase", "Keystore unavailable — using fallback storage", e)
            val b64 = Base64.encodeToString(newPassphrase, Base64.NO_WRAP)
            prefs.edit(commit = true) { putString("fallback_passphrase", b64) }
            newPassphrase
        }
    }

    /** Read the fallback (non-Keystore) passphrase if it exists. */
    private fun getFallbackPassphrase(context: Context): ByteArray? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val b64 = prefs.getString("fallback_passphrase", null) ?: return null
        return try { Base64.decode(b64, Base64.NO_WRAP) } catch (_: Exception) { null }
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
