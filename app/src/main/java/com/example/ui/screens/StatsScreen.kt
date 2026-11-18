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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOnTotalSilence
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PersonalRecord
import com.example.data.model.ShoeMileageStats
import com.example.tracking.GeoMath
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.ElectricOrangeContainer
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MetricCardStyle
import com.example.ui.theme.MetricCompactStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WalkCyan
import com.example.ui.theme.WalkCyanContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.StatsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onWorkoutClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddShoeDialog by remember { mutableStateOf(false) }

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
                    Column {
                        Text(
                            text = "STATS & GEAR",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "ALL-TIME PERSONAL RECORDS & SHOE MILEAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricOrange
                        )
                    }
                    Surface(
                        color = CarbonSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f km", state.totalLifetimeKm),
                                style = MetricCompactStyle,
                                color = ElectricOrange
                            )
                            Text(
                                text = "${state.totalWorkoutsCount} workouts",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Personal Records Grid
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = ElectricOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PERSONAL RECORDS",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }

            item {
                val prs = state.personalRecords
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PersonalRecordCard(
                            title = "FASTEST 1K",
                            record = prs.fastest1K,
                            isDistancePrimary = false,
                            accentColor = ElectricOrange,
                            onClick = { prs.fastest1K?.let { onWorkoutClick(it.workoutId) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pr_card_1k")
                        )
                        PersonalRecordCard(
                            title = "FASTEST 5K",
                            record = prs.fastest5K,
                            isDistancePrimary = false,
                            accentColor = GpsEmerald,
                            onClick = { prs.fastest5K?.let { onWorkoutClick(it.workoutId) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pr_card_5k")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PersonalRecordCard(
                            title = "FASTEST 10K",
                            record = prs.fastest10K,
                            isDistancePrimary = false,
                            accentColor = WalkCyan,
                            onClick = { prs.fastest10K?.let { onWorkoutClick(it.workoutId) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pr_card_10k")
                        )
                        PersonalRecordCard(
                            title = "LONGEST RUN/WALK",
                            record = prs.longestDistance,
                            isDistancePrimary = true,
                            accentColor = WarningAmber,
                            onClick = { prs.longestDistance?.let { onWorkoutClick(it.workoutId) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pr_card_longest")
                        )
                    }
                }
            }

            // Shoe Mileage Tracker Header
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SHOE MILEAGE TRACKER",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }

                    Button(
                        onClick = { showAddShoeDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricOrange,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("add_shoe_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Shoe", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state.shoes.isEmpty()) {
                item {
                    Surface(
                        color = CarbonSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Inventory2,
                                contentDescription = null,
                                tint = WalkCyan,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Running Shoes Logged",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add your trainers to automatically track total kilometers logged across every run and walk.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(items = state.shoes, key = { it.shoe.id }) { shoeStats ->
                    ShoeMileageCard(
                        stats = shoeStats,
                        onSetDefault = { viewModel.setDefaultShoe(shoeStats.shoe.id) },
                        onToggleRetired = { viewModel.toggleShoeRetired(shoeStats.shoe) },
                        onDelete = { viewModel.deleteShoe(shoeStats.shoe.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showAddShoeDialog) {
        AddShoeDialog(
            hasExistingDefault = state.shoes.any { it.shoe.isDefault && !it.shoe.isRetired },
            onDismiss = { showAddShoeDialog = false },
            onSave = { brand, name, initialKm, maxKm, isDefault ->
                viewModel.addShoe(brand, name, initialKm, maxKm, isDefault)
                showAddShoeDialog = false
            }
        )
    }
}

@Composable
private fun PersonalRecordCard(
    title: String,
    record: PersonalRecord?,
    isDistancePrimary: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (record != null) accentColor.copy(alpha = 0.4f) else HairlineBorder),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = record != null, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (record != null) accentColor.copy(alpha = 0.12f) else Color.Transparent,
                            CarbonSurface
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (record == null) {
                Text(
                    text = "--:--",
                    style = MetricCardStyle,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Not yet achieved",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            } else {
                val primaryValue = if (isDistancePrimary) {
                    "${GeoMath.formatDistanceKm(record.distanceMeters)} km"
                } else {
                    GeoMath.formatElapsedTime(record.durationMillis)
                }
                val secondaryValue = if (isDistancePrimary) {
                    "${GeoMath.formatElapsedTime(record.durationMillis)} • ${GeoMath.formatPace(record.paceSecPerKm)}/km"
                } else {
                    "${GeoMath.formatPace(record.paceSecPerKm)} /km"
                }

                Text(
                    text = primaryValue,
                    style = MetricCardStyle,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = secondaryValue,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${record.workoutTitle} • ${dateFormatter.format(Date(record.dateMillis))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ShoeMileageCard(
    stats: ShoeMileageStats,
    onSetDefault: () -> Unit,
    onToggleRetired: () -> Unit,
    onDelete: () -> Unit
) {
    val shoe = stats.shoe
    val progress = (stats.totalDistanceKm / shoe.targetMaxKm.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val barColor = when {
        shoe.isRetired -> TextMuted
        progress >= 0.95f -> DangerRed
        progress >= 0.75f -> WarningAmber
        else -> WalkCyan
    }

    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (shoe.isDefault && !shoe.isRetired) WalkCyan.copy(alpha = 0.55f) else HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shoe_card_${shoe.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = shoe.brand.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricOrange
                        )
                        if (shoe.isDefault && !shoe.isRetired) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(WalkCyanContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "DEFAULT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WalkCyan
                                )
                            }
                        } else if (shoe.isRetired) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SlateElevated)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "RETIRED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = shoe.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (shoe.isRetired) TextSecondary else TextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.US, "%.1f km", stats.totalDistanceKm),
                        style = MetricCardStyle,
                        color = barColor
                    )
                    Text(
                        text = "${stats.workoutCount} ${if (stats.workoutCount == 1) "activity" else "activities"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = barColor,
                    trackColor = ObsidianBg
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tracked: ${String.format(Locale.US, "%.1f", stats.loggedDistanceKm)} km (+${String.format(Locale.US, "%.0f", shoe.initialMileageKm)} km prior)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "Goal ${String.format(Locale.US, "%.0f", shoe.targetMaxKm)} km",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!shoe.isDefault && !shoe.isRetired) {
                        OutlinedButton(
                            onClick = onSetDefault,
                            border = BorderStroke(1.dp, HairlineBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = WalkCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Set Default", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        }
                    }
                    OutlinedButton(
                        onClick = onToggleRetired,
                        border = BorderStroke(1.dp, HairlineBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (shoe.isRetired) "Reactivate" else "Retire",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete shoe",
                        tint = DangerRed
                    )
                }
            }
        }
    }
}

@Composable
private fun AddShoeDialog(
    hasExistingDefault: Boolean,
    onDismiss: () -> Unit,
    onSave: (brand: String, name: String, initialKm: Double, maxKm: Double, isDefault: Boolean) -> Unit
) {
    var brand by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var initialKmText by remember { mutableStateOf("0") }
    var targetKmText by remember { mutableStateOf("800") }
    var setAsDefault by remember { mutableStateOf(!hasExistingDefault) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ElectricOrange,
        unfocusedBorderColor = HairlineBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedLabelColor = ElectricOrange,
        unfocusedLabelColor = TextSecondary
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Add Running Shoe", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand (e.g. Nike, ASICS, Hoka)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("shoe_brand_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Model Name (e.g. Pegasus 41)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("shoe_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = initialKmText,
                        onValueChange = { initialKmText = it },
                        label = { Text("Starting km") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = targetKmText,
                        onValueChange = { targetKmText = it },
                        label = { Text("Max km goal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { setAsDefault = !setAsDefault }
                ) {
                    Checkbox(
                        checked = setAsDefault,
                        onCheckedChange = { setAsDefault = it },
                        colors = CheckboxDefaults.colors(checkedColor = ElectricOrange)
                    )
                    Text("Set as default shoe for new activities", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val initKm = initialKmText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val maxKm = targetKmText.toDoubleOrNull()?.coerceIn(100.0, 5000.0) ?: 800.0
                    onSave(brand, name, initKm, maxKm, setAsDefault)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricOrange,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier.testTag("save_shoe_button")
            ) {
                Text("Save Shoe", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextPrimary)
            }
        }
    )
}
