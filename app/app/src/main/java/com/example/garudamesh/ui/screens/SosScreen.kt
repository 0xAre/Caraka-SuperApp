package com.example.garudamesh.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.garudamesh.ui.theme.*
import com.example.garudamesh.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(viewModel: MainViewModel? = null) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }

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
                            // Optionally clear state or show a snackbar here
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(12.dp),
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
            Spacer(modifier = Modifier.height(16.dp))
            
            // Categories 2x2 Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryButton(
                    title = "Medical", icon = Icons.Default.LocalHospital, color = DangerRed,
                    isSelected = selectedCategory == "Medical",
                    onClick = { selectedCategory = "Medical" },
                    modifier = Modifier.weight(1f)
                )
                CategoryButton(
                    title = "Fire", icon = Icons.Default.LocalFireDepartment, color = WarningYellow,
                    isSelected = selectedCategory == "Fire",
                    onClick = { selectedCategory = "Fire" },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryButton(
                    title = "Security", icon = Icons.Default.Warning, color = WarningYellow,
                    isSelected = selectedCategory == "Security",
                    onClick = { selectedCategory = "Security" },
                    modifier = Modifier.weight(1f)
                )
                CategoryButton(
                    title = "Disaster", icon = Icons.Default.Tsunami, color = DisasterBlue,
                    isSelected = selectedCategory == "Disaster",
                    onClick = { selectedCategory = "Disaster" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Description Input
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("Describe emergency... (Optional)", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark.copy(alpha = 0.5f),
                    unfocusedContainerColor = SurfaceDark.copy(alpha = 0.5f),
                    focusedBorderColor = DangerRed,
                    unfocusedBorderColor = SurfaceDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Location
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AUTO-DETECTED LOCATION", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("-6.2115, 106.8456", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Jakarta, Indonesia", color = TextSecondary, fontSize = 14.sp)
                }
                
                // Mini map placeholder
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavyBackground)
                        .border(1.dp, SurfaceDark, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun CategoryButton(
    title: String,
    icon: ImageVector,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) color.copy(alpha = 0.15f) else SurfaceDark)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) color else color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Preview
@Composable
fun PreviewSosScreen() {
    GarudaMeshTheme {
        SosScreen()
    }
}
