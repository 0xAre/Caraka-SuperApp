package com.example.caraka.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.crypto.QrIdentityManager
import com.example.caraka.ui.components.CarakaBody
import com.example.caraka.ui.components.CarakaListTitle
import com.example.caraka.ui.components.CarakaScreenTitle
import com.example.caraka.ui.components.IdentityQrCard
import com.example.caraka.ui.components.LocalSnackbar
import com.example.caraka.ui.scanner.CarakaQrCaptureActivity
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MainViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrIdentityScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToChat: (peerId: String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val myRole by viewModel.myRole.collectAsStateWithLifecycle()
    val myPeerId by viewModel.myPeerId.collectAsStateWithLifecycle()

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrVisible by remember { mutableStateOf(false) }

    var scanError by remember { mutableStateOf<String?>(null) }
    val scanCancelledMsg = stringResource(R.string.qr_scan_cancelled)
    val scanInvalidMsg = stringResource(R.string.qr_scan_invalid)
    val scanPrompt = stringResource(R.string.qr_scan_prompt)
    val connectedToastTpl = stringResource(R.string.qr_connected_toast)

    LaunchedEffect(myPeerId, displayName, myRole) {
        if (myPeerId.isBlank()) return@LaunchedEffect
        val encPub = viewModel.getEncPubBase64()
        val signPub = viewModel.getSignPubBase64()
        val payload = QrIdentityManager.buildPayload(myPeerId, displayName, myRole, encPub, signPub)
        qrBitmap = withContext(Dispatchers.Default) {
            QrIdentityManager.generateQrBitmap(payload, sizePx = 480)
        }
        qrVisible = true
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val raw = result.contents
        if (raw == null) {
            scanError = scanCancelledMsg
            return@rememberLauncherForActivityResult
        }
        val parsed = QrIdentityManager.parseQrPayload(raw)
        if (parsed == null) {
            scanError = scanInvalidMsg
            return@rememberLauncherForActivityResult
        }
        // Scan QR in person = consent. Verify + connect + jump straight into chat — no extra taps.
        scanError = null
        scope.launch {
            viewModel.saveVerifiedPeer(parsed)               // trust + store their public keys
            viewModel.requestConnectionToPeer(parsed.peerId, autoAccept = true) // proactively link
            viewModel.triggerPriorityConnect(parsed.peerId)  // fast-track WiFi-Direct fallback path
            snackbar.tryEmit(String.format(connectedToastTpl, parsed.name))
            onNavigateToChat(parsed.peerId)                  // land in the conversation
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    CarakaScreenTitle(stringResource(R.string.qr_screen_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.help_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            IdentityQrCard(
                displayName = displayName,
                role = myRole,
                peerId = myPeerId,
                qrBitmap = qrBitmap,
                qrVisible = qrVisible
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LocalCarakaShapes.current.lg)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LocalCarakaShapes.current.lg)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    CarakaListTitle(stringResource(R.string.qr_scan_peer_title))
                }

                Spacer(Modifier.height(16.dp))
                CarakaBody(
                    stringResource(R.string.qr_scan_instructions),
                    muted = true,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    ScanHintChip(icon = Icons.Default.StayCurrentPortrait, text = stringResource(R.string.qr_hint_portrait))
                    ScanHintChip(icon = Icons.Default.CenterFocusStrong, text = stringResource(R.string.qr_hint_fill_box))
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        scanError = null
                        val options = ScanOptions().apply {
                            setCaptureActivity(CarakaQrCaptureActivity::class.java)
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt(scanPrompt)
                            setBeepEnabled(true)
                            setOrientationLocked(true)
                            setBarcodeImageEnabled(false)
                        }
                        scanLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = LocalCarakaShapes.current.md,
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_scan_now_btn), color = MaterialTheme.colorScheme.onPrimary, style = CarakaTextStyles.buttonLabel)
                }

                scanError?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(LocalCarakaShapes.current.sm)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), LocalCarakaShapes.current.sm)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error, style = CarakaTextStyles.bodyDefault)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScanHintChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .clip(LocalCarakaShapes.current.full)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), LocalCarakaShapes.current.full)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = CarakaTextStyles.statValueCompact
        )
    }
}
