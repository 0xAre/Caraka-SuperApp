package com.example.caraka.network

import android.util.Base64
import android.util.Log
import com.example.caraka.crypto.CourierCryptoHelper
import com.example.caraka.crypto.CryptoManager
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.data.local.entity.CourierBundleEntity
import com.example.caraka.repository.CourierRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "CourierManager"

/**
 * CourierManager — Orchestrator protokol Caraka Courier Mode.
 *
 * Menangani semua COURIER_* message types dan mengelola state machine delivery:
 *
 *  Directed Flow:
 *    A: COURIER_OFFER → B accept → COURIER_TRANSFER
 *    B membawa bundle ...
 *    B HANDSHAKE dengan Z → COURIER_DELIVER (auto, jika peerId cocok)
 *    Z → COURIER_ACK
 *    B → COURIER_RECEIPT ke A (jika A masih reachable)
 *
 *  Stealth Flow:
 *    [B & Z aktifkan Caraka Mode]
 *    B → COURIER_BROADCAST (token list, TTL=1)
 *    Z → COURIER_CLAIM (token yang cocok)
 *    B → COURIER_CHALLENGE (nonce)
 *    Z → COURIER_PROOF (signature atas nonce)
 *    B verifikasi → COURIER_DELIVER
 *    Z → COURIER_ACK
 *
 * Thread-safety: semua suspend fun berjalan di Dispatchers.IO via internal scope.
 * Event ke UI dikirim via [courierEvents] SharedFlow.
 */
class CourierManager(
    private val courierRepository: CourierRepository,
    private val cryptoManager: CryptoManager,
    private val identityManager: IdentityManager,
    private val transport: MeshTransport
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Events ke UI ─────────────────────────────────────────────────────────────────────────

    private val _courierEvents = MutableSharedFlow<CourierEvent>(extraBufferCapacity = 32)
    val courierEvents: SharedFlow<CourierEvent> = _courierEvents.asSharedFlow()

    /** Badge count — jumlah bundle yang sedang dibawa oleh node ini. */
    val activeCarryCount: StateFlow<Int> get() = _activeCarryCount
    private val _activeCarryCount = MutableStateFlow(0)

    // ── Challenge state (Stealth) ─────────────────────────────────────────────────────────────
    // bundleId → (challengeNonce, claimantPeerId, timestamp)
    private val pendingChallenges = ConcurrentHashMap<String, Triple<String, String, Long>>()

    // ── Asal bundle (Directed) — bundleId → A.peerId ─────────────────────────────────────────
    // Best-effort di memori: dipakai untuk mengirim COURIER_RECEIPT balik ke A setelah delivered.
    // Hilang saat proses restart (sesuai sifat receipt yang opsional di plan).
    private val bundleSenders = ConcurrentHashMap<String, String>()

    // ── Dedup COURIER_DELIVER (anti-replay sisi Z) ───────────────────────────────────────────
    private val deliveredBundleIds = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // ── Mode flags ────────────────────────────────────────────────────────────────────────────
    /** True jika node B sedang dalam "Caraka Mode" (Stealth broadcast aktif). */
    private val _carakaModeActive = MutableStateFlow(false)
    val carakaModeActive: StateFlow<Boolean> = _carakaModeActive.asStateFlow()

    init {
        // Pantau count dari DB
        scope.launch {
            courierRepository.getActiveCarryCount().collect { count ->
                _activeCarryCount.value = count
            }
        }
    }

    // ── Caraka Mode (Stealth) ─────────────────────────────────────────────────────────────────

    /**
     * B mengaktifkan Caraka Mode: broadcast semua stealth token ke peer sekitar.
     * Dipanggil saat B tiba di lokasi tujuan dan mengaktifkan mode via UI.
     */
    fun activateCarakaMode() {
        scope.launch {
            _carakaModeActive.value = true
            broadcastStealthTokens()
            _courierEvents.emit(CourierEvent.CarakaModeActivated)
            Log.d(TAG, "Caraka Mode ACTIVATED")
        }
    }

    fun deactivateCarakaMode() {
        _carakaModeActive.value = false
        Log.d(TAG, "Caraka Mode deactivated")
    }

    private suspend fun broadcastStealthTokens() {
        val tokens = courierRepository.getStealthTokenList()
        if (tokens.isEmpty()) {
            Log.d(TAG, "No stealth bundles to broadcast")
            return
        }
        val myId = identityManager.getPeerId()
        val msg = courierRepository.buildBroadcastMessage(
            myId, identityManager.getDisplayName(), identityManager.getRole(), tokens
        )
        transport.sendMessage(msg.toJson())
        Log.d(TAG, "COURIER_BROADCAST sent: ${tokens.size} tokens")
    }

    // ── Inbound Message Handler ────────────────────────────────────────────────────────────────

    /**
     * Entry point untuk semua pesan COURIER_*. Dipanggil dari WifiDirectManager.
     * Mendeteksi tipe dan mendelegasikan ke handler yang sesuai.
     */
    suspend fun handleCourierMessage(protocol: MeshProtocol) {
        when (protocol.type) {
            "COURIER_OFFER"     -> handleOffer(protocol)
            "COURIER_ACCEPT"    -> handleAccept(protocol)
            "COURIER_REJECT"    -> handleReject(protocol)
            "COURIER_TRANSFER"  -> handleTransfer(protocol)
            "COURIER_BROADCAST" -> handleBroadcast(protocol)
            "COURIER_CLAIM"     -> handleClaim(protocol)
            "COURIER_CHALLENGE" -> handleChallenge(protocol)
            "COURIER_PROOF"     -> handleProof(protocol)
            "COURIER_DELIVER"   -> handleDeliver(protocol)
            "COURIER_ACK"       -> handleAck(protocol)
            "COURIER_RECEIPT"   -> handleReceipt(protocol)
            else -> Log.w(TAG, "Unknown courier type: ${protocol.type}")
        }
    }

    // ── COURIER_OFFER: A → B ──────────────────────────────────────────────────────────────────

    private suspend fun handleOffer(protocol: MeshProtocol) {
        val bundleId = protocol.courierBundleId ?: return
        val mode = protocol.courierMode ?: return
        val expiry = protocol.courierExpiry ?: return
        Log.d(TAG, "Received COURIER_OFFER bundleId=$bundleId mode=$mode")

        // Emit ke UI — B akan ditampilkan dialog accept/reject
        _courierEvents.emit(
            CourierEvent.OfferReceived(
                bundleId = bundleId,
                fromPeerId = protocol.senderId,
                fromPeerName = protocol.senderName,
                mode = mode,
                expiryMs = expiry,
                locationHintLat = protocol.courierLocationHintLat,
                locationHintLon = protocol.courierLocationHintLon
            )
        )
    }

    // ── COURIER_ACCEPT / COURIER_REJECT: B → A ───────────────────────────────────────────────

    private suspend fun handleAccept(protocol: MeshProtocol) {
        val bundleId = protocol.courierBundleId ?: return
        Log.d(TAG, "B accepted courier offer: bundleId=$bundleId")
        // A sekarang bisa kirim COURIER_TRANSFER
        _courierEvents.emit(CourierEvent.OfferAccepted(bundleId, protocol.senderId))
    }

    private suspend fun handleReject(protocol: MeshProtocol) {
        val bundleId = protocol.courierBundleId ?: return
        Log.d(TAG, "B rejected courier offer: bundleId=$bundleId")
        _courierEvents.emit(CourierEvent.OfferRejected(bundleId, protocol.senderId))
    }

    // ── COURIER_TRANSFER: A → B (bundle payload) ─────────────────────────────────────────────

    private suspend fun handleTransfer(protocol: MeshProtocol) {
        val bundleId = protocol.courierBundleId ?: return
        val claimToken = protocol.courierClaimToken ?: return
        val epkPub = protocol.courierEpkPub ?: return
        val encPayload = protocol.courierEncPayload ?: return
        val encNonce = protocol.courierEncNonce ?: return
        val expiry = protocol.courierExpiry ?: (System.currentTimeMillis() + MeshPolicy.COURIER_BUNDLE_MAX_AGE_MS)

        Log.d(TAG, "Received COURIER_TRANSFER bundleId=$bundleId mode=${protocol.courierMode}")

        val now = System.currentTimeMillis()
        val bundle = CourierBundleEntity(
            bundleId = bundleId,
            mode = protocol.courierMode ?: "DIRECTED",
            state = "CARRYING",
            claimToken = claimToken,
            epkPub = epkPub,
            encPayload = encPayload,
            encNonce = encNonce,
            senderPub = protocol.courierSenderPub,
            signatureA = null, // signature ada di dalam ciphertext, bukan di wire message
            priority = protocol.priority,
            createdAt = now,
            expiry = expiry,
            locationHintLat = protocol.courierLocationHintLat,
            locationHintLon = protocol.courierLocationHintLon
        )

        // Catat asal bundle (A) untuk pengiriman receipt balik (Directed, best-effort).
        bundleSenders[bundleId] = protocol.senderId
        // 1. Upsert bundle ke DB (state = CARRYING) — B langsung mulai membawa
        courierRepository.upsertBundle(bundle)
        // 2. Buat courier_task untuk tracking
        courierRepository.acceptBundle(bundleId)
        _courierEvents.emit(CourierEvent.BundleReceived(bundleId, bundle.mode, bundle.expiry))
        Log.d(TAG, "Bundle CARRYING: $bundleId mode=${bundle.mode}")
    }

    // ── Directed Delivery via HANDSHAKE ──────────────────────────────────────────────────────

    /**
     * Dipanggil dari WifiDirectManager.handleHandshake() saat B bertemu peer baru.
     * Cek apakah ada bundle DIRECTED untuk peer ini dan kirim langsung jika ada.
     */
    suspend fun onPeerHandshake(peerId: String, peerSenderId: String) {
        val bundles = courierRepository.getPendingDirectedBundlesForPeer(peerId)
        if (bundles.isEmpty()) return

        val myId = identityManager.getPeerId()
        val myName = identityManager.getDisplayName()
        val myRole = identityManager.getRole()

        for (bundle in bundles) {
            val deliver = courierRepository.buildDeliverMessage(myId, myName, myRole, peerId, bundle)
            transport.sendToPeer(peerId, deliver.toJson())
            Log.d(TAG, "COURIER_DELIVER sent (directed) to $peerId for bundle=${bundle.bundleId}")
        }
    }

    // ── COURIER_BROADCAST: B → nearby (Stealth) ──────────────────────────────────────────────

    private suspend fun handleBroadcast(protocol: MeshProtocol) {
        val tokens = protocol.courierTokenList ?: return
        if (tokens.isEmpty()) return
        Log.d(TAG, "Received COURIER_BROADCAST with ${tokens.size} tokens from ${protocol.senderId}")

        // Emit ke UI: Z bisa cek apakah ada token yang cocok dengan EPK_priv yang Z miliki
        _courierEvents.emit(
            CourierEvent.StealthBroadcastReceived(
                fromPeerId = protocol.senderId,
                tokenList = tokens
            )
        )
    }

    /**
     * Z aktifkan Caraka Mode dan klaim token.
     * Dipanggil dari UI setelah Z menemukan token yang cocok.
     */
    fun claimToken(targetPeerId: String, claimToken: String) {
        scope.launch {
            val myId = identityManager.getPeerId()
            val claim = MeshProtocol(
                type = "COURIER_CLAIM",
                id = UUID.randomUUID().toString(),
                senderId = myId,
                senderName = identityManager.getDisplayName(),
                senderRole = identityManager.getRole(),
                recipientId = targetPeerId,
                content = "",
                timestamp = System.currentTimeMillis(),
                ttl = 1,
                priority = "NORMAL",
                courierClaimToken = claimToken
            )
            transport.sendToPeer(targetPeerId, claim.toJson())
            Log.d(TAG, "COURIER_CLAIM sent to $targetPeerId token=$claimToken")
        }
    }

    // ── COURIER_CLAIM: Z → B ─────────────────────────────────────────────────────────────────

    private suspend fun handleClaim(protocol: MeshProtocol) {
        val claimToken = protocol.courierClaimToken ?: return
        Log.d(TAG, "Received COURIER_CLAIM token=$claimToken from ${protocol.senderId}")

        val bundle = courierRepository.claimStealthBundle(claimToken) ?: run {
            Log.w(TAG, "No matching stealth bundle for token=$claimToken")
            return
        }

        // Generate challenge nonce → kirim ke Z
        val challengeNonce = CourierCryptoHelper.generateChallenge()
        pendingChallenges[bundle.bundleId] = Triple(challengeNonce, protocol.senderId, System.currentTimeMillis())

        val myId = identityManager.getPeerId()
        val challenge = courierRepository.buildChallengeMessage(
            myId, identityManager.getDisplayName(), identityManager.getRole(),
            protocol.senderId, bundle.bundleId, challengeNonce
        )
        transport.sendToPeer(protocol.senderId, challenge.toJson())
        Log.d(TAG, "COURIER_CHALLENGE sent to ${protocol.senderId} for bundle=${bundle.bundleId}")
    }

    // ── COURIER_CHALLENGE: B → Z ──────────────────────────────────────────────────────────────

    private suspend fun handleChallenge(protocol: MeshProtocol) {
        val bundleId = protocol.courierBundleId ?: return
        val challengeNonce = protocol.courierChallengeNonce ?: return
        Log.d(TAG, "Received COURIER_CHALLENGE for bundle=$bundleId")

        // Z harus punya EPK_priv — emit ke UI agar Z input EPK_priv jika belum ada
        _courierEvents.emit(
            CourierEvent.ChallengeReceived(
                bundleId = bundleId,
                fromPeerId = protocol.senderId,
                challengeNonce = challengeNonce
            )
        )
    }

    /**
     * Z menandatangani challenge menggunakan EPK_priv.
     * Dipanggil dari UI setelah Z memasukkan EPK_priv (Base64).
     */
    fun respondToChallenge(targetPeerId: String, bundleId: String, challengeNonce: String, epkPrivB64: String) {
        scope.launch {
            try {
                val epkPriv = CourierCryptoHelper.decodeKey(epkPrivB64)
                val proofSig = CourierCryptoHelper.signChallenge(challengeNonce, epkPriv)
                val myId = identityManager.getPeerId()
                val proof = MeshProtocol(
                    type = "COURIER_PROOF",
                    id = UUID.randomUUID().toString(),
                    senderId = myId,
                    senderName = identityManager.getDisplayName(),
                    senderRole = identityManager.getRole(),
                    recipientId = targetPeerId,
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    ttl = 1,
                    priority = "NORMAL",
                    courierBundleId = bundleId,
                    courierProofSignature = proofSig
                )
                transport.sendToPeer(targetPeerId, proof.toJson())
                Log.d(TAG, "COURIER_PROOF sent to $targetPeerId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sign challenge", e)
                _courierEvents.emit(CourierEvent.DeliveryFailed(bundleId, "Gagal menandatangani challenge"))
            }
        }
    }

    // ── COURIER_PROOF: Z → B ──────────────────────────────────────────────────────────────────

    private suspend fun handleProof(protocol: MeshProtocol) {
        val bundleId = protocol.courierBundleId ?: return
        val proofSig = protocol.courierProofSignature ?: return

        val (challengeNonce, claimantPeerId, challengeTs) = pendingChallenges[bundleId] ?: run {
            Log.w(TAG, "No pending challenge for bundle=$bundleId")
            return
        }

        // Timeout check
        if (System.currentTimeMillis() - challengeTs > MeshPolicy.COURIER_CHALLENGE_TIMEOUT_MS) {
            pendingChallenges.remove(bundleId)
            Log.w(TAG, "Challenge timeout for bundle=$bundleId")
            return
        }

        val bundle = courierRepository.getBundleById(bundleId) ?: return

        // Verifikasi: apakah Z benar-benar memiliki EPK_priv yang sesuai dengan EPK_pub di bundle?
        val epkPub = CourierCryptoHelper.decodeKey(bundle.epkPub)
        val isValid = CourierCryptoHelper.verifyChallenge(challengeNonce, proofSig, epkPub)

        if (!isValid) {
            Log.w(TAG, "SECURITY: Invalid COURIER_PROOF from ${protocol.senderId} for bundle=$bundleId — drop")
            pendingChallenges.remove(bundleId)
            _courierEvents.emit(CourierEvent.DeliveryFailed(bundleId, "Verifikasi klaim gagal"))
            return
        }

        pendingChallenges.remove(bundleId)
        Log.d(TAG, "COURIER_PROOF valid — delivering bundle=$bundleId to ${protocol.senderId}")

        // Kirim payload ke Z
        val myId = identityManager.getPeerId()
        val deliver = courierRepository.buildDeliverMessage(
            myId, identityManager.getDisplayName(), identityManager.getRole(),
            protocol.senderId, bundle
        )
        transport.sendToPeer(protocol.senderId, deliver.toJson())
    }

    // ── COURIER_DELIVER: B → Z ────────────────────────────────────────────────────────────────

    private suspend fun handleDeliver(protocol: MeshProtocol) {
        val bundleId = protocol.courierBundleId ?: return
        val mode = protocol.courierMode ?: "DIRECTED"
        val encPayload = protocol.courierEncPayload ?: return
        val encNonce = protocol.courierEncNonce ?: return

        Log.d(TAG, "Received COURIER_DELIVER bundleId=$bundleId mode=$mode")

        // Anti-replay: proses payload hanya sekali per bundleId. Replay tetap di-ACK
        // (jaga-jaga ACK pertama hilang) tapi tidak di-emit ulang ke UI.
        val firstTime = deliveredBundleIds.add(bundleId)
        if (firstTime) {
            // Emit ke UI — Z perlu input kunci (EPK_priv untuk Stealth, atau otomatis Directed)
            _courierEvents.emit(
                CourierEvent.DeliveryReceived(
                    bundleId = bundleId,
                    mode = mode,
                    encPayload = encPayload,
                    encNonce = encNonce,
                    senderPub = protocol.courierSenderPub,
                    fromPeerId = protocol.senderId
                )
            )
        } else {
            Log.w(TAG, "COURIER_DELIVER duplikat untuk bundle=$bundleId — abaikan payload, kirim ulang ACK")
        }

        // Kirim ACK ke B
        val myId = identityManager.getPeerId()
        val ack = MeshProtocol(
            type = "COURIER_ACK",
            id = UUID.randomUUID().toString(),
            senderId = myId,
            senderName = identityManager.getDisplayName(),
            senderRole = identityManager.getRole(),
            recipientId = protocol.senderId,
            content = "",
            timestamp = System.currentTimeMillis(),
            ttl = 1,
            priority = "NORMAL",
            courierBundleId = bundleId
        )
        transport.sendToPeer(protocol.senderId, ack.toJson())
    }

    // ── COURIER_ACK: Z → B ───────────────────────────────────────────────────────────────────

    private suspend fun handleAck(protocol: MeshProtocol) {
        val bundleId = protocol.courierBundleId ?: return
        Log.d(TAG, "COURIER_ACK received — bundle delivered! bundleId=$bundleId")

        // Baca bundle SEBELUM dihapus agar tahu mode-nya (markBundleDelivered menghapus bundle).
        val bundle = courierRepository.getBundleById(bundleId)
        val senderId = bundleSenders.remove(bundleId)

        courierRepository.markBundleDelivered(bundleId)
        _courierEvents.emit(CourierEvent.DeliverySuccess(bundleId))

        // COURIER_RECEIPT ke A — best-effort, HANYA untuk Directed.
        // Stealth sengaja TIDAK mengirim receipt: B tidak tahu siapa A (anonimitas).
        if (bundle?.mode == "DIRECTED" && senderId != null) {
            val myId = identityManager.getPeerId()
            val receipt = MeshProtocol(
                type = "COURIER_RECEIPT",
                id = UUID.randomUUID().toString(),
                senderId = myId,
                senderName = identityManager.getDisplayName(),
                senderRole = identityManager.getRole(),
                recipientId = senderId,
                content = "",
                timestamp = System.currentTimeMillis(),
                ttl = MeshPolicy.COURIER_BROADCAST_TTL,
                priority = "NORMAL",
                courierBundleId = bundleId
            )
            transport.sendToPeer(senderId, receipt.toJson())
            Log.d(TAG, "COURIER_RECEIPT sent to A=$senderId for bundle=$bundleId")
        }
    }

    // ── COURIER_RECEIPT: B → A ────────────────────────────────────────────────────────────────

    private suspend fun handleReceipt(protocol: MeshProtocol) {
        val bundleId = protocol.courierBundleId ?: return
        Log.d(TAG, "COURIER_RECEIPT: bundle=$bundleId berhasil disampaikan oleh ${protocol.senderName}")
        _courierEvents.emit(CourierEvent.ReceiptReceived(bundleId, protocol.senderName))
    }
}

// ── Event Sealed Class ────────────────────────────────────────────────────────────────────────

/** Events dari CourierManager ke UI layer. */
sealed class CourierEvent {
    /** B menerima penawaran kurir dari A. */
    data class OfferReceived(
        val bundleId: String,
        val fromPeerId: String,
        val fromPeerName: String,
        val mode: String,
        val expiryMs: Long,
        val locationHintLat: Double?,
        val locationHintLon: Double?
    ) : CourierEvent()

    data class OfferAccepted(val bundleId: String, val byPeerId: String) : CourierEvent()
    data class OfferRejected(val bundleId: String, val byPeerId: String) : CourierEvent()

    /** B sudah menyimpan bundle dan mulai membawa. */
    data class BundleReceived(val bundleId: String, val mode: String, val expiryMs: Long) : CourierEvent()

    /** B mengaktifkan Caraka Mode dan mulai broadcast token. */
    object CarakaModeActivated : CourierEvent()

    /** Z menerima COURIER_BROADCAST — UI perlu cek apakah ada token yang cocok. */
    data class StealthBroadcastReceived(val fromPeerId: String, val tokenList: List<String>) : CourierEvent()

    /** Z menerima challenge dari B — perlu input EPK_priv untuk tanda tangan. */
    data class ChallengeReceived(val bundleId: String, val fromPeerId: String, val challengeNonce: String) : CourierEvent()

    /** Z menerima delivery — payload terenkripsi siap untuk didekripsi. */
    data class DeliveryReceived(
        val bundleId: String,
        val mode: String,
        val encPayload: String,
        val encNonce: String,
        val senderPub: String?,     // null di Stealth mode
        val fromPeerId: String
    ) : CourierEvent()

    /** Pengiriman berhasil (dari sisi B setelah ACK). */
    data class DeliverySuccess(val bundleId: String) : CourierEvent()

    /** A mendapat konfirmasi bundle sudah disampaikan (Directed mode). */
    data class ReceiptReceived(val bundleId: String, val carrierName: String) : CourierEvent()

    /** Error selama proses courier. */
    data class DeliveryFailed(val bundleId: String, val reason: String) : CourierEvent()
}
