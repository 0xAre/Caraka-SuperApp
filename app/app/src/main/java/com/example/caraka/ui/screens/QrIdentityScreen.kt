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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.crypto.QrIdentityManager
import com.example.caraka.ui.components.IdentityQrCard
import com.example.caraka.ui.components.LocalSnackbar
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
            scanError = "Scan dibatalkan"
            return@rememberLauncherForActivityResult
        }
        val parsed = QrIdentityManager.parseQrPayload(raw)
        if (parsed == null) {
            scanError = "QR tidak valid — bukan identitas CARAKA"
            return@rememberLauncherForActivityResult
        }
        // Scan QR in person = consent. Verify + connect + jump straight into chat — no extra taps.
        scanError = null
        scope.launch {
            viewModel.saveVerifiedPeer(parsed)               // trust + store their public keys
            viewModel.requestConnectionToPeer(parsed.peerId, autoAccept = true) // proactively link
            viewModel.triggerPriorityConnect(parsed.peerId)  // fast-track WiFi-Direct fallback path
            snackbar.tryEmit("Terhubung dengan ${parsed.name} ✓")
            onNavigateToChat(parsed.peerId)                  // land in the conversation
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Identitas QR",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground
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
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDark)
                    .border(1.dp, NeonMint.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = NeonMint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SCAN QR PEER", color = NeonMint, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Scan QR dari device lain untuk memverifikasi identitas mereka secara tatap muka",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        scanError = null
                        val options = ScanOptions().apply {
                            setPrompt("Arahkan kamera ke QR identitas CARAKA peer")
                            setBeepEnabled(true)
                            setOrientationLocked(false)
                            setBarcodeImageEnabled(false)
                        }
                        scanLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMint.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonMint.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = NeonMint)
                    Spacer(Modifier.width(8.dp))
                    Text("Buka Kamera & Scan", color = NeonMint, fontWeight = FontWeight.SemiBold)
                }

                scanError?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DangerRed.copy(alpha = 0.1f))
                            .border(1.dp, DangerRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = DangerRed, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
