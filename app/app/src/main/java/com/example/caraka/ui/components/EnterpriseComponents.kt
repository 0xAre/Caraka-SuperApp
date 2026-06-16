package com.example.caraka.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.LocalCarakaDimens

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val dimens = LocalCarakaDimens.current
    CarakaSectionHeader(
        text = text,
        modifier = modifier.padding(top = dimens.sectionHeaderTop, bottom = dimens.sectionHeaderBottom)
    )
}

@Composable
fun ServiceTile(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes illustrationRes: Int? = null
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.91f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "service_tile_scale"
    )

    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            if (illustrationRes != null) {
                Image(
                    painter           = painterResource(illustrationRes),
                    contentDescription = null,
                    modifier          = Modifier.size(58.dp)
                )
            } else {
                Surface(
                    shape    = MaterialTheme.shapes.medium,
                    color    = color.copy(alpha = 0.12f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint     = color,
                        modifier = Modifier.padding(13.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text     = label,
            style    = CarakaTextStyles.serviceLabel,
            color    = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
fun EnterpriseMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.12f),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.padding(9.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                CarakaListTitle(title)
                if (!subtitle.isNullOrBlank()) {
                    CarakaBody(subtitle, muted = true, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
