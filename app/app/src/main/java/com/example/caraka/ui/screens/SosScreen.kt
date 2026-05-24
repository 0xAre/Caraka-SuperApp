package com.example.caraka.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MainViewModel
import com.example.caraka.ui.components.PillShapeChip
import androidx.compose.animation.core.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(viewModel: MainViewModel? = null) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }

    // Pulsing animation for the SOS button
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "EMERGENCY SOS",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp), // Center title roughly
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* TODO back */ }) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground,
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { 
                        selectedCategory?.let {
                            viewModel?.broadcastSos(category = it, description = description, lat = -6.2115, lng = 106.8456)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .graphicsLayer {
                            scaleX = if (selectedCategory != null) pulseScale else 1f
                            scaleY = if (selectedCategory != null) pulseScale else 1f
                        }
                        .shadow(
                            elevation = if (selectedCategory != null) 24.dp else 0.dp, 
                            shape = RoundedCornerShape(24.dp), 
                            ambientColor = DangerRed.copy(alpha = pulseAlpha), 
                            spotColor = DangerRed
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed.copy(alpha = 0.9f),
                        disabledContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(24.dp),
                    enabled = selectedCategory != null
                ) {
                    Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("BROADCAST SOS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Conversational Title
            Text(
                "What is your emergency?",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Categories Row using PillShapeChip
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PillShapeChip(
                    text = "🚨 Medical",
                    isSelected = selectedCategory == "Medical",
                    onClick = { selectedCategory = "Medical" },
                    selectedColor = DangerRed
                )
                PillShapeChip(
                    text = "🔥 Fire",
                    isSelected = selectedCategory == "Fire",
                    onClick = { selectedCategory = "Fire" },
                    selectedColor = WarningYellow
                )
                PillShapeChip(
                    text = "⚠️ Security",
                    isSelected = selectedCategory == "Security",
                    onClick = { selectedCategory = "Security" },
                    selectedColor = WarningYellow
                )
                PillShapeChip(
                    text = "🌊 Disaster",
                    isSelected = selectedCategory == "Disaster",
                    onClick = { selectedCategory = "Disaster" },
                    selectedColor = DisasterBlue
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Conversational Prompt 2
            Text(
                "Any additional details?",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Description Input
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("E.g., 2 people injured, need ambulance...", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = GlassSurface,
                    unfocusedContainerColor = GlassSurface,
                    focusedBorderColor = AmberAccent,
                    unfocusedBorderColor = SurfaceDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Conversational Prompt 3
            Text(
                "Your location will be sent",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Location
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = NeonMint, spotColor = SurfaceDark)
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassSurface)
                    .border(1.dp, SurfaceDark, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = NeonMint, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AUTO-DETECTED LOCATION", color = NeonMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("-6.2115, 106.8456", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Jakarta, Indonesia", color = TextSecondary, fontSize = 14.sp)
                }
                
                // Mini map placeholder
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NavyBackground)
                        .border(1.dp, SurfaceDark, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = TextSecondary)
                }
            }
        }
    }
}

// CategoryButton removed as we use PillShapeChip instead

@Preview
@Composable
fun PreviewSosScreen() {
    CarakaTheme {
        SosScreen()
    }
}
