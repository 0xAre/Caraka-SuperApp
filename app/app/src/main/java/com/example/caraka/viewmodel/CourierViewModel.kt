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
                // A tahu B sudah accept — sekarang A kirim transfer via UI action
                Log.d(TAG, "Event: OfferAccepted by ${event.byPeerId}")
                viewModelScope.launch {
                    _snackbar.emit("Kurir ${event.byPeerId.take(8)} siap membawa pesan!")
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

    /** A mengirim bundle kurir ke kurir terpilih. */
    fun sendCourierRequest(
        courierId: String,
        recipientId: String,
        message: String,
        mode: String,          // "DIRECTED" | "STEALTH"
        epkPrivB64: String?,   // hanya untuk Stealth — EPK_priv Z yang sudah di-share ke A
        locationHintLat: Double? = null,
        locationHintLon: Double? = null
    ) {
        viewModelScope.launch {
            try {
                val bundleId = courierRepository.sendDirectedBundle(
                    courierId = courierId,
                    recipientId = recipientId,
                    message = message,
                    locationHintLat = locationHintLat,
                    locationHintLon = locationHintLon
                )
                _snackbar.emit("📤 Permintaan kurir terkirim! Bundle: ${bundleId.take(8)}")
                Log.d(TAG, "Courier bundle sent: $bundleId to carrier=$courierId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send courier bundle", e)
                _snackbar.emit("Gagal mengirim permintaan kurir: ${e.message}")
            }
            dismissDialog()
        }
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

    /** Z mendekripsi payload Directed dengan private key-nya. Suspend karena getEncryptionKeyPair() is suspend. */
    suspend fun decryptDirectedDelivery(
        bundleId: String,
        encPayload: String,
        encNonce: String,
        senderPub: String?
    ): String? {
        return try {
            val myPriv = identityManager.getEncryptionKeyPair().secretKey.asBytes
            val symKey = if (senderPub != null) {
                CourierCryptoHelper.deriveSymmetricKey(myPriv, CourierCryptoHelper.decodeKey(senderPub).asBytes)
            } else {
                myPriv // fallback
            }
            CourierCryptoHelper.stealthDecrypt(encPayload, encNonce, symKey)
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt Directed failed", e)
            null
        }
    }

    /** Z mendekripsi payload Stealth dengan EPK_priv milik Z. */
    fun decryptStealthDelivery(
        encPayload: String,
        encNonce: String,
        epkPrivB64: String
    ): String? {
        return try {
            val epkPriv = CourierCryptoHelper.decodeKey(epkPrivB64)
            val symKey = CourierCryptoHelper.deriveSymmetricKey(epkPriv.asBytes, epkPriv.asBytes)
            CourierCryptoHelper.stealthDecrypt(encPayload, encNonce, symKey)
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt Stealth failed", e)
            null
        }
    }

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
