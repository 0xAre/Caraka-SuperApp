@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.caraka.ui.courier

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.ui.components.CarakaBody
import com.example.caraka.ui.components.CarakaCard
import com.example.caraka.ui.components.CarakaListTitle
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.LocalStatusColors
import androidx.compose.ui.res.stringResource
import com.example.caraka.R
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import com.example.caraka.crypto.QrIdentityManager
import com.example.caraka.ui.scanner.CarakaQrCaptureActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════════
//  CourierSendSheet — A membuka sheet untuk memilih kurir dan kirim pesan
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CourierSendSheet(
    peers: List<PeerEntity>,
    onSend: (courierId: String, recipientId: String, message: String, mode: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedCourierId by remember { mutableStateOf("") }
    var recipientId by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("DIRECTED") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.DirectionsBike,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.courier_fab_send), style = CarakaTextStyles.dialogTitle)
                    CarakaBody(stringResource(R.string.courier_send_subtitle), muted = true)
                }
            }

            // Mode Selector
            Text(stringResource(R.string.courier_send_mode_label), style = CarakaTextStyles.fieldLabel)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeChip(
                    label = stringResource(R.string.courier_mode_directed),
                    desc = stringResource(R.string.courier_mode_directed_desc),
                    selected = mode == "DIRECTED",
                    onClick = { mode = "DIRECTED" },
                    modifier = Modifier.weight(1f)
                )
                ModeChip(
                    label = stringResource(R.string.courier_mode_stealth),
                    desc = stringResource(R.string.courier_mode_stealth_desc),
                    selected = mode == "STEALTH",
                    onClick = { mode = "STEALTH" },
                    modifier = Modifier.weight(1f)
                )
            }

            // Pilih kurir
            Text(stringResource(R.string.courier_send_pick_courier), style = CarakaTextStyles.fieldLabel)
            if (peers.isEmpty()) {
                CarakaCard(modifier = Modifier.fillMaxWidth()) {
                    CarakaBody(
                        stringResource(R.string.courier_no_peers),
                        muted = true,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(peers) { peer ->
                        PeerPickerItem(
                            peer = peer,
                            selected = selectedCourierId == peer.id,
                            onClick = { selectedCourierId = peer.id }
                        )
                    }
                }
            }

            // Recipient ID (Directed: Z peerId)
            if (mode == "DIRECTED") {
                OutlinedTextField(
                    value = recipientId,
                    onValueChange = { recipientId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.courier_recipient_label)) },
                    placeholder = { Text(stringResource(R.string.courier_recipient_placeholder)) },
                    leadingIcon = {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                    },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )
            } else {
                // Stealth: Z akan klaim berdasarkan token
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LockPerson,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        CarakaBody(
                            stringResource(R.string.courier_stealth_recipient_note),
                            muted = true
                        )
                    }
                }
            }

            // Pesan
            OutlinedTextField(
                value = message,
                onValueChange = { if (it.length <= 512) message = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                label = { Text(stringResource(R.string.courier_message_label)) },
                placeholder = { Text(stringResource(R.string.courier_message_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Default.Message, contentDescription = null)
                },
                shape = MaterialTheme.shapes.medium,
                maxLines = 4,
                supportingText = {
                    Text("${message.length}/512")
                }
            )

            // Tombol kirim
            val canSend = selectedCourierId.isNotBlank() && message.isNotBlank() &&
                    (mode == "STEALTH" || recipientId.isNotBlank())

            Button(
                onClick = {
                    onSend(
                        selectedCourierId,
                        if (mode == "DIRECTED") recipientId else "STEALTH_CLAIM",
                        message,
                        mode
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSend,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.DirectionsBike, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.courier_assign_btn),
                    style = CarakaTextStyles.buttonLabel
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surface

    Surface(
        modifier = modifier
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PeerPickerItem(
    peer: PeerEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (selected) Icons.Default.Check else Icons.Default.AccountCircle,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    peer.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    peer.role,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CourierOfferDialog — B melihat penawaran kurir dari A
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CourierOfferDialog(
    fromPeerName: String,
    mode: String,
    expiryMs: Long,
    locationHintLat: Double?,
    locationHintLon: Double?,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val expiryStr = remember(expiryMs) {
        SimpleDateFormat("dd MMM HH:mm", Locale("id")).format(Date(expiryMs))
    }
    val modeColor = if (mode == "STEALTH") LocalStatusColors.current.stealth else MaterialTheme.colorScheme.primary
    val modeLabel = if (mode == "STEALTH") stringResource(R.string.courier_mode_stealth) else stringResource(R.string.courier_mode_directed)

    AlertDialog(
        onDismissRequest = { /* tidak bisa dismiss, harus pilih */ },
        icon = {
            Surface(
                shape = CircleShape,
                color = modeColor.copy(alpha = 0.14f),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.DirectionsBike,
                    contentDescription = null,
                    tint = modeColor,
                    modifier = Modifier.padding(14.dp)
                )
            }
        },
        title = {
            Text(
                stringResource(R.string.courier_offer_title),
                style = CarakaTextStyles.dialogTitle,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CarakaBody(
                    stringResource(R.string.courier_offer_intro_format, fromPeerName),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Mode badge
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = modeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        modeLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = modeColor
                    )
                }

                InfoRow(
                    icon = Icons.Default.Shield,
                    label = stringResource(R.string.courier_offer_content_label),
                    value = stringResource(R.string.courier_offer_content_value)
                )
                InfoRow(
                    icon = Icons.Default.Key,
                    label = stringResource(R.string.courier_offer_deadline_label),
                    value = expiryStr,
                    valueColor = if (expiryMs - System.currentTimeMillis() < 3_600_000L)
                        MaterialTheme.colorScheme.error
                    else null
                )
                if (locationHintLat != null && locationHintLon != null) {
                    InfoRow(
                        icon = Icons.Default.Map,
                        label = stringResource(R.string.courier_offer_location_label),
                        value = "%.4f, %.4f".format(locationHintLat, locationHintLon)
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CarakaBody(
                        stringResource(R.string.courier_offer_warning),
                        modifier = Modifier.padding(10.dp),
                        muted = false
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.courier_accept))
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onReject) {
                Text(stringResource(R.string.courier_reject))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color? = null
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).padding(top = 2.dp)
        )
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  StealthBroadcastDialog — Z melihat token broadcast dari B
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun StealthBroadcastDialog(
    fromPeerId: String,
    tokenList: List<String>,
    onClaim: (token: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedToken by remember { mutableStateOf<String?>(null) }
    val stealth = LocalStatusColors.current.stealth
    val onStealth = LocalStatusColors.current.onStealth

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = stealth.copy(alpha = 0.14f),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.LockPerson,
                    contentDescription = null,
                    tint = stealth,
                    modifier = Modifier.padding(14.dp)
                )
            }
        },
        title = {
            Text(stringResource(R.string.courier_stealth_broadcast_title), style = CarakaTextStyles.dialogTitle, textAlign = TextAlign.Center)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CarakaBody(
                    stringResource(R.string.courier_stealth_broadcast_intro_format, tokenList.size),
                    muted = true
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(tokenList) { token ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedToken = if (selectedToken == token) null else token },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selectedToken == token)
                                stealth.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (selectedToken == token) Icons.Default.CheckCircle
                                    else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (selectedToken == token) stealth
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    token.take(16) + "...",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedToken?.let { onClaim(it) } },
                enabled = selectedToken != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = stealth,
                    contentColor = onStealth
                )
            ) {
                Text(stringResource(R.string.courier_claim_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.courier_not_mine)) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}

/**
 * Launcher reusable untuk memindai QR kredensial Stealth.
 * Mengembalikan lambda untuk memulai scan; hasilnya mem-feed [onScanned] (epkPriv, nonce).
 */
@Composable
private fun rememberStealthScanLauncher(onScanned: (epkPriv: String, nonce: String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { raw ->
            QrIdentityManager.parseStealthCredentialPayload(raw)?.let { onScanned(it.epkPriv, it.nonce) }
        }
    }
    return {
        launcher.launch(
            ScanOptions().apply {
                setCaptureActivity(CarakaQrCaptureActivity::class.java)
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setOrientationLocked(true)
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  StealthChallengeDialog — Z merespons challenge dari B
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun StealthChallengeDialog(
    bundleId: String,
    fromPeerId: String,
    challengeNonce: String,
    onRespond: (epkPrivB64: String) -> Unit,
    onDismiss: () -> Unit
) {
    var epkPriv by remember { mutableStateOf("") }
    var showPrivKey by remember { mutableStateOf(false) }
    val launchScan = rememberStealthScanLauncher { epk, _ -> epkPriv = epk }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        },
        title = {
            Text(stringResource(R.string.courier_challenge_title), style = CarakaTextStyles.dialogTitle, textAlign = TextAlign.Center)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CarakaBody(
                    stringResource(R.string.courier_challenge_desc),
                    muted = true
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(stringResource(R.string.courier_bundle_id), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            bundleId.take(16) + "...",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                OutlinedTextField(
                    value = epkPriv,
                    onValueChange = { epkPriv = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.courier_epk_label)) },
                    placeholder = { Text(stringResource(R.string.courier_epk_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPrivKey = !showPrivKey }) {
                            Icon(
                                if (showPrivKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (showPrivKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    shape = MaterialTheme.shapes.medium,
                    maxLines = 3
                )
                TextButton(
                    onClick = launchScan,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.courier_scan_qr))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRespond(epkPriv) },
                enabled = epkPriv.isNotBlank()
            ) {
                Text(stringResource(R.string.courier_verify_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.courier_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  DeliveryReceivedSheet — Z mendapat payload
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun DeliveryReceivedSheet(
    bundleId: String,
    mode: String,
    encPayload: String,
    encNonce: String,
    senderPub: String?,
    fromPeerId: String,
    onDecryptDirected: (enc: String, nonce: String, senderPub: String?, onResult: (String?) -> Unit) -> Unit,
    onDecryptStealth: (enc: String, nonce: String, epkPriv: String) -> String?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var decryptedText by remember { mutableStateOf<String?>(null) }
    var epkPriv by remember { mutableStateOf("") }
    var showPrivKey by remember { mutableStateOf(false) }
    var decryptError by remember { mutableStateOf(false) }
    var isDecrypting by remember { mutableStateOf(false) }
    val launchScan = rememberStealthScanLauncher { epk, _ -> epkPriv = epk }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Message,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.courier_delivery_title), style = CarakaTextStyles.dialogTitle)
                    CarakaBody(
                        stringResource(R.string.courier_delivery_from_format, mode, fromPeerId.take(8)),
                        muted = true
                    )
                }
            }

            if (decryptedText == null) {
                // Stealth perlu EPK_priv
                if (mode == "STEALTH") {
                    OutlinedTextField(
                        value = epkPriv,
                        onValueChange = { epkPriv = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.courier_epk_decrypt_label)) },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPrivKey = !showPrivKey }) {
                                Icon(
                                    if (showPrivKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (showPrivKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        shape = MaterialTheme.shapes.medium,
                        isError = decryptError,
                        supportingText = if (decryptError) {
                            { Text(stringResource(R.string.courier_decrypt_wrong_key), color = MaterialTheme.colorScheme.error) }
                        } else null
                    )
                    TextButton(
                        onClick = launchScan,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.courier_scan_qr))
                    }
                }

                if (decryptError) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CarakaBody(
                            stringResource(R.string.courier_decrypt_failed),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (mode == "STEALTH") {
                            val result = onDecryptStealth(encPayload, encNonce, epkPriv)
                            if (result != null) {
                                decryptedText = result
                                decryptError = false
                            } else {
                                decryptError = true
                            }
                        } else {
                            // Directed: async decrypt via callback
                            isDecrypting = true
                            onDecryptDirected(encPayload, encNonce, senderPub) { result ->
                                isDecrypting = false
                                if (result != null) {
                                    decryptedText = result
                                    decryptError = false
                                } else {
                                    decryptError = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = (mode == "DIRECTED" || epkPriv.isNotBlank()) && !isDecrypting,
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.courier_open_message), style = CarakaTextStyles.buttonLabel)
                }
            } else {
                // Pesan berhasil terdekripsi — tampilkan dengan indah
                DecryptedMessageDisplay(decryptedText = decryptedText!!)

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.courier_close), style = CarakaTextStyles.buttonLabel)
                }
            }
        }
    }
}

@Composable
private fun DecryptedMessageDisplay(decryptedText: String) {
    // decryptedText sudah berupa konten polos yang LOLOS verifikasi Ed25519 signature A
    // di CourierRepository.verifyInnerPayload() (mengembalikan null bila signature invalid).
    // Maka keberhasilan dekripsi = pesan asli & terverifikasi.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    stringResource(R.string.courier_verified_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                decryptedText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  DeliverySuccessSheet — animasi konfirmasi sukses (B setelah ACK, atau A setelah receipt)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun DeliverySuccessSheet(
    bundleId: String,
    carrierName: String? = null,   // non-null jika A yang menerima receipt
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Animasi pulse untuk ikon sukses
    val infiniteTransition = rememberInfiniteTransition(label = "success_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Success icon dengan glow effect
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                                )
                            )
                        )
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size((72 * scale).dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Text(
                if (carrierName != null) stringResource(R.string.courier_success_receipt_title)
                else stringResource(R.string.courier_success_delivered_title),
                style = CarakaTextStyles.dialogTitle,
                textAlign = TextAlign.Center
            )

            CarakaBody(
                if (carrierName != null)
                    stringResource(R.string.courier_success_receipt_body_format, carrierName)
                else
                    stringResource(R.string.courier_success_delivered_body),
                muted = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        bundleId.take(8).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.courier_done), style = CarakaTextStyles.buttonLabel)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  StealthCredentialShareSheet — A membagikan EPK_priv + nonce ke Z (QR / salin / chat)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun StealthCredentialShareSheet(
    bundleId: String,
    epkPrivB64: String,
    nonceB64: String,
    peers: List<PeerEntity>,
    onShareViaChat: (recipientId: String, payloadJson: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val stealth = LocalStatusColors.current.stealth

    val payloadJson = remember(bundleId, epkPrivB64, nonceB64) {
        QrIdentityManager.buildStealthCredentialPayload(bundleId, epkPrivB64, nonceB64)
    }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(payloadJson) {
        // QR selalu hitam-di-putih agar scannable di tema apa pun (bukan inverted).
        qrBitmap = withContext(Dispatchers.Default) {
            QrIdentityManager.generateQrBitmap(
                payloadJson,
                sizePx = 560,
                darkColor = 0xFF000000.toInt(),
                lightColor = 0xFFFFFFFF.toInt()
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = stealth.copy(alpha = 0.14f), modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = stealth, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.courier_share_title), style = CarakaTextStyles.dialogTitle)
                    CarakaBody(stringResource(R.string.courier_share_subtitle), muted = true)
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                CarakaBody(
                    stringResource(R.string.courier_share_warning),
                    modifier = Modifier.padding(10.dp)
                )
            }

            // QR — selalu di atas tile putih agar kontras & scannable di tema apa pun.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val bmp = qrBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_courier_qr),
                        modifier = Modifier.size(220.dp)
                    )
                } else {
                    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = stealth)
                    }
                }
            }

            CredentialCopyRow(label = "EPK_priv", value = epkPrivB64) {
                clipboard.setText(AnnotatedString(epkPrivB64))
            }
            CredentialCopyRow(label = "Nonce", value = nonceB64) {
                clipboard.setText(AnnotatedString(nonceB64))
            }

            if (peers.isNotEmpty()) {
                Text(stringResource(R.string.courier_share_via_chat), style = CarakaTextStyles.fieldLabel)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(peers) { peer ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShareViaChat(peer.id, payloadJson) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(peer.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(peer.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.courier_done), style = CarakaTextStyles.buttonLabel)
            }
        }
    }
}

@Composable
private fun CredentialCopyRow(label: String, value: String, onCopy: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value.take(28) + if (value.length > 28) "…" else "",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.courier_copy_format, label), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
