package com.example.caraka.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.caraka.crypto.DatabasePassphraseManager
import com.example.caraka.data.local.dao.MessageDao
import com.example.caraka.data.local.dao.PeerDao
import com.example.caraka.data.local.dao.RelayDao
import com.example.caraka.data.local.entity.MessageEntity
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.data.local.entity.RelayedMessageEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Main Room database for CARAKA — encrypted with SQLCipher.
 *
 * Tables:
 * - messages: All TEXT, SOS, and SYSTEM messages
 * - peers: Known nodes in the mesh network
 * - relayed_messages: Dedup cache to prevent relay loops
 *
 * Security:
 * - All data is encrypted at rest via SQLCipher (AES-256-CBC)
 * - The database passphrase is generated randomly on first launch,
 *   encrypted with an AES-256-GCM key stored in Android Keystore (TEE),
 *   and never stored in plaintext anywhere.
 * - Calling [secureWipe] destroys the passphrase and deletes the DB file,
 *   rendering the encrypted data permanently unreadable.
 */
@Database(
    entities = [
        MessageEntity::class,
        PeerEntity::class,
        RelayedMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class CarakaDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun peerDao(): PeerDao
    abstract fun relayDao(): RelayDao

    companion object {
        @Volatile
        private var INSTANCE: CarakaDatabase? = null

        private const val DB_NAME = "caraka_secure.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE peers ADD COLUMN connectionId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE peers ADD COLUMN status TEXT NOT NULL DEFAULT 'DISCOVERED'")
                database.execSQL("ALTER TABLE peers ADD COLUMN direction TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE peers ADD COLUMN lastAttempt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE peers ADD COLUMN rejectionCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE peers ADD COLUMN hopCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE peers ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): CarakaDatabase {
            return INSTANCE ?: synchronized(this) {
                android.util.Log.d("CarakaDB", "Initializing encrypted database with SQLCipher…")
                // Get (or create) the hardware-backed passphrase
                val passphrase = DatabasePassphraseManager.getOrCreatePassphrase(context)
                val factory = SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CarakaDatabase::class.java,
                    DB_NAME
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2)
                    .build()

                INSTANCE = instance
                android.util.Log.d("CarakaDB", "SQLCipher database ready ✓")
                instance
            }
        }

        /**
         * Secure Wipe — destroys all stored data and the encryption key.
         * After this call the database file is permanently unreadable.
         *
         * Called when user triggers "Emergency Wipe" from Settings.
         */
        fun secureWipe(context: Context) {
            // Close any open DB connection first
            INSTANCE?.close()
            INSTANCE = null

            // Wipe the Keystore key + stored encrypted passphrase
            DatabasePassphraseManager.wipePassphrase(context)

            // Delete the database files
            SQLiteDatabase.loadLibs(context)
            context.applicationContext.deleteDatabase(DB_NAME)
        }
    }
}
