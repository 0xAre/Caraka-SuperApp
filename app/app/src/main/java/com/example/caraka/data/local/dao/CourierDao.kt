package com.example.caraka.data.local.dao

import androidx.room.*
import com.example.caraka.data.local.entity.CourierBundleEntity
import com.example.caraka.data.local.entity.CourierTaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO untuk Caraka Courier Mode — mengelola [CourierBundleEntity] dan [CourierTaskEntity].
 *
 * Semantik berbeda dari OutboxDao:
 *  - Bundle kurir TIDAK di-rebroadcast secara epidemik.
 *  - Bundle hanya dikirim ke penerima yang membuktikan kepemilikan kunci (directed) atau
 *    rendezvous token (stealth).
 *  - Setelah delivered, bundle dihapus — tidak ada retry seperti outbox unicast.
 */
@Dao
interface CourierDao {

    // ── Bundle ───────────────────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBundle(bundle: CourierBundleEntity)

    @Query("SELECT * FROM courier_bundle WHERE bundleId = :bundleId")
    suspend fun getBundleById(bundleId: String): CourierBundleEntity?

    /** Semua bundle yang sedang dibawa (state = CARRYING). */
    @Query("SELECT * FROM courier_bundle WHERE state = 'CARRYING' ORDER BY priority DESC, createdAt ASC")
    suspend fun getCarryingBundles(): List<CourierBundleEntity>

    /** Flow dari semua bundle yang sedang dibawa — untuk UI reaktif. */
    @Query("SELECT * FROM courier_bundle WHERE state = 'CARRYING' ORDER BY priority DESC, createdAt ASC")
    fun getCarryingBundlesFlow(): Flow<List<CourierBundleEntity>>

    /** Bundle DIRECTED yang claimToken-nya cocok dengan peerId tertentu. */
    @Query("SELECT * FROM courier_bundle WHERE mode = 'DIRECTED' AND claimToken = :peerId AND state = 'CARRYING'")
    suspend fun getDirectedBundlesForPeer(peerId: String): List<CourierBundleEntity>

    /** Semua bundle STEALTH yang masih CARRYING — untuk broadcast token list di Caraka Mode. */
    @Query("SELECT * FROM courier_bundle WHERE mode = 'STEALTH' AND state = 'CARRYING'")
    suspend fun getStealthBundles(): List<CourierBundleEntity>

    /** Bundle STEALTH berdasarkan claimToken — dipakai saat Z klaim. */
    @Query("SELECT * FROM courier_bundle WHERE mode = 'STEALTH' AND claimToken = :claimToken AND state = 'CARRYING'")
    suspend fun getStealthBundleByToken(claimToken: String): CourierBundleEntity?

    @Query("UPDATE courier_bundle SET state = :state WHERE bundleId = :bundleId")
    suspend fun updateBundleState(bundleId: String, state: String)

    @Query("UPDATE courier_bundle SET state = 'DELIVERED', deliveredAt = :deliveredAt WHERE bundleId = :bundleId")
    suspend fun markDelivered(bundleId: String, deliveredAt: Long)

    /** Hapus bundle yang sudah expired. */
    @Query("DELETE FROM courier_bundle WHERE expiry > 0 AND expiry < :now")
    suspend fun deleteExpiredBundles(now: Long)

    @Query("DELETE FROM courier_bundle WHERE bundleId = :bundleId")
    suspend fun deleteBundleById(bundleId: String)

    /** Jumlah bundle yang sedang dibawa — untuk badge di HomeScreen. */
    @Query("SELECT COUNT(*) FROM courier_bundle WHERE state = 'CARRYING'")
    fun getActiveCarryCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM courier_bundle WHERE state = 'CARRYING'")
    suspend fun getActiveCarryCountOnce(): Int

    // ── Task ─────────────────────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: CourierTaskEntity)

    @Query("SELECT * FROM courier_task WHERE taskId = :taskId")
    suspend fun getTaskById(taskId: String): CourierTaskEntity?

    @Query("SELECT * FROM courier_task WHERE status = 'ACTIVE' ORDER BY acceptedAt ASC")
    fun getActiveTasks(): Flow<List<CourierTaskEntity>>

    @Query("SELECT * FROM courier_task WHERE bundleId = :bundleId")
    suspend fun getTaskByBundleId(bundleId: String): CourierTaskEntity?

    @Query("UPDATE courier_task SET status = :status, deliveredAt = :deliveredAt WHERE taskId = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: String, deliveredAt: Long?)

    @Query("DELETE FROM courier_task WHERE taskId = :taskId")
    suspend fun deleteTaskById(taskId: String)

    @Query("SELECT COUNT(*) FROM courier_task WHERE status = 'ACTIVE'")
    fun getActiveTaskCount(): Flow<Int>
}
