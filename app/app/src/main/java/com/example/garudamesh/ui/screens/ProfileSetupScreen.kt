package com.example.garudamesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.garudamesh.crypto.IdentityManager
import com.example.garudamesh.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(onSetupComplete: (String, String) -> Unit) {
    var displayName by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(IdentityManager.ROLE_CIVILIAN) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isAuthority = selectedRole != IdentityManager.ROLE_CIVILIAN

    // Authority passwords
    val authorityPasswords = mapOf(
        IdentityManager.ROLE_POLRI to "Presisi",
        IdentityManager.ROLE_PMI to "Sigap",
        IdentityManager.ROLE_BPBD to "Tangguh"
    )

    // Authority display names (fixed)
    val authorityNames = mapOf(
        IdentityManager.ROLE_POLRI to "Polri Security",
        IdentityManager.ROLE_PMI to "PMI Medical",
        IdentityManager.ROLE_BPBD to "BPBD Response"
    )

    val roles = listOf(
        IdentityManager.ROLE_CIVILIAN to "Civilian",
        IdentityManager.ROLE_BPBD to "BPBD Response",
        IdentityManager.ROLE_POLRI to "Polri Security",
        IdentityManager.ROLE_PMI to "PMI Medical"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PROFILE SETUP", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Welcome to Garuda Mesh",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Set up your identity before joining the network.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Role Selection
            Text("Select Role:", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))

            roles.forEach { (roleValue, roleLabel) ->
                val isSelected = selectedRole == roleValue
                val isAuth = roleValue != IdentityManager.ROLE_CIVILIAN
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) SurfaceDark else NavyBackground)
                        .then(
                            if (isSelected) Modifier.border(1.dp, AmberAccent, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable {
                            selectedRole = roleValue
                            password = ""
                            errorMessage = null
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else if (isAuth) Icons.Default.Lock else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (isSelected) AmberAccent else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(roleLabel, color = if (isSelected) TextPrimary else TextSecondary)
                        if (isAuth) {
                            Text("Requires authorization password", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Conditional fields based on role
            if (isAuthority) {
                // Password field for authority roles
                Text(
                    text = "Authorization Password",
                    color = AmberAccent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Enter password for ${roles.find { r -> r.first == selectedRole }?.second ?: ""}", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberAccent,
                        unfocusedBorderColor = SurfaceDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        errorBorderColor = DangerRed
                    ),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = TextSecondary
                            )
                        }
                    },
                    singleLine = true,
                    isError = errorMessage != null
                )
            } else {
                // Display name field for civilians
                Text(
                    text = "Display Name",
                    color = AmberAccent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        errorMessage = null
                    },
                    label = { Text("Enter your name", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberAccent,
                        unfocusedBorderColor = SurfaceDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        errorBorderColor = DangerRed
                    ),
                    singleLine = true,
                    isError = errorMessage != null
                )
            }

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = DangerRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Join button
            Button(
                onClick = {
                    if (isAuthority) {
                        val correctPassword = authorityPasswords[selectedRole]
                        if (password == correctPassword) {
                            val authorityName = authorityNames[selectedRole] ?: "Authority"
                            onSetupComplete(authorityName, selectedRole)
                        } else {
                            errorMessage = "Wrong password! Access denied."
                        }
                    } else {
                        if (displayName.isBlank()) {
                            errorMessage = "Display name is required for Civilian role."
                        } else {
                            onSetupComplete(displayName.trim(), selectedRole)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    if (isAuthority) Icons.Default.Lock else Icons.Default.Person,
                    contentDescription = null,
                    tint = NavyBackground,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isAuthority) "AUTHORIZE & JOIN" else "JOIN NETWORK",
                    color = NavyBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
