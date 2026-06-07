package com.example.caraka.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manajer untuk lapisan fisik Wi-Fi Aware (NAN).
 * Menggantikan WifiP2pManager dengan pendekatan koneksi OOB (Out-of-Band) L2.
 */
@RequiresApi(Build.VERSION_CODES.O)
class WifiAwareManager(
    private val context: Context,
    private val onPeerDiscovered: (peerHandle: PeerHandle, distance: Int?) -> Unit,
    private val onNetworkConnected: (network: Network, peerHandle: PeerHandle) -> Unit
) {
    companion object {
        private const val TAG = "WifiAwareManager"
        private const val AWARE_SERVICE_NAME = "CarakaAwareMesh"
    }

    private val awareManager: android.net.wifi.aware.WifiAwareManager? =
        context.getSystemService(Context.WIFI_AWARE_SERVICE) as? android.net.wifi.aware.WifiAwareManager
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var awareSession: WifiAwareSession? = null
    private var discoverySession: DiscoverySession? = null
    
    private val _isAwareAvailable = MutableStateFlow(false)
    val isAwareAvailable: StateFlow<Boolean> = _isAwareAvailable

    fun isSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
    }

    fun startAwareSession() {
        if (!isSupported() || awareManager == null) {
            Log.e(TAG, "Wi-Fi Aware tidak didukung di perangkat ini.")
            _isAwareAvailable.value = false
            return
        }
        
        if (!awareManager.isAvailable) {
            Log.e(TAG, "Wi-Fi Aware saat ini sedang tidak tersedia (mungkin Wi-Fi mati/konflik).")
            _isAwareAvailable.value = false
            return
        }

        awareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                Log.d(TAG, "Wi-Fi Aware Terpasang (Attached)")
                awareSession = session
                _isAwareAvailable.value = true
                startDiscovery()
            }

            override fun onAttachFailed() {
                Log.e(TAG, "Gagal memasang Wi-Fi Aware Session")
                _isAwareAvailable.value = false
            }
        }, null)
    }

    private fun startDiscovery() {
        val config = PublishConfig.Builder()
            .setServiceName(AWARE_SERVICE_NAME)
            .build()

        awareSession?.publish(config, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                Log.d(TAG, "Publish Session dimulai")
                discoverySession = session
                startSubscribe()
            }

            override fun onSessionConfigFailed() {
                Log.e(TAG, "Gagal memulai Publish Session")
            }
        }, null)
    }

    private fun startSubscribe() {
        val config = SubscribeConfig.Builder()
            .setServiceName(AWARE_SERVICE_NAME)
            .build()

        awareSession?.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                Log.d(TAG, "Subscribe Session dimulai")
                // Kita bisa menggunakan session yang sama atau berbeda untuk Data Path
            }

            override fun onServiceDiscovered(
                peerHandle: PeerHandle,
                serviceSpecificInfo: ByteArray,
                matchFilter: List<ByteArray>
            ) {
                Log.d(TAG, "Ditemukan peer Aware dengan PeerHandle: $peerHandle")
                onPeerDiscovered(peerHandle, null)
                requestDataPath(peerHandle)
            }
        }, null)
    }

    /**
     * Meminta pembuatan Data Path L2 (Out-of-Band) dengan Peer yang ditemukan.
     */
    private fun requestDataPath(peerHandle: PeerHandle) {
        val session = discoverySession ?: return
        
        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(session, peerHandle)
            .setPmk("CarakaSecureMesh2026".toByteArray()) // Shared secret atau passphrase
            .build()
            
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()
            
        connectivityManager.requestNetwork(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Data Path terhubung dengan sukses (L2 Link aktif)")
                onNetworkConnected(network, peerHandle)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Data Path terputus dengan $peerHandle")
            }
            
            override fun onUnavailable() {
                Log.e(TAG, "Gagal membuat Data Path (Unavailable)")
            }
        })
    }

    fun stop() {
        discoverySession?.close()
        discoverySession = null
        awareSession?.close()
        awareSession = null
    }
}
