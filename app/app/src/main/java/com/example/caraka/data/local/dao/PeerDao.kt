package com.example.caraka.data.local.dao

import androidx.room.*
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.data.local.entity.ConnectionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {

    // ========== INSERT ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: PeerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeers(peers: List<PeerEntity>)

    // ========== QUERIES ==========

    /** Get all known peers */
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<PeerEntity>>

    /** Get peers by connection status */
    @Query("SELECT * FROM peers WHERE status = :status ORDER BY lastSeen DESC")
    fun getPeersByStatus(status: String): Flow<List<PeerEntity>>

    /** Get discovered peers (not yet requested) */
    @Query("SELECT * FROM peers WHERE status = 'DISCOVERED' ORDER BY lastSeen DESC")
    fun getDiscoveredPeers(): Flow<List<PeerEntity>>

    /** Get peers with pending connection requests */
    @Query("SELECT * FROM peers WHERE status = 'PENDING_REQUEST' ORDER BY lastSeen DESC")
    fun getPendingRequestPeers(): Flow<List<PeerEntity>>

    /** Get active mesh peers (fully connected) */
    @Query("SELECT * FROM peers WHERE status = 'ACTIVE_MESH' ORDER BY lastSeen DESC")
    fun getActiveMeshPeers(): Flow<List<PeerEntity>>

    /** Get currently connected peers */
    @Query("SELECT * FROM peers WHERE status IN ('CONNECTED', 'ACTIVE_MESH') ORDER BY lastSeen DESC")
    fun getConnectedPeers(): Flow<List<PeerEntity>>

    /** Get a specific peer by ID */
    @Query("SELECT * FROM peers WHERE id = :peerId LIMIT 1")
    suspend fun getPeerById(peerId: String): PeerEntity?

    /** Get a peer by connection ID */
    @Query("SELECT * FROM peers WHERE connectionId = :connectionId LIMIT 1")
    suspend fun getPeerByConnectionId(connectionId: String): PeerEntity?

    /** Get authority peers (BPBD, Polri, PMI) */
    @Query("SELECT * FROM peers WHERE isAuthority = 1")
    fun getAuthorityPeers(): Flow<List<PeerEntity>>

    /** Get connected peer count */
    @Query("SELECT COUNT(*) FROM peers WHERE status IN ('CONNECTED', 'ACTIVE_MESH')")
    fun getConnectedPeerCount(): Flow<Int>

    // ========== UPDATES ==========

    /** Update peer connection state */
    @Query("UPDATE peers SET status = :status, lastSeen = :lastSeen WHERE id = :peerId")
    suspend fun updateConnectionState(peerId: String, status: String, lastSeen: Long = System.currentTimeMillis())

    /** Update peer connection attempt */
    @Query("UPDATE peers SET lastAttempt = :timestamp WHERE id = :peerId")
    suspend fun updateLastAttempt(peerId: String, timestamp: Long = System.currentTimeMillis())

    /** Increment rejection count */
    @Query("UPDATE peers SET rejectionCount = rejectionCount + 1 WHERE id = :peerId")
    suspend fun incrementRejectionCount(peerId: String)

    /** Reset rejection count on successful connection */
    @Query("UPDATE peers SET rejectionCount = 0 WHERE id = :peerId")
    suspend fun resetRejectionCount(peerId: String)

    /** Mark all peers as disconnected (e.g., on app restart) */
    @Query("UPDATE peers SET status = 'DISCOVERED'")
    suspend fun resetAllToDiscovered()

    /** Verify a peer (via QR code) */
    @Query("UPDATE peers SET isVerified = 1 WHERE id = :peerId")
    suspend fun verifyPeer(peerId: String)

    // ========== DELETE ==========

    @Query("DELETE FROM peers WHERE id = :peerId")
    suspend fun deletePeer(peerId: String)

    /** Remove unverified peers (e.g. on app launch) so stale names from old sessions don't linger. */
    @Query("DELETE FROM peers WHERE isVerified = 0")
    suspend fun deleteUnverifiedPeers()

    @Query("DELETE FROM peers")
    suspend fun deleteAllPeers()
}
