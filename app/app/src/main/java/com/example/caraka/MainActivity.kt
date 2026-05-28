package com.example.caraka

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.caraka.ui.components.BottomNavBar
import com.example.caraka.ui.components.Screen
import com.example.caraka.ui.screens.HomeScreen
import com.example.caraka.ui.screens.ChatScreen
import com.example.caraka.ui.screens.MessagesScreen
import com.example.caraka.ui.screens.NetworkScreen
import com.example.caraka.ui.screens.SettingsScreen
import com.example.caraka.ui.screens.SosScreen
import com.example.caraka.ui.theme.CarakaTheme
import com.example.caraka.viewmodel.MainViewModel
import com.example.caraka.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as CarakaApp
        MainViewModelFactory(app.repository, app.identityManager, app.wifiDirectManager, app.connectivityMonitor)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarakaTheme {
                val hasIdentity by viewModel.hasIdentity.collectAsStateWithLifecycle()

                if (!hasIdentity) {
                    com.example.caraka.ui.screens.ProfileSetupScreen { name, role ->
                        viewModel.setupIdentity(name, role)
                    }
                } else {
                    // Always start WiFi Direct — per-operation SecurityExceptions are handled inside
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { _ ->
                        viewModel.startWifiDirect()
                    }

                    LaunchedEffect(Unit) {
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

                    // Floating in-app chat alert (heads-up style)
                    var chatAlert by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf<com.example.caraka.network.ChatAlert?>(null)
                    }
                    LaunchedEffect(Unit) {
                        viewModel.incomingChatAlert.collect { chatAlert = it }
                    }
                    LaunchedEffect(chatAlert) {
                        if (chatAlert != null) {
                            kotlinx.coroutines.delay(4000L)
                            chatAlert = null
                        }
                    }

                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
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
                                composable(Screen.Home.route) { HomeScreen(viewModel, onNavigateToSos = { navController.navigate(Screen.Sos.route) }) }
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
                                composable(Screen.Sos.route) { SosScreen(viewModel, onBack = { navController.popBackStack() }) }
                                composable(Screen.Settings.route) { SettingsScreen(viewModel) }
                            }
                        }

                        com.example.caraka.ui.components.FloatingChatAlert(
                            alert = chatAlert,
                            onClick = { alert ->
                                chatAlert = null
                                navController.navigate("chat/${alert.senderId}")
                            },
                            onDismiss = { chatAlert = null },
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.TopCenter)
                                .statusBarsPadding()
                        )
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