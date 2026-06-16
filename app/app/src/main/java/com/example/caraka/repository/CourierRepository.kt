package com.example.caraka.repository

import android.util.Base64
import android.util.Log
import com.example.caraka.crypto.CourierCryptoHelper
import com.example.caraka.crypto.CryptoManager
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.data.local.dao.CourierDao
import com.example.caraka.data.local.entity.CourierBundleEntity
import com.example.caraka.data.local.entity.CourierTaskEntity
import com.example.caraka.network.MeshPolicy
import com.example.caraka.network.MeshProtocol
import com.example.caraka.network.MeshTransport
import kotlinx.coroutines.flow.Flow
import java.util.UUID

private const val TAG = "CourierRepository"

/**
 * CourierRepository — Single Source of Truth untuk Caraka Courier Mode.
 *
 * Menangani:
 *  - Pembuatan bundle (Directed & Stealth)
 *  - Penyimpanan dan retrieval bundle dari DB
 *  - Logic matching: directed (peerId) & stealth (claim token)
 *  - Challenge-response verification
 *  - State machine: PENDING_ACCEPT → CARRYING → DELIVERED / EXPIRED / REJECTED
 */
class CourierRepository(
    private val courierDao: CourierDao,
    private val cryptoManager: CryptoManager,
    private val identityManager: IdentityManager
) {

    var transport: MeshTransport? = null

    // ── Bundle Creation (sisi A — pengirim) ──────────────────────────────────────────────────

    /**
     * Buat bundle DIRECTED: A kenal Z (sudah QR-exchange).
     * Enkripsi dengan Z_enc_pub, signature A ada di dalam inner payload terenkripsi.
     *
     * @param content Pesan yang akan dikirim ke Z
     * @param recipientPeerId Z.peerId — dipakai sebagai claimToken untuk matching saat HANDSHAKE
     * @param recipientEncPubB64 Base64 Z_enc_pub (dari QR exchange / peer DB)
     * @param locationHintLat Opsional: koordinat tujuan ditampilkan ke B (bukan ke Z)
     * @param locationHintLon Opsional: koordinat tujuan
     * @return Pair(bundleId, bundle entity) atau null jika gagal
     */
    suspend fun createDirectedBundle(
        content: String,
        recipientPeerId: String,
        recipientEncPubB64: String,
        locationHintLat: Double? = null,
        locationHintLon: Double? = null,
        priority: String = "NORMAL"
    ): CourierBundleEntity? {
        return try {
            val myId = identityManager.getPeerId()
            val myEncKeys = identityManager.getEncryptionKeyPair()
            val mySignKeys = identityManager.getSigningKeyPair()

            val bundleId = UUID.randomUUID().toString()

            // 1. Buat inner payload dengan signature A (anti-scam — Z verifikasi setelah decrypt)
            val mySignPubB64 = Base64.encodeToString(mySignKeys.publicKey.asBytes, Base64.NO_WRAP)
            val signature = cryptoManager.signMessage(content, mySignKeys.secretKey) ?: return null
            val innerPayload = CourierCryptoHelper.buildSignedInnerPayload(content, mySignPubB64, signature)

            // 2. Enkripsi inner payload dengan Z_enc_pub (X25519 DH — hanya Z yang bisa buka)
            val zEncPub = cryptoManager.keyFromBase64(recipientEncPubB64)
            val nonce = com.goterl.lazysodium.LazySodiumAndroid(com.goterl.lazysodium.SodiumAndroid())
                .nonce(com.goterl.lazysodium.interfaces.Box.NONCEBYTES)
            val lazySodium = com.goterl.lazysodium.LazySodiumAndroid(com.goterl.lazysodium.SodiumAndroid())
            val kp = com.goterl.lazysodium.utils.KeyPair(zEncPub, myEncKeys.secretKey)
            val ciphertext = lazySodium.cryptoBoxEasy(innerPayload, nonce, kp)
            val encPayload = Base64.encodeToString(ciphertext.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val encNonce = Base64.encodeToString(nonce, Base64.NO_WRAP)

            // 3. Signature A atas bundleId — Z verifikasi keaslian pengirim bundle (bukan isi)
            val bundleSignature = cryptoManager.signMessage(bundleId, mySignKeys.secretKey)
            val senderEncPubB64 = Base64.encodeToString(myEncKeys.publicKey.asBytes, Base64.NO_WRAP)

            val now = System.currentTimeMillis()
            val bundle = CourierBundleEntity(
                bundleId = bundleId,
                mode = "DIRECTED",
                state = "PENDING_ACCEPT",
                claimToken = recipientPeerId,       // Z.peerId sebagai claim token
                epkPub = recipientEncPubB64,        // Z_enc_pub (untuk verifikasi oleh Z)
                encPayload = encPayload,
                encNonce = encNonce,
                senderPub = senderEncPubB64,
                signatureA = bundleSignature,
                priority = priority,
                createdAt = now,
                expiry = now + MeshPolicy.COURIER_BUNDLE_MAX_AGE_MS,
                locationHintLat = locationHintLat,
                locationHintLon = locationHintLon
            )
            courierDao.upsertBundle(bundle)
            Log.d(TAG, "Directed bundle created: $bundleId for $recipientPeerId")
            bundle
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create directed bundle", e)
            null
        }
    }

    /**
     * Buat bundle STEALTH: B dan Z anonim, validasi anti-scam via signature dalam ciphertext.
     *
     * Pre-kondisi: A dan Z sudah QR-exchange → A punya Z_sign_pub (tapi TIDAK perlu Z_enc_pub).
     * EPK pair baru di-generate: EPK_priv dikirim ke Z via QR atau CARAKA chat terenkripsi.
     *
     * @param content Pesan yang akan dikirim ke Z
     * @param nonceSecretB64 Base64 nonce rahasia yang disepakati A dan Z out-of-band
     * @param epkPrivB64 Base64 EPK_priv yang sudah dikirim ke Z (A menyimpan ini untuk generate bundle)
     * @return Pair(bundleId, epkPrivB64 untuk dikonfirmasi Z sudah terima) atau null
     */
    suspend fun createStealthBundle(
        content: String,
        nonceSecretB64: String,
        epkPrivB64: String,
        locationHintLat: Double? = null,
        locationHintLon: Double? = null,
        priority: String = "NORMAL"
    ): CourierBundleEntity? {
        return try {
            val mySignKeys = identityManager.getSigningKeyPair()
            val bundleId = UUID.randomUUID().toString()

            val epkPriv = CourierCryptoHelper.decodeKey(epkPrivB64)
            // X25519 pub: dipakai HANYA untuk derivasi claim token (BLAKE2b).
            val epkPubX = CourierCryptoHelper.derivePublicFromPrivate(epkPriv)
            // Ed25519 verify-key: disimpan di field epkPub agar B bisa verifikasi COURIER_PROOF
            // tanpa konversi X25519→Ed25519 (sumber bug verifikasi sebelumnya).
            val verifyKey = CourierCryptoHelper.deriveVerifyKey(epkPriv)
            val verifyKeyB64 = Base64.encodeToString(verifyKey.asBytes, Base64.NO_WRAP)

            // Claim token: BLAKE2b(EPK_pub_X25519 || nonce_rahasia) — B tidak bisa reverse-engineer siapapun
            val nonceSecret = Base64.decode(nonceSecretB64, Base64.NO_WRAP)
            val claimToken = CourierCryptoHelper.deriveClaimToken(epkPubX.asBytes, nonceSecret)

            // 1. Signed inner payload — signature A ada di DALAM ciphertext
            val mySignPubB64 = Base64.encodeToString(mySignKeys.publicKey.asBytes, Base64.NO_WRAP)
            val signature = cryptoManager.signMessage(content, mySignKeys.secretKey) ?: return null
            val innerPayload = CourierCryptoHelper.buildSignedInnerPayload(content, mySignPubB64, signature)

            // 2. Enkripsi dengan sym_key = KDF(EPK_priv) — B tidak punya kunci ini
            val (encPayload, encNonce) = CourierCryptoHelper.stealthEncrypt(innerPayload, epkPriv)

            val now = System.currentTimeMillis()
            val bundle = CourierBundleEntity(
                bundleId = bundleId,
                mode = "STEALTH",
                state = "PENDING_ACCEPT",
                claimToken = claimToken,
                epkPub = verifyKeyB64,      // Ed25519 verify-key (untuk B verifikasi proof)
                encPayload = encPayload,
                encNonce = encNonce,
                senderPub = null,       // Stealth: tidak ada identitas A
                signatureA = null,      // Stealth: signature ada di DALAM ciphertext
                priority = priority,
                createdAt = now,
                expiry = now + MeshPolicy.COURIER_BUNDLE_MAX_AGE_MS,
                locationHintLat = locationHintLat,
                locationHintLon = locationHintLon
            )
            courierDao.upsertBundle(bundle)
            Log.d(TAG, "Stealth bundle created: $bundleId token=$claimToken")
            bundle
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create stealth bundle", e)
            null
        }
    }

    // ── Bundle Retrieval ──────────────────────────────────────────────────────────────────────

    suspend fun getBundleById(bundleId: String) = courierDao.getBundleById(bundleId)
    suspend fun getCarryingBundlesList() = courierDao.getCarryingBundles()
    /** Flow dari bundle yang sedang dibawa oleh node ini (untuk UI list). */
    fun getCarryingBundles(): Flow<List<CourierBundleEntity>> = courierDao.getCarryingBundlesFlow()
    fun getActiveCarryCount(): Flow<Int> = courierDao.getActiveCarryCount()
    fun getActiveTasks(): Flow<List<CourierTaskEntity>> = courierDao.getActiveTasks()
    /** Riwayat semua tugas kurir (untuk CourierHistoryScreen). */
    fun getAllTasks(): Flow<List<CourierTaskEntity>> = courierDao.getAllTasksFlow()

    // ── Bundle State Transitions ──────────────────────────────────────────────────────────────

    /** Simpan atau update bundle — dipakai saat menerima COURIER_TRANSFER (sisi B). */
    suspend fun upsertBundle(bundle: CourierBundleEntity) {
        courierDao.upsertBundle(bundle)
    }

    /** B accept tugas kurir: ubah state bundle ke CARRYING dan buat courier_task. */
    suspend fun acceptBundle(bundleId: String): CourierTaskEntity? {
        val bundle = courierDao.getBundleById(bundleId) ?: return null
        courierDao.updateBundleState(bundleId, "CARRYING")
        val task = CourierTaskEntity(
            taskId = UUID.randomUUID().toString(),
            bundleId = bundleId,
            acceptedAt = System.currentTimeMillis(),
            status = "ACTIVE",
            locationHintLat = bundle.locationHintLat,
            locationHintLon = bundle.locationHintLon
        )
        courierDao.upsertTask(task)
        Log.d(TAG, "Bundle accepted: $bundleId, task=${task.taskId}")
        return task
    }

    /** B reject permintaan kurir. */
    suspend fun rejectBundle(bundleId: String) {
        courierDao.updateBundleState(bundleId, "REJECTED")
        Log.d(TAG, "Bundle rejected: $bundleId")
    }

    /** Bundle berhasil terdeliver ke Z — bersihkan storage B. */
    suspend fun markBundleDelivered(bundleId: String) {
        val now = System.currentTimeMillis()
        courierDao.markDelivered(bundleId, now)
        val task = courierDao.getTaskByBundleId(bundleId)
        if (task != null) {
            courierDao.updateTaskStatus(task.taskId, "DELIVERED", now)
        }
        // Hapus setelah marking (tidak perlu keep delivered bundles lama)
        courierDao.deleteBundleById(bundleId)
        Log.d(TAG, "Bundle delivered and removed: $bundleId")
    }

    /**
     * Sweeper: tandai task ACTIVE yang bundle-nya kedaluwarsa sebagai EXPIRED (untuk riwayat),
     * lalu hapus bundle expired dari storage.
     */
    suspend fun cleanupExpiredBundles() {
        val now = System.currentTimeMillis()
        val expired = courierDao.getExpiredBundles(now)
        for (bundle in expired) {
            val task = courierDao.getTaskByBundleId(bundle.bundleId)
            if (task != null && task.status == "ACTIVE") {
                courierDao.updateTaskStatus(task.taskId, "EXPIRED", null)
            }
        }
        courierDao.deleteExpiredBundles(now)
        if (expired.isNotEmpty()) Log.d(TAG, "Sweeper: ${expired.size} bundle kurir expired dibersihkan")
    }

    // ── Directed Delivery: HANDSHAKE trigger ─────────────────────────────────────────────────

    /**
     * Dipanggil saat B melakukan HANDSHAKE dengan peer baru.
     * Cek apakah ada bundle DIRECTED untuk peer ini → return list untuk dikirim.
     */
    suspend fun getPendingDirectedBundlesForPeer(peerId: String): List<CourierBundleEntity> {
        return courierDao.getDirectedBundlesForPeer(peerId)
    }

    // ── Stealth Delivery: Caraka Mode ────────────────────────────────────────────────────────

    /**
     * Kumpulkan daftar claim token dari semua bundle STEALTH yang CARRYING.
     * Dikirim dalam COURIER_BROADCAST — B tidak tahu isi/pengirim/penerima.
     */
    suspend fun getStealthTokenList(): List<String> {
        return courierDao.getStealthBundles().map { it.claimToken }
    }

    /**
     * Z mengklaim bundle berdasarkan token. Return bundle jika ditemukan, null jika tidak.
     */
    suspend fun claimStealthBundle(claimToken: String): CourierBundleEntity? {
        return courierDao.getStealthBundleByToken(claimToken)
    }

    // ── Wire Message Builders ─────────────────────────────────────────────────────────────────

    /** Buat COURIER_OFFER wire message: A → B. Hanya metadata, tidak ada isi. */
    fun buildOfferMessage(
        bundle: CourierBundleEntity,
        myId: String,
        myName: String,
        myRole: String,
        targetBId: String
    ): MeshProtocol = MeshProtocol(
        type = "COURIER_OFFER",
        id = UUID.randomUUID().toString(),
        senderId = myId,
        senderName = myName,
        senderRole = myRole,
        recipientId = targetBId,
        content = "",
        timestamp = System.currentTimeMillis(),
        ttl = 1, // local only — langsung ke B yang ada di sekitar
        priority = bundle.priority,
        courierBundleId = bundle.bundleId,
        courierMode = bundle.mode,
        courierExpiry = bundle.expiry,
        courierLocationHintLat = bundle.locationHintLat,
        courierLocationHintLon = bundle.locationHintLon
    )

    /** Buat COURIER_TRANSFER: A → B (setelah B accept). Berisi ciphertext bundle. */
    fun buildTransferMessage(
        bundle: CourierBundleEntity,
        myId: String,
        myName: String,
        myRole: String,
        targetBId: String
    ): MeshProtocol = MeshProtocol(
        type = "COURIER_TRANSFER",
        id = UUID.randomUUID().toString(),
        senderId = myId,
        senderName = myName,
        senderRole = myRole,
        recipientId = targetBId,
        content = "",
        timestamp = System.currentTimeMillis(),
        ttl = 1,
        priority = bundle.priority,
        courierBundleId = bundle.bundleId,
        courierMode = bundle.mode,
        courierClaimToken = bundle.claimToken,
        courierEpkPub = bundle.epkPub,
        courierEncPayload = bundle.encPayload,
        courierEncNonce = bundle.encNonce,
        courierSenderPub = bundle.senderPub, // null di Stealth
        courierExpiry = bundle.expiry,
        courierLocationHintLat = bundle.locationHintLat,
        courierLocationHintLon = bundle.locationHintLon
    )

    /** Buat COURIER_BROADCAST: B → nearby (Stealth Mode). Hanya token hash, TTL=1. */
    fun buildBroadcastMessage(
        myId: String,
        myName: String,
        myRole: String,
        tokenList: List<String>
    ): MeshProtocol = MeshProtocol(
        type = "COURIER_BROADCAST",
        id = UUID.randomUUID().toString(),
        senderId = myId,
        senderName = myName,
        senderRole = myRole,
        recipientId = "BROADCAST",
        content = "",
        timestamp = System.currentTimeMillis(),
        ttl = MeshPolicy.COURIER_BROADCAST_TTL,
        priority = "NORMAL",
        courierTokenList = tokenList
    )

    /** Buat COURIER_CHALLENGE: B → Z setelah menerima COURIER_CLAIM. */
    fun buildChallengeMessage(
        myId: String, myName: String, myRole: String,
        targetId: String, bundleId: String, challengeNonce: String
    ): MeshProtocol = MeshProtocol(
        type = "COURIER_CHALLENGE",
        id = UUID.randomUUID().toString(),
        senderId = myId,
        senderName = myName,
        senderRole = myRole,
        recipientId = targetId,
        content = "",
        timestamp = System.currentTimeMillis(),
        ttl = 1,
        priority = "NORMAL",
        courierBundleId = bundleId,
        courierChallengeNonce = challengeNonce
    )

    /** Buat COURIER_DELIVER: B → Z setelah verifikasi proof. Berisi encPayload. */
    fun buildDeliverMessage(
        myId: String, myName: String, myRole: String,
        targetId: String, bundle: CourierBundleEntity
    ): MeshProtocol = MeshProtocol(
        type = "COURIER_DELIVER",
        id = UUID.randomUUID().toString(),
        senderId = myId,
        senderName = myName,
        senderRole = myRole,
        recipientId = targetId,
        content = "",
        timestamp = System.currentTimeMillis(),
        ttl = 1,
        priority = bundle.priority,
        courierBundleId = bundle.bundleId,
        courierMode = bundle.mode,
        courierEncPayload = bundle.encPayload,
        courierEncNonce = bundle.encNonce,
        courierSenderPub = bundle.senderPub
    )
    // ── Convenience wire-send methods (dipanggil dari ViewModel) ─────────────────────────────

    /** B mengirim COURIER_ACCEPT ke A (transport). */
    suspend fun sendAccept(fromPeerId: String, bundleId: String) {
        val myId = identityManager.getPeerId()
        val accept = MeshProtocol(
            type = "COURIER_ACCEPT",
            id = java.util.UUID.randomUUID().toString(),
            senderId = myId,
            senderName = identityManager.getDisplayName(),
            senderRole = identityManager.getRole(),
            recipientId = fromPeerId,
            content = "",
            timestamp = System.currentTimeMillis(),
            ttl = 1,
            priority = "NORMAL",
            courierBundleId = bundleId
        )
        transport?.sendToPeer(fromPeerId, accept.toJson())
        Log.d(TAG, "COURIER_ACCEPT sent to $fromPeerId bundleId=$bundleId")
    }

    /** B mengirim COURIER_REJECT ke A (transport). */
    suspend fun sendReject(fromPeerId: String, bundleId: String) {
        val myId = identityManager.getPeerId()
        val reject = MeshProtocol(
            type = "COURIER_REJECT",
            id = java.util.UUID.randomUUID().toString(),
            senderId = myId,
            senderName = identityManager.getDisplayName(),
            senderRole = identityManager.getRole(),
            recipientId = fromPeerId,
            content = "",
            timestamp = System.currentTimeMillis(),
            ttl = 1,
            priority = "NORMAL",
            courierBundleId = bundleId
        )
        transport?.sendToPeer(fromPeerId, reject.toJson())
        rejectBundle(bundleId)
        Log.d(TAG, "COURIER_REJECT sent to $fromPeerId bundleId=$bundleId")
    }

    /**
     * A mengirim COURIER_OFFER (metadata saja) ke kurir B untuk bundle yang sudah dibuat.
     * Dipanggil setelah createDirectedBundle / createStealthBundle.
     */
    suspend fun sendOffer(bundle: CourierBundleEntity, courierId: String) {
        val myId = identityManager.getPeerId()
        val offer = buildOfferMessage(
            bundle, myId, identityManager.getDisplayName(), identityManager.getRole(), courierId
        )
        transport?.sendToPeer(courierId, offer.toJson())
        Log.d(TAG, "COURIER_OFFER sent to courier $courierId for bundle=${bundle.bundleId} mode=${bundle.mode}")
    }

    /**
     * A mengirim COURIER_TRANSFER (ciphertext bundle) ke B setelah B accept.
     * Dipanggil dari ViewModel saat menerima event OfferAccepted.
     */
    suspend fun sendTransfer(bundleId: String, courierId: String) {
        val bundle = courierDao.getBundleById(bundleId) ?: run {
            Log.w(TAG, "sendTransfer: bundle tidak ditemukan: $bundleId")
            return
        }
        val myId = identityManager.getPeerId()
        val transfer = buildTransferMessage(
            bundle, myId, identityManager.getDisplayName(), identityManager.getRole(), courierId
        )
        transport?.sendToPeer(courierId, transfer.toJson())
        Log.d(TAG, "COURIER_TRANSFER sent to courier $courierId for bundle=$bundleId")
    }

    // ── Dekripsi + Verifikasi Anti-Scam (sisi Z) ─────────────────────────────────────────────

    /**
     * Z mendekripsi payload DIRECTED (crypto_box_open) lalu memverifikasi signature A
     * yang tertanam di dalam inner payload (signed-then-encrypted).
     *
     * @return konten plaintext jika dekripsi + verifikasi signature berhasil; null jika
     *         dekripsi gagal ATAU signature tidak valid (tolak — kemungkinan scam).
     */
    suspend fun decryptDirectedDelivery(
        encPayload: String,
        encNonce: String,
        senderEncPubB64: String?
    ): String? {
        if (senderEncPubB64 == null) {
            Log.w(TAG, "decryptDirectedDelivery: senderPub null — tidak bisa box_open")
            return null
        }
        val myPriv = identityManager.getEncryptionKeyPair().secretKey
        val senderPub = CourierCryptoHelper.decodeKey(senderEncPubB64)
        val innerJson = CourierCryptoHelper.directedDecrypt(encPayload, encNonce, senderPub, myPriv)
            ?: run { Log.w(TAG, "decryptDirectedDelivery: gagal box_open"); return null }
        return verifyInnerPayload(innerJson)
    }

    /**
     * Z mendekripsi payload STEALTH (crypto_secretbox dengan sym_key = BLAKE2b32(EPK_priv))
     * lalu memverifikasi signature A di dalam inner payload.
     */
    fun decryptStealthDelivery(
        encPayload: String,
        encNonce: String,
        epkPrivB64: String
    ): String? {
        val epkPriv = CourierCryptoHelper.decodeKey(epkPrivB64)
        val innerJson = CourierCryptoHelper.stealthDecrypt(encPayload, encNonce, epkPriv)
            ?: run { Log.w(TAG, "decryptStealthDelivery: gagal secretbox_open"); return null }
        return verifyInnerPayload(innerJson)
    }

    /**
     * Parse inner payload {content,signerPub,signature} dan verifikasi Ed25519 signature A.
     * @return content jika signature valid; null jika format salah atau signature invalid (anti-scam).
     */
    private fun verifyInnerPayload(innerJson: String): String? {
        val inner = CourierCryptoHelper.parseInnerPayload(innerJson) ?: run {
            Log.w(TAG, "verifyInnerPayload: format inner payload tidak valid")
            return null
        }
        val signerKey = try {
            cryptoManager.keyFromBase64(inner.signerPub)
        } catch (e: Exception) {
            Log.w(TAG, "verifyInnerPayload: signerPub tidak valid", e); return null
        }
        val valid = cryptoManager.verifySignature(inner.content, inner.signature, signerKey)
        if (!valid) {
            Log.w(TAG, "SECURITY: signature A tidak valid — tolak pesan (kemungkinan scam). signerPub=${inner.signerPub}")
            return null
        }
        Log.d(TAG, "Inner payload verified ✓ signerPub=${inner.signerPub}")
        return inner.content
    }
}
