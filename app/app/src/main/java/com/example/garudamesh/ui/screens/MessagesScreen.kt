package com.example.garudamesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.garudamesh.ui.theme.*
import com.example.garudamesh.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(viewModel: MainViewModel, onNavigateToChat: (String) -> Unit) {
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MESSAGES", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground
    ) { paddingValues ->
        if (connectedPeers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No peers connected for chat.", color = TextSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                items(connectedPeers.size) { index ->
                    val peer = connectedPeers[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToChat(peer.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(SurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = AmberAccent)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(peer.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Role: ${peer.role}", color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                    Divider(color = SurfaceDark)
                }
            }
        }
    }
}
