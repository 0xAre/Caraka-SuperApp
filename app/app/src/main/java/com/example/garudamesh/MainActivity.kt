package com.example.garudamesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.garudamesh.ui.components.BottomNavBar
import com.example.garudamesh.ui.components.Screen
import com.example.garudamesh.ui.screens.HomeScreen
import com.example.garudamesh.ui.screens.ChatScreen
import com.example.garudamesh.ui.screens.MessagesScreen
import com.example.garudamesh.ui.screens.NetworkScreen
import com.example.garudamesh.ui.screens.SettingsScreen
import com.example.garudamesh.ui.screens.SosScreen
import com.example.garudamesh.ui.theme.GarudaMeshTheme
import com.example.garudamesh.viewmodel.MainViewModel
import com.example.garudamesh.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as GarudaMeshApp
        MainViewModelFactory(app.repository, app.identityManager, app.wifiDirectManager, app.connectivityMonitor)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GarudaMeshTheme {
                val hasIdentity by viewModel.hasIdentity.collectAsStateWithLifecycle()

                if (!hasIdentity) {
                    com.example.garudamesh.ui.screens.ProfileSetupScreen { name, role ->
                        viewModel.setupIdentity(name, role)
                    }
                } else {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val allGranted = permissions.values.all { it }
                        if (allGranted) {
                            viewModel.startWifiDirect()
                        }
                    }

                    LaunchedEffect(Unit) {
                        // Request permissions on launch
                        val permissions = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        val notGranted = permissions.filter {
                            ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                        }

                        if (notGranted.isEmpty()) {
                            viewModel.startWifiDirect()
                        } else {
                            permissionLauncher.launch(notGranted.toTypedArray())
                        }
                    }

                    val navController = rememberNavController()

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = { BottomNavBar(navController = navController) }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                        ) {
                            composable(Screen.Home.route) { HomeScreen(viewModel) }
                            composable(Screen.Messages.route) { 
                                MessagesScreen(viewModel) { peerId ->
                                    navController.navigate("chat/$peerId")
                                }
                            }
                            composable("chat/{peerId}") { backStackEntry ->
                                val peerId = backStackEntry.arguments?.getString("peerId") ?: return@composable
                                ChatScreen(viewModel, peerId = peerId) {
                                    navController.popBackStack()
                                }
                            }
                            composable(Screen.Network.route) { NetworkScreen(viewModel) }
                            composable(Screen.Sos.route) { SosScreen(viewModel) }
                            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopWifiDirect()
    }
}