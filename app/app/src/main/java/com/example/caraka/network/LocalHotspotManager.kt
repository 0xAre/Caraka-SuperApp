package com.example.caraka.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Public credentials of a local emergency hotspot, shared so neighbours converge onto one LAN. */
data class HotspotCredentials(val ssid: String, val passphrase: String)

/** UI-facing snapshot of the local hotspot subsystem. */
data class HotspotUiState(
    val role: String = "NONE",          // NONE | HOST | CLIENT
    val ssid: String? = null,
    val passphrase: String? = null,     // shown to the HOST only (so it can relay creds / show a QR)
    val status: String = ""
)

/**
 * Brings up a single shared L2 network so a phone can talk to MANY peers at once without any router,
 * Google Play Services, or Wi-Fi Aware hardware — closing audit gap C1 (no universal infrastructure-
 * less multi-peer path).
 *
 * How it delivers true M-to-N:
 *  - One device HOSTS a [WifiManager.startLocalOnlyHotspot] AP and gossips its credentials over
 *    whatever transport is already up ([MeshProtocol] type `HOTSPOT_OFFER`, ttl = 1, local radio only).
 *  - Neighbours that hear the offer JOIN it via [WifiNetworkSpecifier] (API 29+) and bind the process
 *    to that [Network], so every node now shares ONE subnet.
 *  - The existing LAN-UDP backbone in [WifiDirectManager] (broadcast + unicast on port 8890) then
 *    auto-meshes everyone — this class is purely the physical-layer enabler; it carries no data of
 *    its own and adds no new socket layer.
 *
 * Caveats (documented, require multi-device field testing): LocalOnlyHotspot SSID/passphrase are
 * framework-generated and OEM-variable; a single Wi-Fi chip means hosting may drop an existing
 * Wi-Fi/Direct link; auto-join below API 29 is unavailable (host still works on API 26+).
 */
class LocalHotspotManager(
    private val context: Context,
    /** Broadcast a wire payload over whatever transport is currently up (to gossip the offer). */
    private val broadcast: (String) -> Unit
) {
    companion object {
        private const val TAG = "LocalHotspot"
        const val OFFER_TYPE = "HOTSPOT_OFFER"
    }

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(HotspotUiState())
    val state: StateFlow<HotspotUiState> = _state

    @Volatile private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    @Volatile private var joinCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var joinedSsid: String? = null
    @Volatile private var requestedSsid: String? = null // ssid of an in-flight join (avoid churn)

    // ─────────────────────────────── HOST ───────────────────────────────

    @SuppressLint("MissingPermission")
    fun startHosting() {
        if (reservation != null) { Log.d(TAG, "Already hosting"); return }
        leave() // cannot host and be a client at once
        _state.value = HotspotUiState(status = "Memulai hotspot darurat…")
        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = res
                    val creds = extractCredentials(res)
                    _state.value = HotspotUiState(
                        role = "HOST",
                        ssid = creds?.ssid,
                        passphrase = creds?.passphrase,
                        status = if (creds != null) "Hotspot aktif — peer dapat bergabung otomatis"
                                 else "Hotspot aktif (kredensial tidak terbaca)"
                    )
                    Log.d(TAG, "LocalOnlyHotspot started ssid=${creds?.ssid}")
                }

                override fun onStopped() {
                    Log.d(TAG, "LocalOnlyHotspot stopped")
                    reservation = null
                    if (_state.value.role == "HOST") _state.value = HotspotUiState(status = "Hotspot dihentikan")
                }

                override fun onFailed(reason: Int) {
                    Log.w(TAG, "LocalOnlyHotspot failed: $reason")
                    reservation = null
                    _state.value = HotspotUiState(status = "Gagal memulai hotspot (kode $reason)")
                }
            }, mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "startLocalOnlyHotspot threw", e)
            _state.value = HotspotUiState(status = "Hotspot tidak didukung perangkat ini")
        }
    }

    fun stopHosting() {
        reservation?.let { try { it.close() } catch (_: Exception) {} }
        reservation = null
        if (_state.value.role == "HOST") _state.value = HotspotUiState(status = "Hotspot dihentikan")
    }

    fun isHosting(): Boolean = reservation != null

    @Suppress("DEPRECATION")
    private fun extractCredentials(res: WifiManager.LocalOnlyHotspotReservation): HotspotCredentials? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cfg = res.softApConfiguration
                val ssid = cfg.ssid ?: return null
                val pass = cfg.passphrase ?: return null
                HotspotCredentials(ssid, pass)
            } else {
                val cfg = res.wifiConfiguration ?: return null
                val ssid = cfg.SSID?.trim('"') ?: return null
                val pass = cfg.preSharedKey?.trim('"') ?: return null
                HotspotCredentials(ssid, pass)
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractCredentials failed", e)
            null
        }
    }

    /**
     * Gossip the current hotspot credentials so neighbours can converge. Caller (MeshManager) invokes
     * this periodically while hosting, passing its identity so the offer is attributable.
     */
    fun broadcastOfferIfHosting(peerId: String, name: String, role: String) {
        val s = _state.value
        if (s.role != "HOST" || s.ssid.isNullOrBlank() || s.passphrase.isNullOrBlank()) return
        val offer = MeshProtocol(
            type = OFFER_TYPE,
            id = java.util.UUID.randomUUID().toString(),
            senderId = peerId,
            senderName = name,
            senderRole = role,
            recipientId = "BROADCAST",
            content = "HOTSPOT_OFFER",
            timestamp = System.currentTimeMillis(),
            ttl = 1, // local radio only — never relayed
            hotspotSsid = s.ssid,
            hotspotPass = s.passphrase
        )
        broadcast(offer.toJson())
    }

    // ─────────────────────────────── CLIENT ───────────────────────────────

    /** Handle a HOTSPOT_OFFER heard from a neighbour; auto-join when appropriate. */
    fun onOfferReceived(ssid: String, passphrase: String, @Suppress("UNUSED_PARAMETER") fromPeerId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _state.value = _state.value.copy(
                status = "Hotspot terdeteksi (butuh Android 10+ untuk gabung otomatis)"
            )
            return
        }
        if (reservation != null) return       // we host our own AP
        if (joinedSsid != null) return         // already on a hotspot — stay put, don't hop
        if (requestedSsid != null) return      // a join is already in flight — avoid cancel/retry churn
        joinHotspot(ssid, passphrase)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    fun joinHotspot(ssid: String, passphrase: String) {
        if (reservation != null) return
        leave() // drop any previous join before requesting a new one
        requestedSsid = ssid
        _state.value = _state.value.copy(status = "Bergabung ke hotspot $ssid…")

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(passphrase)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Route this app's sockets (incl. the LAN backbone) over the hotspot subnet so the
                // existing mesh backbone meshes everyone M-to-N. The hotspot has no internet — fine
                // for an offline app.
                connectivityManager.bindProcessToNetwork(network)
                joinedSsid = ssid
                _state.value = HotspotUiState(
                    role = "CLIENT", ssid = ssid,
                    status = "Terhubung ke hotspot — mesh via LAN"
                )
                Log.d(TAG, "Joined hotspot $ssid; bound process to network")
            }

            override fun onUnavailable() {
                Log.w(TAG, "Hotspot join unavailable for $ssid")
                _state.value = HotspotUiState(status = "Gagal bergabung ke hotspot $ssid")
                joinCallback = null
                requestedSsid = null
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Hotspot network lost")
                try { connectivityManager.bindProcessToNetwork(null) } catch (_: Exception) {}
                joinedSsid = null
                requestedSsid = null
                if (_state.value.role == "CLIENT") _state.value = HotspotUiState(status = "Koneksi hotspot terputus")
            }
        }
        joinCallback = cb
        try {
            connectivityManager.requestNetwork(request, cb)
        } catch (e: Exception) {
            Log.e(TAG, "requestNetwork failed", e)
            _state.value = HotspotUiState(status = "Gagal meminta jaringan hotspot")
            joinCallback = null
            requestedSsid = null
        }
    }

    /** Drop any active client join and unbind the process network. */
    fun leave() {
        joinCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        joinCallback = null
        requestedSsid = null
        if (joinedSsid != null) {
            try { connectivityManager.bindProcessToNetwork(null) } catch (_: Exception) {}
            joinedSsid = null
        }
    }

    /** Full teardown — host + client. */
    fun stop() {
        stopHosting()
        leave()
        _state.value = HotspotUiState()
    }
}
