package com.example.caraka.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.LocalCarakaShapes

@Composable
fun IdentityQrCard(
    displayName: String,
    role: String,
    peerId: String,
    qrBitmap: Bitmap?,
    qrVisible: Boolean,
    modifier: Modifier = Modifier,
    roleBadge: @Composable (String) -> Unit = { RoleBadgeDefault(it) }
) {
    val fingerprint = peerId.take(8).uppercase()
    val shapes = LocalCarakaShapes.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.lg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shapes.lg)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.QrCode2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.qr_my_identity_title),
                color = MaterialTheme.colorScheme.primary,
                style = CarakaTextStyles.serviceLabel
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            displayName.ifBlank { "—" },
            color = MaterialTheme.colorScheme.onSurface,
            style = CarakaTextStyles.dialogTitle,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        roleBadge(role)
        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(
            visible = qrVisible && qrBitmap != null,
            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.85f)
        ) {
            qrBitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(shapes.md)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, MaterialTheme.colorScheme.primary, shapes.md)
                        .padding(8.dp)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.qr_cd_identity, displayName),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        if (!qrVisible) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(shapes.md)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.qr_verbal_fingerprint, fingerprint),
            color = MaterialTheme.colorScheme.primary,
            style = CarakaTextStyles.monoData.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        )
        Spacer(Modifier.height(10.dp))
        CarakaBody(
            stringResource(R.string.qr_show_hint),
            muted = true,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shapes.sm)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.qr_trust_warning),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = CarakaTextStyles.statLabel
            )
        }
    }
}

@Composable
private fun RoleBadgeDefault(role: String) {
    val shapes = LocalCarakaShapes.current
    val label = when (role) {
        IdentityManager.ROLE_BPBD -> "BPBD"
        IdentityManager.ROLE_POLRI -> "POLRI"
        IdentityManager.ROLE_PMI -> "PMI"
        else -> stringResource(R.string.setup_role_civilian)
    }
    Box(
        modifier = Modifier
            .clip(shapes.sm)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), shapes.sm)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = CarakaTextStyles.serviceLabel
        )
    }
}
