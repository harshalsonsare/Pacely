package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOnTotalSilence
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.LapSplitEntity
import com.example.data.model.ActivityType
import com.example.tracking.GeoMath
import com.example.ui.components.CanvasPoint
import com.example.ui.components.ElevationProfileChart
import com.example.ui.components.LiveRouteCanvas
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MetricCardStyle
import com.example.ui.theme.MetricCompactStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WalkCyan
import com.example.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val details by viewModel.observeWorkoutDetail(workoutId).collectAsStateWithLifecycle(initialValue = null)
    val shoes by viewModel.shoes.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var shoeDropdownExpanded by remember { mutableStateOf(false) }

    val gpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        if (uri != null) {
            viewModel.exportWorkoutGpxToUri(context, workoutId, uri) { res ->
                res.onSuccess {
                    onShowMessage("Downloaded route GPX file.")
                }.onFailure { err ->
                    onShowMessage("GPX export failed: ${err.localizedMessage ?: "Unknown error"}")
                }
            }
        }
    }

    val shareGpx = {
        viewModel.prepareShareFile(context, workoutId) { res ->
            res.onSuccess { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/gpx+xml"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Pacely GPX Route")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share GPX Route"))
            }.onFailure { err ->
                onShowMessage("Unable to share GPX: ${err.localizedMessage ?: "Error"}")
            }
        }
    }

    val currentDetails = details
    if (currentDetails == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = ElectricOrange)
        }
        return
    }

    val workout = currentDetails.workout
    val type = ActivityType.fromString(workout.activityType)
    val accentColor = if (type == ActivityType.RUN) ElectricOrange else WalkCyan
    val canvasPoints = remember(currentDetails.trackPoints) {
        currentDetails.trackPoints.map {
            CanvasPoint(
                latitude = it.latitude,
                longitude = it.longitude,
                segmentIndex = it.segmentIndex,
                altitudeMeters = it.altitudeMeters,
                speedMps = it.speedMps
            )
        }
    }
    val dateText = SimpleDateFormat("EEEE, MMM d, yyyy • h:mm a", Locale.getDefault())
        .format(Date(workout.startTimeMillis))
    val assignedShoe = shoes.firstOrNull { it.id == workout.shoeId }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to history",
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = workout.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_workout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete workout",
                            tint = DangerRed
                        )
                    }
                }
            }

            item {
                LiveRouteCanvas(
                    points = canvasPoints,
                    accentColor = accentColor,
                    isLive = false,
                    isTracking = false,
                    avgPaceSecPerKm = workout.avgPaceSecPerKm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            }

            if (shoes.isNotEmpty()) {
                item {
                    Box {
                        Surface(
                            color = CarbonSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { shoeDropdownExpanded = true }
                                .testTag("detail_assign_shoe_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.DoNotDisturbOnTotalSilence,
                                        contentDescription = null,
                                        tint = WalkCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Gear / Shoe",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = assignedShoe?.let { "${it.brand} ${it.name}" } ?: "Tap to assign shoe",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (assignedShoe != null) WalkCyan else TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = shoeDropdownExpanded,
                            onDismissRequest = { shoeDropdownExpanded = false },
                            containerColor = SlateElevated
                        ) {
                            DropdownMenuItem(
                                text = { Text("None", color = TextSecondary) },
                                onClick = {
                                    viewModel.assignShoeToWorkout(workout.id, null)
                                    shoeDropdownExpanded = false
                                }
                            )
                            shoes.forEach { shoe ->
                                DropdownMenuItem(
                                    text = { Text("${shoe.brand} ${shoe.name}", color = TextPrimary) },
                                    onClick = {
                                        viewModel.assignShoeToWorkout(workout.id, shoe.id)
                                        shoeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val fileDate = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(workout.startTimeMillis))
                    Button(
                        onClick = { gpxLauncher.launch("pacely_${type.name.lowercase()}_$fileDate.gpx") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_gpx_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download GPX", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = shareGpx,
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_gpx_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share GPX", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailStatCard(
                            label = "DISTANCE",
                            value = "${GeoMath.formatDistanceKm(workout.distanceMeters)} km",
                            accent = accentColor,
                            modifier = Modifier.weight(1f)
                        )
                        DetailStatCard(
                            label = "DURATION",
                            value = GeoMath.formatElapsedTime(workout.elapsedMillis),
                            accent = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailStatCard(
                            label = "AVG PACE",
                            value = "${GeoMath.formatPace(workout.avgPaceSecPerKm)} /km",
                            accent = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        DetailStatCard(
                            label = "CALORIES",
                            value = "${workout.caloriesBurned} kcal",
                            accent = ElectricOrange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailStatCard(
                            label = "ELEV GAIN",
                            value = "+${workout.elevationGainMeters.roundToInt()} m",
                            accent = GpsEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        DetailStatCard(
                            label = "ELEV LOSS",
                            value = "-${workout.elevationLossMeters.roundToInt()} m",
                            accent = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (canvasPoints.size >= 2) {
                item {
                    ElevationProfileChart(
                        points = canvasPoints,
                        accentColor = accentColor
                    )
                }
            }

            if (currentDetails.splits.isNotEmpty()) {
                item {
                    SplitsBarChartCard(
                        splits = currentDetails.splits,
                        avgPaceSecPerKm = workout.avgPaceSecPerKm,
                        accentColor = accentColor
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = CarbonSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Delete Workout?") },
            text = { Text("This will permanently remove ${workout.title} and its GPS route from your device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteWorkout(workoutId) {
                            onShowMessage("Workout deleted.")
                            onBack()
                        }
                    },
                    modifier = Modifier.testTag("confirm_delete_workout")
                ) {
                    Text("Delete", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = TextPrimary)
                }
            }
        )
    }
}

@Composable
private fun DetailStatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MetricCardStyle,
                color = accent
            )
        }
    }
}

@Composable
private fun SplitsBarChartCard(
    splits: List<LapSplitEntity>,
    avgPaceSecPerKm: Double,
    accentColor: Color
) {
    val validPaces = splits.mapNotNull { if (it.paceSecPerKm > 0.0) it.paceSecPerKm else null }
    val fastestPace = validPaces.minOrNull() ?: 1.0
    val slowestPace = validPaces.maxOrNull() ?: 1.0
    val refAvg = if (avgPaceSecPerKm > 0.0) avgPaceSecPerKm else validPaces.average().takeIf { !it.isNaN() } ?: 1.0

    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("splits_bar_chart_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SPLITS BAR CHART",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Text(
                    text = "Avg ${GeoMath.formatPace(refAvg)}/km",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            // Vertical Bar Chart for quick pace comparison across splits
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                splits.forEach { split ->
                    val ratio = if (split.paceSecPerKm > 0.0 && fastestPace > 0.0) {
                        // Faster split gets taller bar for athletic speed clarity
                        (fastestPace / split.paceSecPerKm).toFloat().coerceIn(0.25f, 1f)
                    } else {
                        0.3f
                    }
                    val barColor = when {
                        split.paceSecPerKm <= 0.0 -> accentColor
                        split.paceSecPerKm < refAvg * 0.96 -> GpsEmerald
                        split.paceSecPerKm > refAvg * 1.06 -> DangerRed
                        else -> ElectricOrange
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = GeoMath.formatPace(split.paceSecPerKm),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((72.dp * ratio).coerceAtLeast(10.dp))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(barColor)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "S${split.lapNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Detailed Split Rows with Duration, Pace, and Elevation Delta
            splits.forEach { split ->
                val barColor = when {
                    split.paceSecPerKm <= 0.0 -> accentColor
                    split.paceSecPerKm < refAvg * 0.96 -> GpsEmerald
                    split.paceSecPerKm > refAvg * 1.06 -> DangerRed
                    else -> ElectricOrange
                }
                val speedRatio = if (slowestPace > 0.0 && split.paceSecPerKm > 0.0) {
                    (fastestPace / split.paceSecPerKm).toFloat().coerceIn(0.25f, 1f)
                } else {
                    0.5f
                }
                val distKmFormatted = String.format(Locale.US, "%.2f", split.distanceMeters / 1000.0)
                    .trimEnd('0').trimEnd('.')
                val distLabel = "S${split.lapNumber} • ${distKmFormatted}km"
                val elevSign = if (split.elevationDeltaMeters >= 0) "+" else ""

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = distLabel,
                        style = MetricCompactStyle,
                        color = TextPrimary,
                        modifier = Modifier.width(68.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SlateElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(speedRatio)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = GeoMath.formatElapsedTime(split.durationMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${GeoMath.formatPace(split.paceSecPerKm)}/km",
                        style = MetricCompactStyle,
                        color = barColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$elevSign${split.elevationDeltaMeters.roundToInt()}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }
        }
    }
}
