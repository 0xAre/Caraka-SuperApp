package com.example.caraka.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.caraka.data.local.dao.MessageDao
import com.example.caraka.data.local.dao.PeerDao
import com.example.caraka.data.local.dao.RelayDao
import com.example.caraka.data.local.entity.MessageEntity
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.data.local.entity.RelayedMessageEntity

/**
 * Main Room database for Garuda Mesh.
 *
 * Tables:
 * - messages: All TEXT, SOS, and SYSTEM messages
 * - peers: Known nodes in the mesh network
 * - relayed_messages: Dedup cache to prevent relay loops
 *
 * Note: In production, this will use SQLCipher for encrypted storage.
 * For now we use standard Room for development simplicity.
 */
@Database(
    entities = [
        MessageEntity::class,
        PeerEntity::class,
        RelayedMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CarakaDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun peerDao(): PeerDao
    abstract fun relayDao(): RelayDao

    companion object {
        @Volatile
        private var INSTANCE: CarakaDatabase? = null

        fun getDatabase(context: Context): CarakaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CarakaDatabase::class.java,
                    "garuda_mesh_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
