package com.example.caraka.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.caraka.crypto.DatabasePassphraseManager
import com.example.caraka.data.local.dao.MessageDao
import com.example.caraka.data.local.dao.OutboxDao
import com.example.caraka.data.local.dao.PeerDao
import com.example.caraka.data.local.dao.RelayDao
import com.example.caraka.data.local.entity.MessageEntity
import com.example.caraka.data.local.entity.OutboxEntity
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
        RelayedMessageEntity::class,
        OutboxEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class CarakaDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun peerDao(): PeerDao
    abstract fun relayDao(): RelayDao
    abstract fun outboxDao(): OutboxDao

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

        /**
         * v2 → v3 (EU-0.4): additive only — no data loss.
         *  - messages.deliveryStatus: outgoing-message lifecycle status (default 'SENT' for existing
         *    rows; matches MessageEntity @ColumnInfo(defaultValue = "SENT") so Room validation passes).
         *  - outbox: new DTN transport-unit table (separate from the chat-log, per blocker B4).
         *    Columns are NOT NULL with no SQL defaults, matching the OutboxEntity schema Room expects
         *    (Kotlin-level defaults are runtime-only and are not part of the DB schema).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN deliveryStatus TEXT NOT NULL DEFAULT 'SENT'")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS outbox (
                        id TEXT NOT NULL,
                        recipientId TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        state TEXT NOT NULL,
                        priority TEXT NOT NULL,
                        attemptCount INTEGER NOT NULL,
                        nextAttemptAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        ttlExpiry INTEGER NOT NULL,
                        replicaCount INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): CarakaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): CarakaDatabase {
            android.util.Log.d("CarakaDB", "Initializing encrypted database with SQLCipher…")
            // loadLibs is a no-op on modern SQLCipher 4.x but ensures native library is
            // available before SupportFactory is constructed on older/custom ROMs.
            SQLiteDatabase.loadLibs(context)
            val passphrase = DatabasePassphraseManager.getOrCreatePassphrase(context)
            val factory = SupportFactory(passphrase)
            return try {
                buildRoomDb(context, factory).also {
                    android.util.Log.d("CarakaDB", "SQLCipher database ready ✓")
                }
            } catch (e: Exception) {
                // The DB file exists but cannot be opened with the current passphrase.
                // This happens when: (a) the DB was created without encryption in an earlier
                // install, (b) the Keystore key was regenerated after a device reset, or
                // (c) the passphrase blob was corrupted.  Delete and recreate so the app
                // can start; existing messages are lost but the app stays functional.
                android.util.Log.e("CarakaDB", "Cannot open encrypted DB — deleting and recreating", e)
                context.applicationContext.deleteDatabase(DB_NAME)
                buildRoomDb(context, factory).also {
                    android.util.Log.d("CarakaDB", "SQLCipher database recreated after open failure ✓")
                }
            }
        }

        private fun buildRoomDb(context: Context, factory: SupportFactory): CarakaDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CarakaDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

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
