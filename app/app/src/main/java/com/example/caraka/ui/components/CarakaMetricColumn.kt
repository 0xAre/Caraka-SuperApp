package com.example.caraka.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.LocalCarakaDimens

enum class MetricValueKind {
    /** Pure digits — 32sp ExtraBold. */
    Number,
    /** Status words e.g. Siaga, Belum ada — 15sp SemiBold, same slot height. */
    Status,
    /** Numeric + small suffix e.g. 0,1 + km — number large, suffix small. */
    WithSuffix
}

@Composable
fun CarakaMetricColumn(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subLabel: String? = null,
    valueSuffix: String? = null,
    valueKind: MetricValueKind = inferMetricValueKind(value, valueSuffix)
) {
    val dimens = LocalCarakaDimens.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.metricValueMinHeight),
            contentAlignment = Alignment.Center
        ) {
            when (valueKind) {
                MetricValueKind.Number -> {
                    Text(
                        text = value,
                        style = CarakaTextStyles.statValue,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                MetricValueKind.Status -> {
                    Text(
                        text = value,
                        style = CarakaTextStyles.statusPrimary,
                        color = color,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                MetricValueKind.WithSuffix -> {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(
                            text = value,
                            style = CarakaTextStyles.statValue,
                            color = color,
                            maxLines = 1
                        )
                        if (!valueSuffix.isNullOrBlank()) {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = valueSuffix,
                                style = CarakaTextStyles.statLabel,
                                color = color.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(dimens.metricValueLabelGap))
        Text(
            text = label,
            style = CarakaTextStyles.statLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (!subLabel.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subLabel,
                style = CarakaTextStyles.statLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun inferMetricValueKind(value: String, suffix: String?): MetricValueKind {
    if (!suffix.isNullOrBlank()) return MetricValueKind.WithSuffix
    if (value.all { it.isDigit() || it == '.' || it == ',' || it == '-' || it == '/' }) {
        return MetricValueKind.Number
    }
    return MetricValueKind.Status
}
