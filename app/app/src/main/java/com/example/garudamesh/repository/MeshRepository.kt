package com.example.garudamesh.repository

import com.example.garudamesh.crypto.CryptoManager
import com.example.garudamesh.crypto.IdentityManager
import com.example.garudamesh.data.local.dao.MessageDao
import com.example.garudamesh.data.local.dao.PeerDao
import com.example.garudamesh.data.local.dao.RelayDao
import com.example.garudamesh.data.local.entity.MessageEntity
import com.example.garudamesh.data.local.entity.PeerEntity
import com.example.garudamesh.network.MeshProtocol
import com.example.garudamesh.network.WifiDirectManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * MeshRepository handles data operations, combining local DB with Crypto.
 * This is the Single Source of Truth for the ViewModel.
 */
class MeshRepository(
    private val messageDao: MessageDao,
    private val peerDao: PeerDao,
    private val relayDao: RelayDao,
    private val cryptoManager: CryptoManager,
    private val identityManager: IdentityManager
) {

    var wifiDirectManager: WifiDirectManager? = null

    // ========== PEER MANAGEMENT ==========

    fun getConnectedPeers(): Flow<List<PeerEntity>> = peerDao.getConnectedPeers()
    fun getAllPeers(): Flow<List<PeerEntity>> = peerDao.getAllPeers()
    fun getConnectedPeerCount(): Flow<Int> = peerDao.getConnectedPeerCount()

    suspend fun getPeerById(peerId: String): PeerEntity? {
        return peerDao.getPeerById(peerId)
    }

    suspend fun savePeer(peer: PeerEntity) {
        peerDao.insertPeer(peer)
    }

    // ========== MESSAGING ==========

    suspend fun saveMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
    }

    fun getMessagesByPeer(peerId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByPeer(peerId)
    }

    fun getSosMessages(): Flow<List<MessageEntity>> = messageDao.getSosMessages()

    fun getRecentAlerts(): Flow<List<MessageEntity>> = messageDao.getRecentAlerts()

    /** Increment local flag count — called when receiving a FLAG wire message. */
    suspend fun flagMessageById(messageId: String) {
        messageDao.flagMessage(messageId)
    }

    /**
     * Flag a message locally AND broadcast a FLAG packet to the mesh.
     * Called when the current user long-presses and flags a message.
     */
    suspend fun flagAndBroadcast(messageId: String) {
        messageDao.flagMessage(messageId)

        val myId = identityManager.getPeerId()
        val myName = identityManager.getDisplayName()
        val myRole = identityManager.getRole()

        val flagPacket = com.example.garudamesh.network.MeshProtocol(
            type = "FLAG",
            id = java.util.UUID.randomUUID().toString(),
            senderId = myId,
            senderName = myName,
            senderRole = myRole,
            recipientId = "BROADCAST",
            content = messageId, // the flagged message's ID
            timestamp = System.currentTimeMillis(),
            ttl = 5,
            priority = "NORMAL"
        )
        wifiDirectManager?.sendMessage(flagPacket.toJson())
    }

    fun getTotalRelayedCount(): kotlinx.coroutines.flow.StateFlow<Int>? =
        wifiDirectManager?.relayedMessageCount

    /**
     * Send a direct text message to a specific peer.
     * Encrypts the payload, signs it, and saves it locally.
     * Transmits it over WiFi Direct sockets.
     */
    suspend fun sendDirectMessage(recipientId: String, content: String) {
        val recipient = peerDao.getPeerById(recipientId) ?: return // Peer not found locally
        
        val myId = identityManager.getPeerId()
        val myName = identityManager.getDisplayName()
        val myRole = identityManager.getRole()
        val mySignKeys = identityManager.getSigningKeyPair()
        val myEncKeys = identityManager.getEncryptionKeyPair()

        // 1. Encrypt content
        val recipientPubKey = cryptoManager.keyFromBase64(recipient.publicKey)
        val encryptedPayload = cryptoManager.encryptMessage(
            plaintext = content,
            recipientPublicKey = recipientPubKey,
            senderSecretKey = myEncKeys.secretKey
        )

        if (encryptedPayload == null) return // Encryption failed

        // 2. Sign the encrypted payload
        val signature = cryptoManager.signMessage(encryptedPayload, mySignKeys.secretKey)

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // 3. Create Entity and save to local DB
        val messageEntity = MessageEntity(
            id = messageId,
            type = "TEXT",
            senderId = myId,
            senderName = myName,
            senderRole = myRole,
            recipientId = recipientId,
            content = content, // Store plaintext locally so UI can show it
            encryptedPayload = encryptedPayload,
            timestamp = timestamp,
            ttl = 5,
            priority = "NORMAL",
            signature = signature,
            sosCategory = null,
            latitude = null,
            longitude = null,
            isIncoming = false
        )

        messageDao.insertMessage(messageEntity)
        
        // 4. Send via WiFi Direct manager
        val protocol = MeshProtocol(
            type = "TEXT",
            id = messageId,
            senderId = myId,
            senderName = myName,
            senderRole = myRole,
            recipientId = recipientId,
            content = "", // Clear plaintext for network transmission
            encryptedPayload = encryptedPayload,
            timestamp = timestamp,
            ttl = 5,
            priority = "NORMAL",
            signature = signature
        )
        wifiDirectManager?.sendMessage(protocol.toJson())
    }

    /**
     * Broadcast an SOS message.
     * SOS is NOT encrypted (so everyone can read it), but it IS signed.
     */
    suspend fun broadcastSos(category: String, description: String, lat: Double?, lng: Double?) {
        val myId = identityManager.getPeerId()
        val myName = identityManager.getDisplayName()
        val myRole = identityManager.getRole()
        val mySignKeys = identityManager.getSigningKeyPair()

        val fullContent = if (description.isNotBlank()) "[$category] $description" else "[$category] EMERGENCY SOS!"

        // SOS is unencrypted, we sign the plaintext
        val signature = cryptoManager.signMessage(fullContent, mySignKeys.secretKey)
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val messageEntity = MessageEntity(
            id = messageId,
            type = "SOS",
            senderId = myId,
            senderName = myName,
            senderRole = myRole,
            recipientId = "BROADCAST",
            content = fullContent,
            encryptedPayload = null,
            timestamp = timestamp,
            ttl = 10, // Higher TTL for SOS
            priority = "EMERGENCY",
            signature = signature,
            sosCategory = category,
            latitude = lat,
            longitude = lng,
            isIncoming = false
        )

        messageDao.insertMessage(messageEntity)
        
        // Broadcast via WiFi Direct sockets
        val protocol = MeshProtocol(
            type = "SOS",
            id = messageId,
            senderId = myId,
            senderName = myName,
            senderRole = myRole,
            recipientId = "BROADCAST",
            content = fullContent,
            encryptedPayload = null,
            timestamp = timestamp,
            ttl = 10,
            priority = "EMERGENCY",
            signature = signature,
            sosCategory = category,
            latitude = lat,
            longitude = lng
        )
        wifiDirectManager?.sendMessage(protocol.toJson())
    }
}
