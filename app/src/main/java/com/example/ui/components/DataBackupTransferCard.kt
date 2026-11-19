package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedContainer
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.GpsEmeraldContainer
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WalkCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TransferBannerState(
    val message: String,
    val isError: Boolean
)

@Composable
fun DataBackupTransferCard(
    workoutCount: Int,
    isTransferring: Boolean,
    bannerState: TransferBannerState?,
    onExportFullBackupZipUri: (Uri, (TransferBannerState) -> Unit) -> Unit,
    onExportJsonUri: (Uri, (TransferBannerState) -> Unit) -> Unit,
    onExportCsvUri: (Uri, (TransferBannerState) -> Unit) -> Unit,
    onImportUri: (Uri, (TransferBannerState) -> Unit) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var localStatus by remember(bannerState) { mutableStateOf(bannerState) }
    val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    val zipBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            onExportFullBackupZipUri(uri) { result ->
                localStatus = result
                onShowMessage(result.message)
            }
        }
    }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExportJsonUri(uri) { result ->
                localStatus = result
                onShowMessage(result.message)
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExportCsvUri(uri) { result ->
                localStatus = result
                onShowMessage(result.message)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportUri(uri) { result ->
                localStatus = result
                onShowMessage(result.message)
            }
        }
    }

    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_backup_transfer_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.SyncAlt,
                        contentDescription = null,
                        tint = ElectricOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Data Backup & Transfer",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "$workoutCount saved",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            Text(
                text = "Export a complete .zip bundle (full_backup.json with stats, splits & settings + individual .gpx routes for every workout), export standalone .json/.csv files, or import .zip, .json, and .gpx archives offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            AnimatedVisibility(visible = isTransferring) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ElectricOrange,
                    trackColor = SlateElevated
                )
            }

            AnimatedVisibility(visible = localStatus != null) {
                localStatus?.let { status ->
                    Surface(
                        color = if (status.isError) DangerRedContainer else GpsEmeraldContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            (if (status.isError) DangerRed else GpsEmerald).copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_status_banner")
                    ) {
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (status.isError) DangerRed else GpsEmerald,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Primary: Full Backup ZIP Bundle (full_backup.json + all .gpx files)
            Button(
                onClick = { zipBackupLauncher.launch("pacely_full_backup_$dateStamp.zip") },
                enabled = workoutCount > 0 && !isTransferring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricOrange,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_full_backup_zip_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderZip,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export Full Backup (.json + .gpx .zip)",
                    fontWeight = FontWeight.Bold
                )
            }

            // Primary Import: JSON, GPX, or ZIP Backup Archive
            OutlinedButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/json",
                            "application/gpx+xml",
                            "application/xml",
                            "text/xml",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                enabled = !isTransferring,
                border = BorderStroke(1.dp, WalkCyan.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WalkCyan.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .testTag("import_workouts_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileUpload,
                    contentDescription = null,
                    tint = WalkCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Import Workouts (.json / .gpx / .zip)",
                    color = WalkCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            // Secondary Row: Standalone JSON & CSV Exports
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { jsonExportLauncher.launch("full_backup_$dateStamp.json") },
                    enabled = workoutCount > 0 && !isTransferring,
                    border = BorderStroke(1.dp, HairlineBorder),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_full_backup_json_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "JSON Only",
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedButton(
                    onClick = { csvExportLauncher.launch("pacely_summary_$dateStamp.csv") },
                    enabled = workoutCount > 0 && !isTransferring,
                    border = BorderStroke(1.dp, HairlineBorder),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_summary_csv_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CSV Table",
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
