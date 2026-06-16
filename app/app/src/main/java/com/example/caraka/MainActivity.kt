package com.example.caraka

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.caraka.service.MeshForegroundService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.caraka.ui.components.BottomNavBar
import com.example.caraka.ui.components.FloatingChatAlert
import com.example.caraka.ui.components.LocalSnackbar
import com.example.caraka.ui.components.OnboardingTourOverlay
import com.example.caraka.ui.components.Screen
import com.example.caraka.ui.dialogs.ConnectionRequestDialog
import com.example.caraka.ui.prefs.LocalUiPrefs
import com.example.caraka.ui.prefs.ProvideLocalizedContext
import com.example.caraka.ui.prefs.UiPreferences
import com.example.caraka.ui.prefs.UiPrefsState
import com.example.caraka.ui.screens.AlertsScreen
import com.example.caraka.ui.screens.ChatScreen
import com.example.caraka.ui.screens.CourierScreen
import com.example.caraka.ui.screens.CourierHistoryScreen
import com.example.caraka.ui.screens.HelpScreen
import com.example.caraka.ui.screens.HomeScreen
import com.example.caraka.ui.screens.MessagesScreen
import com.example.caraka.ui.screens.NetworkScreen
import com.example.caraka.ui.screens.ProfileSetupScreen
import com.example.caraka.ui.screens.QrIdentityScreen
import com.example.caraka.ui.screens.SettingsScreen
import com.example.caraka.ui.screens.SosScreen
import com.example.caraka.ui.theme.CarakaTheme
import com.example.caraka.viewmodel.CourierViewModel
import com.example.caraka.viewmodel.MainViewModel
import com.example.caraka.viewmodel.MainViewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as CarakaApp
        MainViewModelFactory(app.repository, app.identityManager, app.transport, app.connectivityMonitor)
    }

    private val courierViewModel: CourierViewModel by viewModels {
        val app = application as CarakaApp
        CourierViewModel.Factory(
            app.courierManager,
            app.courierRepository,
            app.repository,
            app.identityManager
        )
    }

    private lateinit var uiPrefs: UiPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        uiPrefs = UiPreferences(applicationContext)
        enableEdgeToEdge()
        setContent {
            CarakaRoot(
                viewModel = viewModel,
                courierViewModel = courierViewModel,
                uiPrefs = uiPrefs
            )
        }
    }

    fun startMeshService() {
        // Request battery optimization exemption dulu (kritis untuk HiOS/MIUI/XOS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    ))
                } catch (_: Exception) {}
            }
        }

        // Start foreground service — mesh akan tetap hidup di background dan saat layar mati
        val intent = MeshForegroundService.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // JANGAN stop service di sini — FGS sengaja dibiarkan jalan saat Activity hancur
        // agar mesh tetap hidup di background. Service hanya berhenti via:
        // 1. Tombol "Stop Mesh" di notifikasi persistent
        // 2. clearIdentity() dari user
    }
}

@Composable
private fun CarakaRoot(
    viewModel: MainViewModel,
    courierViewModel: CourierViewModel,
    uiPrefs: UiPreferences
) {
    val scope = rememberCoroutineScope()

    val language by uiPrefs.language.collectAsState(initial = "id")
    val bigText by uiPrefs.bigText.collectAsState(initial = false)
    val highContrast by uiPrefs.highContrast.collectAsState(initial = false)
    val haptics by uiPrefs.haptics.collectAsState(initial = true)
    val onboardingDone by uiPrefs.onboardingDone.collectAsState(initial = true)

    val prefsState = UiPrefsState(
        language = language,
        bigText = bigText,
        highContrast = highContrast,
        haptics = haptics,
        toggleLanguage = {
            scope.launch { uiPrefs.setLanguage(if (language == "id") "en" else "id") }
        },
        toggleBigText = { scope.launch { uiPrefs.setBigText(!bigText) } },
        toggleHighContrast = { scope.launch { uiPrefs.setHighContrast(!highContrast) } },
        toggleHaptics = { scope.launch { uiPrefs.setHaptics(!haptics) } }
    )

    val snackbarFlow = remember { MutableSharedFlow<String>(extraBufferCapacity = 8) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarFlow) {
        snackbarFlow.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    // Relay courier snackbar messages ke snackbar bus
    LaunchedEffect(courierViewModel) {
        courierViewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    CompositionLocalProvider(
        LocalUiPrefs provides prefsState,
        LocalSnackbar provides snackbarFlow
    ) {
        ProvideLocalizedContext(language = language) {
            CarakaTheme(highContrast = highContrast, bigText = bigText) {
                CarakaNav(
                    viewModel = viewModel,
                    courierViewModel = courierViewModel,
                    uiPrefs = uiPrefs,
                    snackbarHostState = snackbarHostState,
                    onboardingDoneFlag = onboardingDone,
                    onOnboardingDismissed = { scope.launch { uiPrefs.setOnboardingDone(true) } }
                )
            }
        }
    }
}

@Composable
private fun CarakaNav(
    viewModel: MainViewModel,
    courierViewModel: CourierViewModel,
    uiPrefs: UiPreferences,
    snackbarHostState: SnackbarHostState,
    onboardingDoneFlag: Boolean,
    onOnboardingDismissed: () -> Unit
) {
    val hasIdentity by viewModel.hasIdentity.collectAsStateWithLifecycle()

    if (!hasIdentity) {
        ProfileSetupScreen { name, role -> viewModel.setupIdentity(name, role) }
        return
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val activity = ctx as? MainActivity

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> activity?.startMeshService() }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Bluetooth runtime permissions (Android 12+) — required by Nearby Connections.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isEmpty()) activity?.startMeshService()
        else permissionLauncher.launch(notGranted.toTypedArray())
    }

    val navController = rememberNavController()

    var chatAlert by remember {
        mutableStateOf<com.example.caraka.network.ChatAlert?>(null)
    }
    LaunchedEffect(Unit) { viewModel.incomingChatAlert.collect { chatAlert = it } }
    LaunchedEffect(chatAlert) {
        if (chatAlert != null) {
            kotlinx.coroutines.delay(4000L)
            chatAlert = null
        }
    }

    var showTour by remember(onboardingDoneFlag) { mutableStateOf(!onboardingDoneFlag) }

    val sosAlerts by viewModel.activeAlerts.collectAsStateWithLifecycle(initialValue = emptyList())
    val lastMessagesPerPeer by viewModel.lastMessagesPerPeer.collectAsStateWithLifecycle(initialValue = emptyMap())
    val lastReadMap by uiPrefs.observeLastReadMap().collectAsStateWithLifecycle(initialValue = emptyMap())

    val incomingConnectionRequest by viewModel.incomingConnectionRequest.collectAsStateWithLifecycle()
    val allPeers by viewModel.allPeers.collectAsStateWithLifecycle()

    val messagesUnreadCount = remember(lastMessagesPerPeer, lastReadMap) {
        lastMessagesPerPeer.count { (peerId, msg) ->
            msg.isIncoming && msg.timestamp > (lastReadMap[peerId] ?: 0L)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Routes that show the global bottom nav bar.
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Messages.route,
        Screen.Network.route,
        Screen.Sos.route,
        Screen.Settings.route,
        Screen.Alerts.route,
        Screen.Courier.route
    )

    // The chat screen owns its own bottom inset (IME + nav bar) via a sticky composer,
    // so the global BottomNavBar is hidden there and the chat content extends to the
    // true screen bottom (bottom padding = 0). See StickyComposer / ChatScreen.
    val isChatRoute = currentRoute == Screen.CHAT_ROUTE_PATTERN

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (showBottomBar) {
                    BottomNavBar(
                        navController = navController,
                        sosBadgeCount = sosAlerts.size,
                        messagesBadgeCount = messagesUnreadCount
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            val contentPadding = if (isChatRoute) {
                PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = 0.dp
                )
            } else {
                innerPadding
            }
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
            ) {
                composable(Screen.Home.route) {
                    val courierCarryCount by courierViewModel.activeCarryCount.collectAsState()
                    HomeScreen(
                        viewModel = viewModel,
                        courierCarryCount = courierCarryCount,
                        onNavigateToSos = { navController.navigate(Screen.Sos.route) },
                        onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) },
                        onNavigateToMessages = { navController.navigate(Screen.Messages.route) },
                        onNavigateToNetwork = { navController.navigate(Screen.Network.route) },
                        onNavigateToProfile = { navController.navigate(Screen.Settings.route) },
                        onNavigateToQr = { navController.navigate(Screen.QrIdentity.route) },
                        onNavigateToHelp = { navController.navigate(Screen.Help.route) },
                        onNavigateToCourier = { navController.navigate(Screen.Courier.route) }
                    )
                }
                composable(Screen.Messages.route) {
                    MessagesScreen(
                        viewModel = viewModel,
                        uiPrefs = uiPrefs,
                        onNavigateToChat = { peerId ->
                            navController.navigate(Screen.chatRoute(peerId))
                        },
                        onNavigateToNetwork = { navController.navigate(Screen.Network.route) }
                    )
                }
                composable(
                    route = Screen.CHAT_ROUTE_PATTERN,
                    arguments = listOf(navArgument("peerId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val peerId = backStackEntry.arguments?.getString("peerId") ?: return@composable
                    ChatScreen(
                        viewModel = viewModel,
                        peerId = peerId,
                        uiPrefs = uiPrefs,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Network.route) {
                    NetworkScreen(
                        viewModel = viewModel,
                        onRequestPermissions = {
                            val permissions = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                            }
                            val missingPermissions = permissions.filter {
                                ContextCompat.checkSelfPermission(ctx, it) !=
                                    PackageManager.PERMISSION_GRANTED
                            }
                            if (missingPermissions.isEmpty()) {
                                viewModel.startPeerScan()
                            } else {
                                permissionLauncher.launch(missingPermissions.toTypedArray())
                            }
                        },
                        onOpenWifiSettings = {
                            ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        }
                    )
                }
                composable(Screen.Sos.route) {
                    SosScreen(viewModel, onBack = { navController.popBackStack() })
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel,
                        onOpenHelp = { navController.navigate(Screen.Help.route) },
                        onOpenQr = { navController.navigate(Screen.QrIdentity.route) }
                    )
                }
                composable(Screen.Help.route) {
                    HelpScreen(
                        onBack = { navController.popBackStack() },
                        onLaunchTour = { showTour = true }
                    )
                }
                composable(Screen.QrIdentity.route) {
                    QrIdentityScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToChat = { peerId ->
                            // Replace the QR screen on the back stack so "back" from chat
                            // returns to Settings, not the camera scanner.
                            navController.navigate(Screen.chatRoute(peerId)) {
                                popUpTo(Screen.QrIdentity.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.Alerts.route) {
                    AlertsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                // ── Caraka Courier Mode ──────────────────────────────────────────────
                composable(Screen.Courier.route) {
                    CourierScreen(
                        viewModel = courierViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToHistory = { navController.navigate(Screen.CourierHistory.route) }
                    )
                }
                composable(Screen.CourierHistory.route) {
                    CourierHistoryScreen(
                        viewModel = courierViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        FloatingChatAlert(
            alert = chatAlert,
            onClick = { alert ->
                chatAlert = null
                navController.navigate(Screen.chatRoute(alert.senderId))
            },
            onDismiss = { chatAlert = null },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
        )

        OnboardingTourOverlay(
            visible = showTour,
            onDismiss = {
                showTour = false
                onOnboardingDismissed()
            }
        )

        val requestPeerId = incomingConnectionRequest
        if (requestPeerId != null) {
            val requestingPeer = allPeers.find { it.id == requestPeerId }
            ConnectionRequestDialog(
                peerId = requestPeerId,
                peerName = requestingPeer?.displayName ?: "Perangkat Tidak Dikenal",
                peerRole = requestingPeer?.role ?: "CIVILIAN",
                onAccept = { viewModel.acceptConnectionRequest(it) },
                onReject = { viewModel.rejectConnectionRequest(it) },
                onDismiss = { viewModel.dismissConnectionRequestDialog() }
            )
        }
    }
}
