package com.example.caraka.data.local.dao

import androidx.room.*
import com.example.caraka.data.local.entity.RelayedMessageEntity

@Dao
interface RelayDao {

    /** Insert a message ID into the relay cache (deduplication) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markAsRelayed(relay: RelayedMessageEntity)

    /** Check if a message has already been relayed */
    @Query("SELECT COUNT(*) > 0 FROM relayed_messages WHERE messageId = :messageId")
    suspend fun hasBeenRelayed(messageId: String): Boolean

    /** Clean up old relay records (older than 24 hours to save storage) */
    @Query("DELETE FROM relayed_messages WHERE relayedAt < :before")
    suspend fun cleanOldRelays(before: Long = System.currentTimeMillis() - 86_400_000)

    @Query("DELETE FROM relayed_messages")
    suspend fun deleteAll()
}
