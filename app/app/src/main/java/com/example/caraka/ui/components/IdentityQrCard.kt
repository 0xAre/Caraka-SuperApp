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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.SurfaceDark
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AmberAccent.copy(0.2f))
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark)
            .border(1.dp, AmberAccent.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.QrCode2, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.qr_my_identity_title),
                color = AmberAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(
            visible = qrVisible && qrBitmap != null,
            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.85f)
        ) {
            qrBitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AmberAccent)
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassSurface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AmberAccent, modifier = Modifier.size(40.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(displayName.ifBlank { "—" }, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        roleBadge(role)
        Spacer(Modifier.height(8.dp))
        Text(
            peerId.take(24) + if (peerId.length > 24) "…" else "",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.qr_verbal_fingerprint, fingerprint),
            color = AmberAccent,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.qr_show_hint),
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.qr_trust_warning),
            color = TextSecondary.copy(alpha = 0.8f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun RoleBadgeDefault(role: String) {
    val (color, label) = when (role) {
        "BPBD" -> com.example.caraka.ui.theme.DisasterBlue to "🛡️ BPBD"
        "POLRI" -> AmberAccent to "🚔 POLRI"
        "PMI" -> com.example.caraka.ui.theme.DangerRed to "🏥 PMI"
        else -> com.example.caraka.ui.theme.NeonMint to "👤 Civilian"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
