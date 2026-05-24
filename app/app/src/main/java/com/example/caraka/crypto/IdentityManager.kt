package com.example.caraka.crypto

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension property for DataStore
private val Context.identityStore: DataStore<Preferences> by preferencesDataStore(name = "identity")

/**
 * IdentityManager — manages the user's cryptographic identity.
 *
 * Stores:
 * - X25519 keypair (encryption)
 * - Ed25519 keypair (signing)
 * - Display name and role
 * - Unique peer ID (fingerprint of public key)
 *
 * Uses DataStore for persistent storage.
 * TODO: Migrate to Android Keystore for production security.
 */
class IdentityManager(
    private val context: Context,
    private val cryptoManager: CryptoManager = CryptoManager()
) {
    companion object {
        // DataStore keys
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_ROLE = stringPreferencesKey("role")
        private val KEY_PEER_ID = stringPreferencesKey("peer_id")

        // Encryption keys (X25519)
        private val KEY_ENC_PUBLIC = stringPreferencesKey("enc_public_key")
        private val KEY_ENC_SECRET = stringPreferencesKey("enc_secret_key")

        // Signing keys (Ed25519)
        private val KEY_SIGN_PUBLIC = stringPreferencesKey("sign_public_key")
        private val KEY_SIGN_SECRET = stringPreferencesKey("sign_secret_key")

        // Roles
        const val ROLE_CIVILIAN = "CIVILIAN"
        const val ROLE_BPBD = "BPBD"
        const val ROLE_POLRI = "POLRI"
        const val ROLE_PMI = "PMI"
    }

    /**
     * Check if identity has been initialized.
     */
    suspend fun hasIdentity(): Boolean {
        val prefs = context.identityStore.data.first()
        return prefs[KEY_PEER_ID] != null
    }

    /**
     * Initialize a new identity (first launch).
     * Generates keypairs, computes fingerprint, saves everything.
     */
    suspend fun createIdentity(displayName: String, role: String = ROLE_CIVILIAN) {
        val encKeyPair = cryptoManager.generateEncryptionKeyPair()
        val signKeyPair = cryptoManager.generateSigningKeyPair()
        val peerId = cryptoManager.fingerprint(encKeyPair.publicKey)

        context.identityStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = displayName
            prefs[KEY_ROLE] = role
            prefs[KEY_PEER_ID] = peerId
            prefs[KEY_ENC_PUBLIC] = cryptoManager.keyToBase64(encKeyPair.publicKey)
            prefs[KEY_ENC_SECRET] = cryptoManager.keyToBase64(encKeyPair.secretKey)
            prefs[KEY_SIGN_PUBLIC] = cryptoManager.keyToBase64(signKeyPair.publicKey)
            prefs[KEY_SIGN_SECRET] = cryptoManager.keyToBase64(signKeyPair.secretKey)
        }
    }

    /**
     * Load identity as a hardcoded authority (for demo).
     * PRD: 3 pre-registered authority identities.
     * Uses deterministic seeds so all devices recognize the same authority keys.
     */
    suspend fun loadAuthorityIdentity(role: String) {
        val name = when (role) {
            ROLE_BPBD -> "BPBD Response"
            ROLE_POLRI -> "Polri Security"
            ROLE_PMI -> "PMI Medical"
            else -> "Civilian"
        }

        if (role == ROLE_CIVILIAN) {
            createIdentity(name, role)
            return
        }

        val seedStr = "GARUDA_MESH_AUTHORITY_${role}".padEnd(32, '0')
        val seedBytes = seedStr.toByteArray(Charsets.UTF_8).take(32).toByteArray()

        val encKeyPair = cryptoManager.generateEncryptionKeyPairFromSeed(seedBytes)
        val signKeyPair = cryptoManager.generateSigningKeyPairFromSeed(seedBytes)
        val peerId = cryptoManager.fingerprint(encKeyPair.publicKey)

        context.identityStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = name
            prefs[KEY_ROLE] = role
            prefs[KEY_PEER_ID] = peerId
            prefs[KEY_ENC_PUBLIC] = cryptoManager.keyToBase64(encKeyPair.publicKey)
            prefs[KEY_ENC_SECRET] = cryptoManager.keyToBase64(encKeyPair.secretKey)
            prefs[KEY_SIGN_PUBLIC] = cryptoManager.keyToBase64(signKeyPair.publicKey)
            prefs[KEY_SIGN_SECRET] = cryptoManager.keyToBase64(signKeyPair.secretKey)
        }
    }

    // ========== GETTERS ==========

    suspend fun getPeerId(): String {
        return context.identityStore.data.first()[KEY_PEER_ID] ?: ""
    }

    suspend fun getDisplayName(): String {
        return context.identityStore.data.first()[KEY_DISPLAY_NAME] ?: "Unknown"
    }

    suspend fun getRole(): String {
        return context.identityStore.data.first()[KEY_ROLE] ?: ROLE_CIVILIAN
    }

    suspend fun isAuthority(): Boolean {
        val role = getRole()
        return role == ROLE_BPBD || role == ROLE_POLRI || role == ROLE_PMI
    }

    suspend fun getEncryptionKeyPair(): KeyPair {
        val prefs = context.identityStore.data.first()
        val pub = cryptoManager.keyFromBase64(prefs[KEY_ENC_PUBLIC]!!)
        val sec = cryptoManager.keyFromBase64(prefs[KEY_ENC_SECRET]!!)
        return KeyPair(pub, sec)
    }

    suspend fun getSigningKeyPair(): KeyPair {
        val prefs = context.identityStore.data.first()
        val pub = cryptoManager.keyFromBase64(prefs[KEY_SIGN_PUBLIC]!!)
        val sec = cryptoManager.keyFromBase64(prefs[KEY_SIGN_SECRET]!!)
        return KeyPair(pub, sec)
    }

    suspend fun getEncryptionPublicKeyBase64(): String {
        return context.identityStore.data.first()[KEY_ENC_PUBLIC] ?: ""
    }

    suspend fun getSigningPublicKeyBase64(): String {
        return context.identityStore.data.first()[KEY_SIGN_PUBLIC] ?: ""
    }

    /**
     * Clear identity (reset app).
     */
    suspend fun clearIdentity() {
        context.identityStore.edit { it.clear() }
    }
}
