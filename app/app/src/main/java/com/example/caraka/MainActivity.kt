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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.caraka.ui.components.BottomNavBar
import com.example.caraka.ui.components.FloatingChatAlert
import com.example.caraka.ui.components.LocalSnackbar
import com.example.caraka.ui.components.OnboardingTourOverlay
import com.example.caraka.ui.components.Screen
import com.example.caraka.ui.prefs.LocalUiPrefs
import com.example.caraka.ui.prefs.ProvideLocalizedContext
import com.example.caraka.ui.prefs.UiPreferences
import com.example.caraka.ui.prefs.UiPrefsState
import com.example.caraka.ui.screens.ChatScreen
import com.example.caraka.ui.screens.HelpScreen
import com.example.caraka.ui.screens.HomeScreen
import com.example.caraka.ui.screens.MessagesScreen
import com.example.caraka.ui.screens.NetworkScreen
import com.example.caraka.ui.screens.ProfileSetupScreen
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
        MainViewModelFactory(app.repository, app.identityManager, app.wifiDirectManager, app.connectivityMonitor)
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
    val onboardingDone by uiPrefs.onboardingDone.collectAsState(initial = true) // assume true until DataStore resolves

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

    // Permission flow (unchanged)
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
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isEmpty()) viewModel.startWifiDirect()
        else permissionLauncher.launch(notGranted.toTypedArray())
    }

    val navController = rememberNavController()

    // Floating in-app chat alert
    var chatAlert by remember {
        androidx.compose.runtime.mutableStateOf<com.example.caraka.network.ChatAlert?>(null)
    }
    LaunchedEffect(Unit) { viewModel.incomingChatAlert.collect { chatAlert = it } }
    LaunchedEffect(chatAlert) {
        if (chatAlert != null) {
            kotlinx.coroutines.delay(4000L)
            chatAlert = null
        }
    }

    // Onboarding overlay
    var showTour by remember(onboardingDoneFlag) { mutableStateOf(!onboardingDoneFlag) }

    // Active SOS alerts → badge on the SOS nav tab
    val sosAlerts by viewModel.activeAlerts.collectAsStateWithLifecycle(initialValue = emptyList())

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = { BottomNavBar(navController = navController, sosBadgeCount = sosAlerts.size) },
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
                    HomeScreen(viewModel, onNavigateToSos = { navController.navigate(Screen.Sos.route) })
                }
                composable(Screen.Messages.route) {
                    MessagesScreen(viewModel) { peerId -> navController.navigate("chat/$peerId") }
                }
                composable("chat/{peerId}") { backStackEntry ->
                    val peerId = backStackEntry.arguments?.getString("peerId") ?: return@composable
                    ChatScreen(viewModel, peerId = peerId) { navController.popBackStack() }
                }
                composable(Screen.Network.route) { NetworkScreen(viewModel) }
                composable(Screen.Sos.route) { SosScreen(viewModel, onBack = { navController.popBackStack() }) }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel, onOpenHelp = { navController.navigate("help") })
                }
                composable("help") {
                    HelpScreen(
                        onBack = { navController.popBackStack() },
                        onLaunchTour = { showTour = true }
                    )
                }
            }
        }

        FloatingChatAlert(
            alert = chatAlert,
            onClick = { alert ->
                chatAlert = null
                navController.navigate("chat/${alert.senderId}")
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
    }
}
