package com.example.caraka.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build

/**
 * Listens for WiFi Direct events from the Android System.
 * Takes a channel provider lambda so it always uses the current (possibly re-initialized) channel.
 */
class WifiDirectReceiver(
    private val manager: WifiP2pManager,
    private val getChannel: () -> WifiP2pManager.Channel,
    private val wifiDirectManager: WifiDirectManager
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val channel = getChannel()
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                wifiDirectManager.onWifiStateChanged(isEnabled)
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                try {
                    manager.requestPeers(channel) { peers ->
                        wifiDirectManager.onPeersAvailable(peers.deviceList.toList())
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = getNetworkInfo(intent)
                if (networkInfo?.isConnected == true) {
                    manager.requestConnectionInfo(channel) { info ->
                        wifiDirectManager.onConnectionInfoAvailable(info)
                    }
                } else {
                    wifiDirectManager.onDisconnected()
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> { /* no-op */ }
        }
    }

    @Suppress("DEPRECATION")
    private fun getNetworkInfo(intent: Intent): NetworkInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
        } else {
            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
        }
    }
}
