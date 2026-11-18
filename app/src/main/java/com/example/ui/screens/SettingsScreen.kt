package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.DataBackupTransferCard
import com.example.ui.components.TransferBannerState
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.ElectricOrangeContainer
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MetricCompactStyle
import com.example.ui.theme.MetricHeroStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WalkCyan
import com.example.ui.viewmodel.SettingsViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var transferBanner by remember { mutableStateOf<TransferBannerState?>(null) }
    var customSplitInput by remember { mutableStateOf("") }

    LaunchedEffect(state.splitIntervalKm) {
        val formatted = String.format(Locale.US, "%.2f", state.splitIntervalKm).trimEnd('0').trimEnd('.')
        if (customSplitInput != formatted) {
            customSplitInput = formatted
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "SETTINGS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "TRACKING PREFERENCES & DATA BACKUP",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricOrange
                )
            }

            // Tracking Automation & Split Alerts Card
            Surface(
                color = CarbonSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Workout Automation",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PauseCircle,
                                contentDescription = null,
                                tint = ElectricOrange
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Auto-Pause",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Pauses after 4s below threshold and resumes automatically when speed recovers.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = state.autoPauseEnabled,
                            onCheckedChange = viewModel::setAutoPauseEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBg,
                                checkedTrackColor = ElectricOrange
                            ),
                            modifier = Modifier.testTag("settings_auto_pause_switch")
                        )
                    }

                    // Auto-Pause Speed Threshold Slider (0.2 m/s to 2.0 m/s) with live km/h conversion
                    val roundedThreshold = ((state.autoPauseSpeedThresholdMps * 10f).roundToInt() / 10f).coerceIn(0.2f, 2.0f)
                    val thresholdKmh = roundedThreshold * 3.6f
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateElevated, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto-Pause Speed Threshold",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f m/s • %.1f km/h", roundedThreshold, thresholdKmh),
                                style = MetricCompactStyle,
                                color = ElectricOrange,
                                modifier = Modifier.testTag("auto_pause_threshold_label")
                            )
                        }
                        Slider(
                            value = roundedThreshold,
                            onValueChange = { raw ->
                                val snapped = ((raw * 10f).roundToInt() / 10f).coerceIn(0.2f, 2.0f)
                                viewModel.setAutoPauseSpeedThresholdMps(snapped)
                            },
                            valueRange = 0.2f..2.0f,
                            steps = 17,
                            enabled = state.autoPauseEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricOrange,
                                activeTrackColor = ElectricOrange,
                                inactiveTrackColor = ObsidianBg
                            ),
                            modifier = Modifier.testTag("auto_pause_threshold_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0.2 m/s (0.7 km/h)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("2.0 m/s (7.2 km/h)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }

                    HorizontalDivider(color = HairlineBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                tint = WalkCyan
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Voice & Haptic Split Alerts",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Vibrates and speaks split pace via offline TextToSpeech at each distance interval.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = state.voiceSplitCuesEnabled,
                            onCheckedChange = viewModel::setVoiceSplitCuesEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBg,
                                checkedTrackColor = WalkCyan
                            ),
                            modifier = Modifier.testTag("settings_voice_splits_switch")
                        )
                    }

                    // Configurable Split Alert Interval (presets 0.5, 1.0, 2.0, 5.0 km + custom decimal input)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateElevated, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Split Alert Interval",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            val intervalFormatted = String.format(Locale.US, "%.2f", state.splitIntervalKm)
                                .trimEnd('0').trimEnd('.')
                            Text(
                                text = "Every $intervalFormatted km",
                                style = MetricCompactStyle,
                                color = WalkCyan,
                                modifier = Modifier.testTag("split_interval_value_label")
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.5f, 1.0f, 2.0f, 5.0f).forEach { preset ->
                                val selected = abs(state.splitIntervalKm - preset) < 0.01f
                                Surface(
                                    color = if (selected) WalkCyan.copy(alpha = 0.22f) else ObsidianBg,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, if (selected) WalkCyan else HairlineBorder),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            viewModel.setSplitIntervalKm(preset)
                                            onShowMessage("Split interval set to $preset km.")
                                        }
                                        .testTag("split_preset_${preset}")
                                ) {
                                    Text(
                                        text = "$preset km",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) WalkCyan else TextSecondary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customSplitInput,
                                onValueChange = { customSplitInput = it },
                                label = { Text("Custom interval (km)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = WalkCyan,
                                    unfocusedBorderColor = HairlineBorder,
                                    focusedLabelColor = WalkCyan,
                                    unfocusedLabelColor = TextSecondary,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_split_interval_input")
                            )
                            Button(
                                onClick = {
                                    val parsed = customSplitInput.trim().replace(',', '.').toFloatOrNull()
                                    if (parsed == null || parsed < 0.1f || parsed > 50.0f) {
                                        onShowMessage("Enter a split interval between 0.1 and 50.0 km.")
                                    } else {
                                        viewModel.setSplitIntervalKm(parsed)
                                        onShowMessage("Split interval updated to $parsed km.")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WalkCyan,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("apply_custom_split_button")
                            ) {
                                Text("Apply", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = HairlineBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DarkMode,
                                contentDescription = null,
                                tint = ElectricOrange
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Dark Mode Map",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Applies an obsidian slate dark theme to offline & online street tiles.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = state.darkMapModeEnabled,
                            onCheckedChange = viewModel::setDarkMapModeEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBg,
                                checkedTrackColor = ElectricOrange
                            ),
                            modifier = Modifier.testTag("settings_dark_map_switch")
                        )
                    }
                }
            }

            // Body Weight Card
            Surface(
                color = CarbonSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.MonitorWeight,
                                contentDescription = null,
                                tint = ElectricOrange
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Body Weight",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "${state.weightKg.roundToInt()} kg",
                            style = MetricHeroStyle,
                            color = ElectricOrange,
                            modifier = Modifier.testTag("weight_value_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Used with dynamic MET speed curves to estimate calories burned during runs and walks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = SlateElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HairlineBorder)
                        ) {
                            IconButton(
                                onClick = { viewModel.updateWeightKg(state.weightKg - 1f) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("weight_decrease_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Decrease body weight",
                                    tint = TextPrimary
                                )
                            }
                        }

                        Slider(
                            value = state.weightKg,
                            onValueChange = { viewModel.updateWeightKg(it) },
                            valueRange = 35f..180f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricOrange,
                                activeTrackColor = ElectricOrange,
                                inactiveTrackColor = SlateElevated
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("weight_slider")
                        )

                        Surface(
                            color = SlateElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HairlineBorder)
                        ) {
                            IconButton(
                                onClick = { viewModel.updateWeightKg(state.weightKg + 1f) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("weight_increase_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Increase body weight",
                                    tint = TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val estRunKcalPerHour = (9.8f * state.weightKg).roundToInt()
                    val estWalkKcalPerHour = (3.8f * state.weightKg).roundToInt()
                    Text(
                        text = "Estimated burn: ~$estRunKcalPerHour kcal/hr running  •  ~$estWalkKcalPerHour kcal/hr walking",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            // Dedicated Data Backup & Transfer Section
            DataBackupTransferCard(
                workoutCount = state.totalWorkoutsCount,
                isTransferring = state.isTransferring,
                bannerState = transferBanner,
                onExportJsonUri = { uri, done ->
                    viewModel.exportAllToUri(context, uri, isJson = true) { result ->
                        val banner = result.fold(
                            onSuccess = { count ->
                                TransferBannerState("Backed up $count workouts to JSON.", isError = false)
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
                                TransferBannerState("Exported $count workouts to CSV.", isError = false)
                            },
                            onFailure = { err ->
                                TransferBannerState("Export failed: ${err.localizedMessage ?: "Unknown error"}", isError = true)
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

            // Offline Privacy Card
            Surface(
                color = CarbonSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = GpsEmerald
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "100% Offline & Private",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pacely requires zero accounts, zero subscriptions, and zero external map billing APIs. All GPS routes stay stored exclusively in your device's Room SQLite database.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Clear Data
            OutlinedButton(
                onClick = { showClearDialog = true },
                enabled = state.totalWorkoutsCount > 0,
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("clear_all_workouts_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = DangerRed
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear All Local Workouts",
                    color = DangerRed,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = CarbonSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Clear All Workouts?") },
            text = { Text("This will permanently delete all ${state.totalWorkoutsCount} saved workouts from local storage. Consider exporting a full JSON backup first.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllWorkouts {
                            transferBanner = null
                            onShowMessage("All local workouts cleared.")
                        }
                    },
                    modifier = Modifier.testTag("confirm_clear_all")
                ) {
                    Text("Delete All", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextPrimary)
                }
            }
        )
    }
}
