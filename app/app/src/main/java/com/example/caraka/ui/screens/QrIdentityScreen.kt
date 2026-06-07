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
import com.example.caraka.ui.components.ConfirmTrustSheet
import com.example.caraka.ui.components.IdentityQrCard
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
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val myRole by viewModel.myRole.collectAsStateWithLifecycle()
    val myPeerId by viewModel.myPeerId.collectAsStateWithLifecycle()

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrVisible by remember { mutableStateOf(false) }

    var scannedPeer by remember { mutableStateOf<QrIdentityManager.QrIdentityPayload?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var saveSuccess by remember { mutableStateOf(false) }
    var showTrustSheet by remember { mutableStateOf(false) }

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
        } else {
            scannedPeer = parsed
            scanError = null
            saveSuccess = false
            showTrustSheet = true
        }
    }

    ConfirmTrustSheet(
        visible = showTrustSheet && !saveSuccess,
        peer = scannedPeer,
        onConfirm = {
            scannedPeer?.let { peer ->
                scope.launch {
                    viewModel.saveVerifiedPeer(peer)
                    // NEW: Send CONNECTION_REQUEST with autoAccept=true
                    // Peer will auto-accept because QR scan = in-person consent
                    viewModel.requestConnectionToPeer(peer.peerId, autoAccept = true)
                    // Trigger immediate WiFi Direct connect to this peer.
                    // Resets cooldown + starts discovery cycle so both devices
                    // connect within seconds of QR scan confirmation.
                    viewModel.triggerPriorityConnect(peer.peerId)
                    saveSuccess = true
                    showTrustSheet = false
                }
            }
        },
        onDismiss = { showTrustSheet = false }
    )

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
                        scannedPeer = null
                        scanError = null
                        saveSuccess = false
                        showTrustSheet = false
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

                scannedPeer?.let { peer ->
                    if (saveSuccess) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeonMint.copy(alpha = 0.15f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonMint, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${peer.name} disimpan sebagai verified peer ✓", color = NeonMint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    } else if (!showTrustSheet) {
                        Spacer(Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(NeonMint.copy(alpha = 0.07f))
                                .border(1.dp, NeonMint.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = NeonMint, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("IDENTITAS TERDETEKSI", color = NeonMint, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            PeerInfoRow("Nama", peer.name)
                            PeerInfoRow("Role", peer.role)
                            PeerInfoRow("Peer ID", peer.peerId.take(20) + "…")
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showTrustSheet = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonMint),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Simpan sebagai Verified Peer", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PeerInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(
            value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (label == "Peer ID") FontFamily.Monospace else FontFamily.Default
        )
    }
}
