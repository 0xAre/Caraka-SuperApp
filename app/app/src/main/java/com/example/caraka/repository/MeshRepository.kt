package com.example.caraka.repository

import com.example.caraka.crypto.CryptoManager
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.data.local.dao.MessageDao
import com.example.caraka.data.local.dao.OutboxDao
import com.example.caraka.data.local.dao.PeerDao
import com.example.caraka.data.local.dao.RelayDao
import com.example.caraka.data.local.entity.MessageEntity
import com.example.caraka.data.local.entity.OutboxEntity
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.network.MeshPolicy
import com.example.caraka.network.MeshProtocol
import com.example.caraka.network.MeshTransport
import com.example.caraka.ui.util.SosAlertText
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
    private val outboxDao: OutboxDao,
    private val cryptoManager: CryptoManager,
    private val identityManager: IdentityManager
) {

    var transport: MeshTransport? = null

    // ========== PEER MANAGEMENT ==========

    fun getConnectedPeers(): Flow<List<PeerEntity>> = peerDao.getConnectedPeers()
    fun getAllPeers(): Flow<List<PeerEntity>> = peerDao.getAllPeers()
    fun getConnectedPeerCount(): Flow<Int> = peerDao.getConnectedPeerCount()
    fun getPeersByStatus(statusName: String): Flow<List<PeerEntity>> = peerDao.getPeersByStatus(statusName)
    fun getActiveMeshPeers(): Flow<List<PeerEntity>> = peerDao.getActiveMeshPeers()

    suspend fun getPeerById(peerId: String): PeerEntity? {
        return peerDao.getPeerById(peerId)
    }

    suspend fun savePeer(peer: PeerEntity) {
        peerDao.insertPeer(peer)
    }

    suspend fun disconnectAllPeers() {
        peerDao.resetAllToDiscovered()
    }

    /** Clear stale unverified peers (called on launch). QR-verified peers are preserved. */
    suspend fun clearUnverifiedPeers() {
        peerDao.deleteUnverifiedPeers()
    }

    /**
     * Save a peer that was verified via QR code scan (in-person).
     * If the peer already exists in the DB, updates their keys and marks isVerified = true.
     * If not, creates a new record.
     */
    suspend fun saveVerifiedPeer(
        peerId: String,
        displayName: String,
        role: String,
        encPubKey: String,
        signPubKey: String
    ) {
        val existing = peerDao.getPeerById(peerId)
        val peer = existing?.copy(
            displayName = displayName,
            role = role,
            publicKey = encPubKey,
            signingKey = signPubKey,
            isVerified = true,
            isAuthority = role in listOf("BPBD", "POLRI", "PMI"),
            lastSeen = System.currentTimeMillis()
        ) ?: PeerEntity(
            id = peerId,
            deviceName = displayName,
            displayName = displayName,
            role = role,
            publicKey = encPubKey,
            signingKey = signPubKey,
            isVerified = true,
            isAuthority = role in listOf("BPBD", "POLRI", "PMI"),
            macAddress = null,
            lastSeen = System.currentTimeMillis(),
            status = com.example.caraka.data.local.entity.ConnectionStatus.DISCOVERED
        )
        peerDao.insertPeer(peer)
    }

    // NEW: Connection state management for manual request flow
    suspend fun updatePeerConnectionState(peerId: String, status: com.example.caraka.data.local.entity.ConnectionStatus) {
        peerDao.updateConnectionState(peerId, status.name)
    }

    suspend fun updateLastAttempt(peerId: String) {
        peerDao.updateLastAttempt(peerId)
    }

    suspend fun incrementRejectionCount(peerId: String) {
        peerDao.incrementRejectionCount(peerId)
    }

    suspend fun resetRejectionCount(peerId: String) {
        peerDao.resetRejectionCount(peerId)
    }

    /** Wipe all local data — called on identity reset/logout so nothing leaks across accounts. */
    suspend fun clearAllData() {
        messageDao.deleteAllMessages()
        peerDao.deleteAllPeers()
        relayDao.deleteAll()
        outboxDao.deleteAll()
    }

    // ========== MESSAGING ==========

    suspend fun saveMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
    }

    /**
     * Persistent dedup gate (D3 / EU-0.2). Returns true if a message with this ID is already
     * stored locally. Survives process restart (the in-memory LRU does not), so a message that was
     * already delivered in a previous session is not re-processed (re-relayed / re-notified).
     */
    suspend fun messageExists(messageId: String): Boolean = messageDao.messageExists(messageId)

    /**
     * Mark a unicast message DELIVERED upon receiving a genuine end-to-end ACK (EU-1.3 / D4).
     * Only acts if the message is in OUR outbox (i.e. we actually sent it) — this rejects spoofed
     * ACKs for messages we never sent, and is the ONLY path that may set DELIVERED (never implicit
     * ACK / overhearing — baseline D5). The transport unit is then removed from the outbox.
     */
    suspend fun markUnicastDelivered(messageId: String) {
        if (outboxDao.getById(messageId) == null) return
        messageDao.updateDeliveryStatus(messageId, "DELIVERED")
        outboxDao.deleteById(messageId)
    }

    /**
     * Bounded retry sweep for un-ACKed unicast messages (EU-1.4 / D4). For each outbox unit that is
     * due (nextAttemptAt ≤ now) and not yet delivered: resend, increment the attempt counter, and
     * reschedule with capped exponential backoff + jitter. When attempts exceed
     * [MeshPolicy.UNICAST_MAX_ATTEMPTS] the message is marked FAILED and removed — never infinite
     * retry. Expired units are evicted first. This method is idempotent and safe to call from any
     * trigger (a new send in Phase 1; the periodic queue-processor in Phase 2 / EU-2.1).
     */
    suspend fun retryDueMessages() {
        val now = System.currentTimeMillis()
        outboxDao.deleteExpired(now)
        for (entry in outboxDao.getDue(now)) {
            val capReached = entry.attemptCount >= MeshPolicy.UNICAST_MAX_ATTEMPTS
            if (capReached) {
                // EU-2.2 / D7: once the active-retry budget is spent, only VERIFIED contacts are
                // carried (held + slowly retried until ttlExpiry). Non-verified recipients fail —
                // this bounds carry to trusted contacts and limits abuse/storage growth.
                val verified = peerDao.getPeerById(entry.recipientId)?.isVerified == true
                if (!verified) {
                    messageDao.updateDeliveryStatus(entry.id, "FAILED")
                    outboxDao.deleteById(entry.id)
                    continue
                }
            }
            transport?.sendToPeer(entry.recipientId, entry.payloadJson)
            val nextAttempt = if (capReached) entry.attemptCount else entry.attemptCount + 1
            val backoff = if (capReached) {
                MeshPolicy.UNICAST_RETRY_MAX_MS // slow carry cadence for verified contacts
            } else {
                (MeshPolicy.UNICAST_RETRY_BASE_MS shl (entry.attemptCount - 1).coerceAtLeast(0))
                    .coerceAtMost(MeshPolicy.UNICAST_RETRY_MAX_MS)
            }
            val jitter = (Math.random() * MeshPolicy.RETRY_JITTER_MS).toLong()
            outboxDao.updateAttempt(entry.id, "SENT", nextAttempt, now + backoff + jitter)
        }
    }

    /**
     * Opportunistic carry delivery (EU-2.2 / D7/D10): when a peer becomes reachable, immediately
     * (re)send everything queued for it instead of waiting for the next timer tick. Caller is
     * responsible for debouncing flapping peers (PEER_FLUSH_DEBOUNCE_MS). Receiver-side dedup makes
     * any overlap with the timer harmless.
     */
    suspend fun flushForPeer(peerId: String) {
        for (entry in outboxDao.getPendingForPeer(peerId)) {
            transport?.sendToPeer(peerId, entry.payloadJson)
        }
    }

    /**
     * Enforce the outbox storage quota (EU-2.3 / D7 Resource Management Model). When the outbox
     * exceeds the message-count or total-byte cap, evict worst-first (lowest priority → oldest →
     * most-replicated) until back within limits. Evicted units are marked FAILED so the UI is
     * honest. Called after each enqueue; eviction is incremental so a small candidate batch suffices.
     */
    suspend fun enforceOutboxQuota() {
        var count = outboxDao.count()
        var bytes = outboxDao.totalPayloadBytes()
        if (count <= MeshPolicy.OUTBOX_MAX_MESSAGES && bytes <= MeshPolicy.OUTBOX_MAX_TOTAL_BYTES) return
        for (candidate in outboxDao.evictionCandidates(limit = 32)) {
            if (count <= MeshPolicy.OUTBOX_MAX_MESSAGES && bytes <= MeshPolicy.OUTBOX_MAX_TOTAL_BYTES) break
            messageDao.updateDeliveryStatus(candidate.id, "FAILED")
            outboxDao.deleteById(candidate.id)
            count -= 1
            bytes -= candidate.payloadJson.length.toLong()
        }
    }

    fun getMessagesByPeer(peerId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByPeer(peerId)
    }

    fun getSosMessages(): Flow<List<MessageEntity>> = messageDao.getSosMessages()

    fun getAllDirectMessages(): Flow<List<MessageEntity>> = messageDao.getAllDirectMessages()

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

        val flagPacket = com.example.caraka.network.MeshProtocol(
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
        transport?.sendMessage(flagPacket.toJson())
    }

    fun getTotalRelayedCount(): kotlinx.coroutines.flow.StateFlow<Int>? =
        transport?.relayedMessageCount

    /**
     * Send a direct text message to a specific peer.
     * Encrypts the payload, signs it, and saves it locally.
     * Transmits it over WiFi Direct sockets.
     */
    suspend fun sendDirectMessage(recipientId: String, content: String) {
        // EU-1.4: opportunistically sweep due retries on each new send (lightweight Phase-1 trigger;
        // the periodic queue-processor in Phase 2 / EU-2.1 will drive this on a timer too).
        retryDueMessages()

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
        val payloadJson = protocol.toJson()

        // 5. Record the transport unit in the persistent outbox (EU-1.1).
        // This is the unit the queue-processor (Phase 2) and ACK path (EU-1.3/1.4) will track.
        // State starts SENT because we attempt an immediate send below; bounded retry (EU-1.4)
        // and DELIVERED-on-ACK (EU-1.3) update it later. ttlExpiry is the coarse wall-clock bound
        // (the hop TTL lives inside the payload), per MeshPolicy / D1 / D7.
        val now = System.currentTimeMillis()
        outboxDao.upsert(
            OutboxEntity(
                id = messageId,
                recipientId = recipientId,
                payloadJson = payloadJson,
                state = "SENT",
                priority = "NORMAL",
                attemptCount = 1,
                nextAttemptAt = now + MeshPolicy.UNICAST_RETRY_BASE_MS,
                createdAt = now,
                ttlExpiry = now + MeshPolicy.MESSAGE_MAX_AGE_MS
            )
        )
        // EU-2.3: keep the outbox within its storage quota after enqueueing.
        enforceOutboxQuota()

        // Directed message → LAN unicast straight to the recipient (true B↔C delivery)
        transport?.sendToPeer(recipientId, payloadJson)
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

        val fullContent = SosAlertText.storageContent(category, description)

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
        transport?.sendMessage(protocol.toJson())
    }
}
