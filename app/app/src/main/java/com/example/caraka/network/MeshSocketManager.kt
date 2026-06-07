package com.example.caraka.network

import android.net.Network
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Listener for mesh socket events.
 */
interface MeshMessageListener {
    fun onMessageReceived(protocol: MeshProtocol, fromAddress: String, connId: String = "")
    /** Raw TCP socket is up — handshake not yet validated. */
    fun onSocketConnected(address: String, isServer: Boolean)
    /** Fires only after a valid CARAKA HANDSHAKE message is received. */
    fun onPeerConnected(address: String, isServer: Boolean)
    fun onPeerDisconnected(address: String)
}

/**
 * Handles raw TCP socket connections over WiFi Direct.
 * Uses 4-byte length-prefixed framing to handle message boundaries.
 */
class MeshSocketManager(private val listener: MeshMessageListener) {

    companion object {
        const val PORT = 8888
        const val TIMEOUT = 5000
        private const val TAG = "MeshSocket"
        private const val MAX_MESSAGE_SIZE = 65536
        // Anti-replay: reject messages older than 5 minutes
        private const val MAX_TIMESTAMP_DRIFT_MS = 5 * 60 * 1_000L
        // Keep at most 2 000 seen IDs in memory (LRU-like via LinkedHashSet)
        private const val MAX_SEEN_IDS = 2_000
    }

    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Support multiple connections per peer by using unique connection IDs
    // Key: unique connection ID (generated per connection)
    // Value: output stream for that connection
    private val clientStreams = java.util.concurrent.ConcurrentHashMap<String, OutputStream>()
    private val connectionRoles = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val connectionAddresses = java.util.concurrent.ConcurrentHashMap<String, String>() // connId -> address
    private val handshakeCompleted = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var activeSocket: Socket? = null // Keep for client close
    private var serverSocket: ServerSocket? = null

    // Generate unique connection ID
    private fun generateConnectionId(): String = java.util.UUID.randomUUID().toString()

    private fun isValidCarakaHandshake(protocol: MeshProtocol): Boolean {
        return protocol.type == "HANDSHAKE"
            && protocol.content == "HANDSHAKE"
            && protocol.senderId.isNotBlank()
            && !protocol.publicKey.isNullOrBlank()
    }

    /**
     * Anti-replay cache.
     * Returns true if the message was already seen (should be dropped).
     * Thread-safe via synchronized access on the set itself.
     */
    private val seenIds: MutableSet<String> =
        java.util.Collections.synchronizedSet(
            object : LinkedHashSet<String>() {
                override fun add(element: String): Boolean {
                    if (size >= MAX_SEEN_IDS) iterator().let { it.next(); it.remove() }
                    return super.add(element)
                }
            }
        )

    fun isDuplicate(id: String, timestamp: Long): Boolean {
        // Timestamp drift check
        val drift = Math.abs(System.currentTimeMillis() - timestamp)
        if (drift > MAX_TIMESTAMP_DRIFT_MS) {
            android.util.Log.w(TAG, "Anti-replay: timestamp drift ${drift}ms for msg $id — dropped")
            return true
        }
        // ID dedup check
        return !seenIds.add(id) // add returns false if already present
    }

    fun startServer() = startServer(PORT)

    /**
     * Open a TCP server socket on [port]. The default [PORT] is used by the Wi-Fi Direct group
     * owner; the Wi-Fi Aware publisher uses its own port (see [MeshManager]) on a separate
     * [MeshSocketManager] instance so the two server sockets never collide.
     */
    fun startServer(port: Int) {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "Server socket opened on port $port")

                while (isActive) {
                    val clientSocket = serverSocket!!.accept()
                    clientSocket.keepAlive = true
                    val address = clientSocket.inetAddress.hostAddress ?: "unknown"
                    val connId = generateConnectionId()
                    Log.d(TAG, "Client connected: $address (connId=$connId)")

                    val out = clientSocket.getOutputStream()
                    clientStreams[connId] = out
                    connectionRoles[connId] = true
                    connectionAddresses[connId] = address
                    listener.onSocketConnected(address, isServer = true)

                    scope.launch {
                        handleIncomingData(clientSocket.getInputStream(), address, connId)
                        clientStreams.remove(connId)
                        connectionAddresses.remove(connId)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server error", e)
                }
            }
        }
    }

    fun startClient(hostAddress: String) {
        clientJob?.cancel()
        clientJob = scope.launch {
            try {
                val socket = Socket()
                socket.bind(null)
                socket.keepAlive = true
                val connId = generateConnectionId()
                Log.d(TAG, "Connecting to GO at $hostAddress:$PORT (connId=$connId)...")
                socket.connect(InetSocketAddress(hostAddress, PORT), TIMEOUT)
                Log.d(TAG, "Client connected to GO!")

                val out = socket.getOutputStream()
                clientStreams[connId] = out
                connectionRoles[connId] = false
                connectionAddresses[connId] = hostAddress
                listener.onSocketConnected(hostAddress, isServer = false)

                handleIncomingData(socket.getInputStream(), hostAddress, connId)
                clientStreams.remove(connId)
                connectionAddresses.remove(connId)
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Client error", e)
                }
            }
        }
    }

    /**
     * Connect to a peer over a specific [network] — used for Wi-Fi Aware Out-of-Band data paths.
     * The socket is bound to the Aware [Network] via [Network.bindSocket] BEFORE connect so traffic
     * leaves over the NAN interface (link-local IPv6) rather than the default route.
     *
     * Each call spawns its own reader coroutine and registers its stream, so a node can hold many
     * concurrent Aware connections (one per neighbour) for multi-hop forwarding. Returns the
     * generated connection id so the caller can map peerId -> connId once the HANDSHAKE arrives.
     */
    fun connectOverNetwork(network: Network, peerAddress: InetAddress, port: Int): String {
        val connId = generateConnectionId()
        val host = peerAddress.hostAddress ?: peerAddress.toString()
        scope.launch {
            try {
                val socket = Socket()
                // Bind to the Aware data-path network so the TCP connection uses that interface.
                network.bindSocket(socket)
                socket.keepAlive = true
                Log.d(TAG, "Aware connect to [$host]:$port (connId=$connId)…")
                // Pass the InetAddress directly so the IPv6 link-local scope id is preserved.
                socket.connect(InetSocketAddress(peerAddress, port), TIMEOUT)
                Log.d(TAG, "Aware data-path socket connected to $host")

                val out = socket.getOutputStream()
                clientStreams[connId] = out
                connectionRoles[connId] = false
                connectionAddresses[connId] = host
                listener.onSocketConnected(host, isServer = false)

                handleIncomingData(socket.getInputStream(), host, connId)
                clientStreams.remove(connId)
                connectionAddresses.remove(connId)
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "Aware client error to $host", e)
                clientStreams.remove(connId)
                connectionAddresses.remove(connId)
            }
        }
        return connId
    }

    /**
     * Read length-prefixed messages from the stream.
     * Format: [4-byte big-endian length][JSON payload]
     */
    private suspend fun handleIncomingData(inputStream: InputStream, address: String, connId: String = "") {
        val dataInput = DataInputStream(BufferedInputStream(inputStream))
        val handshakeKey = if (connId.isNotBlank()) connId else address

        try {
            while (currentCoroutineContext().isActive) {
                val length = dataInput.readInt()
                if (length <= 0 || length > MAX_MESSAGE_SIZE) {
                    Log.w(TAG, "Invalid message length: $length, skipping")
                    continue
                }

                val buffer = ByteArray(length)
                dataInput.readFully(buffer)

                val json = String(buffer, Charsets.UTF_8)
                Log.d(TAG, "Received from $address: $json")

                val protocol = MeshProtocol.fromJson(json)
                if (protocol != null) {
                    if (isValidCarakaHandshake(protocol) && handshakeCompleted.add(handshakeKey)) {
                        listener.onPeerConnected(address, connectionRoles[handshakeKey] ?: false)
                    }
                    listener.onMessageReceived(protocol, address, connId)
                } else {
                    Log.w(TAG, "Failed to parse message JSON")
                }
            }
        } catch (e: EOFException) {
            Log.d(TAG, "Peer disconnected: $address")
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                Log.e(TAG, "Error reading from $address", e)
            }
        } finally {
            listener.onPeerDisconnected(address)
        }
    }

    /**
     * Send a length-prefixed JSON payload to a specific connection (for multi-hop routing).
     */
    fun sendToConnection(connId: String, payload: String) {
        scope.launch {
            try {
                val out = clientStreams[connId]
                if (out == null) {
                    Log.w(TAG, "Connection $connId not found for routing!")
                    return@launch
                }

                val bytes = payload.toByteArray(Charsets.UTF_8)
                val header = ByteBuffer.allocate(4).putInt(bytes.size).array()

                synchronized(out) {
                    out.write(header)
                    out.write(bytes)
                    out.flush()
                }
                Log.d(TAG, "Routed ${bytes.size} bytes to connId $connId")
            } catch (e: Exception) {
                Log.e(TAG, "Error routing payload to $connId", e)
            }
        }
    }

    /**
     * Send a length-prefixed JSON payload to all connected peers (Broadcast).
     */
    fun sendPayload(payload: String) {
        scope.launch {
            try {
                if (clientStreams.isEmpty()) {
                    Log.w(TAG, "No active output streams!")
                    return@launch
                }

                val bytes = payload.toByteArray(Charsets.UTF_8)
                val header = ByteBuffer.allocate(4).putInt(bytes.size).array()

                clientStreams.values.forEach { out ->
                    try {
                        synchronized(out) {
                            out.write(header)
                            out.write(bytes)
                            out.flush()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending to a stream", e)
                    }
                }
                Log.d(TAG, "Sent ${bytes.size} bytes to ${clientStreams.size} peers")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending payload", e)
            }
        }
    }

    fun stopAll() {
        serverJob?.cancel()
        clientJob?.cancel()
        clientStreams.values.forEach { try { it.close() } catch (_: Exception) {} }
        clientStreams.clear()
        connectionRoles.clear()
        handshakeCompleted.clear()
        try { activeSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        activeSocket = null
        serverSocket = null
    }
}
