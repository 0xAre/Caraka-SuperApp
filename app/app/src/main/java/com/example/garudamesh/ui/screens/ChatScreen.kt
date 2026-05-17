package com.example.garudamesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.garudamesh.ui.theme.*
import com.example.garudamesh.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: MainViewModel? = null, peerId: String, onBack: () -> Unit) {
    var messageText by remember { mutableStateOf("") }
    val chatFlow = remember(peerId) { viewModel?.getChatMessages(peerId) }
    val messages by chatFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val connectedPeers by viewModel?.connectedPeers?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    
    val peer = connectedPeers.find { it.id == peerId }

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
//... (keep top bar same)
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(peer?.displayName ?: "Unknown Peer", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            if (peer?.isAuthority == true) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, contentDescription = "Verified", tint = ActiveGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (peer != null) ActiveGreen else TextSecondary))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (peer != null) "Connected via Mesh" else "Disconnected", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyBackground)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark.copy(alpha = 0.5f),
                        unfocusedContainerColor = SurfaceDark.copy(alpha = 0.5f),
                        focusedBorderColor = SurfaceDark,
                        unfocusedBorderColor = SurfaceDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = { 
                        if (messageText.isNotBlank()) {
                            viewModel?.sendDirectMessage(peerId, messageText)
                            messageText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AmberAccent)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
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
                Text("End-to-end encrypted", color = AmberAccent, fontSize = 12.sp)
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
                            isAuthority = peer?.isAuthority == true
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
}

@Composable
fun IncomingMessageBubble(sender: String, message: String, time: String, isFlagged: Boolean = false, isAuthority: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        // Avatar Placeholder
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.LocalHospital, contentDescription = null, tint = DangerRed)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                    .background(IncomingChat)
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sender, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (isAuthority) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Verified, contentDescription = "Verified", tint = ActiveGreen, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(message, color = TextPrimary, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(time, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
                }
            }
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
                    Text("Flagged by users", color = WarningYellow, fontSize = 12.sp)
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
                .clip(RoundedCornerShape(topStart = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                .background(OutgoingChat)
                .padding(12.dp)
        ) {
            Column {
                Text(message, color = TextPrimary, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(time, color = TextSecondary.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
