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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caraka.R
import com.example.caraka.data.local.entity.MessageEntity
import com.example.caraka.ui.components.LocalSnackbar
import com.example.caraka.ui.components.MessageStatusIcon
import com.example.caraka.ui.components.StickyComposer
import com.example.caraka.ui.components.VerifiedBadge
import com.example.caraka.ui.components.deriveMessageDeliveryStatus
import com.example.caraka.ui.prefs.UiPreferences
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel? = null,
    peerId: String,
    uiPrefs: UiPreferences? = null,
    onBack: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val chatFlow = remember(peerId) { viewModel?.getChatMessages(peerId) }
    val messages by chatFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val connectedPeers by viewModel?.connectedPeers?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    var flagTargetId by remember { mutableStateOf<String?>(null) }
    var showFlagDialog by remember { mutableStateOf(false) }

    val peer = connectedPeers.find { it.id == peerId }
    val snackbar = LocalSnackbar.current
    val sentMsg = stringResource(R.string.snack_message_sent)
    val flaggedMsg = stringResource(R.string.snack_flagged)
    val listState = rememberLazyListState()

    LaunchedEffect(peerId, uiPrefs) {
        uiPrefs?.setLastRead(peerId)
    }

    val messageCount = messages.size
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    val meshSubtitle = when {
        peer == null -> stringResource(R.string.chat_disconnected)
        peer.hopCount > 0 -> stringResource(R.string.chat_via_relay)
        else -> stringResource(R.string.chat_direct_peer)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                peer?.displayName ?: stringResource(R.string.chat_unknown_peer),
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (peer?.isAuthority == true) {
                                Spacer(modifier = Modifier.width(4.dp))
                                VerifiedBadge(size = 16.dp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (peer?.role != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (peer.isAuthority) NeonMint.copy(alpha = 0.12f) else SurfaceDark)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(peer.role, color = if (peer.isAuthority) NeonMint else TextSecondary, fontSize = 10.sp)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (peer != null) NeonMint else TextSecondary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(meshSubtitle, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_btn),
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        bottomBar = {
            StickyComposer(
                value = messageText,
                onValueChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank()) {
                        viewModel?.sendDirectMessage(peerId, messageText)
                        snackbar.tryEmit(sentMsg)
                        messageText = ""
                    }
                }
            )
        },
        containerColor = NavyBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark.copy(alpha = 0.5f))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.chat_e2e_banner), color = CyanAccent, fontSize = 12.sp)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(
                    count = messages.size,
                    key = { index -> messages[index].id }
                ) { index ->
                    val msg = messages[index]
                    val prevMsg = messages.getOrNull(index - 1)
                    val showDateSep = shouldShowDateSeparator(msg, prevMsg)

                    // Dynamic vertical spacing
                    if (index > 0) {
                        val spacing = if (showDateSep) 24.dp
                        else if (prevMsg?.isIncoming == msg.isIncoming && prevMsg?.senderName == msg.senderName) 4.dp
                        else 12.dp
                        Spacer(modifier = Modifier.height(spacing))
                    }

                    if (showDateSep) {
                        DateSeparator(timestamp = msg.timestamp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

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
                            time = timeFormatted,
                            deliveryStatus = deriveMessageDeliveryStatus(msg.deliveryStatus, msg.isIncoming)
                        )
                    }
                }
            }
        }
    }

    if (showFlagDialog && flagTargetId != null) {
        AlertDialog(
            onDismissRequest = { showFlagDialog = false },
            icon = { Icon(Icons.Default.Flag, contentDescription = null, tint = WarningCyan) },
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
                    colors = ButtonDefaults.buttonColors(containerColor = WarningCyan)
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

@Composable
private fun DateSeparator(timestamp: Long) {
    val label = remember(timestamp) {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)
        cal.timeInMillis = timestamp
        val msgDay = cal.get(Calendar.DAY_OF_YEAR)
        when {
            today == msgDay -> "Today"
            today - msgDay == 1 -> "Yesterday"
            else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

private fun shouldShowDateSeparator(current: MessageEntity, previous: MessageEntity?): Boolean {
    if (previous == null) return true
    val cal1 = Calendar.getInstance().apply { timeInMillis = previous.timestamp }
    val cal2 = Calendar.getInstance().apply { timeInMillis = current.timestamp }
    return cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR) ||
        cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR)
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
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(if (isAuthority) NeonMint.copy(alpha = 0.2f) else SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            if (isAuthority) {
                VerifiedBadge(size = 20.dp)
            } else {
                Icon(Icons.Default.Person, contentDescription = null, tint = CyanAccent)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                    .background(if (isFlagged) WarningCyan.copy(alpha = 0.08f) else GlassSurface)
                    .border(1.dp, SurfaceDark, RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPress?.invoke() }
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sender, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (isAuthority) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerifiedBadge(size = 14.dp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(message, color = TextPrimary, fontSize = 16.sp, lineHeight = 24.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(time, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        if (!isAuthority) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = stringResource(R.string.chat_hold_to_flag),
                                tint = TextSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
            if (isFlagged) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WarningCyan.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        String.format(stringResource(R.string.chat_flagged_by), flagCount),
                        color = WarningCyan,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OutgoingMessageBubble(
    message: String,
    time: String,
    deliveryStatus: com.example.caraka.ui.components.MessageDeliveryUiStatus =
        com.example.caraka.ui.components.MessageDeliveryUiStatus.SENT
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .shadow(4.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp), ambientColor = OutgoingChat, spotColor = OutgoingChat)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp))
                .background(OutgoingChat.copy(alpha = 0.9f))
                .border(1.dp, OutgoingChat, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column {
                Text(message, color = TextPrimary, fontSize = 16.sp, lineHeight = 24.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                    Text(time, color = TextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    MessageStatusIcon(status = deliveryStatus)
                }
            }
        }
    }
}
