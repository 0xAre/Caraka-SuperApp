package com.example.caraka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.NeonMint
import com.example.caraka.ui.theme.SurfaceDark
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary

@Composable
fun PeerListItem(
    displayName: String,
    role: String,
    isAuthority: Boolean,
    isConnected: Boolean,
    lastMessagePreview: String?,
    isOutgoingPreview: Boolean,
    timeLabel: String?,
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val itemCd = stringResource(
        R.string.peer_list_item_cd,
        displayName,
        unreadCount
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { contentDescription = itemCd },
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgedBox(
                badge = {
                    if (unreadCount > 0) {
                        Badge(containerColor = AmberAccent, contentColor = Color.Black) {
                            Text(
                                if (unreadCount > 99) "99+" else "$unreadCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isAuthority) NeonMint.copy(alpha = 0.15f) else SurfaceDark)
                        .border(
                            2.dp,
                            if (isAuthority) NeonMint.copy(alpha = 0.5f) else SurfaceDark,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAuthority) {
                        VerifiedBadge(size = 24.dp)
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (timeLabel != null) {
                        Text(
                            timeLabel,
                            color = if (unreadCount > 0) AmberAccent else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lastMessagePreview != null) {
                        if (isOutgoingPreview) {
                            Text(
                                stringResource(R.string.messages_you_prefix),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            lastMessagePreview,
                            color = if (unreadCount > 0) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                stringResource(R.string.messages_e2e_hint),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isAuthority) NeonMint.copy(alpha = 0.12f) else SurfaceDark)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            role,
                            color = if (isAuthority) NeonMint else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (isConnected) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NeonMint)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            stringResource(R.string.messages_connected),
                            color = NeonMint,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = SurfaceDark.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
