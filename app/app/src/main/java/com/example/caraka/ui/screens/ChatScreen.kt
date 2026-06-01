package com.example.caraka.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.ui.components.LocalSnackbar
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MainViewModel
import com.example.caraka.ui.components.ChatInputBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(viewModel: MainViewModel? = null, peerId: String, onBack: () -> Unit) {
    var messageText by remember { mutableStateOf("") }
    val chatFlow = remember(peerId) { viewModel?.getChatMessages(peerId) }
    val messages by chatFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val connectedPeers by viewModel?.connectedPeers?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }

    // Flag context menu state
    var flagTargetId by remember { mutableStateOf<String?>(null) }
    var showFlagDialog by remember { mutableStateOf(false) }

    val peer = connectedPeers.find { it.id == peerId }
    val snackbar = LocalSnackbar.current
    val sentMsg = stringResource(R.string.snack_message_sent)
    val flaggedMsg = stringResource(R.string.snack_flagged)

    val listState = rememberLazyListState()

    // Auto-scroll only when a NEW message arrives, not on every recomposition
    val messageCount = messages.size
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(peer?.displayName ?: "Unknown Peer", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            if (peer?.isAuthority == true) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified,
                                    contentDescription = stringResource(R.string.cd_verified),
                                    tint = NeonMint, modifier = Modifier.size(16.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (peer != null) NeonMint else TextSecondary))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (peer != null) stringResource(R.string.chat_connected_mesh) else stringResource(R.string.chat_disconnected),
                                color = TextSecondary, fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_btn),
                            tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        bottomBar = {
            ChatInputBar(
                value = messageText,
                onValueChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank()) {
                        viewModel?.sendDirectMessage(peerId, messageText)
                        snackbar.tryEmit(sentMsg)
                        messageText = ""
                    }
                },
                modifier = Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            )
        },
        containerColor = NavyBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // E2E Encryption Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark.copy(alpha = 0.5f))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.chat_e2e_banner), color = AmberAccent, fontSize = 12.sp)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    count = messages.size,
                    key = { index -> messages[index].id }
                ) { index ->
                    val msg = messages[index]
                    val timeFormatted = remember(msg.timestamp) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                    }
                    if (msg.isIncoming) {
                        IncomingMessageBubble(
                            sender = msg.senderName,
                            message = msg.content,
                            time = timeFormatted,
                            isFlagged = msg.flagCount >= 3,
                            flagCount = msg.flagCount,
                            isAuthority = peer?.isAuthority == true,
                            onLongPress = {
                                flagTargetId = msg.id
                                showFlagDialog = true
                            }
                        )
                    } else {
                        OutgoingMessageBubble(
                            message = msg.content,
                            time = timeFormatted
                        )
                    }
                }
            }
        }
    }

    // ── Flag confirmation dialog ───────────────────────────────────────────
    if (showFlagDialog && flagTargetId != null) {
        AlertDialog(
            onDismissRequest = { showFlagDialog = false },
            icon = { Icon(Icons.Default.Flag, contentDescription = null, tint = WarningYellow) },
            title = { Text(stringResource(R.string.chat_flag_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.chat_flag_desc), color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        flagTargetId?.let { viewModel?.flagMessage(it) }
                        snackbar.tryEmit(flaggedMsg)
                        showFlagDialog = false
                        flagTargetId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningYellow)
                ) { Text(stringResource(R.string.chat_flag_confirm), color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showFlagDialog = false }) {
                    Text(stringResource(R.string.chat_flag_cancel), color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IncomingMessageBubble(
    sender: String,
    message: String,
    time: String,
    isFlagged: Boolean = false,
    flagCount: Int = 0,
    isAuthority: Boolean = false,
    onLongPress: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        // Avatar
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(if (isAuthority) NeonMint.copy(alpha = 0.2f) else SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isAuthority) Icons.Default.Verified else Icons.Default.Person,
                contentDescription = null,
                tint = if (isAuthority) NeonMint else AmberAccent
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                    .background(if (isFlagged) WarningYellow.copy(alpha = 0.08f) else GlassSurface)
                    .border(1.dp, SurfaceDark, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPress?.invoke() }
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sender, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (isAuthority) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Verified, contentDescription = "Verified", tint = NeonMint, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(message, color = TextPrimary, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(time, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (!isAuthority) {
                        Icon(Icons.Default.Flag,
                            contentDescription = stringResource(R.string.chat_hold_to_flag),
                            tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        // Warning badge when flagged by 3+ users
        if (isFlagged) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(WarningYellow.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    String.format(stringResource(R.string.chat_flagged_by), flagCount),
                    color = WarningYellow, fontSize = 12.sp
                )
            }
        }
        }
    }
}

@Composable
fun OutgoingMessageBubble(message: String, time: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .shadow(4.dp, RoundedCornerShape(topStart = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp), ambientColor = OutgoingChat, spotColor = OutgoingChat)
                .clip(RoundedCornerShape(topStart = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                .background(OutgoingChat.copy(alpha = 0.9f))
                .border(1.dp, OutgoingChat, RoundedCornerShape(topStart = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(message, color = TextPrimary, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                    Text(time, color = TextSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.DoneAll,
                        contentDescription = stringResource(R.string.chat_sent_via_mesh),
                        tint = NeonMint.copy(alpha = 0.9f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        stringResource(R.string.chat_mesh_label),
                        color = NeonMint.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
