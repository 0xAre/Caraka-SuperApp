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
import androidx.compose.foundation.layout.Box
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.example.caraka.ui.screens.HelpScreen
import com.example.caraka.ui.screens.HomeScreen
import com.example.caraka.ui.screens.MessagesScreen
import com.example.caraka.ui.screens.NetworkScreen
import com.example.caraka.ui.screens.ProfileSetupScreen
import com.example.caraka.ui.screens.QrIdentityScreen
import com.example.caraka.ui.screens.SettingsScreen
import com.example.caraka.ui.screens.SosScreen
import com.example.caraka.ui.theme.CarakaTheme
import com.example.caraka.viewmodel.MainViewModel
import com.example.caraka.viewmodel.MainViewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as CarakaApp
        MainViewModelFactory(app.repository, app.identityManager, app.transport, app.connectivityMonitor)
    }

    private lateinit var uiPrefs: UiPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiPrefs = UiPreferences(applicationContext)
        enableEdgeToEdge()
        setContent {
            CarakaRoot(viewModel = viewModel, uiPrefs = uiPrefs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopWifiDirect()
    }
}

@Composable
private fun CarakaRoot(viewModel: MainViewModel, uiPrefs: UiPreferences) {
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

    CompositionLocalProvider(
        LocalUiPrefs provides prefsState,
        LocalSnackbar provides snackbarFlow
    ) {
        ProvideLocalizedContext(language = language) {
            CarakaTheme(highContrast = highContrast, bigText = bigText) {
                CarakaNav(
                    viewModel = viewModel,
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> viewModel.startWifiDirect() }

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
        if (notGranted.isEmpty()) viewModel.startWifiDirect()
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                BottomNavBar(
                    navController = navController,
                    sosBadgeCount = sosAlerts.size,
                    messagesBadgeCount = messagesUnreadCount
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToSos = { navController.navigate(Screen.Sos.route) },
                        onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) }
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
                composable(Screen.Network.route) { NetworkScreen(viewModel) }
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
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Alerts.route) {
                    AlertsScreen(
                        viewModel = viewModel,
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
