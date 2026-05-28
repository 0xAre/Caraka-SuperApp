package com.example.caraka.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.crypto.IdentityManager
import com.example.caraka.ui.theme.*
import com.example.caraka.ui.util.rememberHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(onSetupComplete: (String, String) -> Unit) {
    var displayName by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(IdentityManager.ROLE_CIVILIAN) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val haptics = rememberHaptics()
    val isAuthority = selectedRole != IdentityManager.ROLE_CIVILIAN

    val authorityPasswords = mapOf(
        IdentityManager.ROLE_POLRI to "Presisi",
        IdentityManager.ROLE_PMI to "Sigap",
        IdentityManager.ROLE_BPBD to "Tangguh"
    )
    val authorityNames = mapOf(
        IdentityManager.ROLE_POLRI to "Polri Security",
        IdentityManager.ROLE_PMI to "PMI Medical",
        IdentityManager.ROLE_BPBD to "BPBD Response"
    )

    val roles = listOf(
        IdentityManager.ROLE_CIVILIAN to stringResource(R.string.setup_role_civilian),
        IdentityManager.ROLE_BPBD to stringResource(R.string.setup_role_bpbd),
        IdentityManager.ROLE_POLRI to stringResource(R.string.setup_role_polri),
        IdentityManager.ROLE_PMI to stringResource(R.string.setup_role_pmi)
    )
    val wrongPasswordMsg = stringResource(R.string.setup_password_wrong)
    val nameRequiredMsg = stringResource(R.string.setup_name_required)
    val showPasswordCd = stringResource(R.string.setup_show_password)
    val hidePasswordCd = stringResource(R.string.setup_hide_password)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
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
                    Text(stringResource(R.string.setup_welcome), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.setup_subtitle), color = TextSecondary, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(stringResource(R.string.setup_select_role), color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))

            roles.forEach { (roleValue, roleLabel) ->
                val isSelected = selectedRole == roleValue
                val isAuth = roleValue != IdentityManager.ROLE_CIVILIAN
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) SurfaceDark else NavyBackground)
                        .then(if (isSelected) Modifier.border(1.dp, AmberAccent, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable {
                            haptics.tick()
                            selectedRole = roleValue
                            password = ""
                            errorMessage = null
                        }
                        .padding(12.dp)
                        .semantics { contentDescription = "$roleLabel role option. ${if (isSelected) "selected" else "not selected"}" },
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
                            Text(stringResource(R.string.setup_requires_password), color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isAuthority) {
                Text(
                    text = stringResource(R.string.setup_password_label),
                    color = AmberAccent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = {
                        Text(
                            String.format(stringResource(R.string.setup_enter_password),
                                roles.find { it.first == selectedRole }?.second ?: ""),
                            color = TextSecondary
                        )
                    },
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
                                contentDescription = if (passwordVisible) hidePasswordCd else showPasswordCd,
                                tint = TextSecondary
                            )
                        }
                    },
                    singleLine = true,
                    isError = errorMessage != null
                )
            } else {
                Text(
                    text = stringResource(R.string.setup_display_name),
                    color = AmberAccent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; errorMessage = null },
                    label = { Text(stringResource(R.string.setup_enter_name), color = TextSecondary) },
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

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorMessage!!, color = DangerRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    haptics.light()
                    if (isAuthority) {
                        val correctPassword = authorityPasswords[selectedRole]
                        if (password == correctPassword) {
                            val authorityName = authorityNames[selectedRole] ?: "Authority"
                            onSetupComplete(authorityName, selectedRole)
                        } else {
                            errorMessage = wrongPasswordMsg
                        }
                    } else {
                        if (displayName.isBlank()) {
                            errorMessage = nameRequiredMsg
                        } else {
                            onSetupComplete(displayName.trim(), selectedRole)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (isAuthority) Icons.Default.Lock else Icons.Default.Person,
                    contentDescription = null,
                    tint = NavyBackground,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isAuthority) stringResource(R.string.setup_btn_authorize)
                    else stringResource(R.string.setup_btn_join),
                    color = NavyBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
