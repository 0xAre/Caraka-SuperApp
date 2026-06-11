package com.example.caraka.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.ui.components.HoldToConfirmButton
import com.example.caraka.ui.components.LocalSnackbar
import com.example.caraka.ui.theme.*
import com.example.caraka.ui.util.rememberHaptics
import com.example.caraka.viewmodel.MainViewModel

// Custom neon colors
val NeonRed = Color(0xFFFF2A55)
val NeonOrange = Color(0xFFFF8A00)
val NeonCyan = Color(0xFF00E5FF)
val NeonBlue = Color(0xFF00F0FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(viewModel: MainViewModel? = null, onBack: () -> Unit = {}) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var sosSent by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    var location by remember { mutableStateOf<Location?>(null) }
    var locationName by remember { mutableStateOf("") }
    val statusColors = LocalStatusColors.current

    val medicalLabel  = stringResource(R.string.sos_cat_medical)
    val fireLabel     = stringResource(R.string.sos_cat_fire)
    val securityLabel = stringResource(R.string.sos_cat_security)
    val disasterLabel = stringResource(R.string.sos_cat_disaster)
    val sentSnackTpl  = stringResource(R.string.snack_sos_sent)
    val pickFirstMsg  = stringResource(R.string.sos_select_category_first)
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            @SuppressLint("MissingPermission")
            fun tryGetLocation() {
                try {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                    location = loc
                    
                    loc?.let {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            geocoder.getFromLocation(it.latitude, it.longitude, 1) { addresses ->
                                val address = addresses.firstOrNull()
                                if (address != null) {
                                    val city = address.locality ?: address.subAdminArea ?: ""
                                    val country = address.countryName ?: ""
                                    if (city.isNotEmpty() && country.isNotEmpty()) {
                                        locationName = "$city,\n$country"
                                    }
                                }
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                            val address = addresses?.firstOrNull()
                            if (address != null) {
                                val city = address.locality ?: address.subAdminArea ?: ""
                                val country = address.countryName ?: ""
                                if (city.isNotEmpty() && country.isNotEmpty()) {
                                    locationName = "$city,\n$country"
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            tryGetLocation()
        }
    }

    val lat = location?.latitude ?: 0.0
    val lng = location?.longitude ?: 0.0

    val meshBannerMsg = stringResource(R.string.sos_mesh_sent_banner)
    val calmingMsg = stringResource(R.string.sos_calming_message)

    LaunchedEffect(sosSent) {
        if (sosSent) {
            snackbar.tryEmit(meshBannerMsg)
            kotlinx.coroutines.delay(3000L)
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
                        text = stringResource(R.string.sos_title).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.sos_back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasDark)
            )
        },
        containerColor = CanvasDark,
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                // Redesigned Bottom Button to match the mockup
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
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .drawBehind {
                            if (selectedCategory != null && !sosSent) {
                                // 3D Capsule Outer Glow
                                drawRoundRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(NeonRed.copy(alpha = 0.4f), Color.Transparent),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = size.width / 1.5f
                                    ),
                                    size = size.copy(width = size.width + 24.dp.toPx(), height = size.height + 24.dp.toPx()),
                                    topLeft = Offset(-12.dp.toPx(), -12.dp.toPx()),
                                    cornerRadius = CornerRadius(32.dp.toPx(), 32.dp.toPx())
                                )
                                // 3D Beveled Edge
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)
                                    ),
                                    size = size,
                                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (sosSent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(statusColors.online.copy(alpha = 0.12f))
                        .border(1.dp, statusColors.online.copy(alpha = 0.4f * pulseAlpha + 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusColors.online, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.sos_sent_title), color = statusColors.online, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(stringResource(R.string.sos_sent_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(meshBannerMsg, color = statusColors.online.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(calmingMsg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 2x2 Grid using Rows/Columns
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SosCategoryCard(
                        modifier = Modifier.weight(1f),
                        title = medicalLabel,
                        icon = Icons.Default.LocalHospital,
                        glowColor = NeonRed,
                        isSelected = selectedCategory == "Medical",
                        enabled = !sosSent,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); selectedCategory = "Medical" }
                    )
                    SosCategoryCard(
                        modifier = Modifier.weight(1f),
                        title = fireLabel,
                        icon = Icons.Default.LocalFireDepartment,
                        glowColor = NeonOrange,
                        isSelected = selectedCategory == "Fire",
                        enabled = !sosSent,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); selectedCategory = "Fire" }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SosCategoryCard(
                        modifier = Modifier.weight(1f),
                        title = securityLabel,
                        icon = Icons.Default.Warning,
                        glowColor = NeonCyan,
                        isSelected = selectedCategory == "Security",
                        enabled = !sosSent,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); selectedCategory = "Security" }
                    )
                    SosCategoryCard(
                        modifier = Modifier.weight(1f),
                        title = disasterLabel,
                        icon = Icons.Default.Waves,
                        glowColor = NeonBlue,
                        isSelected = selectedCategory == "Disaster",
                        enabled = !sosSent,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); selectedCategory = "Disaster" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Glassmorphism Text Field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.02f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                if (description.isEmpty()) {
                    Text(
                        text = stringResource(R.string.sos_describe_hint),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = description,
                    onValueChange = { if (!sosSent) description = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(NeonRed),
                    enabled = !sosSent,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Premium Glassmorphism Location Widget
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceHigh.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.sos_auto_location),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (locationPermissionGranted && location != null) {
                            Text(
                                "${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lng)}",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                if (!locationPermissionGranted) "Akses Ditolak" else "Mencari...",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.sos_coordinates),
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp
                        )
                    }

                    // Map Placeholder with Grid and Glowing Dot
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceLow)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw grid
                            val gridSpacing = 20.dp.toPx()
                            val lineColor = Color.White.copy(alpha = 0.1f)
                            for (i in 0..size.width.toInt() step gridSpacing.toInt()) {
                                drawLine(lineColor, Offset(i.toFloat(), 0f), Offset(i.toFloat(), size.height))
                            }
                            for (i in 0..size.height.toInt() step gridSpacing.toInt()) {
                                drawLine(lineColor, Offset(0f, i.toFloat()), Offset(size.width, i.toFloat()))
                            }

                            // Glowing dot in center
                            drawCircle(
                                color = NeonCyan.copy(alpha = 0.3f),
                                radius = 12.dp.toPx(),
                                center = Offset(size.width / 2, size.height / 2)
                            )
                            drawCircle(
                                color = NeonCyan,
                                radius = 4.dp.toPx(),
                                center = Offset(size.width / 2, size.height / 2)
                            )
                        }
                        Text(
                            if (locationName.isNotEmpty()) locationName else "Unknown\nLocation",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 11.sp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SosCategoryCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    glowColor: Color,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) glowColor.copy(alpha = 0.25f) else Color(0xFF1A1A1A),
        label = "bgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) glowColor else glowColor.copy(alpha = 0.6f),
        label = "borderColor"
    )
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .aspectRatio(1.2f)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = glowColor,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(24.dp)
                    .background(glowColor, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
