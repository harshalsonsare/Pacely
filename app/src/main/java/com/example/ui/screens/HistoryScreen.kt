package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.WorkoutEntity
import com.example.data.model.ActivityType
import com.example.data.model.WeeklySummary
import com.example.tracking.GeoMath
import com.example.ui.components.DataBackupTransferCard
import com.example.ui.components.MiniRoutePreview
import com.example.ui.components.TransferBannerState
import com.example.ui.theme.CarbonSurface
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
import com.example.ui.theme.WalkCyanContainer
import com.example.ui.viewmodel.HistoryFilter
import com.example.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onWorkoutClick: (Long) -> Unit,
    onStartTrackClick: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var transferBanner by remember { mutableStateOf<TransferBannerState?>(null) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "ACTIVITY LOG",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "LOCAL SQLITE WORKOUT ARCHIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricOrange
                    )
                }
            }

            item {
                WeeklySummaryCard(summary = state.weeklySummary)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryFilter.entries.forEach { filter ->
                        val selected = state.filter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setFilter(filter) },
                            label = {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricOrange,
                                selectedLabelColor = ObsidianBg,
                                containerColor = CarbonSurface,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = HairlineBorder
                            ),
                            modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (state.workouts.isEmpty()) {
                item {
                    EmptyHistoryCard(
                        filter = state.filter,
                        onStartTrackClick = onStartTrackClick
                    )
                }
            } else {
                items(
                    items = state.workouts,
                    key = { it.id }
                ) { workout ->
                    WorkoutHistoryCard(
                        workout = workout,
                        onClick = { onWorkoutClick(workout.id) }
                    )
                }
            }

            item {
                DataBackupTransferCard(
                    workoutCount = state.totalWorkoutCount,
                    isTransferring = state.isTransferring,
                    bannerState = transferBanner,
                    onExportFullBackupZipUri = { uri, done ->
                        viewModel.exportFullBackupZipToUri(context, uri) { result ->
                            val banner = result.fold(
                                onSuccess = { count ->
                                    TransferBannerState("Exported $count workouts (full_backup.json + .gpx routes) to .zip.", isError = false)
                                },
                                onFailure = { err ->
                                    TransferBannerState("Backup failed: ${err.localizedMessage ?: "Unknown error"}", isError = true)
                                }
                            )
                            transferBanner = banner
                            done(banner)
                        }
                    },
                    onExportJsonUri = { uri, done ->
                        viewModel.exportAllToUri(context, uri, isJson = true) { result ->
                            val banner = result.fold(
                                onSuccess = { count ->
                                    TransferBannerState("Exported $count workouts to full JSON backup.", isError = false)
                                },
                                onFailure = { err ->
                                    TransferBannerState("Backup failed: ${err.localizedMessage ?: "Unknown error"}", isError = true)
                                }
                            )
                            transferBanner = banner
                            done(banner)
                        }
                    },
                    onExportCsvUri = { uri, done ->
                        viewModel.exportAllToUri(context, uri, isJson = false) { result ->
                            val banner = result.fold(
                                onSuccess = { count ->
                                    TransferBannerState("Exported $count workouts to CSV summary.", isError = false)
                                },
                                onFailure = { err ->
                                    TransferBannerState("CSV export failed: ${err.localizedMessage ?: "Unknown error"}", isError = true)
                                }
                            )
                            transferBanner = banner
                            done(banner)
                        }
                    },
                    onImportUri = { uri, done ->
                        viewModel.importFromUri(context, uri) { result ->
                            val banner = result.fold(
                                onSuccess = { summary ->
                                    TransferBannerState(summary.toUserMessage(), isError = false)
                                },
                                onFailure = { err ->
                                    TransferBannerState("Import failed: ${err.localizedMessage ?: "Invalid or corrupted file."}", isError = true)
                                }
                            )
                            transferBanner = banner
                            done(banner)
                        }
                    },
                    onShowMessage = onShowMessage
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun WeeklySummaryCard(summary: WeeklySummary) {
    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekly_summary_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ElectricOrange.copy(alpha = 0.16f),
                            CarbonSurface
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = ElectricOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "THIS WEEK",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricOrange
                    )
                }
                Text(
                    text = "${summary.workoutCount} ${if (summary.workoutCount == 1) "session" else "sessions"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = GeoMath.formatDistanceKm(summary.totalDistanceMeters),
                            style = MetricHeroStyle,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "km",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                    Text(
                        text = "TOTAL DISTANCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = GeoMath.formatElapsedTime(summary.totalDurationMillis),
                        style = MetricCardStyle,
                        color = TextPrimary
                    )
                    Text(
                        text = "ACTIVE TIME",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryBadge(
                    label = "${summary.runCount} Runs",
                    tint = ElectricOrange,
                    bg = ElectricOrangeContainer,
                    modifier = Modifier.weight(1f)
                )
                SummaryBadge(
                    label = "${summary.walkCount} Walks",
                    tint = WalkCyan,
                    bg = WalkCyanContainer,
                    modifier = Modifier.weight(1f)
                )
                SummaryBadge(
                    label = "+${summary.totalElevationGainMeters.roundToInt()}m Elev",
                    tint = GpsEmerald,
                    bg = SlateElevated,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryBadge(
    label: String,
    tint: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WorkoutHistoryCard(
    workout: WorkoutEntity,
    onClick: () -> Unit
) {
    val type = ActivityType.fromString(workout.activityType)
    val accentColor = if (type == ActivityType.RUN) ElectricOrange else WalkCyan
    val badgeBg = if (type == ActivityType.RUN) ElectricOrangeContainer else WalkCyanContainer
    val dateFormatter = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())

    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("workout_card_${workout.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniRoutePreview(
                encodedPoints = workout.encodedPreviewPoints,
                accentColor = accentColor,
                modifier = Modifier.size(84.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (type == ActivityType.RUN) {
                                Icons.AutoMirrored.Rounded.DirectionsRun
                            } else {
                                Icons.AutoMirrored.Rounded.DirectionsWalk
                            },
                            contentDescription = type.label,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = type.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Terrain,
                            contentDescription = null,
                            tint = GpsEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "+${workout.elevationGainMeters.roundToInt()}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = workout.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = dateFormatter.format(Date(workout.startTimeMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HistoryMetricItem(
                        label = "DIST",
                        value = "${GeoMath.formatDistanceKm(workout.distanceMeters)} km",
                        valueColor = accentColor
                    )
                    HistoryMetricItem(
                        label = "TIME",
                        value = GeoMath.formatElapsedTime(workout.elapsedMillis),
                        valueColor = TextPrimary
                    )
                    HistoryMetricItem(
                        label = "PACE",
                        value = "${GeoMath.formatPace(workout.avgPaceSecPerKm)}/km",
                        valueColor = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryMetricItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MetricCompactStyle,
            color = valueColor
        )
    }
}

@Composable
private fun EmptyHistoryCard(
    filter: HistoryFilter,
    onStartTrackClick: () -> Unit
) {
    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ElectricOrangeContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Route,
                    contentDescription = null,
                    tint = ElectricOrange,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = when (filter) {
                    HistoryFilter.ALL -> "No Recorded Workouts Yet"
                    HistoryFilter.RUNS -> "No Runs Recorded Yet"
                    HistoryFilter.WALKS -> "No Walks Recorded Yet"
                },
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Record your first GPS route offline or import an existing .json backup / .gpx track below.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onStartTrackClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricOrange,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier.testTag("start_first_activity_button")
            ) {
                Text("Go to Tracker", fontWeight = FontWeight.Bold)
            }
        }
    }
}
