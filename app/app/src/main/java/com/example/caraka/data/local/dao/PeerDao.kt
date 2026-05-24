package com.example.caraka.data.local.dao

import androidx.room.*
import com.example.caraka.data.local.entity.PeerEntity
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

    /** Get currently connected peers */
    @Query("SELECT * FROM peers WHERE isConnected = 1 ORDER BY lastSeen DESC")
    fun getConnectedPeers(): Flow<List<PeerEntity>>

    /** Get a specific peer by ID */
    @Query("SELECT * FROM peers WHERE id = :peerId LIMIT 1")
    suspend fun getPeerById(peerId: String): PeerEntity?

    /** Get authority peers (BPBD, Polri, PMI) */
    @Query("SELECT * FROM peers WHERE isAuthority = 1")
    fun getAuthorityPeers(): Flow<List<PeerEntity>>

    /** Get connected peer count */
    @Query("SELECT COUNT(*) FROM peers WHERE isConnected = 1")
    fun getConnectedPeerCount(): Flow<Int>

    // ========== UPDATES ==========

    /** Update peer connection status */
    @Query("UPDATE peers SET isConnected = :connected, lastSeen = :lastSeen WHERE id = :peerId")
    suspend fun updateConnectionStatus(peerId: String, connected: Boolean, lastSeen: Long = System.currentTimeMillis())

    /** Mark all peers as disconnected (e.g., on app restart) */
    @Query("UPDATE peers SET isConnected = 0")
    suspend fun disconnectAll()

    /** Verify a peer (via QR code) */
    @Query("UPDATE peers SET isVerified = 1 WHERE id = :peerId")
    suspend fun verifyPeer(peerId: String)

    // ========== DELETE ==========

    @Query("DELETE FROM peers WHERE id = :peerId")
    suspend fun deletePeer(peerId: String)

    @Query("DELETE FROM peers")
    suspend fun deleteAllPeers()
}
