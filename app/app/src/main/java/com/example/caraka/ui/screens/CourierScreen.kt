@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.caraka.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.data.local.entity.CourierBundleEntity
import com.example.caraka.ui.components.CarakaBody
import com.example.caraka.ui.components.CarakaCard
import com.example.caraka.ui.components.CarakaListTitle
import com.example.caraka.ui.courier.CourierOfferDialog
import com.example.caraka.ui.courier.CourierSendSheet
import com.example.caraka.ui.courier.DeliveryReceivedSheet
import com.example.caraka.ui.courier.DeliverySuccessSheet
import com.example.caraka.ui.courier.StealthBroadcastDialog
import com.example.caraka.ui.courier.StealthChallengeDialog
import com.example.caraka.ui.courier.StealthCredentialShareSheet
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.LocalStatusColors
import com.example.caraka.viewmodel.CourierDialogState
import com.example.caraka.viewmodel.CourierViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CourierScreen(
    viewModel: CourierViewModel,
    onBack: () -> Unit,
    onNavigateToHistory: () -> Unit = {}
) {
    val dialogState by viewModel.dialogState.collectAsState()
    val carakaModeActive by viewModel.carakaModeActive.collectAsState()
    val carryingBundles by viewModel.carryingBundles.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val stealthCreds by viewModel.stealthCredentials.collectAsState()
    val scope = rememberCoroutineScope()

    // State untuk hasil decrypt Directed (harus async karena getEncryptionKeyPair() suspend)
    var directedDecryptResult by remember { mutableStateOf<String?>(null) }
    var directedDecryptError by remember { mutableStateOf(false) }

    // ── Dialog/Sheet Routing ────────────────────────────────────────────────────
    when (val state = dialogState) {
        is CourierDialogState.SendRequest -> {
            CourierSendSheet(
                peers = state.availablePeers,
                onSend = { courierId, recipientId, message, mode ->
                    viewModel.sendCourierRequest(courierId, recipientId, message, mode, null)
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is CourierDialogState.OfferReceived -> {
            CourierOfferDialog(
                fromPeerName = state.fromPeerName,
                mode = state.mode,
                expiryMs = state.expiryMs,
                locationHintLat = state.locationHintLat,
                locationHintLon = state.locationHintLon,
                onAccept = { viewModel.acceptOffer(state.bundleId, state.fromPeerId) },
                onReject = { viewModel.rejectOffer(state.bundleId, state.fromPeerId) }
            )
        }
        is CourierDialogState.StealthBroadcast -> {
            StealthBroadcastDialog(
                fromPeerId = state.fromPeerId,
                tokenList = state.tokenList,
                onClaim = { token -> viewModel.claimToken(state.fromPeerId, token) },
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is CourierDialogState.ChallengeReceived -> {
            StealthChallengeDialog(
                bundleId = state.bundleId,
                fromPeerId = state.fromPeerId,
                challengeNonce = state.challengeNonce,
                onRespond = { epkPrivB64 ->
                    viewModel.respondToChallenge(
                        state.bundleId, state.fromPeerId, state.challengeNonce, epkPrivB64
                    )
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is CourierDialogState.DeliveryReceived -> {
            // Directed decrypt berjalan di coroutine — hasilnya dikembalikan via state
            DeliveryReceivedSheet(
                bundleId = state.bundleId,
                mode = state.mode,
                encPayload = state.encPayload,
                encNonce = state.encNonce,
                senderPub = state.senderPub,
                fromPeerId = state.fromPeerId,
                onDecryptDirected = { enc, nonce, pub, onResult ->
                    scope.launch {
                        val result = viewModel.decryptDirectedDelivery(state.bundleId, enc, nonce, pub)
                        onResult(result)
                    }
                },
                onDecryptStealth = { enc, nonce, epkPriv ->
                    viewModel.decryptStealthDelivery(enc, nonce, epkPriv)
                },
                onDismiss = { viewModel.dismissDeliverySuccess(state.bundleId) }
            )
        }
        is CourierDialogState.DeliverySuccess -> {
            DeliverySuccessSheet(
                bundleId = state.bundleId,
                onDismiss = { viewModel.dismissDeliverySuccess(state.bundleId) }
            )
        }
        is CourierDialogState.ReceiptReceived -> {
            DeliverySuccessSheet(
                bundleId = state.bundleId,
                carrierName = state.carrierName,
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is CourierDialogState.None -> { /* nothing */ }
    }

    // Setelah A membuat bundle STEALTH: tampilkan sheet berbagi kredensial (EPK_priv + nonce).
    stealthCreds?.let { creds ->
        StealthCredentialShareSheet(
            bundleId = creds.bundleId,
            epkPrivB64 = creds.epkPrivB64,
            nonceB64 = creds.nonceSecretB64,
            peers = connectedPeers,
            onShareViaChat = { peerId, payloadJson -> viewModel.shareCredentialsViaChat(peerId, payloadJson) },
            onDismiss = { viewModel.clearStealthCredentials() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.courier_title),
                            style = CarakaTextStyles.screenTitle
                        )
                        Text(
                            stringResource(R.string.courier_subtitle),
                            style = CarakaTextStyles.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.cd_courier_history))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (connectedPeers.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openSendRequest() },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.courier_fab_send)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Caraka Mode Toggle ──────────────────────────────────────────────
            item {
                CarakaModeCard(
                    active = carakaModeActive,
                    onToggle = {
                        if (carakaModeActive) viewModel.deactivateCarakaMode()
                        else viewModel.activateCarakaMode()
                    }
                )
            }

            // ── Bundle sedang dibawa ────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.courier_carrying_section),
                        style = CarakaTextStyles.sectionTitle
                    )
                    if (carryingBundles.isNotEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "${carryingBundles.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = CarakaTextStyles.badge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (carryingBundles.isEmpty()) {
                item {
                    EmptyBundleCard()
                }
            } else {
                items(carryingBundles, key = { it.bundleId }) { bundle ->
                    BundleCarryItem(bundle = bundle)
                }
            }
        }
    }
}

// ── Caraka Mode Card ────────────────────────────────────────────────────────────

@Composable
private fun CarakaModeCard(
    active: Boolean,
    onToggle: () -> Unit
) {
    val bgColor = if (active)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val iconTint = if (active)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.14f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (active) Icons.Default.LockPerson else Icons.Default.DirectionsBike,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                CarakaListTitle(
                    if (active) stringResource(R.string.courier_mode_active_title) else stringResource(R.string.courier_mode_title),
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                CarakaBody(
                    if (active) stringResource(R.string.courier_mode_active_desc)
                    else stringResource(R.string.courier_mode_desc),
                    muted = true
                )
            }
            Switch(
                checked = active,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

// ── Bundle Carry Item ───────────────────────────────────────────────────────────

@Composable
private fun BundleCarryItem(bundle: CourierBundleEntity) {
    val modeColor = when (bundle.mode) {
        "STEALTH" -> LocalStatusColors.current.stealth
        else -> MaterialTheme.colorScheme.primary
    }
    val modeLabel = if (bundle.mode == "STEALTH") stringResource(R.string.courier_mode_stealth) else stringResource(R.string.courier_mode_directed)
    val expiryDate = androidx.compose.runtime.remember(bundle.expiry) {
        SimpleDateFormat("dd MMM HH:mm", Locale("id")).format(Date(bundle.expiry))
    }
    val isExpiringSoon = bundle.expiry > 0 && (bundle.expiry - System.currentTimeMillis()) < 3_600_000L

    CarakaCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = modeColor.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = modeColor,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        modeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = modeColor
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            bundle.bundleId.take(8).uppercase(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.courier_expire_format, expiryDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isExpiringSoon)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (bundle.state == "CARRYING") Icons.Default.RadioButtonUnchecked
                else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (bundle.state == "CARRYING") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Empty State ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyBundleCard() {
    CarakaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.DirectionsBike,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            CarakaListTitle(stringResource(R.string.courier_empty_title))
            CarakaBody(
                stringResource(R.string.courier_empty_desc),
                muted = true
            )
        }
    }
}
