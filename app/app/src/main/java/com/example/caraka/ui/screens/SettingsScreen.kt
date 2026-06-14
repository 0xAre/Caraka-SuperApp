package com.example.caraka.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.ui.components.CarakaCard
import com.example.caraka.ui.components.CarakaMetricColumn
import com.example.caraka.ui.components.CarakaTopBarTitle
import com.example.caraka.ui.components.CarakaBody
import com.example.caraka.ui.components.CarakaListTitle
import com.example.caraka.ui.components.IdentityDisplayRow
import com.example.caraka.ui.components.SectionTitle
import com.example.caraka.ui.components.LocalSnackbar
import com.example.caraka.ui.components.VerifiedBadge
import com.example.caraka.ui.prefs.LocalUiPrefs
import com.example.caraka.ui.theme.*
import com.example.caraka.ui.util.rememberHaptics
import com.example.caraka.viewmodel.MainViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel? = null,
    onOpenHelp: () -> Unit = {},
    onOpenQr: () -> Unit = {}
) {
    val displayName by viewModel?.displayName?.collectAsStateWithLifecycle(initialValue = "") ?: remember { mutableStateOf("") }
    val myRole by viewModel?.myRole?.collectAsStateWithLifecycle(initialValue = "") ?: remember { mutableStateOf("") }
    val myPeerId by viewModel?.myPeerId?.collectAsStateWithLifecycle(initialValue = "") ?: remember { mutableStateOf("") }
    val meshNodeCount by viewModel?.meshNodeCount?.collectAsStateWithLifecycle(initialValue = 1) ?: remember { mutableStateOf(1) }
    val relayed by viewModel?.relayedMessageCount?.collectAsStateWithLifecycle(initialValue = 0) ?: remember { mutableStateOf(0) }
    val connectedPeerCount by viewModel?.connectedPeerCount?.collectAsStateWithLifecycle(initialValue = 0) ?: remember { mutableStateOf(0) }
    val batteryLevel by viewModel?.batteryLevel?.collectAsStateWithLifecycle(initialValue = 100) ?: remember { mutableStateOf(100) }
    val attackSimActive by viewModel?.attackSimActive?.collectAsStateWithLifecycle(initialValue = false) ?: remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = LocalUiPrefs.current
    val haptics = rememberHaptics()
    val snackbar = LocalSnackbar.current
    val copiedMsg = stringResource(R.string.identity_copied)

    var showResetDialog by remember { mutableStateOf(false) }
    var showRoleSheet by remember { mutableStateOf(false) }
    var selectedPendingRole by remember { mutableStateOf<String?>(null) }
    var showRoleConfirmDialog by remember { mutableStateOf(false) }

    val helpCd = stringResource(R.string.settings_help)
    val statusColors = LocalStatusColors.current
    val isAuthority = myRole in listOf("BPBD", "POLRI", "PMI")
    val roleColor = when (myRole) {
        "BPBD" -> statusColors.hybrid
        "POLRI" -> MaterialTheme.colorScheme.tertiary
        "PMI" -> MaterialTheme.colorScheme.error
        else -> statusColors.online
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    CarakaTopBarTitle(
                        title = "Profil",
                        subtitle = "Identitas, keamanan, dan preferensi CARAKA"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            SectionTitle("Identitas")

            CarakaCard(
                modifier = Modifier.fillMaxWidth(),
                hasSubtleBorder = true,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ill_profile_identity),
                            contentDescription = null,
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = displayName.ifBlank { "—" }.replaceFirstChar { it.titlecase() },
                                style = CarakaTextStyles.profileName,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(roleColor.copy(alpha = 0.15f))
                                        .clickable { showRoleSheet = true }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        myRole.ifBlank { "CIVILIAN" },
                                        color = roleColor,
                                        style = CarakaTextStyles.badge
                                    )
                                }
                                if (isAuthority) {
                                    Spacer(Modifier.width(6.dp))
                                    VerifiedBadge(tint = roleColor, size = 14.dp)
                                }
                            }
                        }
                    }

                    if (myPeerId.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        IdentityDisplayRow(
                            peerId = myPeerId,
                            onQrClick = { haptics.tick(); onOpenQr() },
                            onCopied = { snackbar.tryEmit(copiedMsg) }
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { haptics.tick(); onOpenQr() },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            shape = LocalCarakaShapes.current.md,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.settings_qr_btn),
                                style = CarakaTextStyles.buttonLabel,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle("Jaringan")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsStatChip(stringResource(R.string.settings_stat_nodes), "$meshNodeCount", Icons.Default.Hub, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                SettingsStatChip(stringResource(R.string.settings_stat_peers), "$connectedPeerCount", Icons.Default.People, statusColors.online, Modifier.weight(1f))
                SettingsStatChip(stringResource(R.string.settings_stat_relayed), "$relayed", Icons.Default.SwapHoriz, statusColors.hybrid, Modifier.weight(1f))
            }
            val goIntentValue = if (isAuthority) 15 else (batteryLevel * 15 / 100).coerceIn(0, 15)
            val goIntentColor = when {
                goIntentValue <= 3 -> MaterialTheme.colorScheme.error
                goIntentValue <= 7 -> MaterialTheme.colorScheme.tertiary
                else -> statusColors.online
            }
            SettingsStatChip(
                label = "Prioritas GO",
                value = "$goIntentValue/15",
                icon = Icons.Default.Hub,
                color = goIntentColor,
                modifier = Modifier.fillMaxWidth(),
                subLabel = "Tinggi = lebih sering jadi relay utama",
                onInfoClick = {
                    haptics.tick()
                    snackbar.tryEmit("Semakin tinggi nilai, semakin diprioritaskan menjadi Group Owner jaringan mesh")
                }
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle("Tampilan & aksesibilitas")

            CarakaCard(
                modifier = Modifier.fillMaxWidth(),
                hasSubtleBorder = true,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        CarakaListTitle(stringResource(R.string.settings_language))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(LocalCarakaShapes.current.lg)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp)
                    ) {
                        LanguagePill(
                            label = stringResource(R.string.settings_language_id),
                            selected = prefs.language == "id",
                            onClick = { if (prefs.language != "id") { haptics.tick(); prefs.toggleLanguage() } },
                            modifier = Modifier.weight(1f)
                        )
                        LanguagePill(
                            label = stringResource(R.string.settings_language_en),
                            selected = prefs.language == "en",
                            onClick = { if (prefs.language != "en") { haptics.tick(); prefs.toggleLanguage() } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            ToggleRow(
                icon = Icons.Default.TextFields,
                title = stringResource(R.string.settings_big_text),
                subtitle = stringResource(R.string.settings_big_text_desc),
                checked = prefs.bigText,
                onToggle = { haptics.tick(); prefs.toggleBigText() }
            )
            ToggleRow(
                icon = Icons.Default.Contrast,
                title = stringResource(R.string.settings_high_contrast),
                subtitle = stringResource(R.string.settings_high_contrast_desc),
                checked = prefs.highContrast,
                onToggle = { haptics.tick(); prefs.toggleHighContrast() }
            )
            ToggleRow(
                icon = Icons.Default.Vibration,
                title = stringResource(R.string.settings_haptics),
                subtitle = stringResource(R.string.settings_haptics_desc),
                checked = prefs.haptics,
                onToggle = { prefs.toggleHaptics(); if (prefs.haptics) haptics.tick() }
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle("Bantuan & info")

            CarakaCard(
                modifier = Modifier.fillMaxWidth().clickable { haptics.tick(); onOpenHelp() }.semantics { contentDescription = helpCd },
                hasSubtleBorder = true,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    CarakaListTitle(
                        stringResource(R.string.settings_help),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            CarakaCard(
                modifier = Modifier.fillMaxWidth(),
                hasSubtleBorder = true,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsInfoRow(Icons.Default.Info, stringResource(R.string.settings_label_app), stringResource(R.string.settings_app_info))
                    SettingsInfoRow(Icons.Default.WifiTethering, stringResource(R.string.settings_label_protocol), stringResource(R.string.settings_protocol))
                    SettingsInfoRow(Icons.Default.Lock, stringResource(R.string.settings_label_encryption), stringResource(R.string.settings_encryption))
                    SettingsInfoRow(Icons.Default.Router, stringResource(R.string.settings_label_relay), stringResource(R.string.settings_relay))
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle("Zona berbahaya")
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                shape = LocalCarakaShapes.current.md,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Hapus data darurat",
                    style = CarakaTextStyles.buttonLabel,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle("Mode demo")
            AttackSimulatorCard(
                isActive = attackSimActive,
                onToggle = { 
                    haptics.tick()
                    viewModel?.toggleAttackSim()
                }
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Hapus seluruh data CARAKA?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Tindakan ini akan MENGHAPUS SEMUA DATA mesh, riwayat pesan, dan identitas Anda dari perangkat secara permanen. Lanjutkan?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        com.example.caraka.data.local.CarakaDatabase.secureWipe(context)
                        viewModel?.clearIdentity()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        "WIPE SEKARANG",
                        style = CarakaTextStyles.buttonLabel.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.settings_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showRoleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRoleSheet = false },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Pilih Role Jaringan",
                    style = CarakaTextStyles.dialogTitle,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                val roles = listOf(
                    Triple("CIVILIAN", Icons.Default.Person, "Pengguna umum jaringan mesh"),
                    Triple("BPBD", Icons.Default.Warning, "Badan Penanggulangan Bencana Daerah"),
                    Triple("POLRI", Icons.Default.Shield, "Kepolisian Negara Republik Indonesia"),
                    Triple("PMI", Icons.Default.LocalHospital, "Palang Merah Indonesia")
                )
                
                roles.forEach { (roleName, icon, desc) ->
                    val isAuthorityItem = roleName != "CIVILIAN"
                    ListItem(
                        modifier = Modifier.clickable {
                            if (isAuthorityItem) {
                                selectedPendingRole = roleName
                                showRoleConfirmDialog = true
                            } else {
                                viewModel?.updateRole(roleName)
                                showRoleSheet = false
                            }
                        },
                        headlineContent = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CarakaListTitle(roleName)
                                if (isAuthorityItem) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "(Perlu Verifikasi QR)",
                                        style = CarakaTextStyles.statLabel,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        supportingContent = {
                            CarakaBody(desc, muted = true)
                        },
                        leadingContent = { Icon(icon, contentDescription = null, tint = if (isAuthorityItem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showRoleConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRoleConfirmDialog = false },
            title = { Text("Konfirmasi Role", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Role ini memerlukan verifikasi identitas dari otoritas terkait. Lanjutkan?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    selectedPendingRole?.let { viewModel?.updateRole(it) }
                    showRoleConfirmDialog = false
                    showRoleSheet = false
                }) {
                    Text(
                        "Lanjutkan",
                        style = CarakaTextStyles.buttonLabel.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRoleConfirmDialog = false }) { Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun LanguagePill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = CarakaTextStyles.buttonLabel.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    CarakaCard(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.semantics { contentDescription = "$title. ${if (checked) "Enabled" else "Disabled"}" },
        hasSubtleBorder = true,
            containerColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                CarakaListTitle(title)
                CarakaBody(subtitle, muted = true)
            }
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun SettingsStatChip(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    onInfoClick: (() -> Unit)? = null
) {
    CarakaCard(
        modifier = modifier,
        hasSubtleBorder = true,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CarakaMetricColumn(
                value = value,
                label = label,
                color = color,
                icon = icon,
                subLabel = subLabel
            )
            if (onInfoClick != null) {
                Spacer(Modifier.height(4.dp))
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onInfoClick() }
                )
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = CarakaTextStyles.statLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = CarakaTextStyles.listSubtitle.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun AttackSimulatorCard(isActive: Boolean, onToggle: () -> Unit) {
    CarakaCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = LocalCarakaShapes.current.md,
        containerColor = if (isActive) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer
    ) {
        Column {
            ListItem(
                headlineContent = {
                    Text(
                        text = if (isActive) stringResource(R.string.home_attack_sim_active_title)
                               else stringResource(R.string.home_attack_sim_title),
                        style = CarakaTextStyles.listTitle,
                        color = if (isActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                supportingContent = {
                    Text(
                        text = if (isActive) stringResource(R.string.home_attack_sim_active_subtitle)
                               else stringResource(R.string.home_attack_sim_subtitle),
                        style = CarakaTextStyles.listSubtitle,
                        color = if (isActive) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = if (isActive) Icons.Default.FlashOff else Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isActive,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.error
                        )
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            Text(
                text = "Fitur khusus demonstrasi — jangan aktifkan saat operasi nyata",
                style = CarakaTextStyles.statLabel,
                color = if (isActive) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
    }
}
