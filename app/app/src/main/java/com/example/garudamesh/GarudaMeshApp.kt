package com.example.garudamesh

import android.app.Application
import com.example.garudamesh.crypto.CryptoManager
import com.example.garudamesh.crypto.IdentityManager
import com.example.garudamesh.data.local.GarudaMeshDatabase
import com.example.garudamesh.network.ConnectivityMonitor
import com.example.garudamesh.network.WifiDirectManager
import com.example.garudamesh.repository.MeshRepository

/**
 * Custom Application class for manual Dependency Injection.
 * We use this to initialize our database, managers, and repository globally.
 */
class GarudaMeshApp : Application() {

    lateinit var database: GarudaMeshDatabase
    lateinit var cryptoManager: CryptoManager
    lateinit var identityManager: IdentityManager
    lateinit var repository: MeshRepository
    lateinit var wifiDirectManager: WifiDirectManager
    lateinit var connectivityMonitor: ConnectivityMonitor

    override fun onCreate() {
        super.onCreate()

        // Initialize dependencies
        database = GarudaMeshDatabase.getDatabase(this)
        cryptoManager = CryptoManager()
        identityManager = IdentityManager(this, cryptoManager)
        
        repository = MeshRepository(
            messageDao = database.messageDao(),
            peerDao = database.peerDao(),
            relayDao = database.relayDao(),
            cryptoManager = cryptoManager,
            identityManager = identityManager
        )

        wifiDirectManager = WifiDirectManager(
            context = this,
            repository = repository,
            identityManager = identityManager,
            cryptoManager = cryptoManager
        )

        // Resolve circular dependency
        repository.wifiDirectManager = wifiDirectManager

        // Connectivity monitor — tracks internet vs mesh-only state
        connectivityMonitor = ConnectivityMonitor(this)
    }
}
