package com.example.garudamesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.garudamesh.ui.theme.*
import com.example.garudamesh.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel? = null) {
    val activeAlerts by viewModel?.activeAlerts?.collectAsStateWithLifecycle(initialValue = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val connectedNodes by viewModel?.connectedPeerCount?.collectAsStateWithLifecycle(initialValue = 0) ?: remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = AmberAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GARUDA MESH",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = AmberAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBackground
                )
            )
        },
        containerColor = NavyBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            ConnectionBanner(connectedNodes)
            
            Spacer(modifier = Modifier.height(32.dp))
            SosButtonBig(onClick = { /* TODO Navigate to SOS */ })
            
            Spacer(modifier = Modifier.height(32.dp))
            ActiveAlertsSection(activeAlerts)
        }
    }
}

@Composable
fun ConnectionBanner(connectedNodes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, AmberAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(DangerRed)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "MESH MODE ACTIVE - $connectedNodes Nodes Connected",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SosButtonBig(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(SurfaceDark.copy(alpha = 0.5f))
            .border(2.dp, SurfaceDark, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Outer glow/ring
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .border(4.dp, DangerRed.copy(alpha = 0.5f), CircleShape)
        )
        // Inner Button
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(DangerRed)
                .shadow(8.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SOS",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "EMERGENCY",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ActiveAlertsSection(alerts: List<com.example.garudamesh.data.local.entity.MessageEntity>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Active Alerts",
            color = AmberAccent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (alerts.isEmpty()) {
                item {
                    Text("No active alerts nearby.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                items(alerts.size) { index ->
                    val alert = alerts[index]
                    AlertCard(
                        title = alert.content,
                        sender = "${alert.senderName} (${alert.senderRole})",
                        time = "Just now",
                        icon = if (alert.sosCategory == "MEDICAL") Icons.Default.LocalHospital else Icons.Default.Warning,
                        iconTint = if (alert.sosCategory == "MEDICAL") DangerRed else WarningYellow
                    )
                }
            }
        }
    }
}

@Composable
fun AlertCard(
    title: String,
    sender: String,
    time: String,
    icon: ImageVector,
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, iconTint.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = sender, color = TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = time, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Preview
@Composable
fun PreviewHomeScreen() {
    GarudaMeshTheme {
        HomeScreen()
    }
}
