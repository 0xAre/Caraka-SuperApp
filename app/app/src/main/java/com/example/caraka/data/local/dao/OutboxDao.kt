package com.example.caraka.data.local.dao

import androidx.room.*
import com.example.caraka.data.local.entity.OutboxEntity

/**
 * DAO for the DTN transport outbox (architecture baseline: D4/D7/D10).
 *
 * Introduced by EU-0.4 (schema only). Query/mutation methods are consumed starting in Phase 1
 * (outbox write EU-1.1, ACK→DELIVERED EU-1.3, bounded retry EU-1.4) and Phase 2 (timer flush
 * EU-2.1, carry EU-2.2, quota eviction EU-2.3). No behaviour is wired up by EU-0.4 itself.
 */
@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun getById(id: String): OutboxEntity?

    /** Pending units due for a (re)send attempt at or before [now]. */
    @Query("SELECT * FROM outbox WHERE state IN ('QUEUED','SENT') AND nextAttemptAt <= :now ORDER BY createdAt ASC")
    suspend fun getDue(now: Long): List<OutboxEntity>

    /** Pending units addressed to a specific peer (used when that peer becomes reachable). */
    @Query("SELECT * FROM outbox WHERE recipientId = :recipientId AND state IN ('QUEUED','SENT')")
    suspend fun getPendingForPeer(recipientId: String): List<OutboxEntity>

    @Query("UPDATE outbox SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: String)

    @Query("UPDATE outbox SET state = :state, attemptCount = :attemptCount, nextAttemptAt = :nextAttemptAt WHERE id = :id")
    suspend fun updateAttempt(id: String, state: String, attemptCount: Int, nextAttemptAt: Long)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(LENGTH(payloadJson)), 0) FROM outbox")
    suspend fun totalPayloadBytes(): Long

    /**
     * Eviction candidates ordered worst-first for quota enforcement (EU-2.3 / D7):
     * LOWEST priority first, then OLDEST, then MOST-replicated. The first rows are the safest to drop.
     */
    @Query(
        """
        SELECT * FROM outbox ORDER BY
          CASE priority WHEN 'EMERGENCY' THEN 3 WHEN 'HIGH' THEN 2 WHEN 'NORMAL' THEN 2 ELSE 1 END ASC,
          createdAt ASC,
          replicaCount DESC
        LIMIT :limit
        """
    )
    suspend fun evictionCandidates(limit: Int): List<OutboxEntity>

    /** Remove units past their coarse wall-clock expiry bound. */
    @Query("DELETE FROM outbox WHERE ttlExpiry > 0 AND ttlExpiry < :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM outbox")
    suspend fun deleteAll()
}
