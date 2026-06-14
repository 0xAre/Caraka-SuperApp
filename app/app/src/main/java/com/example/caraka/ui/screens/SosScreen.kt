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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import com.example.caraka.R
import com.example.caraka.ui.components.HoldToConfirmButton
import com.example.caraka.ui.components.LocalSnackbar
import com.example.caraka.ui.theme.*
import com.example.caraka.viewmodel.MainViewModel

// Semantic SOS category colors (operational, distinct — no neon)
private val SosMedicalColor  = CoralError          // red
private val SosFireColor     = Color(0xFFE8833A)   // muted orange
private val SosSecurityColor = TealPrimary         // blue (police/security)
private val SosDisasterColor = WarningCyan         // amber (natural disaster)

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
    val shapes = LocalCarakaShapes.current

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
                        text = stringResource(R.string.sos_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = CarakaTextStyles.dialogTitle,
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.sos_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedCategory == null && !sosSent) {
                    Text(
                        stringResource(R.string.sos_button_disabled_hint),
                        style = CarakaTextStyles.statusSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                }
                HoldToConfirmButton(
                    label = stringResource(R.string.sos_broadcast_btn),
                    holdingLabel = stringResource(R.string.sos_confirming),
                    completedLabel = stringResource(R.string.sos_sent_title),
                    enabled = selectedCategory != null && !sosSent,
                    completed = sosSent,
                    holdDurationMs = 2000L,
                    hintLabel = stringResource(R.string.sos_hold_hint),
                    onConfirm = {
                        selectedCategory?.let {
                            viewModel?.broadcastSos(category = it, description = description, lat = lat, lng = lng)
                            sosSent = true
                            snackbar.tryEmit(String.format(sentSnackTpl, viewModel?.meshNodeCount?.value ?: 1))
                        } ?: snackbar.tryEmit(pickFirstMsg)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Pilih jenis keadaan darurat",
                style = CarakaTextStyles.screenTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Informasi ini membantu peer memprioritaskan respons.",
                style = CarakaTextStyles.screenSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (sosSent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shapes.md)
                        .background(statusColors.online.copy(alpha = 0.12f))
                        .border(1.dp, statusColors.online.copy(alpha = 0.4f * pulseAlpha + 0.3f), shapes.md)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusColors.online, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.sos_sent_title), style = CarakaTextStyles.listTitle, color = statusColors.online)
                        Text(stringResource(R.string.sos_sent_subtitle), style = CarakaTextStyles.bodyDefault, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text(meshBannerMsg, style = CarakaTextStyles.statusPrimary, color = statusColors.online.copy(alpha = 0.9f))
                        Spacer(Modifier.height(4.dp))
                        Text(calmingMsg, style = CarakaTextStyles.statusSecondary, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 2x2 category grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SosCategoryCard(
                        modifier = Modifier.weight(1f),
                        title = medicalLabel,
                        icon = Icons.Default.LocalHospital,
                        illustrationRes = R.drawable.ill_sos_medical,
                        accentColor = SosMedicalColor,
                        isSelected = selectedCategory == "Medical",
                        enabled = !sosSent,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); selectedCategory = "Medical" }
                    )
                    SosCategoryCard(
                        modifier = Modifier.weight(1f),
                        title = fireLabel,
                        icon = Icons.Default.LocalFireDepartment,
                        illustrationRes = R.drawable.ill_sos_fire,
                        accentColor = SosFireColor,
                        isSelected = selectedCategory == "Fire",
                        enabled = !sosSent,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); selectedCategory = "Fire" }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SosCategoryCard(
                        modifier = Modifier.weight(1f),
                        title = securityLabel,
                        icon = Icons.Default.Warning,
                        illustrationRes = R.drawable.ill_sos_security,
                        accentColor = SosSecurityColor,
                        isSelected = selectedCategory == "Security",
                        enabled = !sosSent,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); selectedCategory = "Security" }
                    )
                    SosCategoryCard(
                        modifier = Modifier.weight(1f),
                        title = disasterLabel,
                        icon = Icons.Default.Waves,
                        illustrationRes = R.drawable.ill_sos_disaster,
                        accentColor = SosDisasterColor,
                        isSelected = selectedCategory == "Disaster",
                        enabled = !sosSent,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); selectedCategory = "Disaster" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description text field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(shapes.md)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shapes.md)
                    .padding(16.dp)
            ) {
                if (description.isEmpty()) {
                    Text(
                        text = stringResource(R.string.sos_describe_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = CarakaTextStyles.bodyDefault
                    )
                }
                BasicTextField(
                    value = description,
                    onValueChange = { if (!sosSent) description = it },
                    textStyle = CarakaTextStyles.bodyDefault.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    enabled = !sosSent,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location widget
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shapes.md)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shapes.md)
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.sos_auto_location),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = CarakaTextStyles.statLabel
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (locationPermissionGranted && location != null) {
                            Text(
                                "${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lng)}",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = CarakaTextStyles.monoData.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            )
                        } else {
                            Text(
                                if (!locationPermissionGranted) "Akses Ditolak" else "Mencari...",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = CarakaTextStyles.statusPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.sos_coordinates),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = CarakaTextStyles.listSubtitle
                        )
                    }

                    // Map placeholder with grid + position dot
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(shapes.sm)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        val dotColor = MaterialTheme.colorScheme.primary
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val gridSpacing = 18.dp.toPx()
                            for (i in 0..size.width.toInt() step gridSpacing.toInt()) {
                                drawLine(gridColor, Offset(i.toFloat(), 0f), Offset(i.toFloat(), size.height))
                            }
                            for (i in 0..size.height.toInt() step gridSpacing.toInt()) {
                                drawLine(gridColor, Offset(0f, i.toFloat()), Offset(size.width, i.toFloat()))
                            }
                            drawCircle(
                                color = dotColor.copy(alpha = 0.3f),
                                radius = 12.dp.toPx(),
                                center = Offset(size.width / 2, size.height / 2)
                            )
                            drawCircle(
                                color = dotColor,
                                radius = 4.dp.toPx(),
                                center = Offset(size.width / 2, size.height / 2)
                            )
                        }
                        Text(
                            if (locationName.isNotEmpty()) locationName else "Unknown\nLocation",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            style = CarakaTextStyles.badge,
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
    @DrawableRes illustrationRes: Int? = null,
    accentColor: Color,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shapes = LocalCarakaShapes.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.10f) else surfaceColor,
        label = "bgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant,
        label = "borderColor"
    )
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .heightIn(min = 104.dp)
            .scale(scale)
            .clip(shapes.md)
            .background(bgColor)
            .border(borderWidth, borderColor, shapes.md)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (illustrationRes != null) {
                Image(
                    painter = painterResource(illustrationRes),
                    contentDescription = title,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = CarakaTextStyles.sosCategoryTitle
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(24.dp)
                    .background(accentColor, CircleShape),
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
