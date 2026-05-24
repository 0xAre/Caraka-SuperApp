package com.example.caraka.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel? = null) {
    Box(
        modifier = Modifier.fillMaxSize().background(NavyBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Settings Screen", color = TextPrimary)
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { viewModel?.clearIdentity() },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
            ) {
                Text("Logout / Reset Identity", color = TextPrimary)
            }
        }
    }
}
