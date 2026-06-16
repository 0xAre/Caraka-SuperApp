package com.example.caraka.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tugas kurir yang sedang aktif di sisi node B.
 *
 * Satu [CourierTaskEntity] merepresentasikan satu sesi "aku sedang membawa bundle X".
 * B bisa membawa beberapa task sekaligus (multi-bundle courier).
 *
 * Lifecycle [status]:
 *   ACTIVE → DELIVERED | CANCELLED | EXPIRED
 */
@Entity(tableName = "courier_task")
data class CourierTaskEntity(

    @PrimaryKey
    val taskId: String,                 // UUID task (bisa sama dengan bundleId)

    val bundleId: String,               // FK ke CourierBundleEntity.bundleId

    val acceptedAt: Long,               // kapan B menerima tugas ini

    val status: String = "ACTIVE",      // ACTIVE | DELIVERED | CANCELLED | EXPIRED

    /** Hint koordinat tujuan (opsional) — ditampilkan di UI B sebagai panduan arah. */
    val locationHintLat: Double? = null,
    val locationHintLon: Double? = null,

    val deliveredAt: Long? = null
)
