package com.example.caraka.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.caraka.R

@Composable
fun CarakaStatTile(
    modifier: Modifier = Modifier,
    nodeCount: Int,
    coverageKm: Float,
    alarmCount: Int,
    forwardedCount: Int
) {
    CarakaCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            CarakaMetricColumn(
                value = "$nodeCount",
                label = stringResource(R.string.home_stat_nodes),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            CarakaMetricColumn(
                value = "${"%.1f".format(coverageKm)}",
                valueSuffix = "km",
                label = stringResource(R.string.home_stat_range),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            CarakaMetricColumn(
                value = "$alarmCount",
                label = stringResource(R.string.home_stat_alerts),
                color = if (alarmCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            CarakaMetricColumn(
                value = "$forwardedCount",
                label = stringResource(R.string.settings_label_relay),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
