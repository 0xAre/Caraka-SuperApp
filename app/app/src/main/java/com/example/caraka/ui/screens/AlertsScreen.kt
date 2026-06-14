package com.example.caraka.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.ui.components.EmergencyAlertCard
import com.example.caraka.ui.components.EmptyStateIllustration
import com.example.caraka.viewmodel.MainViewModel

private enum class AlertFilter { ALL, MEDICAL, FIRE, SECURITY, DISASTER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val activeAlerts by viewModel.activeAlerts.collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by remember { mutableStateOf(AlertFilter.ALL) }

    val filtered = remember(activeAlerts, filter) {
        when (filter) {
            AlertFilter.ALL -> activeAlerts
            AlertFilter.MEDICAL -> activeAlerts.filter { it.sosCategory.equals("MEDICAL", true) }
            AlertFilter.FIRE -> activeAlerts.filter { it.sosCategory.equals("FIRE", true) }
            AlertFilter.SECURITY -> activeAlerts.filter { it.sosCategory.equals("SECURITY", true) }
            AlertFilter.DISASTER -> activeAlerts.filter { it.sosCategory.equals("DISASTER", true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.alerts_screen_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_btn),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
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
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.alerts_screen_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                AlertFilterRow(selected = filter, onSelect = { filter = it })
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateIllustration(
                        icon = Icons.Default.NotificationsNone,
                        message = stringResource(R.string.alerts_empty),
                        modifier = Modifier.padding(vertical = 48.dp)
                    )
                }
            } else {
                items(filtered, key = { it.id }) { alert ->
                    EmergencyAlertCard(alert = alert)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertFilterRow(selected: AlertFilter, onSelect: (AlertFilter) -> Unit) {
    val chips = listOf(
        AlertFilter.ALL to R.string.alerts_filter_all,
        AlertFilter.MEDICAL to R.string.alerts_filter_medical,
        AlertFilter.FIRE to R.string.alerts_filter_fire,
        AlertFilter.SECURITY to R.string.alerts_filter_security,
        AlertFilter.DISASTER to R.string.alerts_filter_disaster
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        items(chips.size) { index ->
            val (f, labelRes) = chips[index]
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = { Text(stringResource(labelRes), fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
