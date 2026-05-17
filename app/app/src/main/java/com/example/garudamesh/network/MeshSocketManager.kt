package com.example.garudamesh.network

import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Listener for mesh socket events.
 */
interface MeshMessageListener {
    fun onMessageReceived(protocol: MeshProtocol, fromAddress: String)
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
    }

    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val clientStreams = java.util.concurrent.ConcurrentHashMap<String, OutputStream>()
    private var activeSocket: Socket? = null // Keep for client close
    private var serverSocket: ServerSocket? = null

    fun startServer() {
        serverJob?.cancel()
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Server socket opened on port $PORT")

                while (isActive) {
                    val clientSocket = serverSocket!!.accept()
                    val address = clientSocket.inetAddress.hostAddress ?: "unknown"
                    Log.d(TAG, "Client connected: $address")

                    val out = clientSocket.getOutputStream()
                    clientStreams[address] = out
                    listener.onPeerConnected(address, isServer = true)

                    scope.launch {
                        handleIncomingData(clientSocket.getInputStream(), address)
                        clientStreams.remove(address)
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
                Log.d(TAG, "Connecting to GO at $hostAddress:$PORT...")
                socket.connect(InetSocketAddress(hostAddress, PORT), TIMEOUT)
                Log.d(TAG, "Client connected to GO!")

                val out = socket.getOutputStream()
                clientStreams[hostAddress] = out
                listener.onPeerConnected(hostAddress, isServer = false)

                handleIncomingData(socket.getInputStream(), hostAddress)
                clientStreams.remove(hostAddress)
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Client error", e)
                }
            }
        }
    }

    /**
     * Read length-prefixed messages from the stream.
     * Format: [4-byte big-endian length][JSON payload]
     */
    private suspend fun handleIncomingData(inputStream: InputStream, address: String) {
        val dataInput = DataInputStream(BufferedInputStream(inputStream))

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
                    listener.onMessageReceived(protocol, address)
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
     * Send a length-prefixed JSON payload to the connected peer.
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
        try { activeSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        activeSocket = null
        serverSocket = null
    }
}
