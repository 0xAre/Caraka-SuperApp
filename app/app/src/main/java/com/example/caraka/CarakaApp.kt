package com.example.caraka

import android.app.Application
import com.example.caraka.crypto.CryptoManager
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.data.local.CarakaDatabase
import com.example.caraka.network.ConnectivityMonitor
import com.example.caraka.network.MeshManager
import com.example.caraka.network.MeshTransport
import com.example.caraka.repository.MeshRepository
import net.sqlcipher.database.SQLiteDatabase

/**
 * Custom Application class for manual Dependency Injection.
 * We use this to initialize our database, managers, and repository globally.
 */
class CarakaApp : Application() {

    lateinit var database: CarakaDatabase
    lateinit var cryptoManager: CryptoManager
    lateinit var identityManager: IdentityManager
    lateinit var repository: MeshRepository
    lateinit var transport: MeshTransport
    lateinit var connectivityMonitor: ConnectivityMonitor

    override fun onCreate() {
        super.onCreate()

        // Load SQLCipher native libraries — MUST be called before any DB access
        SQLiteDatabase.loadLibs(this)

        // Initialize dependencies
        database = CarakaDatabase.getDatabase(this)
        cryptoManager = CryptoManager()
        identityManager = IdentityManager(this, cryptoManager)
        
        repository = MeshRepository(
            messageDao = database.messageDao(),
            peerDao = database.peerDao(),
            relayDao = database.relayDao(),
            outboxDao = database.outboxDao(),
            cryptoManager = cryptoManager,
            identityManager = identityManager
        )

        // Mesh facade: Wi-Fi Aware primary (when supported) + Wi-Fi Direct fallback/brain.
        transport = MeshManager(
            context = this,
            repository = repository,
            identityManager = identityManager,
            cryptoManager = cryptoManager
        )

        // Resolve circular dependency
        repository.transport = transport

        // Connectivity monitor — tracks internet vs mesh-only state
        connectivityMonitor = ConnectivityMonitor(this)
    }
}
