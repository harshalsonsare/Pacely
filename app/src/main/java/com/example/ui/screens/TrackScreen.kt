package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DoNotDisturbOnTotalSilence
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ShoeEntity
import com.example.data.model.ActivityType
import com.example.data.model.TrackingStatus
import com.example.data.model.TrackingUiState
import com.example.tracking.GeoMath
import com.example.ui.components.CanvasPoint
import com.example.ui.components.OsmRouteMap
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.ElectricOrangeContainer
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MetricCardStyle
import com.example.ui.theme.MetricCompactStyle
import com.example.ui.theme.MetricHeroStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WalkCyan
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.TrackingViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TrackScreen(
    viewModel: TrackingViewModel,
    onWorkoutSaved: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shoes by viewModel.shoes.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startCompass()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopCompass()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.startCompass()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopCompass()
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showFinishDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            viewModel.warmUpGps(context)
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.warmUpGps(context)
        }
    }

    val requestTrackingPermissions = {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    val accentColor = if (state.activityType == ActivityType.RUN) ElectricOrange else WalkCyan
    val canvasPoints = remember(state.livePoints) {
        state.livePoints.map {
            CanvasPoint(
                latitude = it.latitude,
                longitude = it.longitude,
                segmentIndex = it.segmentIndex,
                altitudeMeters = it.altitudeMeters,
                speedMps = it.speedMps
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp)
    ) {
        TrackHeader(
            activityType = state.activityType,
            isLocked = state.status != TrackingStatus.IDLE,
            accentColor = accentColor,
            onSelectType = viewModel::selectActivityType
        )

        // Quick Pills: Auto-Pause toggle + Active Shoe Selector
        QuickTrackingBar(
            autoPauseEnabled = state.autoPauseEnabled,
            isAutoPaused = state.status == TrackingStatus.PAUSED && state.isAutoPaused,
            shoes = shoes.filter { !it.isRetired },
            selectedShoeId = state.selectedShoeId,
            onToggleAutoPause = { viewModel.setAutoPauseEnabled(!state.autoPauseEnabled) },
            onSelectShoe = viewModel::selectShoe,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        AnimatedVisibility(visible = state.status == TrackingStatus.PAUSED && state.isAutoPaused) {
            AutoPausedBanner(
                thresholdMps = state.autoPauseSpeedThresholdMps,
                onResumeNow = { viewModel.togglePauseResume(context) },
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        AnimatedVisibility(visible = !hasLocationPermission) {
            PermissionBanner(
                onRequestPermission = requestTrackingPermissions,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWide = maxWidth >= 680.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OsmRouteMap(
                        points = canvasPoints,
                        accentColor = accentColor,
                        isLive = true,
                        isTracking = state.status == TrackingStatus.TRACKING,
                        avgPaceSecPerKm = state.avgPaceSecPerKm,
                        gpsAccuracyMeters = state.gpsAccuracyMeters,
                        lastKnownLocation = state.lastKnownLocation,
                        headingDegrees = state.activeHeadingDegrees,
                        headingSourceMode = state.headingSourceMode,
                        isUsingGpsBearing = state.isUsingGpsBearing,
                        needsCompassCalibration = state.needsCompassCalibration,
                        isDarkMapMode = state.darkMapModeEnabled,
                        onToggleHeadingSource = viewModel::toggleHeadingSourceMode,
                        onToggleDarkMapMode = viewModel::toggleDarkMapMode,
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight()
                            .padding(bottom = 16.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryDeck(state = state, accentColor = accentColor)
                        Spacer(modifier = Modifier.height(14.dp))
                        WorkoutControls(
                            status = state.status,
                            isAutoPaused = state.isAutoPaused,
                            activityType = state.activityType,
                            accentColor = accentColor,
                            hasPermission = hasLocationPermission,
                            onGrantPermission = requestTrackingPermissions,
                            onStart = { viewModel.startWorkout(context) },
                            onTogglePause = { viewModel.togglePauseResume(context) },
                            onRequestStop = { showFinishDialog = true }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OsmRouteMap(
                        points = canvasPoints,
                        accentColor = accentColor,
                        isLive = true,
                        isTracking = state.status == TrackingStatus.TRACKING,
                        avgPaceSecPerKm = state.avgPaceSecPerKm,
                        gpsAccuracyMeters = state.gpsAccuracyMeters,
                        lastKnownLocation = state.lastKnownLocation,
                        headingDegrees = state.activeHeadingDegrees,
                        headingSourceMode = state.headingSourceMode,
                        isUsingGpsBearing = state.isUsingGpsBearing,
                        needsCompassCalibration = state.needsCompassCalibration,
                        isDarkMapMode = state.darkMapModeEnabled,
                        onToggleHeadingSource = viewModel::toggleHeadingSourceMode,
                        onToggleDarkMapMode = viewModel::toggleDarkMapMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(270.dp)
                    )

                    TelemetryDeck(state = state, accentColor = accentColor)

                    WorkoutControls(
                        status = state.status,
                        isAutoPaused = state.isAutoPaused,
                        activityType = state.activityType,
                        accentColor = accentColor,
                        hasPermission = hasLocationPermission,
                        onGrantPermission = requestTrackingPermissions,
                        onStart = { viewModel.startWorkout(context) },
                        onTogglePause = { viewModel.togglePauseResume(context) },
                        onRequestStop = { showFinishDialog = true }
                    )
                }
            }
        }
    }

    if (showFinishDialog) {
        val isVeryShort = state.distanceMeters < 20.0 && state.livePoints.size < 2
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            containerColor = CarbonSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = if (isVeryShort) "Workout Too Short" else "Finish ${state.activityType.label}?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = if (isVeryShort) {
                        "Less than 20 meters recorded. Discard this session or resume tracking?"
                    } else {
                        "Save ${GeoMath.formatDistanceKm(state.distanceMeters)} km (${GeoMath.formatElapsedTime(state.elapsedMillis)}) to your offline log?"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                if (!isVeryShort) {
                    Button(
                        onClick = {
                            showFinishDialog = false
                            viewModel.stopAndSaveWorkout(context) { savedId ->
                                if (savedId != null) onWorkoutSaved(savedId)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.testTag("confirm_save_workout_button")
                    ) {
                        Text("Save Workout")
                    }
                } else {
                    TextButton(
                        onClick = {
                            showFinishDialog = false
                            viewModel.discardWorkout(context)
                        },
                        modifier = Modifier.testTag("discard_workout_button")
                    ) {
                        Text("Discard", color = DangerRed)
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isVeryShort) {
                        TextButton(
                            onClick = {
                                showFinishDialog = false
                                viewModel.discardWorkout(context)
                            }
                        ) {
                            Text("Discard", color = DangerRed)
                        }
                    }
                    TextButton(onClick = { showFinishDialog = false }) {
                        Text("Keep Going", color = TextPrimary)
                    }
                }
            }
        )
    }
}

@Composable
private fun QuickTrackingBar(
    autoPauseEnabled: Boolean,
    isAutoPaused: Boolean,
    shoes: List<ShoeEntity>,
    selectedShoeId: Long?,
    onToggleAutoPause: () -> Unit,
    onSelectShoe: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var shoeMenuExpanded by remember { mutableStateOf(false) }
    val selectedShoe = shoes.firstOrNull { it.id == selectedShoeId }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (isAutoPaused) WarningAmber.copy(alpha = 0.2f)
            else if (autoPauseEnabled) ElectricOrangeContainer
            else CarbonSurface,
            shape = RoundedCornerShape(50),
            border = BorderStroke(
                1.dp,
                when {
                    isAutoPaused -> WarningAmber
                    autoPauseEnabled -> ElectricOrange.copy(alpha = 0.55f)
                    else -> HairlineBorder
                }
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onToggleAutoPause)
                .testTag("auto_pause_toggle")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PauseCircle,
                    contentDescription = null,
                    tint = when {
                        isAutoPaused -> WarningAmber
                        autoPauseEnabled -> ElectricOrange
                        else -> TextSecondary
                    },
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isAutoPaused -> "AUTO-PAUSED"
                        autoPauseEnabled -> "Auto-Pause ON"
                        else -> "Auto-Pause OFF"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isAutoPaused -> WarningAmber
                        autoPauseEnabled -> TextPrimary
                        else -> TextSecondary
                    }
                )
            }
        }

        if (shoes.isNotEmpty()) {
            Box {
                Surface(
                    color = CarbonSurface,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { shoeMenuExpanded = true }
                        .testTag("track_shoe_selector")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DoNotDisturbOnTotalSilence,
                            contentDescription = null,
                            tint = WalkCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedShoe?.let { "${it.brand} ${it.name}" } ?: "No Shoe",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }
                DropdownMenu(
                    expanded = shoeMenuExpanded,
                    onDismissRequest = { shoeMenuExpanded = false },
                    containerColor = SlateElevated
                ) {
                    DropdownMenuItem(
                        text = { Text("No Shoe", color = TextSecondary) },
                        onClick = {
                            onSelectShoe(null)
                            shoeMenuExpanded = false
                        }
                    )
                    shoes.forEach { shoe ->
                        DropdownMenuItem(
                            text = { Text("${shoe.brand} ${shoe.name}", color = TextPrimary) },
                            onClick = {
                                onSelectShoe(shoe.id)
                                shoeMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackHeader(
    activityType: ActivityType,
    isLocked: Boolean,
    accentColor: Color,
    onSelectType: (ActivityType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "PACELY",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "OFFLINE GPS TELEMETRY",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )
        }

        Surface(
            color = CarbonSurface,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, HairlineBorder)
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActivityType.entries.forEach { type ->
                    val selected = activityType == type
                    val pillColor = if (type == ActivityType.RUN) ElectricOrange else WalkCyan
                    val icon = if (type == ActivityType.RUN) {
                        Icons.AutoMirrored.Rounded.DirectionsRun
                    } else {
                        Icons.AutoMirrored.Rounded.DirectionsWalk
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) pillColor else Color.Transparent)
                            .clickable(enabled = !isLocked) { onSelectType(type) }
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("toggle_${type.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = type.label,
                            tint = if (selected) ObsidianBg else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = type.label.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) ObsidianBg else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Activity locked while active",
                        tint = TextSecondary,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoPausedBanner(
    thresholdMps: Float,
    onResumeNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = WarningAmber.copy(alpha = 0.16f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.75f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("auto_paused_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PauseCircle,
                    contentDescription = "Auto-Paused",
                    tint = WarningAmber
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "AUTO-PAUSED",
                        style = MaterialTheme.typography.titleMedium,
                        color = WarningAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "Speed below %.1f m/s — auto-resumes on movement",
                            thresholdMps
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            TextButton(onClick = onResumeNow) {
                Text("Resume", color = WarningAmber, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ElectricOrangeContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ElectricOrange.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = ElectricOrange
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "GPS Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Enable precise location to track distance, pace, and routes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricOrange),
                modifier = Modifier.testTag("grant_gps_button")
            ) {
                Text("Allow", color = TextPrimary)
            }
        }
    }
}

@Composable
private fun TelemetryDeck(
    state: TrackingUiState,
    accentColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = CarbonSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ELAPSED TIME",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = GeoMath.formatElapsedTime(state.elapsedMillis),
                        style = MetricHeroStyle,
                        color = TextPrimary,
                        modifier = Modifier.testTag("metric_elapsed_time")
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "DISTANCE",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = GeoMath.formatDistanceKm(state.distanceMeters),
                            style = MetricHeroStyle,
                            color = accentColor,
                            modifier = Modifier.testTag("metric_distance_km")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "km",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                icon = Icons.Rounded.Speed,
                label = "CURRENT PACE",
                value = GeoMath.formatPace(state.currentPaceSecPerKm),
                unit = "/km",
                accent = accentColor,
                tag = "metric_current_pace",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Rounded.Timer,
                label = "AVG PACE",
                value = GeoMath.formatPace(state.avgPaceSecPerKm),
                unit = "/km",
                accent = TextSecondary,
                tag = "metric_avg_pace",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                icon = Icons.Rounded.LocalFireDepartment,
                label = "CALORIES",
                value = "${state.caloriesBurned.roundToInt()}",
                unit = "kcal",
                accent = ElectricOrange,
                tag = "metric_calories",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Rounded.Terrain,
                label = "ELEV GAIN",
                value = "+${state.elevationGainMeters.roundToInt()}",
                unit = "m",
                accent = GpsEmerald,
                tag = "metric_elevation_gain",
                modifier = Modifier.weight(1f)
            )
        }

        Surface(
            color = SlateElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            val splitTargetMeters = (state.splitIntervalKm * 1000.0).coerceAtLeast(100.0)
            val intervalText = String.format(Locale.US, "%.2f", state.splitIntervalKm)
                .trimEnd('0').trimEnd('.')
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SPLIT #${state.currentLapNumber} ($intervalText KM)",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor
                    )
                    val lastSplit = state.completedSplits.lastOrNull()
                    Text(
                        text = if (lastSplit != null) {
                            "Last S${lastSplit.lapNumber}: ${GeoMath.formatPace(lastSplit.paceSecPerKm)} /km"
                        } else {
                            "${state.currentLapDistanceMeters.roundToInt()} / ${splitTargetMeters.roundToInt()} m"
                        },
                        style = MetricCompactStyle,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (state.currentLapDistanceMeters / splitTargetMeters).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accentColor,
                    trackColor = ObsidianBg
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    accent: Color,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MetricCardStyle,
                    color = TextPrimary,
                    modifier = Modifier.testTag(tag)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun WorkoutControls(
    status: TrackingStatus,
    isAutoPaused: Boolean,
    activityType: ActivityType,
    accentColor: Color,
    hasPermission: Boolean,
    onGrantPermission: () -> Unit,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onRequestStop: () -> Unit
) {
    when (status) {
        TrackingStatus.IDLE -> {
            Button(
                onClick = {
                    if (hasPermission) onStart() else onGrantPermission()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("start_workout_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasPermission) "START ${activityType.label.uppercase()}" else "ENABLE GPS & START",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        TrackingStatus.TRACKING, TrackingStatus.PAUSED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isPaused = status == TrackingStatus.PAUSED
                Button(
                    onClick = onTogglePause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) GpsEmerald else SlateElevated,
                        contentColor = if (isPaused) ObsidianBg else TextPrimary
                    ),
                    border = if (isPaused) null else BorderStroke(1.dp, HairlineBorder),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .testTag("pause_resume_button")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isPaused && isAutoPaused -> "RESUME"
                            isPaused -> "RESUME"
                            else -> "PAUSE"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onRequestStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .testTag("stop_workout_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "FINISH",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
