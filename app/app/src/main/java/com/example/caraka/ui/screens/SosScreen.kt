package com.example.caraka.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.ui.components.HoldToConfirmButton
import com.example.caraka.ui.components.LocalSnackbar
import com.example.caraka.ui.components.PillShapeChip
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(viewModel: MainViewModel? = null, onBack: () -> Unit = {}) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var sosSent by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    var location by remember { mutableStateOf<Location?>(null) }

    val medicalLabel  = stringResource(R.string.sos_cat_medical)
    val fireLabel     = stringResource(R.string.sos_cat_fire)
    val securityLabel = stringResource(R.string.sos_cat_security)
    val disasterLabel = stringResource(R.string.sos_cat_disaster)
    val sentSnackTpl  = stringResource(R.string.snack_sos_sent)
    val pickFirstMsg  = stringResource(R.string.sos_select_category_first)

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

    // After SOS is sent, briefly show confirmation then go back.
    LaunchedEffect(sosSent) {
        if (sosSent) {
            kotlinx.coroutines.delay(1800L)
            onBack()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "sos")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.sos_title),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.sos_back),
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground,
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                HoldToConfirmButton(
                    label = stringResource(R.string.sos_broadcast_btn),
                    holdingLabel = stringResource(R.string.sos_confirming),
                    completedLabel = stringResource(R.string.sos_sent_title),
                    enabled = selectedCategory != null && !sosSent,
                    completed = sosSent,
                    holdDurationMs = 2000L,
                    onConfirm = {
                        selectedCategory?.let {
                            viewModel?.broadcastSos(category = it, description = description, lat = lat, lng = lng)
                            sosSent = true
                            snackbar.tryEmit(String.format(sentSnackTpl, viewModel?.meshNodeCount?.value ?: 1))
                        } ?: snackbar.tryEmit(pickFirstMsg)
                    }
                )
                if (selectedCategory == null && !sosSent) {
                    Spacer(Modifier.height(6.dp))
                    Text(pickFirstMsg, color = WarningYellow, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp))
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeonMint.copy(alpha = 0.12f))
                        .border(1.dp, NeonMint.copy(alpha = 0.4f * pulseAlpha + 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonMint, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.sos_sent_title), color = NeonMint, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(stringResource(R.string.sos_sent_subtitle), color = TextSecondary, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                stringResource(R.string.sos_question),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "Emergency category prompt" }
            )
            Spacer(modifier = Modifier.height(16.dp))

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PillShapeChip(text = medicalLabel,  isSelected = selectedCategory == "Medical",
                    onClick = { if (!sosSent) selectedCategory = "Medical" }, selectedColor = DangerRed)
                PillShapeChip(text = fireLabel,     isSelected = selectedCategory == "Fire",
                    onClick = { if (!sosSent) selectedCategory = "Fire" }, selectedColor = WarningYellow)
                PillShapeChip(text = securityLabel, isSelected = selectedCategory == "Security",
                    onClick = { if (!sosSent) selectedCategory = "Security" }, selectedColor = WarningYellow)
                PillShapeChip(text = disasterLabel, isSelected = selectedCategory == "Disaster",
                    onClick = { if (!sosSent) selectedCategory = "Disaster" }, selectedColor = DisasterBlue)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                stringResource(R.string.sos_details_label),
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
                placeholder = { Text(stringResource(R.string.sos_details_placeholder), color = TextSecondary) },
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
                stringResource(R.string.sos_location_label),
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
                            if (location != null) stringResource(R.string.sos_gps_detected)
                            else stringResource(R.string.sos_default_location),
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
                        if (location != null) stringResource(R.string.sos_gps_live)
                        else stringResource(R.string.sos_gps_default),
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
