package com.example.caraka.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
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
import androidx.compose.ui.platform.LocalContext
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
fun SosScreen(viewModel: MainViewModel? = null, onBack: () -> Unit = {}) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var sosSent by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var location by remember { mutableStateOf<Location?>(null) }

    LaunchedEffect(Unit) {
        @SuppressLint("MissingPermission")
        fun tryGetLocation() {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            } catch (_: Exception) {}
        }
        tryGetLocation()
    }

    val lat = location?.latitude ?: -6.2115
    val lng = location?.longitude ?: 106.8456

    // After SOS is sent, show the green confirmation briefly then return to the previous screen.
    LaunchedEffect(sosSent) {
        if (sosSent) {
            kotlinx.coroutines.delay(1800L)
            onBack()
        }
    }

    // Pulsing animation for the SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "sos")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "EMERGENCY SOS",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                        if (!sosSent) {
                            selectedCategory?.let {
                                viewModel?.broadcastSos(category = it, description = description, lat = lat, lng = lng)
                                sosSent = true
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .graphicsLayer {
                            scaleX = if (selectedCategory != null && !sosSent) pulseScale else 1f
                            scaleY = if (selectedCategory != null && !sosSent) pulseScale else 1f
                        }
                        .shadow(
                            elevation = if (selectedCategory != null) 24.dp else 0.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = if (sosSent) NeonMint.copy(alpha = pulseAlpha) else DangerRed.copy(alpha = pulseAlpha),
                            spotColor = if (sosSent) NeonMint else DangerRed
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sosSent) NeonMint else DangerRed.copy(alpha = 0.9f),
                        disabledContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(24.dp),
                    enabled = selectedCategory != null
                ) {
                    if (sosSent) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SOS BROADCAST SENT", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BROADCAST SOS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
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

            if (sosSent) {
                // Confirmation state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeonMint.copy(alpha = 0.12f))
                        .border(1.dp, NeonMint.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonMint, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("SOS broadcast sent to the mesh!", color = NeonMint, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Help is on the way. Stay calm.", color = TextSecondary, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                "What is your emergency?",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PillShapeChip(
                    text = "🚨 Medical",
                    isSelected = selectedCategory == "Medical",
                    onClick = { if (!sosSent) selectedCategory = "Medical" },
                    selectedColor = DangerRed
                )
                PillShapeChip(
                    text = "🔥 Fire",
                    isSelected = selectedCategory == "Fire",
                    onClick = { if (!sosSent) selectedCategory = "Fire" },
                    selectedColor = WarningYellow
                )
                PillShapeChip(
                    text = "⚠️ Security",
                    isSelected = selectedCategory == "Security",
                    onClick = { if (!sosSent) selectedCategory = "Security" },
                    selectedColor = WarningYellow
                )
                PillShapeChip(
                    text = "🌊 Disaster",
                    isSelected = selectedCategory == "Disaster",
                    onClick = { if (!sosSent) selectedCategory = "Disaster" },
                    selectedColor = DisasterBlue
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Any additional details?",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { if (!sosSent) description = it },
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
                shape = RoundedCornerShape(24.dp),
                enabled = !sosSent
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Your location",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                        Text(
                            if (location != null) "GPS DETECTED" else "DEFAULT LOCATION",
                            color = if (location != null) NeonMint else WarningYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (location != null) "Live GPS position" else "Jakarta, Indonesia",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NavyBackground)
                        .border(1.dp, SurfaceDark, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (location != null) Icons.Default.GpsFixed else Icons.Default.Map,
                        contentDescription = null,
                        tint = if (location != null) NeonMint else TextSecondary
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewSosScreen() {
    CarakaTheme {
        SosScreen()
    }
}
