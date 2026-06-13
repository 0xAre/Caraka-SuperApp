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
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
) : MeshMessageListener, MeshTransport {

    companion object {
        private const val TAG = "WifiDirect"
        private const val LAN_DISCOVERY_PORT = 8890
        private const val LAN_DISCOVERY_INTERVAL_MS = 3_000L
        private const val DISCOVERY_INTERVAL_MS = 6_000L
        private const val AUTO_CONNECT_COOLDOWN_MS = 20_000L

        // WiFi P2P DNS-SD Service Discovery constants
        // Only devices advertising "_caraka._tcp" will be discovered by CARAKA peers.
        // Smart TVs, speakers, and other non-CARAKA devices are invisible to us.
        private const val CARAKA_SERVICE_NAME = "caraka-mesh"
        private const val CARAKA_SERVICE_TYPE = "_caraka._tcp"
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
    private var autoConnectLoopJob: Job? = null  // NEW: periodic connection requests to all peers
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
    override val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled

    private val _availablePeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    override val availablePeers: StateFlow<List<WifiP2pDevice>> = _availablePeers

    private val _connectionState = MutableStateFlow("IDLE")
    override val connectionState: StateFlow<String> = _connectionState

    private val socketManager = MeshSocketManager(this)
    private var pendingWifiDirectMac: String? = null

    private val _relayedMessageCount = MutableStateFlow(0)
    override val relayedMessageCount: StateFlow<Int> = _relayedMessageCount

    // When used standalone (no Wi-Fi Aware hardware) this stays false. MeshManager flips its own
    // copy; WifiDirectManager itself never drives an Aware data path.
    private val _isAwareActive = MutableStateFlow(false)
    override val isAwareActive: StateFlow<Boolean> = _isAwareActive

    /**
     * Outbound seams for layered overlay transports (Wi-Fi Aware, Nearby Connections, …). [MeshManager]
     * registers a sink per active overlay so EVERY send — including internal relays, handshakes and
     * gossip that call [sendMessage]/[sendToPeer] directly — also fans out across every overlay. Empty
     * in the plain-fallback case, making them zero-cost.
     */
    private val overlayBroadcastSinks = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()
    private val overlayUnicastSinks = java.util.concurrent.CopyOnWriteArrayList<(String, String) -> Unit>()

    fun addOverlayBroadcastSink(sink: (String) -> Unit) { overlayBroadcastSinks.add(sink) }
    fun addOverlayUnicastSink(sink: (String, String) -> Unit) { overlayUnicastSinks.add(sink) }

    // Emits when a direct chat message arrives — used to show an in-app floating alert.
    private val _incomingChatAlert = MutableSharedFlow<ChatAlert>(extraBufferCapacity = 8)
    override val incomingChatAlert: SharedFlow<ChatAlert> = _incomingChatAlert

    // Auto-connect cooldown tracker
    @Volatile private var lastConnectAttemptMs = 0L

    /** IP addresses of LAN peers verified via UDP HANDSHAKE. */
    private val carakaLanPeerHosts = mutableSetOf<String>()

    /**
     * LAN mesh backbone registry: peerId → last-known IP address.
     * This is the key to true full mesh — when all devices share a WiFi, any device can
     * unicast directly to any other peer's IP (true B↔C), no Group-Owner relay needed.
     */
    private val peerIpRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** peerId → last time we received any LAN packet from them (for liveness pruning). */
    private val peerLastLanSeen = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** peerId → last time we flushed the outbox to them on appearance (debounce, EU-2.2). */
    private val lastPeerFlushAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // EU-3.1 / D14: two-state duty cycle. Beacon/gossip/discovery run tight when there is recent
    // mesh activity and widen when idle, to save battery during long disaster operation. Any
    // inbound/outbound traffic refreshes [lastActivityAt]; intervals scale via [dutyInterval].
    @Volatile private var lastActivityAt = System.currentTimeMillis()
    private fun touchActivity() { lastActivityAt = System.currentTimeMillis() }
    private fun isMeshIdle(): Boolean =
        System.currentTimeMillis() - lastActivityAt > MeshPolicy.IDLE_THRESHOLD_MS
    private fun dutyInterval(baseMs: Long): Long =
        if (isMeshIdle()) baseMs * MeshPolicy.IDLE_INTERVAL_MULTIPLIER else baseMs

    // EU-3.2 / D14: deep idle suspends only ACTIVE Wi-Fi Direct discovery (the costly part). The
    // passive LAN listener is never stopped, so the node stays reachable and inbound traffic
    // (→ touchActivity) returns it to ACTIVE. Full Wi-Fi power-down awaits the BLE channel (D15).
    private fun isMeshDeepIdle(): Boolean =
        System.currentTimeMillis() - lastActivityAt > MeshPolicy.DEEP_IDLE_THRESHOLD_MS

    /** How long a LAN peer stays "connected" without a fresh beacon before being pruned. */
    private val LAN_PEER_TIMEOUT_MS = 20_000L

    private var peerListGossipJob: Job? = null
    private var livenessPruneJob: Job? = null

    /** WiFi Direct MACs of peers verified after a successful CARAKA HANDSHAKE. */
    private val carakaHandshakedMacs = mutableSetOf<String>()

    /**
     * WiFi Direct MACs of peers discovered via DNS-SD (Bonjour) service advertisement.
     * Only devices actively advertising "_caraka._tcp" are added here.
     * Used in onPeersAvailable() as Tier 1 (highest priority) for auto-connect.
     */
    private val carakaServicePeerMacs = mutableSetOf<String>()

    private var carakaValidationJob: Job? = null
    @Volatile private var carakaHandshakeValidated = false

    // Cached authority flag — read once from IdentityManager on first connect
    @Volatile private var cachedIsAuthority = false

    /** Tracks whether the CARAKA DNS-SD local service has been registered. */
    @Volatile private var serviceDiscoveryRegistered = false

    /**
     * PeerId of a QR-scanned peer we want to connect to ASAP.
     * Cleared once the peer's HANDSHAKE is received.
     * Resets auto-connect cooldown so next discovery cycle attempts immediately.
     */
    @Volatile private var priorityPeerId: String? = null

    override fun setPriorityPeerId(peerId: String) {
        priorityPeerId = peerId
        lastConnectAttemptMs = 0L
        Log.d(TAG, "Priority peer set: $peerId — cooldown reset, connecting on next scan")
        discoverPeers()  // trigger immediate discovery
    }

    /** Current battery level (0-100). Updated on each connect attempt. */
    private val _batteryLevel = MutableStateFlow(100)
    override val batteryLevel: StateFlow<Int> = _batteryLevel

    // ========== LIFECYCLE ==========

    override fun startListening() {
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

        // NOTE: No WiFi-Direct auto-connect loop. The LAN backbone (startLanDiscovery)
        // auto-establishes the full mesh: every device broadcasts a beacon, hears every
        // other beacon, and is marked ACTIVE_MESH. This avoids BUG-01 (inviting TVs) and
        // the WiFi-Direct star-topology limitation (BUG-03).
        // Remove any stale P2P group from a previous session before starting fresh
        clearGroupThenDiscover()
        startPeriodicDiscovery()
        startLanDiscovery()
    }

    /**
     * Register CARAKA as a WiFi P2P DNS-SD (Bonjour) service.
     * After this, other CARAKA devices running discoverServices() will find us.
     * Non-CARAKA devices (TV, speakers, laptops) are completely invisible.
     */
    @SuppressLint("MissingPermission")
    private fun registerCarakaService() {
        if (serviceDiscoveryRegistered) return
        val record = mapOf(
            "v" to "1",
            "app" to "caraka"
        )
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            CARAKA_SERVICE_NAME, CARAKA_SERVICE_TYPE, record
        )
        manager.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                serviceDiscoveryRegistered = true
                Log.d(TAG, "CARAKA DNS-SD service registered — visible to other CARAKA peers")
                setupServiceDiscoveryListeners()
                startServiceDiscovery()
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "addLocalService failed ($reason) — falling back to peer discovery only")
                // Fallback: use regular peer discovery with post-connect validation
                discoverPeers()
            }
        })
    }

    /**
     * Set up listeners for DNS-SD service responses.
     * When a CARAKA peer is found via DNS-SD, their MAC is stored in carakaServicePeerMacs
     * and auto-connect is attempted immediately.
     */
    private fun setupServiceDiscoveryListeners() {
        // TXT record listener — fires when a CARAKA device's metadata arrives
        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomainName, record, device ->
            if (fullDomainName.contains(CARAKA_SERVICE_NAME, ignoreCase = true)) {
                Log.d(TAG, "CARAKA DNS-SD TXT: ${device.deviceName} (${device.deviceAddress}) record=$record")
                carakaServicePeerMacs.add(device.deviceAddress)
            }
        }

        // Service response listener — fires when a matching service is found
        val serviceListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, registrationType, device ->
            if (registrationType.contains(CARAKA_SERVICE_TYPE, ignoreCase = true)
                || instanceName.contains(CARAKA_SERVICE_NAME, ignoreCase = true)) {
                Log.d(TAG, "CARAKA peer found via DNS-SD: ${device.deviceName} (${device.deviceAddress})")
                carakaServicePeerMacs.add(device.deviceAddress)
                // NEW: Just mark as discovered, don't auto-connect
                // User will click [CONNECT] button from UI
                Log.d(TAG, "DNS-SD CARAKA peer discovered (manual connect via UI): ${device.deviceName}")
            }
        }

        manager.setDnsSdResponseListeners(channel, serviceListener, txtListener)
        Log.d(TAG, "DNS-SD response listeners configured")
    }

    /** Backoff attempt counter for discoverServices BUSY recovery (BUG-04). */
    @Volatile private var serviceDiscoveryAttempt = 0
    private val MAX_SERVICE_DISCOVERY_BACKOFF_MS = 30_000L

    /**
     * Start WiFi P2P service discovery.
     * This discovers ONLY devices advertising the CARAKA DNS-SD service type.
     * Runs alongside regular discoverPeers() for robustness.
     *
     * BUG-04: On OEMs like XOS/MIUI/HiOS, discoverServices() frequently returns BUSY in a tight
     * loop with no recovery. We add exponential backoff (capped) so the radio gets time to settle
     * instead of hammering it. The LAN backbone keeps the mesh working regardless.
     */
    @SuppressLint("MissingPermission")
    private fun startServiceDiscovery() {
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance(
            CARAKA_SERVICE_NAME, CARAKA_SERVICE_TYPE
        )
        manager.addServiceRequest(channel, serviceRequest, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager.discoverServices(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d(TAG, "DNS-SD service discovery started — scanning for CARAKA peers")
                        serviceDiscoveryAttempt = 0 // reset backoff on success
                    }
                    override fun onFailure(reason: Int) {
                        if (reason == WifiP2pManager.BUSY) {
                            val backoff = minOf(
                                1_000L * (1L shl serviceDiscoveryAttempt.coerceAtMost(5)),
                                MAX_SERVICE_DISCOVERY_BACKOFF_MS
                            )
                            serviceDiscoveryAttempt++
                            Log.w(TAG, "discoverServices BUSY — backing off ${backoff}ms (attempt $serviceDiscoveryAttempt)")
                            scope.launch { delay(backoff); startServiceDiscovery() }
                        } else {
                            Log.w(TAG, "discoverServices failed ($reason) — using peer discovery fallback")
                            discoverPeers()
                        }
                    }
                })
            }
            override fun onFailure(reason: Int) {
                if (reason == WifiP2pManager.BUSY) {
                    val backoff = minOf(
                        1_000L * (1L shl serviceDiscoveryAttempt.coerceAtMost(5)),
                        MAX_SERVICE_DISCOVERY_BACKOFF_MS
                    )
                    serviceDiscoveryAttempt++
                    Log.w(TAG, "addServiceRequest BUSY — backing off ${backoff}ms (attempt $serviceDiscoveryAttempt)")
                    scope.launch { delay(backoff); startServiceDiscovery() }
                } else {
                    Log.w(TAG, "addServiceRequest failed ($reason) — using peer discovery fallback")
                    discoverPeers()
                }
            }
        })
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

    override fun stopListening() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
        scanJob?.cancel(); scanJob = null
        lanBroadcastJob?.cancel(); lanBroadcastJob = null
        lanListenJob?.cancel(); lanListenJob = null
        autoConnectLoopJob?.cancel(); autoConnectLoopJob = null
        peerListGossipJob?.cancel(); peerListGossipJob = null
        livenessPruneJob?.cancel(); livenessPruneJob = null
        lanSocket?.close(); lanSocket = null
        releaseMulticastLock()
        socketManager.stopAll()
    }

    override fun startFallbackDiscovery() {
        startLanDiscovery()
    }

    // NEW: Auto-connect loop for fully connected mesh
    private fun startAutoConnectLoop() {
        autoConnectLoopJob?.cancel()
        autoConnectLoopJob = scope.launch {
            try {
                while (true) {
                    delay(10_000L) // Check every 10 seconds
                    autoConnectToPeers()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Auto-connect loop cancelled")
            }
        }
        Log.d(TAG, "Auto-connect loop started")
    }

    /**
     * Send CONNECTION_REQUEST to all DISCOVERED peers automatically.
     * This enables fully connected mesh where every device tries to connect to all others.
     */
    private suspend fun autoConnectToPeers() {
        try {
            // Get all discovered peers from database
            val allPeers = repository.getPeersByStatus(com.example.caraka.data.local.entity.ConnectionStatus.DISCOVERED.name)
                .first()  // Get current value from Flow

            val now = System.currentTimeMillis()
            val cooldownMs = 120_000L  // 2 minute cooldown between retries

            allPeers.forEach { peer ->
                // Skip if recently attempted
                if (now - peer.lastAttempt < cooldownMs) {
                    return@forEach
                }

                // Skip if already rejected multiple times (user said no)
                if (peer.rejectionCount >= 3) {
                    Log.d(TAG, "Skipping ${peer.displayName} — rejected 3+ times")
                    return@forEach
                }

                Log.d(TAG, "Auto-requesting connection to ${peer.displayName} (${peer.id})")
                requestConnectionToPeer(peer.id, autoAccept = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in autoConnectToPeers", e)
        }
    }

    // ========== WIFI DIRECT ACTIONS ==========

    /**
     * Stop any ongoing discovery, then start fresh.
     * Calling discoverPeers() while a scan is active returns BUSY.
     * stopPeerDiscovery → discoverPeers avoids that.
     */
    private val busyStates = setOf("CONNECTING", "CONNECTED", "CONNECTED_GO", "CONNECTED_CLIENT")

    // Serialize discovery: only one stop→discover cycle in flight, and only one BUSY retry pending.
    @Volatile private var discoveryInFlight = false
    @Volatile private var retryScheduled = false

    @SuppressLint("MissingPermission")
    override fun discoverPeers() {
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

    /** Last device name requested — retried when WiFi P2P becomes enabled. */
    @Volatile private var pendingDeviceName: String? = null

    override fun updateDeviceName(name: String) {
        val carakaName = if (name.startsWith("CRK:", ignoreCase = true)) name else "CRK:$name"
        pendingDeviceName = carakaName
        applyDeviceName(carakaName)
    }

    private fun applyDeviceName(carakaName: String) {
        try {
            val method = manager.javaClass.getMethod(
                "setDeviceName",
                WifiP2pManager.Channel::class.java,
                String::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            method.invoke(manager, channel, carakaName, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { Log.d(TAG, "Device name set to $carakaName") }
                override fun onFailure(reason: Int) { Log.w(TAG, "setDeviceName failed: $reason") }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Reflection setDeviceName failed", e)
        }
    }

    private fun WifiP2pDevice.isCarakaDevice(): Boolean {
        return deviceName.startsWith("CRK:", ignoreCase = true)
            || deviceName.startsWith("CARAKA", ignoreCase = true)
    }

    @Volatile private var connectInFlight = false

    @SuppressLint("MissingPermission")
    override fun connectToPeer(device: WifiP2pDevice) {
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

    /**
     * Initiate a connection to a peer — used by QR fast-connect and any explicit [CONNECT] action.
     *
     * If the peer is already reachable on the LAN (we have its IP), the mesh link is effectively
     * already up: we mark it ACTIVE_MESH immediately and send a directed hello so the other side
     * does the same — instant connect, no waiting on discovery (fixes BUG-02).
     *
     * If the peer is NOT yet on the LAN, we broadcast a CONNECTION_REQUEST; the peer answers as
     * soon as its beacon/registry entry appears.
     */
    override fun requestConnectionToPeer(peerId: String, autoAccept: Boolean) {
        scope.launch {
            val peer = repository.getPeerById(peerId) ?: run {
                Log.w(TAG, "Peer not found: $peerId")
                return@launch
            }

            val myId = identityManager.getPeerId()
            val myName = identityManager.getDisplayName()
            val myRole = identityManager.getRole()

            val knownIp = peerIpRegistry[peerId]
            val request = MeshProtocol(
                type = "CONNECTION_REQUEST",
                id = java.util.UUID.randomUUID().toString(),
                senderId = myId,
                senderName = myName,
                senderRole = myRole,
                recipientId = peerId,
                content = "Request to connect",
                timestamp = System.currentTimeMillis(),
                autoAccept = autoAccept
            )

            if (knownIp != null) {
                // LAN path known → instant connect.
                Log.d(TAG, "Peer $peerId reachable on LAN ($knownIp) — instant connect")
                repository.updatePeerConnectionState(peerId, com.example.caraka.data.local.entity.ConnectionStatus.ACTIVE_MESH)
                sendToPeer(peerId, com.google.gson.Gson().toJson(request))
            } else {
                // No LAN path. Broadcast the request (in case they surface on the LAN)...
                repository.updatePeerConnectionState(peerId, com.example.caraka.data.local.entity.ConnectionStatus.PENDING_REQUEST)
                Log.d(TAG, "No LAN path for $peerId — broadcasting CONNECTION_REQUEST (autoAccept=$autoAccept)")
                sendMessage(com.google.gson.Gson().toJson(request))

                // ...and if we know their WiFi-Direct MAC, kick off a P2P connection now (offline path).
                val mac = peer.macAddress?.takeIf { it.isNotBlank() && !it.startsWith("lan:") }
                if (mac != null) {
                    connectToWifiDeviceByMac(mac)
                }
            }
        }
    }

    /**
     * Manually initiate a WiFi-Direct P2P connection to a specific device MAC, if it is currently
     * in the discovered list. Used by the network map's "Hubungkan" button and the offline
     * fallback in requestConnectionToPeer.
     */
    override fun connectToWifiDeviceByMac(mac: String) {
        val device = _availablePeers.value.firstOrNull { it.deviceAddress == mac }
        if (device != null) {
            Log.d(TAG, "Manual WiFi-Direct connect to ${device.deviceName} ($mac)")
            connectToPeer(device)
        } else {
            Log.w(TAG, "WiFi-Direct device $mac not in discovered list — triggering rediscovery")
            discoverPeers()
        }
    }

    /**
     * Send a mesh message (broadcast).
     * LAN UDP is tried first (more reliable, no star-topology limit, works across all Android
     * versions). WiFi Direct socket is also used so that offline (no-WiFi) scenarios work.
     */
    override fun sendMessage(json: String) {
        // Mark outgoing messages in the anti-replay cache so this device never
        // processes its own relayed copies (BUG-P2: duplicate notifications/DB entries).
        MeshProtocol.fromJson(json)?.let { p ->
            if (p.type !in listOf("HANDSHAKE", "CONNECTION_REQUEST", "CONNECTION_ACCEPT", "CONNECTION_REJECT")) {
                socketManager.markSent(p.id)
            }
        }
        sendLanPayload(json)       // LAN first — reliable, any-to-any
        socketManager.sendPayload(json) // WiFi Direct socket — offline fallback
        overlayBroadcastSinks.forEach { it(json) } // Aware / Nearby overlays (when active)
    }

    /**
     * Send a message directed at a specific peer. Uses LAN unicast straight to the peer's
     * known IP (true B↔C delivery, no Group-Owner relay), falling back to broadcast and the
     * WiFi-Direct socket when the IP isn't known.
     */
    override fun sendToPeer(peerId: String, json: String) {
        sendDirectedMessage(peerId, json)
        overlayUnicastSinks.forEach { it(peerId, json) } // route via Aware next-hop / Nearby when active
    }

    // ========== CALLBACKS FROM BROADCAST RECEIVER ==========

    internal fun onWifiStateChanged(isEnabled: Boolean) {
        _isWifiP2pEnabled.value = isEnabled
        Log.d(TAG, "WiFi P2P enabled: $isEnabled")
        if (isEnabled) {
            pendingDeviceName?.let { name ->
                scope.launch {
                    delay(600L)
                    applyDeviceName(name)
                    Log.d(TAG, "Device name retry after P2P enable: $name")
                }
            }
            // registerCarakaService() internally starts discoverServices() which
            // subsumes discoverPeers(). Calling both causes BUSY storm.
            // Only call discoverPeers() directly if service registration fails (handled in fallback).
            scope.launch {
                delay(800L)
                registerCarakaService()
            }
            requestCurrentPeers()
            // Delay discoverPeers so it doesn't conflict with discoverServices()
            scope.launch { delay(1_200L); if (!serviceDiscoveryRegistered) discoverPeers() }
        } else {
            _availablePeers.value = emptyList()
            _connectionState.value = "WIFI_P2P_DISABLED"
            serviceDiscoveryRegistered = false
        }
    }

    @Volatile private var lastNonEmptyPeersMs = 0L

    internal fun onPeersAvailable(peers: List<WifiP2pDevice>) {
        // Smooth out transient empty reports. WiFi Direct on many OEMs flips between a full and an
        // empty peer list every scan, which makes nodes blink in and out on the map. If we just
        // got peers moments ago, ignore a sudden empty report rather than clearing the UI.
        val now = System.currentTimeMillis()
        if (peers.isEmpty()) {
            if (now - lastNonEmptyPeersMs < 12_000L) {
                Log.d(TAG, "Ignoring transient empty peer list (had peers ${now - lastNonEmptyPeersMs}ms ago)")
                return
            }
            _availablePeers.value = emptyList()
        } else {
            lastNonEmptyPeersMs = now
            _availablePeers.value = peers
        }
        val prevState = _connectionState.value

        // Never overwrite a forming/active connection — doing so would resume discovery
        // and break P2P negotiation with BUSY churn.
        if (prevState !in busyStates) {
            _connectionState.value = if (peers.isEmpty()) "NO_PEERS" else "PEERS_FOUND"
        }

        Log.d(TAG, "Peers available: ${peers.size} — ${peers.map { it.deviceName }}")

        // Skip auto-connect if this device is already a Group Owner.
        // As GO, clients will connect TO us — we don't need to initiate.
        if (_connectionState.value == "CONNECTED_GO") {
            Log.d(TAG, "Already GO — skipping auto-connect, waiting for clients")
            return
        }

        // Auto-connect ONLY to verified CARAKA peers. This is the connectivity path that works
        // with no shared router (forms a WiFi Direct group → star). The LAN backbone then layers
        // full mesh on top when a router is present.
        //
        // BUG-01 fix: we deliberately DO NOT have a Tier-4 "any nearby device" fallback. Every
        // tier below is CARAKA-only, so TVs / speakers / laptops are never invited.
        //   Tier 1: DNS-SD verified  — they advertise our "_caraka._tcp" service
        //   Tier 2: HANDSHAKE-verified MAC — proven CARAKA in a prior exchange
        //   Tier 3: "CRK:" name prefix — CARAKA device whose name we can read
        if (peers.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastConnectAttemptMs > AUTO_CONNECT_COOLDOWN_MS) {
                val dnsVerified = peers.firstOrNull {
                    it.deviceAddress in carakaServicePeerMacs && it.status != WifiP2pDevice.CONNECTED
                }
                val handshakeVerified = peers.firstOrNull {
                    it.deviceAddress in carakaHandshakedMacs && it.status != WifiP2pDevice.CONNECTED
                }
                val carakaByName = peers.firstOrNull {
                    it.isCarakaDevice() && it.status == WifiP2pDevice.AVAILABLE
                } ?: peers.firstOrNull {
                    it.isCarakaDevice() && it.status != WifiP2pDevice.CONNECTED
                }

                val candidate = dnsVerified ?: handshakeVerified ?: carakaByName
                if (candidate != null) {
                    lastConnectAttemptMs = now
                    val tier = when (candidate) {
                        dnsVerified -> "DNS-SD"
                        handshakeVerified -> "HANDSHAKE-verified"
                        else -> "CRK:-name"
                    }
                    Log.d(TAG, "Auto-connecting to CARAKA peer ${candidate.deviceName} [$tier] (${candidate.deviceAddress})")
                    connectToPeer(candidate)
                } else {
                    Log.d(TAG, "No CARAKA peers to auto-connect (${peers.size} nearby, none verified) — ignoring non-CARAKA devices")
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
            startCarakaValidation()
        } else if (info.groupFormed) {
            Log.d(TAG, "I am Client — connecting to GO: $groupOwnerAddress")
            _connectionState.value = "CONNECTED_CLIENT"
            if (groupOwnerAddress != null) {
                socketManager.startClient(groupOwnerAddress)
            }
            startCarakaValidation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCarakaValidation() {
        carakaValidationJob?.cancel()
        carakaHandshakeValidated = false
        carakaValidationJob = scope.launch {
            delay(10_000L)
            if (!carakaHandshakeValidated) {
                Log.w(TAG, "No CARAKA HANDSHAKE received in 10s — disconnecting non-CARAKA device")
                try {
                    manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            Log.d(TAG, "Removed non-CARAKA group after validation timeout")
                            clearGroupThenDiscover()
                        }
                        override fun onFailure(reason: Int) {
                            Log.w(TAG, "removeGroup after validation timeout failed ($reason)")
                            clearGroupThenDiscover()
                        }
                    })
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permission missing during non-CARAKA disconnect", e)
                    clearGroupThenDiscover()
                }
            }
        }
    }

    internal fun onDisconnected() {
        Log.d(TAG, "P2P disconnected")
        carakaValidationJob?.cancel()
        carakaHandshakeValidated = false
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
                delay(dutyInterval(DISCOVERY_INTERVAL_MS)) // EU-3.1: widen when idle
                // EU-3.2: during deep idle, suspend active Wi-Fi Direct discovery to save battery.
                // The loop keeps spinning cheaply (so it resumes instantly once activity returns via
                // touchActivity), and the passive LAN listener stays up so we remain reachable.
                if (isMeshDeepIdle()) continue
                // Keep WiFi-Direct discovery alive unless we are in an ACTUAL P2P connection.
                // (MESH_ACTIVE / PEERS_FOUND / DISCOVERING etc. must NOT block rediscovery.)
                val state = _connectionState.value
                if (state !in setOf("CONNECTED_GO", "CONNECTED_CLIENT", "CONNECTING")) {
                    // Self-heal: clear a possibly-stuck in-flight flag so discovery can never
                    // get permanently wedged (the root cause of "mDiscoveryStarted=false").
                    discoveryInFlight = false
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
        startPeerListGossip()
        startLivenessPruning()
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

                    // Update the LAN registry on EVERY packet — this is what makes
                    // direct unicast (true full mesh) possible.
                    peerIpRegistry[protocol.senderId] = hostAddress
                    peerLastLanSeen[protocol.senderId] = System.currentTimeMillis()

                    when {
                        protocol.type == "HANDSHAKE" && protocol.content == "LAN_DISCOVERY" ->
                            saveLanPeer(protocol, hostAddress)
                        protocol.type == "PEER_LIST" ->
                            handlePeerList(protocol)
                        else ->
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
                delay(dutyInterval(LAN_DISCOVERY_INTERVAL_MS)) // EU-3.1: widen when idle
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
        carakaLanPeerHosts.add(hostAddress)

        // A peer we can hear on the LAN is, by definition, reachable — so we mark it
        // ACTIVE_MESH directly (design decision: direct connect, no accept/reject dialog).
        // Preserve any existing verification flag (e.g. from a prior QR scan).
        val existing = repository.getPeerById(protocol.senderId)
        val peer = (existing ?: PeerEntity(
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
            status = com.example.caraka.data.local.entity.ConnectionStatus.ACTIVE_MESH,
            hopCount = 0
        )).copy(
            displayName = protocol.senderName,
            role = protocol.senderRole,
            publicKey = protocol.publicKey ?: existing?.publicKey ?: "",
            signingKey = protocol.signingKey ?: existing?.signingKey ?: "",
            macAddress = "lan:$hostAddress",
            lastSeen = System.currentTimeMillis(),
            status = com.example.caraka.data.local.entity.ConnectionStatus.ACTIVE_MESH,
            hopCount = 0
        )
        repository.savePeer(peer)
        // Use a distinct, NON-busy state so WiFi-Direct peer discovery keeps running. Reusing
        // "CONNECTED" here previously put us into busyStates and silently killed discovery.
        if (_connectionState.value !in busyStates) _connectionState.value = "MESH_ACTIVE"
        Log.d(TAG, "LAN peer ACTIVE_MESH: ${peer.displayName} at $hostAddress (verified=${peer.isVerified})")
    }

    // ========== PEER_LIST GOSSIP (full-mesh awareness) ==========

    /**
     * Merge a received PEER_LIST into our local DB so we learn about peers we have never
     * directly heard from — e.g. client B learning about client C via Group Owner A,
     * or any device backfilling peers that joined before it did.
     */
    private suspend fun handlePeerList(protocol: MeshProtocol) {
        val myId = identityManager.getPeerId()
        val shared = protocol.peers ?: return
        shared.forEach { share ->
            if (share.peerId.isBlank() || share.peerId == myId) return@forEach
            share.ip?.takeIf { it.isNotBlank() }?.let { ip ->
                // Only learn an IP if we don't already have a fresher one of our own.
                peerIpRegistry.putIfAbsent(share.peerId, ip)
            }
            val existing = repository.getPeerById(share.peerId)
            // Don't downgrade a peer we can already hear directly on the LAN.
            val directlyLive = (System.currentTimeMillis() - (peerLastLanSeen[share.peerId] ?: 0L)) < LAN_PEER_TIMEOUT_MS
            val status = if (directlyLive) com.example.caraka.data.local.entity.ConnectionStatus.ACTIVE_MESH
                         else com.example.caraka.data.local.entity.ConnectionStatus.DISCOVERED
            // T5: TOFU — jangan timpa key yang sudah dikenal dengan key dari gossip.
            // Key dari PEER_LIST bisa dipalsukan; hanya HANDSHAKE langsung yang lebih tepercaya.
            val safeEncPub = when {
                share.encPub.isBlank() -> existing?.publicKey ?: ""
                existing?.publicKey?.isNotBlank() == true && share.encPub != existing.publicKey -> {
                    Log.w(TAG, "TOFU: encPub berbeda untuk ${share.peerId} via gossip — abaikan")
                    existing.publicKey
                }
                else -> share.encPub
            }
            val safeSignPub = when {
                share.signPub.isBlank() -> existing?.signingKey ?: ""
                existing?.signingKey?.isNotBlank() == true && share.signPub != existing.signingKey -> {
                    Log.w(TAG, "TOFU: signPub berbeda untuk ${share.peerId} via gossip — abaikan")
                    existing.signingKey
                }
                else -> share.signPub
            }

            val peer = (existing ?: PeerEntity(
                id = share.peerId,
                deviceName = share.name,
                displayName = share.name,
                role = share.role,
                publicKey = safeEncPub,
                signingKey = safeSignPub,
                isVerified = false,
                isAuthority = share.role in listOf("BPBD", "POLRI", "PMI"),
                macAddress = share.ip?.let { "lan:$it" },
                lastSeen = System.currentTimeMillis(),
                status = status,
                hopCount = 1
            )).copy(
                displayName = share.name,
                role = share.role,
                publicKey = safeEncPub,
                signingKey = safeSignPub,
                lastSeen = System.currentTimeMillis(),
                status = status
            )
            repository.savePeer(peer)
        }
    }

    /** Periodically broadcast everything we know so the whole mesh converges on a shared view. */
    private fun startPeerListGossip() {
        peerListGossipJob?.cancel()
        peerListGossipJob = scope.launch {
            try {
                while (true) {
                    delay(dutyInterval(5_000L)) // EU-3.1: widen when idle
                    broadcastPeerList()
                }
            } catch (_: Exception) { Log.d(TAG, "Peer-list gossip stopped") }
        }
    }

    private suspend fun broadcastPeerList() {
        val myId = identityManager.getPeerId()
        if (myId.isBlank()) return
        val known = repository.getAllPeers().first()
        if (known.isEmpty()) return
        val now = System.currentTimeMillis()
        // Only advertise peers we are CURRENTLY, directly connected to (live within the timeout).
        // This stops stale/phantom peers from a previous session propagating around the mesh and
        // inflating everyone's node count.
        val shares = known.mapNotNull { p ->
            val live = (now - (peerLastLanSeen[p.id] ?: 0L)) < LAN_PEER_TIMEOUT_MS
            if (p.id == myId || p.publicKey.isBlank() || !live) null
            else PeerShare(
                peerId = p.id,
                name = p.displayName,
                role = p.role,
                encPub = p.publicKey,
                signPub = p.signingKey,
                ip = peerIpRegistry[p.id] ?: p.macAddress?.removePrefix("lan:")
            )
        }
        if (shares.isEmpty()) return
        val gossip = MeshProtocol(
            type = "PEER_LIST",
            id = UUID.randomUUID().toString(),
            senderId = myId,
            senderName = identityManager.getDisplayName(),
            senderRole = identityManager.getRole(),
            recipientId = "BROADCAST",
            content = "PEER_LIST",
            timestamp = System.currentTimeMillis(),
            peers = shares
        )
        val json = gossip.toJson()
        sendLanPayload(json)            // reaches everyone on the same WiFi
        socketManager.sendPayload(json) // and rides the WiFi-Direct relay when offline
    }

    // ========== LIVENESS PRUNING ==========

    /**
     * Downgrade LAN peers we haven't heard a beacon from within the timeout window.
     *
     * IMPORTANT: only peers established over the LAN (present in peerIpRegistry) are eligible.
     * WiFi-Direct peers don't emit a 3s LAN beacon, so they are left to the WiFi-Direct
     * disconnect callback (onPeerDisconnected) — pruning them here would wrongly drop a live
     * P2P link and cause the "peer appears then vanishes" flicker.
     */
    private fun startLivenessPruning() {
        livenessPruneJob?.cancel()
        livenessPruneJob = scope.launch {
            try {
                while (true) {
                    delay(8_000L)
                    val now = System.currentTimeMillis()
                    val active = repository.getActiveMeshPeers().first()
                    active.forEach { p ->
                        // Only LAN-registry peers are subject to beacon-based pruning.
                        if (!peerIpRegistry.containsKey(p.id)) return@forEach
                        val lastSeen = peerLastLanSeen[p.id] ?: 0L
                        if (now - lastSeen > LAN_PEER_TIMEOUT_MS) {
                            peerIpRegistry.remove(p.id)
                            repository.updatePeerConnectionState(
                                p.id,
                                com.example.caraka.data.local.entity.ConnectionStatus.DISCOVERED
                            )
                            Log.d(TAG, "Pruned stale LAN peer ${p.displayName} (no beacon ${now - lastSeen}ms)")
                        }
                    }
                }
            } catch (_: Exception) { Log.d(TAG, "Liveness pruning stopped") }
        }
    }

    /** Send a directed message straight to a peer's IP when we know it; else broadcast. */
    private fun sendDirectedMessage(peerId: String, json: String) {
        val ip = peerIpRegistry[peerId]
        if (ip != null) {
            sendLanUnicast(ip, json)
        } else {
            sendLanPayload(json)            // fall back to LAN broadcast
        }
        socketManager.sendPayload(json)     // and the WiFi-Direct relay for offline reach
    }

    private fun sendLanUnicast(ip: String, json: String) {
        scope.launch {
            try {
                val bytes = json.toByteArray(Charsets.UTF_8)
                DatagramSocket().use { socket ->
                    val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName(ip), LAN_DISCOVERY_PORT)
                    socket.send(packet)
                }
            } catch (e: Exception) {
                Log.d(TAG, "LAN unicast to $ip failed (${e.message}) — falling back to broadcast")
                sendLanPayload(json)
            }
        }
    }

    private fun localBroadcastAddresses(): Set<InetAddress> {
        // Always include the limited broadcast — it works even with no regular WiFi, and is the
        // path that lets beacons traverse an active WiFi-Direct group (no router needed).
        val addresses = linkedSetOf<InetAddress>(InetAddress.getByName("255.255.255.255"))

        // Regular WiFi subnet broadcast (when connected to a router/hotspot).
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val dhcp = wifiManager?.dhcpInfo
        if (dhcp != null && dhcp.ipAddress != 0) {
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            addresses.add(InetAddress.getByAddress(byteArrayOf(
                (broadcast and 0xff).toByte(),
                ((broadcast shr 8) and 0xff).toByte(),
                ((broadcast shr 16) and 0xff).toByte(),
                ((broadcast shr 24) and 0xff).toByte()
            )))
        }

        // Per-interface broadcast addresses — this picks up the WiFi-Direct group interface
        // (p2p-...) so the LAN mesh works inside a WiFi Direct group with no router present.
        try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                if (!iface.isUp || iface.isLoopback) return@forEach
                iface.interfaceAddresses.forEach { ia ->
                    ia.broadcast?.let { addresses.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Interface enumeration failed (${e.message})")
        }

        return addresses
    }

    // ========== MESH MESSAGE LISTENER ==========

    override fun onMessageReceived(protocol: MeshProtocol, fromAddress: String, connId: String) {
        if (protocol.type !in listOf("HANDSHAKE", "CONNECTION_REQUEST", "CONNECTION_ACCEPT", "CONNECTION_REJECT") &&
            socketManager.isDuplicate(protocol.id, protocol.timestamp)) {
            Log.d(TAG, "Anti-replay: dropped ${protocol.type} id=${protocol.id}")
            return
        }
        scope.launch {
            // Persistent dedup (D3 / EU-0.2): the in-memory LRU above is empty after a process
            // restart, so a TEXT/SOS we already stored in a previous session would otherwise be
            // re-processed (re-notify/re-relay). messageExists() survives restart and stops that.
            // Transit messages (not addressed to us, never stored) are unaffected and still relay.
            if (protocol.type in listOf("TEXT", "SOS") && repository.messageExists(protocol.id)) {
                Log.d(TAG, "Persistent dedup: already-stored ${protocol.type} id=${protocol.id} — skipped")
                return@launch
            }
            // EU-3.1 / D14: real payload traffic (not background gossip/heartbeat/handshake beacons)
            // counts as mesh activity and keeps the duty cycle in the tight ACTIVE state.
            if (protocol.type in listOf("TEXT", "SOS", "ACK", "FLAG")) touchActivity()
            when (protocol.type) {
                "HANDSHAKE" -> handleHandshake(protocol, fromAddress)
                "TEXT"      -> handleTextMessage(protocol)
                "SOS"       -> handleSosMessage(protocol)
                "ACK"       -> handleAck(protocol)
                "FLAG"      -> handleFlagMessage(protocol)
                "CONNECTION_REQUEST" -> handleConnectionRequest(protocol, fromAddress)
                "CONNECTION_ACCEPT"  -> handleConnectionAccept(protocol, fromAddress)
                "CONNECTION_REJECT"  -> handleConnectionReject(protocol, fromAddress)
                "PEER_LIST"          -> handlePeerList(protocol) // gossip arriving over a socket/Aware relay (offline path)
                "ROUTING_HEARTBEAT"  -> { /* handled by MeshRouter on the Aware transport; ignore here */ }
                else -> Log.w(TAG, "Unknown type: ${protocol.type}")
            }
        }
    }

    override fun onSocketConnected(address: String, isServer: Boolean) {
        Log.d(TAG, "Socket ready: $address (server=$isServer) — sending HANDSHAKE")
        scope.launch { sendHandshake() }
    }

    override fun onPeerConnected(address: String, isServer: Boolean) {
        Log.d(TAG, "CARAKA peer validated: $address (server=$isServer)")
        carakaHandshakeValidated = true
        carakaValidationJob?.cancel()
        _connectionState.value = "CONNECTED"
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
        if (protocol.content != "HANDSHAKE") return
        Log.d(TAG, "HANDSHAKE from ${protocol.senderName} (${protocol.senderId})")
        carakaHandshakeValidated = true
        carakaValidationJob?.cancel()
        // Track the WiFi Direct MAC of this verified CARAKA peer
        val wifiDirectMac = pendingWifiDirectMac
            ?: _availablePeers.value.firstOrNull { it.isCarakaDevice() }?.deviceAddress
            ?: fromAddress
        if (wifiDirectMac.isNotBlank() && !wifiDirectMac.startsWith("lan:")) {
            carakaHandshakedMacs.add(wifiDirectMac)
            Log.d(TAG, "HANDSHAKE verified peer MAC: $wifiDirectMac")
        }

        // QR priority peer fast-track: if this HANDSHAKE matches the peer we just QR-scanned,
        // log it and clear the priority flag.
        val priority = priorityPeerId
        if (priority != null && protocol.senderId == priority) {
            Log.d(TAG, "Priority peer connected via QR: ${protocol.senderName}")
            priorityPeerId = null
        }

        // A completed HANDSHAKE over the WiFi-Direct socket means the peer is connected.
        // Mark ACTIVE_MESH so it shows as connected; preserve any prior QR verification.
        val existing = repository.getPeerById(protocol.senderId)

        // T5: TOFU — jika peer sudah dikenal dengan signingKey berbeda, ini potensi MITM.
        // Pertahankan key lama dan jangan terima key baru sampai QR re-scan dilakukan.
        if (existing != null &&
            existing.signingKey.isNotBlank() &&
            !protocol.signingKey.isNullOrBlank() &&
            existing.signingKey != protocol.signingKey
        ) {
            Log.w(TAG, "SECURITY ALERT (TOFU): Signing key berubah untuk ${protocol.senderName}!")
            Log.w(TAG, "  Key lama: ${existing.signingKey.take(20)}...")
            Log.w(TAG, "  Key baru (DIABAIKAN): ${protocol.signingKey.take(20)}...")
            // Update status koneksi tapi JANGAN timpa key — QR re-scan diperlukan untuk update key
            repository.savePeer(existing.copy(
                displayName = protocol.senderName,
                lastSeen = System.currentTimeMillis(),
                status = com.example.caraka.data.local.entity.ConnectionStatus.ACTIVE_MESH,
                isVerified = false  // Reset verified karena key mismatch mencurigakan
            ))
            peerLastLanSeen[protocol.senderId] = System.currentTimeMillis()
            return  // Jangan lanjutkan — key lama dipertahankan
        }

        val peer = (existing ?: PeerEntity(
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
            status = com.example.caraka.data.local.entity.ConnectionStatus.ACTIVE_MESH,
            hopCount = 0
        )).copy(
            displayName = protocol.senderName,
            role = protocol.senderRole,
            publicKey = protocol.publicKey ?: existing?.publicKey ?: "",
            signingKey = protocol.signingKey ?: existing?.signingKey ?: "",
            macAddress = wifiDirectMac,
            lastSeen = System.currentTimeMillis(),
            status = com.example.caraka.data.local.entity.ConnectionStatus.ACTIVE_MESH,
            hopCount = 0
        )
        repository.savePeer(peer)
        // Mark as freshly seen so liveness pruning treats this WiFi-Direct peer as live.
        peerLastLanSeen[protocol.senderId] = System.currentTimeMillis()
        Log.d(TAG, "Peer ACTIVE_MESH via WiFi-Direct HANDSHAKE: ${peer.displayName}")

        // EU-2.2 / D10: a directly-connected peer just appeared → opportunistically flush any
        // carried/queued messages for it now, instead of waiting for the next timer tick.
        // Debounced to absorb discovery flapping (PEER_FLUSH_DEBOUNCE_MS).
        val nowFlush = System.currentTimeMillis()
        val lastFlush = lastPeerFlushAt[protocol.senderId] ?: 0L
        if (nowFlush - lastFlush > MeshPolicy.PEER_FLUSH_DEBOUNCE_MS) {
            lastPeerFlushAt[protocol.senderId] = nowFlush
            repository.flushForPeer(protocol.senderId)
        }
    }

    private suspend fun handleTextMessage(protocol: MeshProtocol) {
        val myId = identityManager.getPeerId()
        val isBroadcast = protocol.recipientId == "BROADCAST" || protocol.recipientId.isBlank()
        val isForMe = protocol.recipientId == myId || isBroadcast

        // Relay unicast messages not addressed to us.
        if (!isForMe && protocol.ttl > 1) {
            sendMessage(protocol.copy(ttl = protocol.ttl - 1).toJson())
            _relayedMessageCount.value++
            return
        }
        if (!isForMe) return

        // Relay broadcast messages so they reach nodes beyond the first hop (BUG-P1).
        // Only relay if we are not the original sender (avoids redundant re-flood).
        if (isBroadcast && protocol.senderId != myId && protocol.ttl > 1) {
            sendMessage(protocol.copy(ttl = protocol.ttl - 1).toJson())
            _relayedMessageCount.value++
        }

        val senderPeer = repository.getPeerById(protocol.senderId)

        // T4: Verifikasi signature Ed25519 sebelum simpan ke DB.
        // Relay sudah terjadi di atas, tapi penyimpanan lokal memerlukan signature valid.
        // Catatan: jika senderPeer belum dikenal (peer baru, belum HANDSHAKE), kita skip verifikasi
        // karena kita belum punya signingKey — pesan tetap diterima tapi tidak bisa diverifikasi.
        if (!protocol.signature.isNullOrBlank() && senderPeer?.signingKey?.isNotBlank() == true) {
            val signingKey = cryptoManager.keyFromBase64(senderPeer.signingKey)
            val messageToVerify = protocol.encryptedPayload ?: protocol.content
            if (!cryptoManager.verifySignature(messageToVerify, protocol.signature, signingKey)) {
                Log.w(TAG, "SECURITY: Signature TEXT tidak valid dari ${protocol.senderId} (${protocol.senderName}) — drop lokal")
                return
            }
        }

        // T4: Badge authority (BPBD/POLRI/PMI) hanya diberikan jika signingKey sudah diketahui.
        // Peer yang baru pertama kali muncul belum bisa diklaim sebagai authority tanpa QR-scan.
        val effectiveRole = if (
            protocol.senderRole in listOf("BPBD", "POLRI", "PMI") &&
            senderPeer?.signingKey.isNullOrBlank()
        ) {
            Log.w(TAG, "Authority role '${protocol.senderRole}' diklaim oleh ${protocol.senderName} tapi key belum terverifikasi — degradasi ke CIVILIAN")
            "CIVILIAN"
        } else {
            protocol.senderRole
        }

        var plaintext = protocol.content
        if (protocol.encryptedPayload != null && senderPeer != null) {
            val senderPubKey = cryptoManager.keyFromBase64(senderPeer.publicKey)
            val mySecretKey = identityManager.getEncryptionKeyPair().secretKey
            val decrypted = cryptoManager.decryptMessage(protocol.encryptedPayload, senderPubKey, mySecretKey)
            if (decrypted != null) plaintext = decrypted
        }

        repository.saveMessage(
            MessageEntity(
                id = protocol.id,
                type = protocol.type,
                senderId = protocol.senderId,
                senderName = protocol.senderName,
                senderRole = effectiveRole,
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
                senderRole = effectiveRole,
                content = plaintext
            )
        )

        // EU-1.2: acknowledge unicast TEXT addressed to us with an end-to-end ACK back to the
        // sender. Broadcast/SOS are best-effort and intentionally NOT acknowledged (baseline D6 —
        // avoids ACK implosion). The ACK carries the original message id in `content` so the sender
        // can correlate it to its outbox entry (EU-1.3).
        if (!isBroadcast && protocol.senderId != myId) {
            sendAck(protocol.id, protocol.senderId)
        }
    }

    /**
     * Handle an incoming ACK (EU-1.3 / D4/D5). If it is addressed to us, mark the corresponding
     * outbox message DELIVERED (only if we actually sent it — spoof-safe in the repository). If it
     * is for someone else, forward it toward the origin while TTL allows. DELIVERED is set ONLY
     * here, from a real ACK — never from overhearing (D5).
     */
    private suspend fun handleAck(protocol: MeshProtocol) {
        val myId = identityManager.getPeerId()
        if (protocol.recipientId == myId) {
            repository.markUnicastDelivered(protocol.content)
            return
        }
        // Not for us — relay the ACK toward its origin if there is hop budget left.
        if (protocol.ttl > 1) {
            sendToPeer(protocol.recipientId, protocol.copy(ttl = protocol.ttl - 1).toJson())
        }
    }

    /** Send an end-to-end ACK for [ackedMessageId] back to [originSenderId] (EU-1.2 / D4). */
    private suspend fun sendAck(ackedMessageId: String, originSenderId: String) {
        val ack = MeshProtocol(
            type = "ACK",
            id = java.util.UUID.randomUUID().toString(),
            senderId = identityManager.getPeerId(),
            senderName = identityManager.getDisplayName(),
            senderRole = identityManager.getRole(),
            recipientId = originSenderId,
            content = ackedMessageId,
            timestamp = System.currentTimeMillis(),
            ttl = 5,
            priority = "NORMAL"
        )
        sendToPeer(originSenderId, ack.toJson())
    }

    private suspend fun handleSosMessage(protocol: MeshProtocol) {
        Log.d(TAG, "SOS from ${protocol.senderName}: ${protocol.content}")
        // SOS di-relay dulu (DTN principle: propagasi cepat) baru verifikasi lokal.
        if (protocol.ttl > 1) {
            sendMessage(protocol.copy(ttl = protocol.ttl - 1).toJson())
            _relayedMessageCount.value++
        }

        // T4: Verifikasi signature SOS sebelum simpan ke DB dan tampilkan notifikasi.
        val senderPeer = repository.getPeerById(protocol.senderId)
        if (!protocol.signature.isNullOrBlank() && senderPeer?.signingKey?.isNotBlank() == true) {
            val signingKey = cryptoManager.keyFromBase64(senderPeer.signingKey)
            if (!cryptoManager.verifySignature(protocol.content, protocol.signature, signingKey)) {
                Log.w(TAG, "SECURITY: Signature SOS tidak valid dari ${protocol.senderId} — drop lokal")
                return
            }
        }

        // T4: Badge authority untuk SOS juga memerlukan key yang diketahui.
        val effectiveRole = if (
            protocol.senderRole in listOf("BPBD", "POLRI", "PMI") &&
            senderPeer?.signingKey.isNullOrBlank()
        ) "CIVILIAN" else protocol.senderRole

        repository.saveMessage(
            MessageEntity(
                id = protocol.id,
                type = "SOS",
                senderId = protocol.senderId,
                senderName = protocol.senderName,
                senderRole = effectiveRole,
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

    // ========== NEW: CONNECTION REQUEST HANDLERS ==========

    /**
     * Handle incoming CONNECTION_REQUEST from peer.
     * Design decision: direct connect, no accept/reject dialog. We always accept and mark
     * the peer ACTIVE_MESH. WiFi Direct P2P is only used as an offline fallback when the
     * peer is NOT reachable on the LAN.
     */
    private suspend fun handleConnectionRequest(protocol: MeshProtocol, fromAddress: String) {
        val peerId = protocol.senderId
        Log.d(TAG, "CONNECTION_REQUEST from ${protocol.senderName} ($peerId)")

        repository.updatePeerConnectionState(peerId, com.example.caraka.data.local.entity.ConnectionStatus.ACTIVE_MESH)
        sendConnectionAccept(peerId, protocol.senderName, protocol.senderRole)
        connectToPeerAfterAccept(peerId)
    }

    /**
     * Handle CONNECTION_ACCEPT response from peer.
     */
    private suspend fun handleConnectionAccept(protocol: MeshProtocol, fromAddress: String) {
        val peerId = protocol.senderId
        Log.d(TAG, "CONNECTION_ACCEPT from $peerId")
        repository.updatePeerConnectionState(peerId, com.example.caraka.data.local.entity.ConnectionStatus.ACTIVE_MESH)
        connectToPeerAfterAccept(peerId)
    }

    /**
     * Handle CONNECTION_REJECT response from peer.
     * Means peer rejected our CONNECTION_REQUEST.
     */
    private suspend fun handleConnectionReject(protocol: MeshProtocol, fromAddress: String) {
        val peerId = protocol.senderId
        Log.w(TAG, "CONNECTION_REJECT from $peerId")

        // Increment rejection count
        repository.incrementRejectionCount(peerId)

        // Reset to DISCOVERED, user can retry after cooldown
        repository.updatePeerConnectionState(peerId, com.example.caraka.data.local.entity.ConnectionStatus.DISCOVERED)

        _connectionState.value = "CONNECTION_REJECTED:$peerId"
    }

    /**
     * Send CONNECTION_ACCEPT back to peer (private version called from handler).
     */
    private suspend fun sendConnectionAccept(peerId: String, peerName: String, peerRole: String) {
        val accept = MeshProtocol(
            type = "CONNECTION_ACCEPT",
            id = java.util.UUID.randomUUID().toString(),
            senderId = identityManager.getPeerId(),
            senderName = identityManager.getDisplayName(),
            senderRole = identityManager.getRole(),
            recipientId = peerId,
            content = "Accept connection",
            timestamp = System.currentTimeMillis()
        )
        Log.d(TAG, "Sending CONNECTION_ACCEPT to $peerId")
        sendToPeer(peerId, accept.toJson())
    }

    /**
     * PUBLIC: Send CONNECTION_ACCEPT message (called from ViewModel after user accepts).
     */
    override fun sendConnectionAcceptMessage(peerId: String, peerName: String, peerRole: String) {
        scope.launch {
            sendConnectionAccept(peerId, peerName, peerRole)
        }
    }

    /**
     * Establish a path to the peer. If the peer is reachable on the LAN (the common case
     * when all devices share a WiFi), do NOTHING — LAN unicast/broadcast already carries
     * traffic both ways, and the mesh is fully connected without any Group-Owner relay.
     *
     * WiFi Direct P2P is reserved strictly for the offline fallback: when we have no LAN IP
     * for the peer at all. This is what prevents BUG-01 — we never blindly invite whatever
     * WiFi-Direct device happens to be nearby (TVs, speakers, laptops).
     */
    private fun connectToPeerAfterAccept(peerId: String) {
        if (peerIpRegistry.containsKey(peerId)) {
            Log.d(TAG, "Peer $peerId reachable on LAN — no WiFi Direct needed (full mesh via LAN)")
            return
        }
        Log.d(TAG, "Peer $peerId has no LAN path — offline WiFi Direct fallback not auto-triggered")
        // Intentionally NOT auto-connecting to arbitrary WiFi-Direct devices.
        // Offline P2P is initiated only by explicit user action via connectToPeer(device).
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
