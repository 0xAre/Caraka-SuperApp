package com.example.caraka.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.caraka.ui.theme.MonoFamily

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
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("$nodeCount", "Node", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatItem("${"%.1f".format(coverageKm)} km", "Jangkauan", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            StatItem(
                "$alarmCount",
                "Alarm",
                if (alarmCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f)
            )
            StatItem("$forwardedCount", "Relay", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = MonoFamily,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
