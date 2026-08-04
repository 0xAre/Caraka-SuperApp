@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.caraka.ui.courier

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.crypto.QrIdentityManager
import com.example.caraka.data.local.entity.CourierBundleEntity
import com.example.caraka.data.local.entity.PeerEntity
import com.example.caraka.ui.components.CarakaBody
import com.example.caraka.ui.components.CarakaCard
import com.example.caraka.ui.components.CarakaListTitle
import com.example.caraka.ui.scanner.CarakaQrCaptureActivity
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.viewmodel.CarakaContact
import com.example.caraka.viewmodel.CourierViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════════
//  CarakaTab — isi tab "Caraka" di layar Pesan (alur Directed: titip pesan via kurir)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CarakaTab(
    viewModel: CourierViewModel,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val carryingBundles by viewModel.carryingBundles.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tombol kirim + riwayat
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.openSendRequest() },
                    enabled = connectedPeers.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.courier_fab_send), style = CarakaTextStyles.buttonLabel)
                }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onOpenHistory() }
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = stringResource(R.string.cd_courier_history),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Kurir tersedia (peer terhubung = B)
        item { Text(stringResource(R.string.caraka_section_carriers), style = CarakaTextStyles.sectionTitle) }
        if (connectedPeers.isEmpty()) {
            item { InfoCard(stringResource(R.string.caraka_no_carriers_title), stringResource(R.string.caraka_no_carriers_desc)) }
        } else {
            items(connectedPeers, key = { "carrier_${it.id}" }) { peer ->
                CarrierRow(peer = peer, onClick = { viewModel.openSendRequest(peer.id) })
            }
        }

        // Sedang kamu bawa
        item {
            Text(
                stringResource(R.string.courier_carrying_section),
                style = CarakaTextStyles.sectionTitle,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (carryingBundles.isEmpty()) {
            item { EmptyBundleCard() }
        } else {
            items(carryingBundles, key = { it.bundleId }) { bundle -> BundleCarryItem(bundle) }
        }
    }
}

@Composable
private fun CarrierRow(peer: PeerEntity, onClick: () -> Unit) {
    CarakaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.displayName.ifBlank { peer.id.take(8) }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(peer.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.courier_fab_send), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun InfoCard(title: String, desc: String) {
    CarakaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CarakaListTitle(title)
            CarakaBody(desc, muted = true)
        }
    }
}

// ── Bundle yang sedang dibawa (B) ────────────────────────────────────────────────

@Composable
internal fun BundleCarryItem(bundle: CourierBundleEntity) {
    val expiryDate = remember(bundle.expiry) {
        SimpleDateFormat("dd MMM HH:mm", Locale("id")).format(Date(bundle.expiry))
    }
    val isExpiringSoon = bundle.expiry > 0 && (bundle.expiry - System.currentTimeMillis()) < 3_600_000L
    val accent = MaterialTheme.colorScheme.primary

    CarakaCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = accent, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    bundle.bundleId.take(8).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.courier_expire_format, expiryDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isExpiringSoon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun EmptyBundleCard() {
    CarakaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            CarakaListTitle(stringResource(R.string.courier_empty_title))
            CarakaBody(stringResource(R.string.courier_empty_desc), muted = true)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CarakaSendSheet — A: pilih kurir B → tujuan Z → pesan Z → catatan B (Directed)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CarakaSendSheet(
    viewModel: CourierViewModel,
    preselectCourierId: String?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val carriers by viewModel.connectedPeers.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    var courierId by remember { mutableStateOf(preselectCourierId ?: "") }
    var targetId by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showAddContact by remember { mutableStateOf(false) }

    if (showAddContact) {
        AddContactDialog(
            onAdd = { pid, name -> viewModel.addManualContact(pid, name); showAddContact = false },
            onAddViaQr = { payload -> viewModel.addContactViaQr(payload); showAddContact = false },
            onDismiss = { showAddContact = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.courier_fab_send), style = CarakaTextStyles.dialogTitle)
                    CarakaBody(stringResource(R.string.courier_send_subtitle), muted = true)
                }
            }

            // 1. Kurir (B)
            Text(stringResource(R.string.caraka_pick_carrier), style = CarakaTextStyles.fieldLabel)
            LazyColumn(modifier = Modifier.fillMaxWidth().height(96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(carriers, key = { it.id }) { peer ->
                    PickRow(
                        title = peer.displayName.ifBlank { peer.id.take(8) },
                        subtitle = peer.role,
                        selected = courierId == peer.id,
                        enabled = true,
                        onClick = { courierId = peer.id }
                    )
                }
            }

            // 2. Tujuan (Z) dari kontak
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.caraka_pick_target), style = CarakaTextStyles.fieldLabel, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAddContact = true }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.caraka_add_contact))
                }
            }
            if (contacts.isEmpty()) {
                CarakaBody(stringResource(R.string.caraka_no_contacts), muted = true)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(120.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(contacts, key = { it.peerId }) { c ->
                        PickRow(
                            title = c.name,
                            subtitle = if (c.hasKey) c.role else stringResource(R.string.caraka_needs_connection),
                            selected = targetId == c.peerId,
                            enabled = c.hasKey,
                            onClick = { if (c.hasKey) targetId = c.peerId },
                            trailing = if (!c.hasKey) {
                                {
                                    IconButton(onClick = {
                                        viewModel.removeContact(c.peerId)
                                        if (targetId == c.peerId) targetId = ""
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.caraka_remove_contact),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else null
                        )
                    }
                }
            }

            // 3. Pesan untuk penerima Z (terenkripsi)
            OutlinedTextField(
                value = message,
                onValueChange = { if (it.length <= 512) message = it },
                modifier = Modifier.fillMaxWidth().height(96.dp),
                label = { Text(stringResource(R.string.caraka_msg_for_target)) },
                placeholder = { Text(stringResource(R.string.caraka_msg_for_target_ph)) },
                leadingIcon = { Icon(Icons.Default.Message, contentDescription = null) },
                shape = MaterialTheme.shapes.medium,
                maxLines = 4
            )

            // 4. Catatan untuk kurir B (plaintext)
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 200) note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.caraka_note_for_carrier)) },
                placeholder = { Text(stringResource(R.string.caraka_note_for_carrier_ph)) },
                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                shape = MaterialTheme.shapes.medium,
                maxLines = 2,
                supportingText = { Text(stringResource(R.string.caraka_note_hint), style = MaterialTheme.typography.labelSmall) }
            )

            val canSend = courierId.isNotBlank() && targetId.isNotBlank() && message.isNotBlank()
            Button(
                onClick = {
                    viewModel.sendCourierRequest(
                        courierId = courierId,
                        recipientId = targetId,
                        message = message,
                        mode = "DIRECTED",
                        epkPrivB64 = null,
                        note = note
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSend,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.courier_assign_btn), style = CarakaTextStyles.buttonLabel)
            }
        }
    }
}

@Composable
private fun PickRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = bg
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.Send,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun AddContactDialog(
    onAdd: (peerId: String, name: String) -> Unit,
    onAddViaQr: (QrIdentityManager.QrIdentityPayload) -> Unit,
    onDismiss: () -> Unit
) {
    var peerId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    val scanInvalidMsg = stringResource(R.string.qr_scan_invalid)
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val raw = result.contents ?: return@rememberLauncherForActivityResult
        val parsed = QrIdentityManager.parseQrPayload(raw)
        if (parsed == null) {
            scanError = scanInvalidMsg
        } else {
            scanError = null
            onAddViaQr(parsed)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.caraka_add_contact_title), style = CarakaTextStyles.dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scanError = null
                        val options = ScanOptions().apply {
                            setCaptureActivity(CarakaQrCaptureActivity::class.java)
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setBeepEnabled(true)
                            setOrientationLocked(true)
                        }
                        scanLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.caraka_scan_qr_btn), style = CarakaTextStyles.buttonLabel)
                }
                scanError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                OutlinedTextField(
                    value = peerId,
                    onValueChange = { peerId = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.caraka_peerid_label)) },
                    placeholder = { Text(stringResource(R.string.caraka_peerid_ph)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.caraka_contact_name_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                CarakaBody(stringResource(R.string.caraka_needs_connection_hint), muted = true)
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(peerId, name) }, enabled = peerId.isNotBlank()) {
                Text(stringResource(R.string.caraka_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.courier_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}
