package com.example.caraka.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.LocalCarakaDimens

enum class EmptyStateVariant {
    Compact,
    Default
}

@Composable
fun EmptyStateIllustration(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    contentDescription: String? = null,
    @DrawableRes illustrationRes: Int? = null,
    variant: EmptyStateVariant = EmptyStateVariant.Default
) {
    val dimens = LocalCarakaDimens.current
    val illustrationSize = if (variant == EmptyStateVariant.Compact) {
        dimens.illustrationCompact
    } else {
        dimens.illustrationDefault
    }
    Column(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .semantics { contentDescription?.let { this.contentDescription = it } },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (illustrationRes != null) {
            Image(
                painter = painterResource(illustrationRes),
                contentDescription = null,
                modifier = Modifier.size(illustrationSize)
            )
        } else {
            CarakaGlassSurface(
                modifier = Modifier.size(if (variant == EmptyStateVariant.Compact) 72.dp else 88.dp),
                shape = CircleShape
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(if (variant == EmptyStateVariant.Compact) 72.dp else 88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (variant == EmptyStateVariant.Compact) 30.dp else 38.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(if (variant == EmptyStateVariant.Compact) 12.dp else 16.dp))
        Text(
            message,
            style = CarakaTextStyles.emptyTitle,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(actionLabel, style = CarakaTextStyles.buttonLabel)
            }
        }
    }
}
