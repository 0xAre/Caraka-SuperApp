package com.example.caraka.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.ui.theme.LocalCarakaShapes
import com.example.caraka.ui.theme.LocalStatusColors

/**
 * In-app help center. Doubles as the artefact required for the HCI evaluation
 * — anyone (juror, instructor, user) can open it to inspect which usability
 * heuristics and accessibility features are implemented.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit, onLaunchTour: () -> Unit) {
    val shapes = LocalCarakaShapes.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.help_title), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.help_back),
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Replay tour CTA ───────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shapes.xl)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), shapes.xl)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.padding(end = 12.dp)) {
                        Text("Tur Interaktif", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Putar ulang panduan 5 langkah", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onLaunchTour) {
                        Text("Mulai", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Basics Q&A ────────────────────────────────────────────────
            item { SectionHeader(R.string.help_section_basics) }
            item { QaCard(R.string.help_q_what_is_mesh, R.string.help_a_what_is_mesh) }
            item { QaCard(R.string.help_q_ttl, R.string.help_a_ttl) }
            item { QaCard(R.string.help_q_e2e, R.string.help_a_e2e) }
            item { QaCard(R.string.help_q_authority, R.string.help_a_authority) }
            item { QaCard(R.string.help_q_sos, R.string.help_a_sos) }
            item { QaCard(R.string.help_q_flag, R.string.help_a_flag) }

            item { SectionHeader(R.string.help_section_offline) }
            item { QaCard(R.string.help_section_offline, R.string.help_offline_sos) }
            item { QaCard(R.string.help_q_offline_flag, R.string.help_offline_flag) }
            item { QaCard(R.string.help_q_offline_qr, R.string.help_offline_qr) }

            // ── HCI Heuristics ────────────────────────────────────────────
            item { SectionHeader(R.string.help_section_hci) }
            item {
                Text(stringResource(R.string.help_hci_intro), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            item { HciItem(1, R.string.help_hci_visibility) }
            item { HciItem(2, R.string.help_hci_match) }
            item { HciItem(3, R.string.help_hci_control) }
            item { HciItem(4, R.string.help_hci_consistency) }
            item { HciItem(5, R.string.help_hci_error_prev) }
            item { HciItem(6, R.string.help_hci_recognition) }
            item { HciItem(7, R.string.help_hci_flexibility) }
            item { HciItem(8, R.string.help_hci_minimalist) }
            item { HciItem(9, R.string.help_hci_recovery) }
            item { HciItem(10, R.string.help_hci_help) }

            // ── Accessibility ─────────────────────────────────────────────
            item { SectionHeader(R.string.help_section_a11y) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shapes.md)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, LocalStatusColors.current.authority.copy(alpha = 0.3f), shapes.md)
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Accessibility, contentDescription = null, tint = LocalStatusColors.current.authority, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.help_a11y_a), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(textRes: Int) {
    Text(
        stringResource(textRes),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun QaCard(qRes: Int, aRes: Int) {
    val shapes = LocalCarakaShapes.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.md)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shapes.md)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.QuestionMark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(qRes), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Text(stringResource(aRes), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun HciItem(index: Int, textRes: Int) {
    val shapes = LocalCarakaShapes.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.sm)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(LocalStatusColors.current.authority.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$index", color = LocalStatusColors.current.authority, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(textRes), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(6.dp))
    }
}
