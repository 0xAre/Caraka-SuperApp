package com.example.caraka.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.data.local.entity.MessageEntity
import com.example.caraka.ui.components.CarakaBody
import com.example.caraka.ui.theme.CarakaTextStyles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsBottomSheet(
    visible: Boolean,
    alerts: List<MessageEntity>,
    onDismiss: () -> Unit,
    onViewAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(R.string.alerts_sheet_title),
                color = MaterialTheme.colorScheme.primary,
                style = CarakaTextStyles.dialogTitle
            )
            Spacer(Modifier.height(4.dp))
            CarakaBody(
                stringResource(R.string.alerts_sheet_subtitle, alerts.size),
                muted = true
            )
            Spacer(Modifier.height(16.dp))

            if (alerts.isEmpty()) {
                CarakaBody(
                    stringResource(R.string.home_no_alerts),
                    muted = true,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alerts, key = { it.id }) { alert ->
                        EmergencyAlertCard(alert = alert)
                    }
                }
            }

            if (onViewAll != null && alerts.isNotEmpty()) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onViewAll()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.alerts_view_all),
                        color = MaterialTheme.colorScheme.primary,
                        style = CarakaTextStyles.listTitle
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
