package com.example.caraka.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.caraka.R
import com.example.caraka.ui.theme.NeonMint

@Composable
fun VerifiedBadge(
    modifier: Modifier = Modifier,
    tint: Color = NeonMint,
    size: Dp = 16.dp
) {
    val cd = stringResource(R.string.cd_verified)
    Icon(
        imageVector = Icons.Default.Verified,
        contentDescription = cd,
        tint = tint,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = cd }
    )
}
