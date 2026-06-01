package com.example.caraka.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(viewModel: MainViewModel, onNavigateToChat: (String) -> Unit) {
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle(initialValue = emptyList())
    val lastMessagesPerPeer by viewModel.lastMessagesPerPeer.collectAsStateWithLifecycle(initialValue = emptyMap())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground
    ) { paddingValues ->
        if (connectedPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.messages_no_peers),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.messages_no_peers_hint),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(connectedPeers.size, key = { connectedPeers[it].id }) { index ->
                    val peer = connectedPeers[index]
                    val lastMsg = lastMessagesPerPeer[peer.id]
                    val justNow = stringResource(R.string.messages_just_now)
                    val minAgo = stringResource(R.string.messages_min_ago)
                    val timeStr = lastMsg?.let { msg ->
                        val diff = System.currentTimeMillis() - msg.timestamp
                        when {
                            diff < 60_000 -> justNow
                            diff < 3_600_000 -> "${diff / 60_000}$minAgo"
                            diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                            else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(msg.timestamp))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToChat(peer.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (peer.isAuthority) NeonMint.copy(alpha = 0.15f) else SurfaceDark)
                                .border(2.dp, if (peer.isAuthority) NeonMint.copy(alpha = 0.5f) else SurfaceDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (peer.isAuthority) Icons.Default.Verified else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (peer.isAuthority) NeonMint else AmberAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    peer.displayName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (timeStr != null) {
                                    Text(timeStr, color = TextSecondary, fontSize = 11.sp)
                                }
                            }

                            Spacer(Modifier.height(2.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (lastMsg != null) {
                                    if (!lastMsg.isIncoming) {
                                        Text(
                                            stringResource(R.string.messages_you_prefix),
                                            color = TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Text(
                                        lastMsg.content,
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(11.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(stringResource(R.string.messages_e2e_hint), color = TextSecondary, fontSize = 13.sp)
                                    }
                                }
                            }

                            // Role badge
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (peer.isAuthority) NeonMint.copy(alpha = 0.12f) else SurfaceDark)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(peer.role, color = if (peer.isAuthority) NeonMint else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NeonMint)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(stringResource(R.string.messages_connected), color = NeonMint, fontSize = 10.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = SurfaceDark.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
