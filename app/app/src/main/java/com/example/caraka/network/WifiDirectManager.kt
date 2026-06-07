package com.example.caraka.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.UUID

/** A transient in-app alert for an incoming direct chat message. */
data class ChatAlert(
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val content: String
)

class WifiDirectManager(
    private val context: Context,
    private val repository: MeshRepository,
    private val identityManager: IdentityManager,
    private val cryptoManager: CryptoManager
) : MeshMessageListener {

    companion object {
        private const val TAG = "WifiDirect"
        private const val LAN_DISCOVERY_PORT = 8890
        private const val LAN_DISCOVERY_INTERVAL_MS = 3_000L
        private const val DISCOVERY_INTERVAL_MS = 6_000L
        // Long cooldown is deliberate: a connect attempt makes us non-discoverable while it runs,
        // so frequent attempts stop the peer from ever finding us. Staying discoverable most of
        // the time lets BOTH peers see each other and lets GO negotiation succeed.
        private const val AUTO_CONNECT_COOLDOWN_MS = 20_000L
    }

    private val manager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager

    // Channel as var so it can be re-initialized when disconnected
    @Volatile
    private var channel: WifiP2pManager.Channel = initChannel()

    private fun initChannel(): WifiP2pManager.Channel {
        return manager.initialize(context, context.mainLooper) {
            // Channel disconnected (WiFi toggled, app backgrounded, etc.)
            Log.w(TAG, "P2P channel disconnected — re-initializing")
            channel = initChannel()
            // Re-register receiver with new channel
            val old = receiver
            if (old != null) {
                try { context.unregisterReceiver(old) } catch (_: Exception) {}
            }
            val newReceiver = WifiDirectReceiver(manager, { channel }, this)
            receiver = newReceiver
            ContextCompat.registerReceiver(context, newReceiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)
            // Restart discovery on the fresh channel
            scope.launch(Dispatchers.Main) {
                delay(1_000L)
                discoverPeers()
            }
        }
    }

    private var receiver: WifiDirectReceiver? = null
    private var scanJob: Job? = null
    private var lanListenJob: Job? = null
    private var lanBroadcastJob: Job? = null
    private var lanSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    // ========== STATE FLOWS ==========

    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled

    private val _availablePeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val availablePeers: StateFlow<List<WifiP2pDevice>> = _availablePeers

    private val _connectionState = MutableStateFlow("IDLE")
    val connectionState: StateFlow<String> = _connectionState

    private val socketManager = MeshSocketManager(this)
    private var pendingWifiDirectMac: String? = null

    private val _relayedMessageCount = MutableStateFlow(0)
    val relayedMessageCount: StateFlow<Int> = _relayedMessageCount

    // Emits when a direct chat message arrives — used to show an in-app floating alert.
    private val _incomingChatAlert = MutableSharedFlow<ChatAlert>(extraBufferCapacity = 8)
    val incomingChatAlert: SharedFlow<ChatAlert> = _incomingChatAlert

    // Auto-connect cooldown tracker
    @Volatile private var lastConnectAttemptMs = 0L

    // Cached authority flag — read once from IdentityManager on first connect
    @Volatile private var cachedIsAuthority = false

    /** Current battery level (0-100). Updated on each connect attempt. */
    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel

    // ========== LIFECYCLE ==========

    fun startListening() {
        // Cache authority flag for GO election (avoids suspend call in hot path)
        scope.launch {
            cachedIsAuthority = identityManager.isAuthority()
            Log.d(TAG, "GO election: authority=$cachedIsAuthority")
        }

        if (receiver != null) {
            clearGroupThenDiscover()
            startLanDiscovery()
            return
        }

        val newReceiver = WifiDirectReceiver(manager, { channel }, this)
        receiver = newReceiver
        ContextCompat.registerReceiver(
            context,
            newReceiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
        // Remove any stale P2P group from a previous session before starting fresh
        clearGroupThenDiscover()
        startPeriodicDiscovery()
        startLanDiscovery()
    }

    /** Remove stale active group + delete all persistent groups, then discover. */
    @SuppressLint("MissingPermission")
    private fun clearGroupThenDiscover() {
        deletePersistentGroups()
        try {
            manager.requestGroupInfo(channel) { group ->
                if (group != null) {
                    Log.d(TAG, "Removing stale P2P group: ${group.networkName}")
                    manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            Log.d(TAG, "Stale group removed — starting discovery")
                            scope.launch { delay(500L); requestCurrentPeers(); discoverPeers() }
                        }
                        override fun onFailure(reason: Int) {
                            Log.w(TAG, "removeGroup failed ($reason) — discovering anyway")
                            requestCurrentPeers(); discoverPeers()
                        }
                    })
                } else {
                    requestCurrentPeers(); discoverPeers()
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission missing in clearGroupThenDiscover", e)
            discoverPeers()
        }
    }

    /**
     * Delete all persistent P2P group credentials via reflection.
     * Persistent groups store channel/credential info that can cause NO_COMMON_CHANNEL
     * after app reinstall if device remembers a 5GHz-only group the peer no longer has.
     */
    @SuppressLint("MissingPermission")
    private fun deletePersistentGroups() {
        try {
            val requestPGI = WifiP2pManager::class.java.getMethod(
                "requestPersistentGroupInfo",
                WifiP2pManager.Channel::class.java,
                Class.forName("android.net.wifi.p2p.WifiP2pManager\$PersistentGroupInfoListener")
            )
            val listenerClass = Class.forName("android.net.wifi.p2p.WifiP2pManager\$PersistentGroupInfoListener")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onPersistentGroupInfoAvailable" && args != null) {
                    val groupList = args[0] as? WifiP2pGroup
                    // WifiP2pGroupList.getGroupList() — we got WifiP2pGroup directly
                    // Actually the listener receives WifiP2pGroupList, iterate its entries
                    try {
                        val getGroupList = args[0].javaClass.getMethod("getGroupList")
                        @Suppress("UNCHECKED_CAST")
                        val groups = getGroupList.invoke(args[0]) as? Collection<WifiP2pGroup> ?: return@newProxyInstance null
                        for (g in groups) {
                            val netId = g.javaClass.getMethod("getNetworkId").invoke(g) as? Int ?: continue
                            Log.d(TAG, "Deleting persistent group netId=$netId (${g.networkName})")
                            try {
                                val del = WifiP2pManager::class.java.getMethod(
                                    "deletePersistentGroup",
                                    WifiP2pManager.Channel::class.java,
                                    Int::class.java,
                                    WifiP2pManager.ActionListener::class.java
                                )
                                del.invoke(manager, channel, netId, object : WifiP2pManager.ActionListener {
                                    override fun onSuccess() { Log.d(TAG, "Deleted persistent group $netId") }
                                    override fun onFailure(r: Int) { Log.w(TAG, "deletePersistentGroup $netId failed: $r") }
                                })
                            } catch (e: Exception) { Log.w(TAG, "deletePersistentGroup invoke failed", e) }
                        }
                    } catch (e: Exception) { Log.w(TAG, "Enumerate persistent groups failed", e) }
                }
                null
            }
            requestPGI.invoke(manager, channel, proxy)
        } catch (e: Exception) {
            Log.d(TAG, "deletePersistentGroups not available: ${e.message}")
        }
    }

    fun stopListening() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
        scanJob?.cancel(); scanJob = null
        lanBroadcastJob?.cancel(); lanBroadcastJob = null
        lanListenJob?.cancel(); lanListenJob = null
        lanSocket?.close(); lanSocket = null
        releaseMulticastLock()
        socketManager.stopAll()
    }

    fun startFallbackDiscovery() {
        startLanDiscovery()
    }

    // ========== WIFI DIRECT ACTIONS ==========

    /**
     * Stop any ongoing discovery, then start fresh.
     * Calling discoverPeers() while a scan is active returns BUSY.
     * stopPeerDiscovery → discoverPeers avoids that.
     */
    private val busyStates = setOf("CONNECTING", "CONNECTED", "CONNECTED_GO", "CONNECTED_CLIENT")

    // Serialize discovery: only one stop→discover cycle in flight, and only one BUSY retry pending.
    // Without this, the many discovery triggers (periodic loop, wifi-state, receiver, group-clear)
    // stack up and each BUSY schedules its own retry → a self-amplifying BUSY storm.
    @Volatile private var discoveryInFlight = false
    @Volatile private var retryScheduled = false

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        // Don't kick off discovery while a connection is forming/active, or while one is already running.
        if (_connectionState.value in busyStates) return
        if (discoveryInFlight) return
        discoveryInFlight = true
        _connectionState.value = "DISCOVERING"
        try {
            manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = startDiscovery()
                override fun onFailure(reason: Int) = startDiscovery()
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission missing in stopPeerDiscovery", e)
            startDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        try {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Discovery started OK")
                    discoveryInFlight = false
                    requestCurrentPeers()
                }

                override fun onFailure(reasonCode: Int) {
                    Log.w(TAG, "Discovery start failed: $reasonCode")
                    discoveryInFlight = false
                    if (reasonCode == WifiP2pManager.BUSY) {
                        scheduleDiscoveryRetry()
                    } else {
                        _connectionState.value = "DISCOVERY_FAILED"
                    }
                    requestCurrentPeers()
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for WiFi Direct discovery", e)
            discoveryInFlight = false
            _connectionState.value = "PERMISSION_MISSING"
        }
    }

    private fun scheduleDiscoveryRetry() {
        if (retryScheduled) return
        retryScheduled = true
        scope.launch {
            delay(3_000L)
            retryScheduled = false
            if (_connectionState.value !in busyStates) discoverPeers()
        }
    }

    fun updateDeviceName(name: String) {
        try {
            val method = manager.javaClass.getMethod(
                "setDeviceName",
                WifiP2pManager.Channel::class.java,
                String::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            method.invoke(manager, channel, name, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { Log.d(TAG, "Device name set to $name") }
                override fun onFailure(reason: Int) { Log.w(TAG, "setDeviceName failed: $reason") }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Reflection setDeviceName failed", e)
        }
    }

    @Volatile private var connectInFlight = false

    @SuppressLint("MissingPermission")
    fun connectToPeer(device: WifiP2pDevice) {
        if (connectInFlight) return
        connectInFlight = true
        _connectionState.value = "CONNECTING"
        pendingWifiDirectMac = device.deviceAddress

        // Smart GO election — compute intent based on battery, role, and relay load
        // instead of hardcoded 15 (always-GO) which causes group collapse when
        // a low-battery device wins the GO role and later drops out.
        val batteryPct = GoIntentCalculator.getBatteryLevel(context)
        _batteryLevel.value = batteryPct
        val goIntent = GoIntentCalculator.calculate(
            context = context,
            isAuthority = cachedIsAuthority,
            currentRelayLoad = _relayedMessageCount.value
        )
        Log.d(TAG, "Connecting to ${device.deviceName} — GO intent=$goIntent (battery=$batteryPct%, authority=$cachedIsAuthority)")

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = goIntent
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connect request sent to ${device.deviceName}")
                startConnectingWatchdog()
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Connect failed: $reason")
                connectInFlight = false
                _connectionState.value = "CONNECT_FAILED"
                pendingWifiDirectMac = null
                // Keep cooldown so we don't storm; rediscover so we stay visible and retry soon.
                scope.launch { delay(2_500L); discoverPeers() }
            }
        })
    }

    private var watchdogJob: Job? = null

    /** If a connection negotiation stalls silently, reset and rediscover. */
    private fun startConnectingWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(9_000L)
            if (_connectionState.value == "CONNECTING") {
                Log.w(TAG, "Connecting watchdog fired — negotiation stalled, resetting")
                connectInFlight = false
                _connectionState.value = "CONNECT_FAILED"
                pendingWifiDirectMac = null
                lastConnectAttemptMs = 0L
                clearGroupThenDiscover()
            }
        }
    }

    fun sendMessage(json: String) {
        socketManager.sendPayload(json)
        sendLanPayload(json)
    }

    // ========== CALLBACKS FROM BROADCAST RECEIVER ==========

    internal fun onWifiStateChanged(isEnabled: Boolean) {
        _isWifiP2pEnabled.value = isEnabled
        Log.d(TAG, "WiFi P2P enabled: $isEnabled")
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
        val prevState = _connectionState.value

        // Never overwrite a forming/active connection — doing so would resume discovery
        // and break P2P negotiation with BUSY churn.
        if (prevState !in busyStates) {
            _connectionState.value = if (peers.isEmpty()) "NO_PEERS" else "PEERS_FOUND"
        }

        Log.d(TAG, "Peers available: ${peers.size} — ${peers.map { it.deviceName }}")

        // Auto-connect to the first available peer if we're not already connected
        if (peers.isNotEmpty() && prevState !in busyStates) {
            val now = System.currentTimeMillis()
            if (now - lastConnectAttemptMs > AUTO_CONNECT_COOLDOWN_MS) {
                val candidate = peers.firstOrNull { it.status == WifiP2pDevice.AVAILABLE }
                    ?: peers.firstOrNull { it.status == WifiP2pDevice.FAILED }
                    ?: peers.firstOrNull()
                if (candidate != null) {
                    // Both sides keep discovering AND initiating — WiFi Direct requires both peers
                    // to be discoverable for GO negotiation, so a "wait for the other" scheme breaks
                    // it. The connectInFlight guard + cooldown stop a single device from storming.
                    lastConnectAttemptMs = now
                    Log.d(TAG, "Auto-connecting to ${candidate.deviceName} (${candidate.deviceAddress})")
                    connectToPeer(candidate)
                }
            }
        }
    }

    internal fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        watchdogJob?.cancel()
        connectInFlight = false
        val groupOwnerAddress = info.groupOwnerAddress?.hostAddress
        if (info.groupFormed && info.isGroupOwner) {
            Log.d(TAG, "I am Group Owner — starting server socket")
            _connectionState.value = "CONNECTED_GO"
            socketManager.startServer()
        } else if (info.groupFormed) {
            Log.d(TAG, "I am Client — connecting to GO: $groupOwnerAddress")
            _connectionState.value = "CONNECTED_CLIENT"
            if (groupOwnerAddress != null) {
                socketManager.startClient(groupOwnerAddress)
            }
        }
    }

    internal fun onDisconnected() {
        Log.d(TAG, "P2P disconnected")
        _connectionState.value = "DISCONNECTED"
        connectInFlight = false
        socketManager.stopAll()
        scope.launch { repository.disconnectAllPeers() }
        requestCurrentPeers()
        // Re-start discovery after disconnect
        scope.launch {
            delay(2_000L)
            discoverPeers()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentPeers() {
        try {
            manager.requestPeers(channel) { peers ->
                onPeersAvailable(peers.deviceList.toList())
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission to requestPeers", e)
        } catch (e: Exception) {
            Log.w(TAG, "requestPeers failed", e)
        }
    }

    private fun startPeriodicDiscovery() {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            while (true) {
                delay(DISCOVERY_INTERVAL_MS)
                // Only rediscover if not already connected
                val state = _connectionState.value
                if (state !in setOf("CONNECTED", "CONNECTED_GO", "CONNECTED_CLIENT", "CONNECTING")) {
                    discoverPeers()
                }
            }
        }
    }

    // ========== LAN DISCOVERY ==========

    private fun startLanDiscovery() {
        acquireMulticastLock()
        startLanListener()
        startLanBroadcaster()
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        multicastLock = wifiManager.createMulticastLock("caraka-mesh-lan").apply {
            setReferenceCounted(false)
            try { acquire() } catch (e: Exception) { Log.w(TAG, "multicastLock acquire failed", e) }
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            try { if (lock.isHeld) lock.release() } catch (e: Exception) { Log.w(TAG, "multicastLock release failed", e) }
        }
        multicastLock = null
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
                    val hostAddress = packet.address.hostAddress ?: "unknown"
                    if (protocol.senderId.isBlank() || protocol.senderId == myId) continue
                    if (protocol.type == "HANDSHAKE" && protocol.content == "LAN_DISCOVERY") {
                        saveLanPeer(protocol, hostAddress)
                    } else {
                        onMessageReceived(protocol, "lan:$hostAddress")
                    }
                }
            } catch (_: SocketException) {
                // Normal on shutdown
            } catch (e: Exception) {
                Log.w(TAG, "LAN listener error", e)
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
        sendLanPayloadInternal(handshake.toJson())
    }

    private fun sendLanPayload(json: String) {
        scope.launch { sendLanPayloadInternal(json) }
    }

    private suspend fun sendLanPayloadInternal(json: String) {
        val addresses = localBroadcastAddresses()
        if (addresses.isEmpty()) return
        val bytes = json.toByteArray(Charsets.UTF_8)
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                addresses.forEach { address ->
                    val packet = DatagramPacket(bytes, bytes.size, address, LAN_DISCOVERY_PORT)
                    socket.send(packet)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "LAN broadcast skipped (${e.message})")
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
        if (dhcp.ipAddress == 0) return emptySet() // Not connected to any WiFi — skip broadcast
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

    // ========== MESH MESSAGE LISTENER ==========

    override fun onMessageReceived(protocol: MeshProtocol, fromAddress: String) {
        if (protocol.type != "HANDSHAKE" && socketManager.isDuplicate(protocol.id, protocol.timestamp)) {
            Log.d(TAG, "Anti-replay: dropped ${protocol.type} id=${protocol.id}")
            return
        }
        scope.launch {
            when (protocol.type) {
                "HANDSHAKE" -> handleHandshake(protocol, fromAddress)
                "TEXT"      -> handleTextMessage(protocol)
                "SOS"       -> handleSosMessage(protocol)
                "FLAG"      -> handleFlagMessage(protocol)
                else -> Log.w(TAG, "Unknown type: ${protocol.type}")
            }
        }
    }

    override fun onPeerConnected(address: String, isServer: Boolean) {
        Log.d(TAG, "Socket connected: $address (server=$isServer)")
        _connectionState.value = "CONNECTED"
        scope.launch { sendHandshake() }
    }

    override fun onPeerDisconnected(address: String) {
        Log.d(TAG, "Socket disconnected: $address")
        _connectionState.value = "DISCONNECTED"
    }

    // ========== MESSAGE HANDLERS ==========

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

    private suspend fun handleHandshake(protocol: MeshProtocol, fromAddress: String) {
        Log.d(TAG, "HANDSHAKE from ${protocol.senderName} (${protocol.senderId})")
        val wifiDirectMac = pendingWifiDirectMac
            ?: _availablePeers.value.firstOrNull()?.deviceAddress
            ?: fromAddress
        pendingWifiDirectMac = null

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
        Log.d(TAG, "Peer saved: ${peer.displayName}")
    }

    private suspend fun handleTextMessage(protocol: MeshProtocol) {
        val myId = identityManager.getPeerId()
        val isForMe = protocol.recipientId == myId || protocol.recipientId == "BROADCAST"

        if (!isForMe && protocol.ttl > 1) {
            sendMessage(protocol.copy(ttl = protocol.ttl - 1).toJson())
            _relayedMessageCount.value++
            return
        }
        if (!isForMe) return

        var plaintext = protocol.content
        if (protocol.encryptedPayload != null) {
            val senderPeer = repository.getPeerById(protocol.senderId)
            if (senderPeer != null) {
                val senderPubKey = cryptoManager.keyFromBase64(senderPeer.publicKey)
                val mySecretKey = identityManager.getEncryptionKeyPair().secretKey
                val decrypted = cryptoManager.decryptMessage(protocol.encryptedPayload, senderPubKey, mySecretKey)
                if (decrypted != null) plaintext = decrypted
            }
        }

        repository.saveMessage(
            MessageEntity(
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
        )
        showMessageNotification(protocol.senderName, plaintext, protocol.senderId)
        _incomingChatAlert.tryEmit(
            ChatAlert(
                senderId = protocol.senderId,
                senderName = protocol.senderName,
                senderRole = protocol.senderRole,
                content = plaintext
            )
        )
    }

    private suspend fun handleSosMessage(protocol: MeshProtocol) {
        Log.d(TAG, "SOS from ${protocol.senderName}: ${protocol.content}")
        if (protocol.ttl > 1) {
            sendMessage(protocol.copy(ttl = protocol.ttl - 1).toJson())
            _relayedMessageCount.value++
        }
        repository.saveMessage(
            MessageEntity(
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
        )
        showSosNotification(protocol.senderName, protocol.content)
    }

    private suspend fun handleFlagMessage(protocol: MeshProtocol) {
        val targetId = protocol.content
        if (targetId.isNotBlank()) repository.flagMessageById(targetId)
        if (protocol.ttl > 1) sendMessage(protocol.copy(ttl = protocol.ttl - 1).toJson())
    }

    private fun showSosNotification(sender: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS missing — notification skipped")
            return
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sos_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Emergency SOS Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Critical emergency messages from the mesh network"
                }
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        try {
            notificationManager.notify(
                System.currentTimeMillis().toInt(),
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("EMERGENCY SOS: $sender")
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "SOS notification failed", e)
        }
    }

    private fun showMessageNotification(sender: String, message: String, senderId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "chat_messages"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Mesh Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Incoming chat messages from mesh peers"
                }
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            context, senderId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        try {
            notificationManager.notify(
                senderId.hashCode(),
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle(sender)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Message notification failed", e)
        }
    }
}
