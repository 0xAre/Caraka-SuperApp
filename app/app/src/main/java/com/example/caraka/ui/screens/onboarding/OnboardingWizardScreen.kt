package com.example.caraka.ui.screens.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.caraka.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingWizardScreen(
    onRequestPermissions: () -> Unit,
    onComplete: (name: String, role: String) -> Unit
) {
    var stepIndex by remember { mutableStateOf(0) }
    var savedName by remember { mutableStateOf("") }
    var savedRole by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == stepIndex) 10.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == stepIndex) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (stepIndex > 0) {
                        IconButton(onClick = { stepIndex-- }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back_btn)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Crossfade(
            targetState = stepIndex,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            label = "onboardingStep"
        ) { currentStep ->
            when (currentStep) {
                0 -> WelcomeStep(onNext = { stepIndex = 1 })
                1 -> IdentityStep { name, role ->
                    savedName = name
                    savedRole = role
                    stepIndex = 2
                }
                2 -> PermissionRationaleStep(
                    onRequestPermissions = {
                        onRequestPermissions()
                        stepIndex = 3
                    }
                )
                3 -> FeatureHighlightStep(
                    onFinish = { onComplete(savedName, savedRole) }
                )
            }
        }
    }
}
