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
import androidx.compose.foundation.shape.RoundedCornerShape
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
                    Text("Kirim via Kurir", style = CarakaTextStyles.dialogTitle)
                    CarakaBody("Pesan akan dibawa oleh kurir ke penerima", muted = true)
                }
            }

            // Mode Selector
            Text("Mode Pengiriman", style = CarakaTextStyles.fieldLabel)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeChip(
                    label = "📍 Directed",
                    desc = "Kurir tahu ke mana tuju",
                    selected = mode == "DIRECTED",
                    onClick = { mode = "DIRECTED" },
                    modifier = Modifier.weight(1f)
                )
                ModeChip(
                    label = "🔮 Stealth",
                    desc = "Anonim penuh — 2 arah",
                    selected = mode == "STEALTH",
                    onClick = { mode = "STEALTH" },
                    modifier = Modifier.weight(1f)
                )
            }

            // Pilih kurir
            Text("Pilih Kurir", style = CarakaTextStyles.fieldLabel)
            if (peers.isEmpty()) {
                CarakaCard(modifier = Modifier.fillMaxWidth()) {
                    CarakaBody(
                        "Tidak ada peer terkoneksi. Hubungkan ke peer terlebih dahulu.",
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
                    label = { Text("Peer ID Penerima (Z)") },
                    placeholder = { Text("Paste Peer ID penerima...") },
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
                            "Penerima tidak perlu diketahui kurir. Z akan klaim dengan EPK_priv yang sudah kamu bagikan.",
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
                label = { Text("Pesan Rahasia") },
                placeholder = { Text("Tulis pesan yang akan dienkripsi...") },
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
                    "Tugaskan Kurir",
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
    val modeColor = if (mode == "STEALTH") Color(0xFF7C4DFF) else MaterialTheme.colorScheme.primary
    val modeLabel = if (mode == "STEALTH") "🔮 Stealth" else "📍 Directed"

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
                "Permintaan Kurir",
                style = CarakaTextStyles.dialogTitle,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CarakaBody(
                    "$fromPeerName memintamu menjadi kurir.",
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
                    label = "Isinya",
                    value = "Terenkripsi — kamu tidak bisa membacanya"
                )
                InfoRow(
                    icon = Icons.Default.Key,
                    label = "Deadline",
                    value = expiryStr,
                    valueColor = if (expiryMs - System.currentTimeMillis() < 3_600_000L)
                        MaterialTheme.colorScheme.error
                    else null
                )
                if (locationHintLat != null && locationHintLon != null) {
                    InfoRow(
                        icon = Icons.Default.Map,
                        label = "Lokasi tujuan",
                        value = "%.4f, %.4f".format(locationHintLat, locationHintLon)
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CarakaBody(
                        "⚠️ Kamu akan membawa bundle terenkripsi. Jika kamu kehilangan device, bundle akan expire otomatis.",
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
                Text("Terima")
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onReject) {
                Text("Tolak")
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

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = Color(0xFF7C4DFF).copy(alpha = 0.14f),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.LockPerson,
                    contentDescription = null,
                    tint = Color(0xFF7C4DFF),
                    modifier = Modifier.padding(14.dp)
                )
            }
        },
        title = {
            Text("Token Stealth Diterima", style = CarakaTextStyles.dialogTitle, textAlign = TextAlign.Center)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CarakaBody(
                    "Kurir di sekitarmu broadcast ${tokenList.size} token. Apakah ada yang milikmu?",
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
                                Color(0xFF7C4DFF).copy(alpha = 0.14f)
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
                                    tint = if (selectedToken == token) Color(0xFF7C4DFF)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) {
                Text("Klaim Token")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Bukan milikku") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
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
            Text("Verifikasi Identitas", style = CarakaTextStyles.dialogTitle, textAlign = TextAlign.Center)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CarakaBody(
                    "Kurir memintamu membuktikan kepemilikan kunci EPK_priv. Masukkan kunci yang diterima dari pengirim.",
                    muted = true
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Bundle ID", style = MaterialTheme.typography.labelSmall,
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
                    label = { Text("EPK_priv (Base64)") },
                    placeholder = { Text("Paste kunci dari pengirim...") },
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onRespond(epkPriv) },
                enabled = epkPriv.isNotBlank()
            ) {
                Text("Verifikasi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
                    Text("Pesan Diterima!", style = CarakaTextStyles.dialogTitle)
                    CarakaBody(
                        "Dari kurir $mode — ${fromPeerId.take(8)}",
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
                        label = { Text("EPK_priv untuk dekripsi") },
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
                            { Text("Kunci salah atau data rusak", color = MaterialTheme.colorScheme.error) }
                        } else null
                    )
                }

                if (decryptError) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CarakaBody(
                            "❌ Dekripsi gagal. Pastikan kunci yang dimasukkan benar.",
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
                    Text("Buka Pesan", style = CarakaTextStyles.buttonLabel)
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
                    Text("Tutup", style = CarakaTextStyles.buttonLabel)
                }
            }
        }
    }
}

@Composable
private fun DecryptedMessageDisplay(decryptedText: String) {
    // Parse inner payload jika format JSON signed payload
    val displayText = if (decryptedText.startsWith("{\"content\"")) {
        try {
            val regex = Regex(""""content"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            regex.find(decryptedText)?.groupValues?.get(1) ?: decryptedText
        } catch (_: Exception) {
            decryptedText
        }
    } else decryptedText

    val isVerified = decryptedText.contains("\"signerPub\"") && decryptedText.contains("\"signature\"")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isVerified) {
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
                        "Terverifikasi — Pesan asli dari pengirim",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                displayText,
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
                if (carrierName != null) "Pesan Berhasil Disampaikan!" else "Terkirim ke Penerima!",
                style = CarakaTextStyles.dialogTitle,
                textAlign = TextAlign.Center
            )

            CarakaBody(
                if (carrierName != null)
                    "Kurirmu ($carrierName) berhasil menyampaikan pesan ke penerima."
                else
                    "Bundle berhasil disampaikan. Identitas penerima tetap anonim.",
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
                Text("Selesai", style = CarakaTextStyles.buttonLabel)
            }
        }
    }
}
