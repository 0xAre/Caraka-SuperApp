package com.example.caraka.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caraka.crypto.CourierCryptoHelper
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.data.local.entity.CourierBundleEntity
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.network.CourierEvent
import com.example.caraka.network.CourierManager
import com.example.caraka.repository.CourierRepository
import com.example.caraka.repository.MeshRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "CourierViewModel"

/** Kredensial Stealth yang harus A bagikan ke Z lewat jalur aman (QR / chat). */
data class StealthCredentials(
    val bundleId: String,
    val epkPrivB64: String,
    val nonceSecretB64: String
)

/** UI State untuk dialog/bottom sheet yang sedang terbuka. */
sealed class CourierDialogState {
    object None : CourierDialogState()

    /** A membuka sheet untuk memilih mode, kurir, dan menulis pesan. */
    data class SendRequest(val availablePeers: List<PeerEntity>) : CourierDialogState()

    /** B menerima penawaran — dialog accept/reject. */
    data class OfferReceived(
        val bundleId: String,
        val fromPeerId: String,
        val fromPeerName: String,
        val mode: String,
        val expiryMs: Long,
        val locationHintLat: Double?,
        val locationHintLon: Double?
    ) : CourierDialogState()

    /** Z menerima broadcast stealth token — pilih token yang cocok. */
    data class StealthBroadcast(
        val fromPeerId: String,
        val tokenList: List<String>
    ) : CourierDialogState()

    /** Z menerima challenge dari B — perlu input EPK_priv untuk tanda tangan. */
    data class ChallengeReceived(
        val bundleId: String,
        val fromPeerId: String,
        val challengeNonce: String
    ) : CourierDialogState()

    /** Z menerima payload — siap didekripsi dan ditampilkan. */
    data class DeliveryReceived(
        val bundleId: String,
        val mode: String,
        val encPayload: String,
        val encNonce: String,
        val senderPub: String?,
        val fromPeerId: String,
        val decryptedText: String? = null  // sudah di-decrypt jika Directed
    ) : CourierDialogState()

    /** Delivery berhasil — animasi sukses. */
    data class DeliverySuccess(val bundleId: String) : CourierDialogState()

    /** A mendapat konfirmasi pesan berhasil disampaikan. */
    data class ReceiptReceived(val bundleId: String, val carrierName: String) : CourierDialogState()
}

class CourierViewModel(
    private val courierManager: CourierManager,
    private val courierRepository: CourierRepository,
    private val meshRepository: MeshRepository,
    private val identityManager: IdentityManager
) : ViewModel() {

    // ── Dialog state ─────────────────────────────────────────────────────────────────────────
    private val _dialogState = MutableStateFlow<CourierDialogState>(CourierDialogState.None)
    val dialogState: StateFlow<CourierDialogState> = _dialogState.asStateFlow()

    // ── Caraka Mode ─────────────────────────────────────────────────────────────────────────
    val carakaModeActive: StateFlow<Boolean> = courierManager.carakaModeActive

    // ── Bundle badge count (berapa bundle yang sedang dibawa B) ─────────────────────────────
    val activeCarryCount: StateFlow<Int> = courierManager.activeCarryCount

    // ── Snackbar ─────────────────────────────────────────────────────────────────────────────
    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    // ── Kredensial Stealth terakhir (EPK_priv + nonce) untuk dibagikan A → Z out-of-band ─────
    // UI sharing (QR / chat) akan mengonsumsi state ini; dikerjakan di UI layer terpisah.
    private val _stealthCredentials = MutableStateFlow<StealthCredentials?>(null)
    val stealthCredentials: StateFlow<StealthCredentials?> = _stealthCredentials.asStateFlow()
    fun clearStealthCredentials() { _stealthCredentials.value = null }

    // ── My bundles (B sedang membawa) ────────────────────────────────────────────────────────
    val carryingBundles: StateFlow<List<CourierBundleEntity>> =
        courierRepository.getCarryingBundles()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Connected peers (untuk picker kurir) ─────────────────────────────────────────────────
    val connectedPeers: StateFlow<List<PeerEntity>> = meshRepository.getConnectedPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Dengarkan CourierEvent dari manager
        viewModelScope.launch {
            courierManager.courierEvents.collect { event ->
                handleEvent(event)
            }
        }
    }

    private fun handleEvent(event: CourierEvent) {
        when (event) {
            is CourierEvent.OfferReceived -> {
                Log.d(TAG, "Event: OfferReceived bundleId=${event.bundleId}")
                _dialogState.value = CourierDialogState.OfferReceived(
                    bundleId = event.bundleId,
                    fromPeerId = event.fromPeerId,
                    fromPeerName = event.fromPeerName,
                    mode = event.mode,
                    expiryMs = event.expiryMs,
                    locationHintLat = event.locationHintLat,
                    locationHintLon = event.locationHintLon
                )
            }
            is CourierEvent.OfferAccepted -> {
                // A tahu B sudah accept — sekarang A KIRIM bundle (COURIER_TRANSFER) ke B.
                Log.d(TAG, "Event: OfferAccepted by ${event.byPeerId} — mengirim TRANSFER")
                viewModelScope.launch {
                    courierRepository.sendTransfer(event.bundleId, event.byPeerId)
                    _snackbar.emit("Kurir ${event.byPeerId.take(8)} menerima — bundle dikirim.")
                }
            }
            is CourierEvent.OfferRejected -> {
                Log.d(TAG, "Event: OfferRejected by ${event.byPeerId}")
                viewModelScope.launch {
                    _snackbar.emit("Kurir menolak untuk membawa pesan.")
                }
                dismissDialog()
            }
            is CourierEvent.BundleReceived -> {
                Log.d(TAG, "Event: BundleReceived ${event.bundleId}")
                viewModelScope.launch {
                    _snackbar.emit("📦 Bundle kurir diterima. Kamu sedang membawa ${event.mode} bundle.")
                }
            }
            is CourierEvent.CarakaModeActivated -> {
                Log.d(TAG, "Event: CarakaModeActivated")
                viewModelScope.launch {
                    _snackbar.emit("🔁 Caraka Mode aktif — broadcast stealth token ke sekitar.")
                }
            }
            is CourierEvent.StealthBroadcastReceived -> {
                Log.d(TAG, "Event: StealthBroadcast ${event.tokenList.size} tokens")
                _dialogState.value = CourierDialogState.StealthBroadcast(
                    fromPeerId = event.fromPeerId,
                    tokenList = event.tokenList
                )
            }
            is CourierEvent.ChallengeReceived -> {
                Log.d(TAG, "Event: ChallengeReceived for bundle=${event.bundleId}")
                _dialogState.value = CourierDialogState.ChallengeReceived(
                    bundleId = event.bundleId,
                    fromPeerId = event.fromPeerId,
                    challengeNonce = event.challengeNonce
                )
            }
            is CourierEvent.DeliveryReceived -> {
                Log.d(TAG, "Event: DeliveryReceived bundleId=${event.bundleId} mode=${event.mode}")
                // Jika Directed mode: auto-decrypt jika Z memiliki private key
                // Untuk Directed: encNonce berisi info untuk decrypt via Z's own private key
                _dialogState.value = CourierDialogState.DeliveryReceived(
                    bundleId = event.bundleId,
                    mode = event.mode,
                    encPayload = event.encPayload,
                    encNonce = event.encNonce,
                    senderPub = event.senderPub,
                    fromPeerId = event.fromPeerId
                )
            }
            is CourierEvent.DeliverySuccess -> {
                Log.d(TAG, "Event: DeliverySuccess ${event.bundleId}")
                _dialogState.value = CourierDialogState.DeliverySuccess(event.bundleId)
            }
            is CourierEvent.ReceiptReceived -> {
                Log.d(TAG, "Event: ReceiptReceived ${event.bundleId} by ${event.carrierName}")
                _dialogState.value = CourierDialogState.ReceiptReceived(
                    bundleId = event.bundleId,
                    carrierName = event.carrierName
                )
            }
            is CourierEvent.DeliveryFailed -> {
                Log.w(TAG, "Event: DeliveryFailed ${event.bundleId}: ${event.reason}")
                viewModelScope.launch {
                    _snackbar.emit("❌ Pengiriman gagal: ${event.reason}")
                }
            }
        }
    }

    // ── Actions ──────────────────────────────────────────────────────────────────────────────

    /** A membuka sheet permintaan kurir. */
    fun openSendRequest() {
        _dialogState.value = CourierDialogState.SendRequest(connectedPeers.value)
    }

    /** A mengirim bundle kurir ke kurir terpilih. Signature dijaga tetap (dipakai UI). */
    fun sendCourierRequest(
        courierId: String,
        recipientId: String,
        message: String,
        mode: String,          // "DIRECTED" | "STEALTH"
        epkPrivB64: String?,   // opsional: EPK_priv yang sudah disepakati (Stealth). Jika null → auto-generate.
        locationHintLat: Double? = null,
        locationHintLon: Double? = null
    ) {
        viewModelScope.launch {
            try {
                if (mode == "STEALTH") {
                    sendStealthRequest(courierId, message, epkPrivB64, locationHintLat, locationHintLon)
                } else {
                    sendDirectedRequest(courierId, recipientId, message, locationHintLat, locationHintLon)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send courier bundle", e)
                _snackbar.emit("Gagal mengirim permintaan kurir: ${e.message}")
            }
            dismissDialog()
        }
    }

    /** DIRECTED — resolve enc_pub Z dari peer DB (hasil QR exchange) lalu kirim OFFER. */
    private suspend fun sendDirectedRequest(
        courierId: String,
        recipientId: String,
        message: String,
        locationHintLat: Double?,
        locationHintLon: Double?
    ) {
        val peer = meshRepository.getPeerById(recipientId)
        if (peer == null || peer.publicKey.isBlank()) {
            _snackbar.emit("Tidak bisa kirim Directed: kunci publik penerima belum ada. Lakukan QR exchange dulu.")
            Log.w(TAG, "sendDirectedRequest: enc_pub Z tidak ditemukan untuk $recipientId")
            return
        }
        val bundle = courierRepository.createDirectedBundle(
            content = message,
            recipientPeerId = recipientId,
            recipientEncPubB64 = peer.publicKey,
            locationHintLat = locationHintLat,
            locationHintLon = locationHintLon
        ) ?: throw Exception("Gagal membuat bundle directed")
        courierRepository.sendOffer(bundle, courierId)
        _snackbar.emit("📤 Permintaan kurir terkirim! Bundle: ${bundle.bundleId.take(8)}")
        Log.d(TAG, "Directed bundle sent: ${bundle.bundleId} to carrier=$courierId")
    }

    /**
     * STEALTH — A men-generate ephemeral key + nonce rahasia, membuat bundle, kirim OFFER.
     * Kredensial (EPK_priv + nonce) diekspos via [stealthCredentials] agar A dapat
     * membagikannya ke Z out-of-band (QR / chat). UI sharing menyusul (dikerjakan UI layer).
     */
    private suspend fun sendStealthRequest(
        courierId: String,
        message: String,
        providedEpkPrivB64: String?,
        locationHintLat: Double?,
        locationHintLon: Double?
    ) {
        val epkPrivB64 = providedEpkPrivB64?.takeIf { it.isNotBlank() }
            ?: CourierCryptoHelper.encodePrivKey(CourierCryptoHelper.generateEphemeralKeyPair().secretKey)
        val nonceSecretB64 = CourierCryptoHelper.generateChallenge() // 32 byte acak → nonce rahasia
        val bundle = courierRepository.createStealthBundle(
            content = message,
            nonceSecretB64 = nonceSecretB64,
            epkPrivB64 = epkPrivB64,
            locationHintLat = locationHintLat,
            locationHintLon = locationHintLon
        ) ?: throw Exception("Gagal membuat bundle stealth")
        courierRepository.sendOffer(bundle, courierId)
        _stealthCredentials.value = StealthCredentials(bundle.bundleId, epkPrivB64, nonceSecretB64)
        _snackbar.emit("🔮 Bundle stealth dibuat. Bagikan EPK_priv + nonce ke Z secara out-of-band.")
        Log.d(TAG, "STEALTH bundle=${bundle.bundleId} EPK_PRIV=$epkPrivB64 NONCE=$nonceSecretB64")
    }

    /** B accept tawaran kurir dari A. */
    fun acceptOffer(bundleId: String, fromPeerId: String) {
        viewModelScope.launch {
            courierRepository.sendAccept(
                fromPeerId = fromPeerId,
                bundleId = bundleId
            )
            _snackbar.emit("✅ Kamu siap menjadi kurir.")
        }
        dismissDialog()
    }

    /** B menolak tawaran kurir dari A. */
    fun rejectOffer(bundleId: String, fromPeerId: String) {
        viewModelScope.launch {
            courierRepository.sendReject(
                fromPeerId = fromPeerId,
                bundleId = bundleId
            )
        }
        dismissDialog()
    }

    /** B mengaktifkan Caraka Mode (stealth broadcast). */
    fun activateCarakaMode() = courierManager.activateCarakaMode()

    /** B menonaktifkan Caraka Mode. */
    fun deactivateCarakaMode() = courierManager.deactivateCarakaMode()

    /** Z mengklaim token stealth dari broadcast. */
    fun claimToken(fromPeerId: String, token: String) {
        courierManager.claimToken(fromPeerId, token)
        dismissDialog()
    }

    /** Z merespons challenge dari B dengan EPK_priv. */
    fun respondToChallenge(bundleId: String, fromPeerId: String, challengeNonce: String, epkPrivB64: String) {
        courierManager.respondToChallenge(fromPeerId, bundleId, challengeNonce, epkPrivB64)
        dismissDialog()
    }

    /**
     * Z mendekripsi payload Directed (crypto_box_open) + verifikasi signature A (anti-scam).
     * Mengembalikan konten plaintext, atau null jika dekripsi/verifikasi gagal.
     * Suspend karena butuh enc-keypair Z. Signature dijaga tetap (dipakai UI).
     */
    suspend fun decryptDirectedDelivery(
        bundleId: String,
        encPayload: String,
        encNonce: String,
        senderPub: String?
    ): String? = courierRepository.decryptDirectedDelivery(encPayload, encNonce, senderPub)

    /**
     * Z mendekripsi payload Stealth (secretbox dengan KDF(EPK_priv)) + verifikasi signature A.
     * Mengembalikan konten plaintext, atau null jika gagal.
     */
    fun decryptStealthDelivery(
        encPayload: String,
        encNonce: String,
        epkPrivB64: String
    ): String? = courierRepository.decryptStealthDelivery(encPayload, encNonce, epkPrivB64)

    /** Tutup dialog apapun yang sedang terbuka. */
    fun dismissDialog() {
        _dialogState.value = CourierDialogState.None
    }

    /** Hapus bundle dari DB setelah berhasil disampaikan. */
    fun dismissDeliverySuccess(bundleId: String) {
        viewModelScope.launch {
            courierRepository.markBundleDelivered(bundleId)
        }
        dismissDialog()
    }

    class Factory(
        private val courierManager: CourierManager,
        private val courierRepository: CourierRepository,
        private val meshRepository: MeshRepository,
        private val identityManager: IdentityManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CourierViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CourierViewModel(courierManager, courierRepository, meshRepository, identityManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: $modelClass")
        }
    }
}
