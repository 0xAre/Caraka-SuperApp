@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.caraka.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.caraka.R
import com.example.caraka.data.local.entity.CourierTaskEntity
import com.example.caraka.ui.components.CarakaBody
import com.example.caraka.ui.components.CarakaCard
import com.example.caraka.ui.components.CarakaListTitle
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.viewmodel.CourierViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CourierHistoryScreen(
    viewModel: CourierViewModel,
    onBack: () -> Unit
) {
    val tasks by viewModel.historyTasks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.courier_history_title), style = CarakaTextStyles.screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (tasks.isEmpty()) {
            EmptyHistory(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasks, key = { it.taskId }) { task ->
                    HistoryRow(task = task)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(task: CourierTaskEntity) {
    val (icon, accent) = statusVisual(task.status)
    val accepted = remember(task.acceptedAt) {
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id")).format(Date(task.acceptedAt))
    }
    val delivered = task.deliveredAt?.let {
        remember(it) { SimpleDateFormat("dd MMM HH:mm", Locale("id")).format(Date(it)) }
    }

    CarakaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(9.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.bundleId.take(8).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (delivered != null) stringResource(R.string.courier_history_received_done_format, accepted, delivered)
                    else stringResource(R.string.courier_history_received_format, accepted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = accent.copy(alpha = 0.14f)
            ) {
                Text(
                    statusLabel(task.status),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun statusVisual(status: String): Pair<ImageVector, Color> = when (status) {
    "DELIVERED" -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
    "ACTIVE"    -> Icons.Default.LocalShipping to MaterialTheme.colorScheme.secondary
    "EXPIRED"   -> Icons.Default.Schedule to MaterialTheme.colorScheme.error
    else        -> Icons.Default.Cancel to MaterialTheme.colorScheme.onSurfaceVariant // CANCELLED / lainnya
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    "DELIVERED" -> stringResource(R.string.courier_history_status_delivered)
    "ACTIVE"    -> stringResource(R.string.courier_history_status_active)
    "EXPIRED"   -> stringResource(R.string.courier_history_status_expired)
    "CANCELLED" -> stringResource(R.string.courier_history_status_cancelled)
    else        -> status
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        CarakaListTitle(stringResource(R.string.courier_history_empty))
        CarakaBody(
            stringResource(R.string.courier_history_empty_desc),
            muted = true
        )
    }
}
