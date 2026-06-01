package com.example.caraka.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.ui.prefs.LocalUiPrefs
import com.example.caraka.ui.theme.*
import com.example.caraka.ui.util.rememberHaptics
import com.example.caraka.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel? = null,
    onOpenHelp: () -> Unit = {}
) {
    val displayName by viewModel?.displayName?.collectAsStateWithLifecycle(initialValue = "") ?: remember { mutableStateOf("") }
    val myRole by viewModel?.myRole?.collectAsStateWithLifecycle(initialValue = "") ?: remember { mutableStateOf("") }
    val myPeerId by viewModel?.myPeerId?.collectAsStateWithLifecycle(initialValue = "") ?: remember { mutableStateOf("") }
    val meshNodeCount by viewModel?.meshNodeCount?.collectAsStateWithLifecycle(initialValue = 1) ?: remember { mutableStateOf(1) }
    val relayed by viewModel?.relayedMessageCount?.collectAsStateWithLifecycle(initialValue = 0) ?: remember { mutableStateOf(0) }
    val connectedPeerCount by viewModel?.connectedPeerCount?.collectAsStateWithLifecycle(initialValue = 0) ?: remember { mutableStateOf(0) }

    val prefs = LocalUiPrefs.current
    val haptics = rememberHaptics()

    var showResetDialog by remember { mutableStateOf(false) }

    val isAuthority = myRole in listOf("BPBD", "POLRI", "PMI")
    val roleColor = when (myRole) {
        "BPBD" -> DisasterBlue
        "POLRI" -> AmberAccent
        "PMI" -> DangerRed
        else -> NeonMint
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Identity Card ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = roleColor.copy(alpha = 0.3f), spotColor = SurfaceDark)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassSurface)
                    .border(1.dp, roleColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(roleColor.copy(alpha = 0.15f))
                            .border(2.dp, roleColor.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAuthority) Icons.Default.Shield else Icons.Default.Person,
                            contentDescription = null,
                            tint = roleColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = displayName.ifBlank { "—" },
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(roleColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(myRole.ifBlank { "CIVILIAN" }, color = roleColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (isAuthority) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Verified, contentDescription = stringResource(R.string.cd_verified), tint = roleColor, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                if (myPeerId.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_peer_id), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = myPeerId.take(32) + if (myPeerId.length > 32) "…" else "",
                        color = TextPrimary.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // ── Network Stats ────────────────────────────────────────────
            Text(stringResource(R.string.settings_network_stats), color = AmberAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsStatChip(stringResource(R.string.settings_stat_nodes), "$meshNodeCount", Icons.Default.Hub, AmberAccent, Modifier.weight(1f))
                SettingsStatChip(stringResource(R.string.settings_stat_peers), "$connectedPeerCount", Icons.Default.People, NeonMint, Modifier.weight(1f))
                SettingsStatChip(stringResource(R.string.settings_stat_relayed), "$relayed", Icons.Default.SwapHoriz, DisasterBlue, Modifier.weight(1f))
            }

            // ── Accessibility / Preferences ──────────────────────────────
            Text(stringResource(R.string.settings_accessibility), color = AmberAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            // Language toggle (segmented)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassSurface)
                    .border(1.dp, SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.settings_language), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark.copy(alpha = 0.6f))
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

            // Big-text toggle
            ToggleRow(
                icon = Icons.Default.TextFields,
                title = stringResource(R.string.settings_big_text),
                subtitle = stringResource(R.string.settings_big_text_desc),
                checked = prefs.bigText,
                onToggle = { haptics.tick(); prefs.toggleBigText() }
            )
            // High-contrast toggle
            ToggleRow(
                icon = Icons.Default.Contrast,
                title = stringResource(R.string.settings_high_contrast),
                subtitle = stringResource(R.string.settings_high_contrast_desc),
                checked = prefs.highContrast,
                onToggle = { haptics.tick(); prefs.toggleHighContrast() }
            )
            // Haptics toggle
            ToggleRow(
                icon = Icons.Default.Vibration,
                title = stringResource(R.string.settings_haptics),
                subtitle = stringResource(R.string.settings_haptics_desc),
                checked = prefs.haptics,
                onToggle = { prefs.toggleHaptics(); if (prefs.haptics) haptics.tick() }
            )

            // Help & HCI Info entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassSurface)
                    .border(1.dp, AmberAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { haptics.tick(); onOpenHelp() }
                    .padding(14.dp)
                    .semantics { contentDescription = "Open help and HCI info" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.settings_help), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
            }

            // ── About ────────────────────────────────────────────────────
            Text(stringResource(R.string.settings_about), color = AmberAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassSurface)
                    .border(1.dp, SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsInfoRow(Icons.Default.Info, stringResource(R.string.settings_label_app), stringResource(R.string.settings_app_info))
                SettingsInfoRow(Icons.Default.WifiTethering, stringResource(R.string.settings_label_protocol), stringResource(R.string.settings_protocol))
                SettingsInfoRow(Icons.Default.Lock, stringResource(R.string.settings_label_encryption), stringResource(R.string.settings_encryption))
                SettingsInfoRow(Icons.Default.Router, stringResource(R.string.settings_label_relay), stringResource(R.string.settings_relay))
            }

            // ── Danger Zone ──────────────────────────────────────────────
            Text(stringResource(R.string.settings_danger_zone), color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = DangerRed)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_reset_btn), color = DangerRed, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed) },
            title = { Text(stringResource(R.string.settings_reset_dialog_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.settings_reset_dialog_desc), color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.clearIdentity()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text(stringResource(R.string.settings_reset_confirm), color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.settings_cancel), color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun LanguagePill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AmberAccent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) AmberAccent else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, SurfaceDark, RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .padding(14.dp)
            .semantics { contentDescription = "$title. ${if (checked) "Enabled" else "Disabled"}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AmberAccent,
                uncheckedTrackColor = SurfaceDark
            )
        )
    }
}

@Composable
private fun SettingsStatChip(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurface)
            .border(1.dp, SurfaceDark, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun SettingsInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = TextSecondary, fontSize = 11.sp)
            Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
