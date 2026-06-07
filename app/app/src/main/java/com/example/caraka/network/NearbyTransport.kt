package com.example.caraka.network

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.util.concurrent.ConcurrentHashMap

/**
 * Google Nearby Connections (P2P_CLUSTER) transport — the primary reliable mesh overlay on
 * commodity phones that lack Wi-Fi Aware hardware.
 *
 * Why this beats raw Wi-Fi Direct here: Nearby manages Bluetooth/BLE/Wi-Fi under the hood and
 * upgrades the medium automatically, and CLUSTER is an M-to-N topology where every device can both
 * advertise and discover — so device B↔C connect directly without a Group Owner, which is exactly
 * the single-group limitation that broke the old Wi-Fi Direct star topology.
 *
 * Multi-hop beyond direct neighbours is provided by the existing application brain
 * ([WifiDirectManager]): inbound payloads are fed into its message handlers, whose TTL re-broadcast
 * floods the message back out through [MeshManager]'s overlay sinks (anti-replay dedupe prevents
 * loops). The endpoint's advertised name carries the peerId so we can unicast to a known next hop.
 */
class NearbyTransport(
    context: Context,
    private val onMessage: (json: String, fromEndpointId: String) -> Unit,
    private val onEndpointConnected: (endpointId: String) -> Unit
) {
    companion object {
        private const val TAG = "NearbyTransport"
        private const val SERVICE_ID = "com.example.caraka.MESH"
        private val STRATEGY = Strategy.P2P_CLUSTER
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private var localName: String = "caraka"

    val connectedEndpoints = ConcurrentHashMap.newKeySet<String>()
    private val endpointToPeer = ConcurrentHashMap<String, String>()
    private val peerToEndpoint = ConcurrentHashMap<String, String>()
    private val requested = ConcurrentHashMap.newKeySet<String>()

    fun start(localName: String) {
        this.localName = localName.ifBlank { "caraka" }
        startAdvertising()
        startDiscovery()
    }

    private fun startAdvertising() {
        client.startAdvertising(
            localName, SERVICE_ID, lifecycle,
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        )
            .addOnSuccessListener { Log.d(TAG, "Advertising dimulai sebagai $localName") }
            .addOnFailureListener { Log.w(TAG, "Advertising gagal: ${it.message}") }
    }

    private fun startDiscovery() {
        client.startDiscovery(
            SERVICE_ID, discovery,
            DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        )
            .addOnSuccessListener { Log.d(TAG, "Discovery dimulai") }
            .addOnFailureListener { Log.w(TAG, "Discovery gagal: ${it.message}") }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            // endpointName carries the remote peerId.
            mapEndpoint(endpointId, info.endpointName)
            // Break symmetry: in P2P_CLUSTER both sides discover each other, and if both call
            // requestConnection at once they collide → STATUS_ENDPOINT_IO_ERROR (8012). Only the
            // lexicographically-smaller peerId initiates; the other waits and accepts via
            // onConnectionInitiated.
            if (localName >= info.endpointName) {
                Log.d(TAG, "Menunggu '${info.endpointName}' menginisiasi (tiebreaker)")
                return
            }
            if (requested.add(endpointId)) {
                Log.d(TAG, "Endpoint ditemukan '${info.endpointName}' — requesting connection")
                client.requestConnection(localName, endpointId, lifecycle)
                    .addOnFailureListener {
                        Log.w(TAG, "requestConnection gagal: ${it.message}")
                        requested.remove(endpointId)
                    }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint hilang: $endpointId")
            requested.remove(endpointId)
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            mapEndpoint(endpointId, info.endpointName)
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                Log.d(TAG, "Terhubung ke endpoint $endpointId (peer=${endpointToPeer[endpointId]})")
                onEndpointConnected(endpointId)
            } else {
                Log.w(TAG, "Koneksi gagal $endpointId: status=${resolution.status.statusCode}")
                requested.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Endpoint disconnected: $endpointId")
            connectedEndpoints.remove(endpointId)
            requested.remove(endpointId)
            endpointToPeer.remove(endpointId)?.let { peerToEndpoint.remove(it) }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            onMessage(String(bytes, Charsets.UTF_8), endpointId)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun mapEndpoint(endpointId: String, peerId: String) {
        if (peerId.isBlank()) return
        endpointToPeer[endpointId] = peerId
        peerToEndpoint[peerId] = endpointId
    }

    /** Flood a payload to every directly-connected endpoint. */
    fun broadcast(json: String) {
        val targets = connectedEndpoints.toList()
        if (targets.isEmpty()) return
        client.sendPayload(targets, Payload.fromBytes(json.toByteArray(Charsets.UTF_8)))
    }

    /** Unicast to a peer's endpoint if directly connected; otherwise flood (brain dedups). */
    fun sendToPeer(peerId: String, json: String) {
        val endpointId = peerToEndpoint[peerId]
        if (endpointId != null && connectedEndpoints.contains(endpointId)) {
            client.sendPayload(endpointId, Payload.fromBytes(json.toByteArray(Charsets.UTF_8)))
        } else {
            broadcast(json)
        }
    }

    fun stop() {
        try { client.stopAllEndpoints() } catch (_: Exception) {}
        try { client.stopAdvertising() } catch (_: Exception) {}
        try { client.stopDiscovery() } catch (_: Exception) {}
        connectedEndpoints.clear()
        endpointToPeer.clear()
        peerToEndpoint.clear()
        requested.clear()
    }
}
