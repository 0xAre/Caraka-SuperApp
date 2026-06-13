package com.example.caraka.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet6Address
import java.util.concurrent.ConcurrentHashMap

/**
 * Physical layer for Wi-Fi Aware (NAN). Replaces the Wi-Fi Direct star/Group-Owner topology with
 * Out-of-Band (OOB) L2 data paths, which have no single-group limitation — every neighbour gets its
 * own dedicated link, enabling true any-to-any multi-hop on top (see [MeshRouter]).
 *
 * Connection model (canonical Android NAN bring-up):
 *  1. Each device both publishes and subscribes the CARAKA service (symmetric discovery).
 *  2. A subscriber that discovers a publisher sends it a tiny ping message and immediately requests
 *     an INITIATOR (client) network from its subscribe session + the publisher's PeerHandle.
 *  3. The publisher, on receiving that ping, requests a RESPONDER (server) network from its publish
 *     session + the subscriber's PeerHandle, advertising [AWARE_PORT] via setPort().
 *  4. On the initiator side, [ConnectivityManager.NetworkCallback.onCapabilitiesChanged] yields the
 *     peer's link-local IPv6 + port; we hand that to [onClientDataPathReady] so the caller can open
 *     a TCP socket bound to the Aware [Network] (see [MeshSocketManager.connectOverNetwork]).
 *
 * The server side does not need the peer address — its [MeshSocketManager] already listens on
 * [AWARE_PORT] and the framework routes incoming connections over the Aware interface.
 */
@RequiresApi(Build.VERSION_CODES.O)
class WifiAwareManager(
    private val context: Context,
    private val onPeerDiscovered: (peerHandle: PeerHandle, distance: Int?) -> Unit,
    /** Initiator side: a data path is up; open a client socket to [peerIpv6]:[port] over [network]. */
    private val onClientDataPathReady: (network: Network, peerIpv6: Inet6Address, port: Int) -> Unit
) {
    companion object {
        private const val TAG = "WifiAwareManager"
        private const val AWARE_SERVICE_NAME = "CarakaAwareMesh"
        /** TCP port the publisher (server) listens on over the Aware data path. */
        const val AWARE_PORT = 8989
        private const val PING_MESSAGE_ID = 1

        /**
         * Derive a per-pair WPA2 passphrase from two peer IDs so every device-pair uses a
         * unique PSK instead of the old shared hardcoded "CarakaSecureMesh2026".
         * Sorted so both sides produce the same string regardless of who initiates.
         */
        fun deriveAwarePsk(peerId1: String, peerId2: String): String {
            val sorted = listOf(peerId1, peerId2).sorted()
            // 16 chars from each side → 33-char PSK (WPA2 allows 8-63 chars)
            return "CRK-${sorted[0].take(16)}-${sorted[1].take(16)}"
        }
    }

    // Populated in startAwareSession() so PSK derivation has access to our own peerId.
    private var localPeerId: String = ""

    private val awareManager: android.net.wifi.aware.WifiAwareManager? =
        context.getSystemService(Context.WIFI_AWARE_SERVICE) as? android.net.wifi.aware.WifiAwareManager
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null

    // Avoid requesting duplicate data paths for the same peer (discovery fires repeatedly).
    private val initiatorRequested = ConcurrentHashMap.newKeySet<PeerHandle>()
    private val responderRequested = ConcurrentHashMap.newKeySet<PeerHandle>()
    private val activeCallbacks = mutableListOf<ConnectivityManager.NetworkCallback>()

    private val _isAwareAvailable = MutableStateFlow(false)
    val isAwareAvailable: StateFlow<Boolean> = _isAwareAvailable

    fun isSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)

    fun startAwareSession(localPeerId: String = "") {
        if (localPeerId.isNotBlank()) this.localPeerId = localPeerId
        if (!isSupported() || awareManager == null) {
            Log.e(TAG, "Wi-Fi Aware tidak didukung di perangkat ini.")
            _isAwareAvailable.value = false
            return
        }
        if (!awareManager.isAvailable) {
            Log.e(TAG, "Wi-Fi Aware saat ini tidak tersedia (Wi-Fi mati / konflik).")
            _isAwareAvailable.value = false
            return
        }

        awareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                Log.d(TAG, "Wi-Fi Aware Terpasang (Attached)")
                awareSession = session
                _isAwareAvailable.value = true
                startPublish()
                startSubscribe()
            }

            override fun onAttachFailed() {
                Log.e(TAG, "Gagal memasang Wi-Fi Aware Session")
                _isAwareAvailable.value = false
            }
        }, mainHandler)
    }

    private fun startPublish() {
        // Embed our peerId in serviceSpecificInfo so subscribers can derive the per-pair PSK.
        val ssi = localPeerId.toByteArray(Charsets.UTF_8).takeIf { it.isNotEmpty() }
        val config = PublishConfig.Builder()
            .setServiceName(AWARE_SERVICE_NAME)
            .apply { if (ssi != null) setServiceSpecificInfo(ssi) }
            .build()
        awareSession?.publish(config, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                Log.d(TAG, "Publish session dimulai")
                publishSession = session
            }

            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                // Subscriber sends its peerId as the ping payload so we can derive the same PSK.
                val remotePeerId = String(message, Charsets.UTF_8)
                    .takeIf { it.isNotBlank() && it != "CRK" } ?: "unknown"
                Log.d(TAG, "Ping dari subscriber (peerId=$remotePeerId) — menyiapkan responder")
                requestResponderNetwork(peerHandle, remotePeerId)
            }

            override fun onSessionConfigFailed() {
                Log.e(TAG, "Gagal memulai Publish session")
            }
        }, mainHandler)
    }

    private fun startSubscribe() {
        val config = SubscribeConfig.Builder().setServiceName(AWARE_SERVICE_NAME).build()
        awareSession?.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                Log.d(TAG, "Subscribe session dimulai")
                subscribeSession = session
            }

            override fun onServiceDiscovered(
                peerHandle: PeerHandle,
                serviceSpecificInfo: ByteArray,
                matchFilter: List<ByteArray>
            ) {
                // Publisher embeds its peerId in serviceSpecificInfo so we can derive the PSK.
                val remotePeerId = serviceSpecificInfo.takeIf { it.isNotEmpty() }
                    ?.let { String(it, Charsets.UTF_8) }
                    ?.takeIf { it.isNotBlank() } ?: "unknown"
                Log.d(TAG, "Ditemukan peer Aware: $peerHandle (peerId=$remotePeerId)")
                onPeerDiscovered(peerHandle, null)
                // Send our peerId as ping so the publisher can derive the same per-pair PSK.
                subscribeSession?.sendMessage(peerHandle, PING_MESSAGE_ID, localPeerId.toByteArray(Charsets.UTF_8))
                // Initiate our client-side data path with the per-pair PSK.
                requestInitiatorNetwork(peerHandle, remotePeerId)
            }

            override fun onSessionConfigFailed() {
                Log.e(TAG, "Gagal memulai Subscribe session")
            }
        }, mainHandler)
    }

    /** Initiator (client): build a data path from the subscribe session and connect a socket. */
    private fun requestInitiatorNetwork(peerHandle: PeerHandle, remotePeerId: String) {
        val session = subscribeSession ?: return
        if (!initiatorRequested.add(peerHandle)) return // already requested

        val psk = deriveAwarePsk(localPeerId, remotePeerId)
        val specifier = WifiAwareNetworkSpecifier.Builder(session, peerHandle)
            .setPskPassphrase(psk)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(specifier)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val info = caps.transportInfo as? WifiAwareNetworkInfo ?: return
                val ipv6 = info.peerIpv6Addr ?: return
                val port = info.port
                Log.d(TAG, "Data path INITIATOR siap → [$ipv6]:$port")
                onClientDataPathReady(network, ipv6, port)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Data path initiator terputus dari $peerHandle")
                initiatorRequested.remove(peerHandle)
            }

            override fun onUnavailable() {
                Log.e(TAG, "Data path initiator gagal (Unavailable) untuk $peerHandle")
                initiatorRequested.remove(peerHandle)
            }
        }
        registerNetwork(request, callback)
    }

    /** Responder (server): build a data path from the publish session, advertising [AWARE_PORT]. */
    private fun requestResponderNetwork(peerHandle: PeerHandle, remotePeerId: String) {
        val session = publishSession ?: return
        if (!responderRequested.add(peerHandle)) return

        val psk = deriveAwarePsk(localPeerId, remotePeerId)
        val specifier = WifiAwareNetworkSpecifier.Builder(session, peerHandle)
            .setPskPassphrase(psk)
            .setPort(AWARE_PORT)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(specifier)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Server socket (started once by MeshManager) accepts the incoming connection over
                // this network; nothing else to do here but keep the request alive.
                Log.d(TAG, "Data path RESPONDER aktif untuk $peerHandle")
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Data path responder terputus dari $peerHandle")
                responderRequested.remove(peerHandle)
            }
        }
        registerNetwork(request, callback)
    }

    private fun registerNetwork(request: NetworkRequest, callback: ConnectivityManager.NetworkCallback) {
        connectivityManager.requestNetwork(request, callback)
        synchronized(activeCallbacks) { activeCallbacks.add(callback) }
    }

    fun stop() {
        synchronized(activeCallbacks) {
            activeCallbacks.forEach { try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {} }
            activeCallbacks.clear()
        }
        initiatorRequested.clear()
        responderRequested.clear()
        publishSession?.close(); publishSession = null
        subscribeSession?.close(); subscribeSession = null
        awareSession?.close(); awareSession = null
        _isAwareAvailable.value = false
    }
}
