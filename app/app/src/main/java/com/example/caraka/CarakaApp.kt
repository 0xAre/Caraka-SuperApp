package com.example.caraka

import android.app.Application
import com.example.caraka.crypto.CryptoManager
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.data.local.CarakaDatabase
import com.example.caraka.network.ConnectivityMonitor
import com.example.caraka.network.WifiDirectManager
import com.example.caraka.repository.MeshRepository

/**
 * Custom Application class for manual Dependency Injection.
 * We use this to initialize our database, managers, and repository globally.
 */
class CarakaApp : Application() {

    lateinit var database: CarakaDatabase
    lateinit var cryptoManager: CryptoManager
    lateinit var identityManager: IdentityManager
    lateinit var repository: MeshRepository
    lateinit var wifiDirectManager: WifiDirectManager
    lateinit var connectivityMonitor: ConnectivityMonitor

    override fun onCreate() {
        super.onCreate()

        // Initialize dependencies
        database = CarakaDatabase.getDatabase(this)
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
