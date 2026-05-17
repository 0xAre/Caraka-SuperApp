package com.example.garudamesh.data.local.dao

import androidx.room.*
import com.example.garudamesh.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // ========== INSERT ==========

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    // ========== QUERIES ==========

    /** Get all messages for a specific conversation (with a specific peer) */
    @Query("""
        SELECT * FROM messages 
        WHERE (senderId = :peerId AND recipientId != 'BROADCAST') 
           OR (recipientId = :peerId) 
        ORDER BY timestamp ASC
    """)
    fun getMessagesByPeer(peerId: String): Flow<List<MessageEntity>>

    /** Get all SOS broadcast messages, newest first */
    @Query("SELECT * FROM messages WHERE type = 'SOS' ORDER BY timestamp DESC")
    fun getSosMessages(): Flow<List<MessageEntity>>

    /** Get all broadcast messages (SOS + SYSTEM), newest first */
    @Query("SELECT * FROM messages WHERE recipientId = 'BROADCAST' ORDER BY timestamp DESC")
    fun getBroadcastMessages(): Flow<List<MessageEntity>>

    /** Get recent active alerts (SOS from last 24h) */
    @Query("SELECT * FROM messages WHERE type = 'SOS' AND timestamp > :since ORDER BY timestamp DESC")
    fun getRecentAlerts(since: Long = System.currentTimeMillis() - 86_400_000): Flow<List<MessageEntity>>

    /** Check if a message ID already exists (for dedup) */
    @Query("SELECT COUNT(*) > 0 FROM messages WHERE id = :messageId")
    suspend fun messageExists(messageId: String): Boolean

    /** Get unread message count for a peer */
    @Query("""
        SELECT COUNT(*) FROM messages 
        WHERE senderId = :peerId AND isIncoming = 1 AND isRead = 0
    """)
    fun getUnreadCount(peerId: String): Flow<Int>

    // ========== UPDATES ==========

    /** Mark messages from a peer as read */
    @Query("UPDATE messages SET isRead = 1 WHERE senderId = :peerId AND isIncoming = 1")
    suspend fun markAsRead(peerId: String)

    /** Increment flag count for a message */
    @Query("UPDATE messages SET flagCount = flagCount + 1 WHERE id = :messageId")
    suspend fun flagMessage(messageId: String)

    /** Mark message as relayed */
    @Query("UPDATE messages SET isRelayed = 1 WHERE id = :messageId")
    suspend fun markAsRelayed(messageId: String)

    // ========== DELETE ==========

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}
