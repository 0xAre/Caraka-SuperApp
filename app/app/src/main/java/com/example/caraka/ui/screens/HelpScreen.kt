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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.NavyBackground
import com.example.caraka.ui.theme.NeonMint
import com.example.caraka.ui.theme.SurfaceDark
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary

/**
 * In-app help center. Doubles as the artefact required for the HCI evaluation
 * — anyone (juror, instructor, user) can open it to inspect which usability
 * heuristics and accessibility features are implemented.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit, onLaunchTour: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.help_title), color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.help_back),
                            tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground
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
                        .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = AmberAccent, spotColor = SurfaceDark)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlassSurface)
                        .border(1.dp, AmberAccent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.padding(end = 12.dp)) {
                        Text("Tur Interaktif", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Putar ulang panduan 5 langkah", color = TextSecondary, fontSize = 12.sp)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = onLaunchTour) {
                        Text("Mulai", color = AmberAccent, fontWeight = FontWeight.Bold)
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

            // ── HCI Heuristics ────────────────────────────────────────────
            item { SectionHeader(R.string.help_section_hci) }
            item {
                Text(stringResource(R.string.help_hci_intro), color = TextSecondary, fontSize = 13.sp)
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassSurface)
                        .border(1.dp, NeonMint.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Accessibility, contentDescription = null, tint = NeonMint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.help_a11y_a), color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
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
        color = AmberAccent,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun QaCard(qRes: Int, aRes: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, SurfaceDark, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.QuestionMark, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(qRes), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Text(stringResource(aRes), color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun HciItem(index: Int, textRes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(NeonMint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$index", color = NeonMint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(textRes), color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(6.dp))
    }
}

