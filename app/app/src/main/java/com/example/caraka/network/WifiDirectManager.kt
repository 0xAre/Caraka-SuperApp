package com.example.caraka.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.caraka.MainActivity
import com.example.caraka.crypto.CryptoManager
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.data.local.entity.MessageEntity
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.repository.MeshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.UUID

/**
 * Manages WiFi Direct API calls, socket lifecycle, and mesh protocol.
 * Implements MeshMessageListener to handle incoming data from sockets.
 */
class WifiDirectManager(
    private val context: Context,
    private val repository: MeshRepository,
    private val identityManager: IdentityManager,
    private val cryptoManager: CryptoManager
) : MeshMessageListener {

    companion object {
        private const val TAG = "WifiDirect"
        private const val LAN_DISCOVERY_PORT = 8890
        private const val LAN_DISCOVERY_INTERVAL_MS = 5_000L
    }

    private val manager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel =
        manager.initialize(context, context.mainLooper, null)
    private var receiver: WifiDirectReceiver? = null
    private var scanJob: Job? = null
    private var lanListenJob: Job? = null
    private var lanBroadcastJob: Job? = null
    private var lanSocket: DatagramSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    // ========== STATE FLOWS (exposed to ViewModel) ==========

    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled

    private val _availablePeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val availablePeers: StateFlow<List<WifiP2pDevice>> = _availablePeers

    private val _connectionState = MutableStateFlow("IDLE")
    val connectionState: StateFlow<String> = _connectionState

    // Socket manager for data transfer
    private val socketManager = MeshSocketManager(this)

    // Track the WiFi Direct MAC of the device we're connecting to
    private var pendingWifiDirectMac: String? = null

    // Stats exposed to ViewModel
    private val _relayedMessageCount = MutableStateFlow(0)
    val relayedMessageCount: StateFlow<Int> = _relayedMessageCount

    // ========== LIFECYCLE ==========

    fun startListening() {
        if (receiver != null) {
            discoverPeers()
            startLanDiscovery()
            return
        }

        val newReceiver = WifiDirectReceiver(manager, channel, this)
        receiver = newReceiver
        ContextCompat.registerReceiver(
            context,
            newReceiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
        requestCurrentPeers()
        discoverPeers()
        startPeriodicDiscovery()
        startLanDiscovery()
    }

    fun stopListening() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore if not registered
            }
        }
        receiver = null
        scanJob?.cancel()
        scanJob = null
        lanBroadcastJob?.cancel()
        lanBroadcastJob = null
        lanListenJob?.cancel()
        lanListenJob = null
        lanSocket?.close()
        lanSocket = null
        socketManager.stopAll()
    }

    fun startFallbackDiscovery() {
        startLanDiscovery()
    }

    // ========== WIFI DIRECT ACTIONS ==========

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        if (!_isWifiP2pEnabled.value) {
            Log.d(TAG, "WiFi P2P state not confirmed yet; trying discovery anyway")
        }

        _connectionState.value = "DISCOVERING"
        try {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Discovery started")
                    requestCurrentPeers()
                }

                override fun onFailure(reasonCode: Int) {
                    Log.e(TAG, "Discovery failed: $reasonCode")
                    _connectionState.value = "DISCOVERY_FAILED"
                    requestCurrentPeers()
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for WiFi Direct discovery", e)
            _connectionState.value = "PERMISSION_MISSING"
        }
    }

    /**
     * Use reflection to set the WiFi Direct device name.
     * This ensures other devices see the user's Display Name during discovery.
     */
    fun updateDeviceName(name: String) {
        try {
            val method = manager.javaClass.getMethod(
                "setDeviceName",
                WifiP2pManager.Channel::class.java,
                String::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            method.invoke(manager, channel, name, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Device name changed to $name")
                }
                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Failed to change device name: $reason")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Reflection failed to set device name", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(device: WifiP2pDevice) {
        _connectionState.value = "CONNECTING"
        pendingWifiDirectMac = device.deviceAddress
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connecting to ${device.deviceName}...")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connect failed: $reason")
                _connectionState.value = "CONNECT_FAILED"
                pendingWifiDirectMac = null
            }
        })
    }

    /**
     * Send a MeshProtocol message over the active socket.
     */
    fun sendMessage(json: String) {
        socketManager.sendPayload(json)
    }

    // ========== CALLBACKS FROM BROADCAST RECEIVER ==========

    internal fun onWifiStateChanged(isEnabled: Boolean) {
        _isWifiP2pEnabled.value = isEnabled
        if (isEnabled) {
            requestCurrentPeers()
            discoverPeers()
        } else {
            _availablePeers.value = emptyList()
            _connectionState.value = "WIFI_P2P_DISABLED"
        }
    }

    internal fun onPeersAvailable(peers: List<WifiP2pDevice>) {
        _availablePeers.value = peers
        _connectionState.value = if (peers.isEmpty()) "NO_PEERS" else "PEERS_FOUND"
    }

    internal fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        val groupOwnerAddress = info.groupOwnerAddress?.hostAddress

        if (info.groupFormed && info.isGroupOwner) {
            Log.d(TAG, "I am the Group Owner. Starting server socket.")
            _connectionState.value = "CONNECTED_GO"
            socketManager.startServer()
        } else if (info.groupFormed) {
            Log.d(TAG, "I am a Client. Connecting to GO: $groupOwnerAddress")
            _connectionState.value = "CONNECTED_CLIENT"
            if (groupOwnerAddress != null) {
                socketManager.startClient(groupOwnerAddress)
            }
        }
    }

    internal fun onDisconnected() {
        Log.d(TAG, "Disconnected")
        _connectionState.value = "DISCONNECTED"
        socketManager.stopAll()
        scope.launch {
            repository.disconnectAllPeers()
        }
        requestCurrentPeers()
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentPeers() {
        try {
            manager.requestPeers(channel) { peers ->
                onPeersAvailable(peers.deviceList.toList())
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to request WiFi Direct peers", e)
            _connectionState.value = "PERMISSION_MISSING"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request WiFi Direct peers", e)
        }
    }

    private fun startPeriodicDiscovery() {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            while (true) {
                discoverPeers()
                delay(15_000L)
            }
        }
    }

    private fun startLanDiscovery() {
        startLanListener()
        startLanBroadcaster()
    }

    private fun startLanListener() {
        if (lanListenJob?.isActive == true) return
        lanListenJob = scope.launch {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(LAN_DISCOVERY_PORT))
                }
                lanSocket = socket
                val buffer = ByteArray(65_536)

                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val json = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val protocol = MeshProtocol.fromJson(json) ?: continue
                    val myId = identityManager.getPeerId()

                    if (protocol.type == "HANDSHAKE" &&
                        protocol.content == "LAN_DISCOVERY" &&
                        protocol.senderId.isNotBlank() &&
                        protocol.senderId != myId
                    ) {
                        saveLanPeer(protocol, packet.address.hostAddress ?: "unknown")
                    }
                }
            } catch (_: SocketException) {
                // Socket is closed during normal shutdown.
            } catch (e: Exception) {
                Log.e(TAG, "LAN discovery listener failed", e)
            }
        }
    }

    private fun startLanBroadcaster() {
        if (lanBroadcastJob?.isActive == true) return
        lanBroadcastJob = scope.launch {
            while (true) {
                sendLanHandshake()
                delay(LAN_DISCOVERY_INTERVAL_MS)
            }
        }
    }

    private suspend fun sendLanHandshake() {
        val myId = identityManager.getPeerId()
        if (myId.isBlank()) return

        val handshake = MeshProtocol(
            type = "HANDSHAKE",
            id = UUID.randomUUID().toString(),
            senderId = myId,
            senderName = identityManager.getDisplayName(),
            senderRole = identityManager.getRole(),
            recipientId = "BROADCAST",
            content = "LAN_DISCOVERY",
            timestamp = System.currentTimeMillis(),
            publicKey = identityManager.getEncryptionPublicKeyBase64(),
            signingKey = identityManager.getSigningPublicKeyBase64()
        )
        val bytes = handshake.toJson().toByteArray(Charsets.UTF_8)

        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                localBroadcastAddresses().forEach { address ->
                    val packet = DatagramPacket(bytes, bytes.size, address, LAN_DISCOVERY_PORT)
                    socket.send(packet)
                }
            }
            Log.d(TAG, "LAN discovery handshake broadcast")
        } catch (e: Exception) {
            Log.e(TAG, "LAN discovery broadcast failed", e)
        }
    }

    private suspend fun saveLanPeer(protocol: MeshProtocol, hostAddress: String) {
        val peer = PeerEntity(
            id = protocol.senderId,
            deviceName = protocol.senderName,
            displayName = protocol.senderName,
            role = protocol.senderRole,
            publicKey = protocol.publicKey ?: "",
            signingKey = protocol.signingKey ?: "",
            isVerified = false,
            isAuthority = protocol.senderRole in listOf("BPBD", "POLRI", "PMI"),
            macAddress = "lan:$hostAddress",
            lastSeen = System.currentTimeMillis(),
            isConnected = true,
            hopCount = 0
        )
        repository.savePeer(peer)
        _connectionState.value = "LAN_PEER_FOUND"
        Log.d(TAG, "LAN peer saved: ${peer.displayName} at $hostAddress")
    }

    private fun localBroadcastAddresses(): Set<InetAddress> {
        val addresses = linkedSetOf(InetAddress.getByName("255.255.255.255"))
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val dhcp = wifiManager?.dhcpInfo ?: return addresses
        val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
        val bytes = byteArrayOf(
            (broadcast and 0xff).toByte(),
            ((broadcast shr 8) and 0xff).toByte(),
            ((broadcast shr 16) and 0xff).toByte(),
            ((broadcast shr 24) and 0xff).toByte()
        )
        addresses.add(InetAddress.getByAddress(bytes))
        return addresses
    }

    // ========== MESH MESSAGE LISTENER (from MeshSocketManager) ==========

    override fun onMessageReceived(protocol: MeshProtocol, fromAddress: String) {
        // ── Anti-replay gate ─────────────────────────────────────────────────
        // HANDSHAKE is exempt from dedup (each new socket sends one)
        if (protocol.type != "HANDSHAKE" && socketManager.isDuplicate(protocol.id, protocol.timestamp)) {
            Log.d(TAG, "Anti-replay: dropped duplicate/stale ${protocol.type} id=${protocol.id}")
            return
        }
        scope.launch {
            when (protocol.type) {
                "HANDSHAKE" -> handleHandshake(protocol, fromAddress)
                "TEXT"      -> handleTextMessage(protocol)
                "SOS"       -> handleSosMessage(protocol)
                "FLAG"      -> handleFlagMessage(protocol)
                else -> Log.w(TAG, "Unknown message type: ${protocol.type}")
            }
        }
    }

    override fun onPeerConnected(address: String, isServer: Boolean) {
        Log.d(TAG, "Socket peer connected: $address (server=$isServer)")
        _connectionState.value = "CONNECTED"
        // Send handshake with our identity
        scope.launch {
            sendHandshake()
        }
    }

    override fun onPeerDisconnected(address: String) {
        Log.d(TAG, "Socket peer disconnected: $address")
        _connectionState.value = "DISCONNECTED"
    }

    // ========== MESSAGE HANDLERS ==========

    /**
     * Send handshake containing our identity + public keys.
     * This is the first message sent after TCP connection is established.
     */
    private suspend fun sendHandshake() {
        val handshake = MeshProtocol(
            type = "HANDSHAKE",
            id = UUID.randomUUID().toString(),
            senderId = identityManager.getPeerId(),
            senderName = identityManager.getDisplayName(),
            senderRole = identityManager.getRole(),
            recipientId = "BROADCAST",
            content = "HANDSHAKE",
            timestamp = System.currentTimeMillis(),
            publicKey = identityManager.getEncryptionPublicKeyBase64(),
            signingKey = identityManager.getSigningPublicKeyBase64()
        )
        socketManager.sendPayload(handshake.toJson())
        Log.d(TAG, "Handshake sent")
    }

    /**
     * Process incoming handshake — save peer to local DB.
     */
    private suspend fun handleHandshake(protocol: MeshProtocol, fromAddress: String) {
        Log.d(TAG, "Received HANDSHAKE from ${protocol.senderName} (${protocol.senderId})")

        // Use pending WiFi Direct MAC if we initiated the connection,
        // otherwise try to find the peer in available peers list
        val wifiDirectMac = pendingWifiDirectMac
            ?: _availablePeers.value.firstOrNull()?.deviceAddress
            ?: fromAddress
        pendingWifiDirectMac = null
        Log.d(TAG, "Resolved WiFi Direct MAC: $wifiDirectMac for ${protocol.senderName}")

        val peer = PeerEntity(
            id = protocol.senderId,
            deviceName = protocol.senderName,
            displayName = protocol.senderName,
            role = protocol.senderRole,
            publicKey = protocol.publicKey ?: "",
            signingKey = protocol.signingKey ?: "",
            isVerified = false,
            isAuthority = protocol.senderRole in listOf("BPBD", "POLRI", "PMI"),
            macAddress = wifiDirectMac,
            lastSeen = System.currentTimeMillis(),
            isConnected = true,
            hopCount = 0
        )
        repository.savePeer(peer)
        Log.d(TAG, "Peer saved: ${peer.displayName} with MAC: $wifiDirectMac")
    }

    /**
     * Process incoming TEXT message.
     * Multi-hop relay: if this message isn't addressed to us, forward it (TTL−1).
     * Only save to DB when the message is meant for this device.
     */
    private suspend fun handleTextMessage(protocol: MeshProtocol) {
        val myId = identityManager.getPeerId()
        val isForMe = protocol.recipientId == myId || protocol.recipientId == "BROADCAST"

        // ── Multi-hop relay ───────────────────────────────────────────────────
        if (!isForMe && protocol.ttl > 1) {
            val relayed = protocol.copy(ttl = protocol.ttl - 1)
            socketManager.sendPayload(relayed.toJson())
            _relayedMessageCount.value++
            Log.d(TAG, "Relaying TEXT ${protocol.id} → ${protocol.recipientId}, TTL=${relayed.ttl}")
            return // Don't persist — not our message
        }
        if (!isForMe) {
            Log.d(TAG, "TEXT TTL exhausted, not for us — dropping")
            return
        }

        Log.d(TAG, "Received TEXT from ${protocol.senderName}")

        // Decrypt if encrypted
        var plaintext = protocol.content
        if (protocol.encryptedPayload != null) {
            val senderPeer = repository.getPeerById(protocol.senderId)
            if (senderPeer != null) {
                val senderPubKey = cryptoManager.keyFromBase64(senderPeer.publicKey)
                val mySecretKey = identityManager.getEncryptionKeyPair().secretKey
                val decrypted = cryptoManager.decryptMessage(
                    protocol.encryptedPayload, senderPubKey, mySecretKey
                )
                if (decrypted != null) plaintext = decrypted
                else Log.w(TAG, "Failed to decrypt message from ${protocol.senderName}")
            }
        }

        val entity = MessageEntity(
            id = protocol.id,
            type = protocol.type,
            senderId = protocol.senderId,
            senderName = protocol.senderName,
            senderRole = protocol.senderRole,
            recipientId = protocol.recipientId,
            content = plaintext,
            encryptedPayload = protocol.encryptedPayload,
            timestamp = protocol.timestamp,
            ttl = protocol.ttl,
            priority = protocol.priority,
            signature = protocol.signature,
            sosCategory = null,
            latitude = null,
            longitude = null,
            isIncoming = true
        )
        repository.saveMessage(entity)
    }

    /**
     * Process incoming SOS message.
     * SOS is never encrypted. Always relay to extend coverage, then persist locally.
     */
    private suspend fun handleSosMessage(protocol: MeshProtocol) {
        Log.d(TAG, "Received SOS from ${protocol.senderName}: ${protocol.content}")

        // ── Multi-hop relay ───────────────────────────────────────────────────
        if (protocol.ttl > 1) {
            val relayed = protocol.copy(ttl = protocol.ttl - 1)
            socketManager.sendPayload(relayed.toJson())
            _relayedMessageCount.value++
            Log.d(TAG, "Relaying SOS ${protocol.id}, TTL=${relayed.ttl}")
        }

        val entity = MessageEntity(
            id = protocol.id,
            type = "SOS",
            senderId = protocol.senderId,
            senderName = protocol.senderName,
            senderRole = protocol.senderRole,
            recipientId = "BROADCAST",
            content = protocol.content,
            encryptedPayload = null,
            timestamp = protocol.timestamp,
            ttl = protocol.ttl,
            priority = "EMERGENCY",
            signature = protocol.signature,
            sosCategory = protocol.sosCategory,
            latitude = protocol.latitude,
            longitude = protocol.longitude,
            isIncoming = true
        )
        repository.saveMessage(entity)
        showSosNotification(protocol.senderName, protocol.content)
    }

    /**
     * Process incoming FLAG message — increment flag count for the target message,
     * then relay so the flag propagates through the mesh.
     */
    private suspend fun handleFlagMessage(protocol: MeshProtocol) {
        val targetId = protocol.content // content carries the ID of the flagged message
        if (targetId.isNotBlank()) {
            repository.flagMessageById(targetId)
            Log.d(TAG, "Flagged message $targetId by ${protocol.senderName}")
        }
        // Relay the flag
        if (protocol.ttl > 1) {
            socketManager.sendPayload(protocol.copy(ttl = protocol.ttl - 1).toJson())
        }
    }

    private fun showSosNotification(sender: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sos_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical emergency messages from the mesh network"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("EMERGENCY SOS: $sender")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
