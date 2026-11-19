package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.ScreenRotationAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HeadingSourceMode
import com.example.map.OsmMapManager
import com.example.tracking.CompassMath
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MetricCompactStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WalkCyan
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class OfflineDownloadUiState(
    val isDownloading: Boolean = false,
    val progressTiles: Int = 0,
    val totalTiles: Int = 0,
    val currentZoom: Int = OsmMapManager.OFFLINE_MIN_ZOOM,
    val errorCount: Int = 0,
    val completedMessage: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("ClickableViewAccessibility")
@Composable
fun OsmRouteMap(
    points: List<CanvasPoint>,
    accentColor: Color,
    isLive: Boolean,
    isTracking: Boolean,
    avgPaceSecPerKm: Double? = null,
    gpsAccuracyMeters: Float? = null,
    lastKnownLocation: Pair<Double, Double>? = null,
    headingDegrees: Float? = null,
    headingSourceMode: HeadingSourceMode = HeadingSourceMode.AUTO,
    isUsingGpsBearing: Boolean = false,
    needsCompassCalibration: Boolean = false,
    isDarkMapMode: Boolean = true,
    mapOrientationDegrees: Float = 180f,
    onToggleHeadingSource: (() -> Unit)? = null,
    onToggleDarkMapMode: (() -> Unit)? = null,
    onToggleMapOrientation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val isInspection = LocalInspectionMode.current
    val coroutineScope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val isOnline by OsmMapManager.observeNetworkConnectivity(context)
        .collectAsStateWithLifecycle(initialValue = OsmMapManager.isNetworkAvailable(context))

    var isAutoFollow by remember { mutableStateOf(true) }
    var hasInitialCentered by remember { mutableStateOf(false) }

    var mapCenterLat by remember {
        mutableDoubleStateOf(lastKnownLocation?.first ?: points.lastOrNull()?.latitude ?: 37.7749)
    }
    var mapCenterLon by remember {
        mutableDoubleStateOf(lastKnownLocation?.second ?: points.lastOrNull()?.longitude ?: -122.4194)
    }
    var mapZoomLevel by remember { mutableDoubleStateOf(16.8) }
    var gridSpacing100mPx by remember { mutableFloatStateOf(0f) }

    var hasCachedTilesForArea by remember { mutableStateOf(false) }
    var cachedStorageMb by remember { mutableDoubleStateOf(0.0) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var selectedRadiusKm by remember { mutableDoubleStateOf(2.0) }
    var estimatedTileCount by remember { mutableIntStateOf(0) }
    var downloadState by remember { mutableStateOf(OfflineDownloadUiState()) }
    var activeCacheManager by remember { mutableStateOf<CacheManager?>(null) }

    val referenceAvgPaceSecPerKm = remember(points, avgPaceSecPerKm) {
        if (avgPaceSecPerKm != null && avgPaceSecPerKm > 0.0) {
            avgPaceSecPerKm
        } else {
            val movingSpeeds = points.mapNotNull { pt ->
                if (pt.speedMps > 0.4f) pt.speedMps.toDouble() else null
            }
            if (movingSpeeds.isNotEmpty()) 1000.0 / movingSpeeds.average() else 0.0
        }
    }

    // Initialize OSMDroid configuration cleanly before MapView creation
    if (!isInspection) {
        remember(context) {
            OsmMapManager.ensureInitialized(context)
            true
        }
    }

    val routeOverlay = remember {
        OsmRouteOverlay(densityPx = density.density)
    }

    val mapView = remember(isInspection) {
        if (isInspection) null
        else {
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 4.0
                maxZoomLevel = 20.0
                isTilesScaledToDpi = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                overlayManager.tilesOverlay.loadingBackgroundColor = android.graphics.Color.TRANSPARENT
                overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.TRANSPARENT
                overlayManager.tilesOverlay.setColorFilter(
                    if (isDarkMapMode) OsmMapManager.createDarkModeColorFilter() else null
                )
                setMapOrientation(mapOrientationDegrees)
                controller.setZoom(16.8)
                controller.setCenter(
                    OsmGeoPoint(
                        lastKnownLocation?.first ?: points.lastOrNull()?.latitude ?: 37.7749,
                        lastKnownLocation?.second ?: points.lastOrNull()?.longitude ?: -122.4194
                    )
                )
                overlayManager.add(routeOverlay)

                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                        isAutoFollow = false
                    }
                    false
                }

                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        val c = mapCenter
                        mapCenterLat = c.latitude
                        mapCenterLon = c.longitude
                        mapZoomLevel = zoomLevelDouble
                        gridSpacing100mPx = projection.metersToPixels(100f)
                        return false
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        val c = mapCenter
                        mapCenterLat = c.latitude
                        mapCenterLon = c.longitude
                        mapZoomLevel = zoomLevelDouble
                        gridSpacing100mPx = projection.metersToPixels(100f)
                        return false
                    }
                })
            }
        }
    }

    // Manage MapView lifecycle cleanly
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView?.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activeCacheManager?.cancelAllJobs()
            mapView?.onPause()
            mapView?.onDetach()
        }
    }

    // Automatically toggle OSMDroid network connection off when device is offline
    LaunchedEffect(mapView, isOnline) {
        mapView?.setUseDataConnection(isOnline)
    }

    // Keep OSMDroid tile color filter synced with Dark Mode Map setting
    LaunchedEffect(mapView, isDarkMapMode) {
        mapView?.let { mv ->
            mv.overlayManager.tilesOverlay.setColorFilter(
                if (isDarkMapMode) OsmMapManager.createDarkModeColorFilter() else null
            )
            mv.invalidate()
        }
    }

    // Keep OSMDroid MapView display orientation synced (180° / 0°) while preserving center-lock
    LaunchedEffect(mapView, mapOrientationDegrees) {
        mapView?.let { mv ->
            val targetCenter = if (isAutoFollow && isLive && lastKnownLocation != null) {
                OsmGeoPoint(lastKnownLocation.first, lastKnownLocation.second)
            } else {
                OsmGeoPoint(mv.mapCenter.latitude, mv.mapCenter.longitude)
            }
            mv.setMapOrientation(mapOrientationDegrees)
            mv.controller.setCenter(targetCenter)
            mv.invalidate()
        }
    }

    // Keep overlay data synced & auto-center smoothly
    LaunchedEffect(
        mapView,
        points,
        accentColor,
        isLive,
        referenceAvgPaceSecPerKm,
        gpsAccuracyMeters,
        lastKnownLocation,
        headingDegrees,
        isUsingGpsBearing,
        isAutoFollow
    ) {
        routeOverlay.updateData(
            points = points,
            accentColorArgb = accentColor.toArgb(),
            avgPaceSecPerKm = referenceAvgPaceSecPerKm,
            isLive = isLive,
            liveLocation = lastKnownLocation,
            gpsAccuracyMeters = gpsAccuracyMeters,
            headingDegrees = headingDegrees,
            isUsingGpsBearing = isUsingGpsBearing
        )
        mapView?.invalidate()

        if (mapView != null && isAutoFollow) {
            if (isLive && lastKnownLocation != null) {
                val target = OsmGeoPoint(lastKnownLocation.first, lastKnownLocation.second)
                if (!hasInitialCentered) {
                    mapView.controller.setZoom(17.0)
                    mapView.controller.setCenter(target)
                    hasInitialCentered = true
                } else {
                    mapView.controller.animateTo(target)
                }
            } else if (!isLive && points.size >= 2 && !hasInitialCentered) {
                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLon = points.minOf { it.longitude }
                val maxLon = points.maxOf { it.longitude }
                val padLat = ((maxLat - minLat) * 0.15).coerceAtLeast(0.0012)
                val padLon = ((maxLon - minLon) * 0.15).coerceAtLeast(0.0012)
                val bbox = BoundingBox(
                    (maxLat + padLat).coerceIn(-85.0, 85.0),
                    (maxLon + padLon).coerceIn(-180.0, 180.0),
                    (minLat - padLat).coerceIn(-85.0, 85.0),
                    (minLon - padLon).coerceIn(-180.0, 180.0)
                )
                mapView.post {
                    mapView.zoomToBoundingBox(bbox, false, 64)
                }
                hasInitialCentered = true
            }
        }
    }

    // Refresh disk cache status & cached storage MB whenever center, zoom, or download completion changes
    LaunchedEffect(
        mapCenterLat,
        mapCenterLon,
        mapZoomLevel.roundToInt(),
        downloadState.isDownloading,
        showDownloadDialog
    ) {
        val checkLat = lastKnownLocation?.first ?: mapCenterLat
        val checkLon = lastKnownLocation?.second ?: mapCenterLon
        val zoomInt = mapZoomLevel.roundToInt().coerceIn(1, 19)
        hasCachedTilesForArea = OsmMapManager.hasCachedTilesForArea(context, checkLat, checkLon, zoomInt)
        cachedStorageMb = OsmMapManager.getCachedStorageMb(context)
    }

    // Update estimated tile count when dialog opens or radius changes
    LaunchedEffect(showDownloadDialog, selectedRadiusKm, lastKnownLocation, mapView) {
        if (showDownloadDialog && mapView != null) {
            val lat = lastKnownLocation?.first ?: mapCenterLat
            val lon = lastKnownLocation?.second ?: mapCenterLon
            val bbox = OsmMapManager.computeBoundingBoxForRadiusKm(lat, lon, selectedRadiusKm)
            val cm = activeCacheManager ?: CacheManager(mapView).also { activeCacheManager = it }
            estimatedTileCount = cm.possibleTilesInArea(
                bbox,
                OsmMapManager.OFFLINE_MIN_ZOOM,
                OsmMapManager.OFFLINE_MAX_ZOOM
            )
            cachedStorageMb = OsmMapManager.getCachedStorageMb(context)
        }
    }

    val mapBgBrush = remember(isDarkMapMode) {
        if (isDarkMapMode) {
            Brush.radialGradient(colors = listOf(Color(0xFF141B26), ObsidianBg))
        } else {
            Brush.radialGradient(colors = listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0)))
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(mapBgBrush)
            .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
            .testTag("live_route_canvas")
    ) {
        // 100m Coordinate Grid Fallback behind MapView (rotated in sync with MapView orientation)
        Dark100mGridCanvas(
            centerLat = mapCenterLat,
            centerLon = mapCenterLon,
            zoomLevel = mapZoomLevel,
            measured100mPx = gridSpacing100mPx,
            isDarkMode = isDarkMapMode,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = mapOrientationDegrees }
        )

        if (mapView != null) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top-Start HUD: Offline Map Status + Live Coordinates + Accuracy + Heading + Calibration
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 10.dp, end = 64.dp)
        ) {
            // Compact Offline Status + Lat/Lon + Horizontal Accuracy Pill
            val statusColor = if (hasCachedTilesForArea) GpsEmerald else WarningAmber
            val statusTitle = if (hasCachedTilesForArea) "Offline Map Ready" else "No Cached Tiles for Area"
            val coordText = if (lastKnownLocation != null) {
                val accPart = gpsAccuracyMeters?.let { "±${it.roundToInt()}m" } ?: "±--m"
                String.format(
                    Locale.US,
                    "%.5f, %.5f • %s",
                    lastKnownLocation.first,
                    lastKnownLocation.second,
                    accPart
                )
            } else if (!isLive && points.isNotEmpty()) {
                String.format(Locale.US, "%.5f, %.5f", points.first().latitude, points.first().longitude)
            } else {
                "Waiting GPS lock..."
            }

            Surface(
                color = CarbonSurface.copy(alpha = 0.90f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("offline_map_status_pill")
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasCachedTilesForArea) Icons.Rounded.CheckCircle else Icons.Rounded.CloudOff,
                            contentDescription = statusTitle,
                            tint = statusColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isOnline) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = coordText,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }

            if (isLive) {
                val headingDegText = headingDegrees?.let {
                    val deg = CompassMath.displayDegrees(it)
                    val card = CompassMath.formatCardinal(it)
                    String.format(Locale.US, "%03d° %s", deg, card)
                } ?: "--°"
                val sourceLabel = when (headingSourceMode) {
                    HeadingSourceMode.AUTO -> if (isUsingGpsBearing) "Auto•GPS" else "Auto•Mag"
                    HeadingSourceMode.MAGNETOMETER_ONLY -> "Mag Only"
                }

                Surface(
                    color = CarbonSurface.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        1.dp,
                        if (isUsingGpsBearing) GpsEmerald.copy(alpha = 0.6f) else WalkCyan.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = onToggleHeadingSource != null) {
                            onToggleHeadingSource?.invoke()
                        }
                        .testTag("heading_source_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Navigation,
                            contentDescription = "Compass heading",
                            tint = if (isUsingGpsBearing) GpsEmerald else WalkCyan,
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer {
                                    rotationZ = CompassMath.normalizeDegrees(
                                        (headingDegrees ?: 0f) + mapOrientationDegrees
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "$headingDegText • $sourceLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (needsCompassCalibration) {
                    Surface(
                        color = WarningAmber.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("compass_calibration_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "Compass calibration warning",
                                tint = WarningAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Calibrate Compass: Move phone in figure-8",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Top-Right Controls: Recenter + Dark/Light Map Theme Toggle
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            Surface(
                color = CarbonSurface.copy(alpha = 0.90f),
                shape = CircleShape,
                border = BorderStroke(
                    1.dp,
                    if (isAutoFollow) HairlineBorder else accentColor
                )
            ) {
                IconButton(
                    onClick = {
                        isAutoFollow = true
                        if (mapView != null) {
                            if (isLive && lastKnownLocation != null) {
                                mapView.controller.setZoom(17.0)
                                mapView.controller.animateTo(
                                    OsmGeoPoint(lastKnownLocation.first, lastKnownLocation.second)
                                )
                            } else if (!isLive && points.size >= 2) {
                                val minLat = points.minOf { it.latitude }
                                val maxLat = points.maxOf { it.latitude }
                                val minLon = points.minOf { it.longitude }
                                val maxLon = points.maxOf { it.longitude }
                                val padLat = ((maxLat - minLat) * 0.15).coerceAtLeast(0.0012)
                                val padLon = ((maxLon - minLon) * 0.15).coerceAtLeast(0.0012)
                                mapView.zoomToBoundingBox(
                                    BoundingBox(maxLat + padLat, maxLon + padLon, minLat - padLat, minLon - padLon),
                                    true,
                                    64
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("recenter_map_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Recenter route",
                        tint = if (isAutoFollow) TextSecondary else accentColor
                    )
                }
            }

            if (onToggleDarkMapMode != null) {
                Surface(
                    color = CarbonSurface.copy(alpha = 0.90f),
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        if (isDarkMapMode) WalkCyan.copy(alpha = 0.55f) else ElectricOrange.copy(alpha = 0.65f)
                    )
                ) {
                    IconButton(
                        onClick = onToggleDarkMapMode,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_dark_map_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkMapMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                            contentDescription = if (isDarkMapMode) "Switch to light map" else "Switch to dark map",
                            tint = if (isDarkMapMode) WalkCyan else ElectricOrange
                        )
                    }
                }
            }
        }

        // Bottom-Start Controls: Rotate 180° / Reset (0°) + One-Tap Offline Area Pre-Downloader
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            if (onToggleMapOrientation != null) {
                val isRotated180 = mapOrientationDegrees != 0f
                val toggleColor = if (isRotated180) ElectricOrange else WalkCyan
                Surface(
                    color = CarbonSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, toggleColor.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onToggleMapOrientation() }
                        .testTag("toggle_map_rotation_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScreenRotationAlt,
                            contentDescription = if (isRotated180) "Reset map rotation to 0 degrees" else "Rotate map 180 degrees",
                            tint = toggleColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRotated180) "Reset (0°)" else "Rotate 180°",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isLive) {
                Surface(
                    color = CarbonSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, ElectricOrange.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showDownloadDialog = true }
                        .testTag("download_offline_map_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = "Download Offline Map",
                            tint = ElectricOrange,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val btnText = if (downloadState.isDownloading && downloadState.totalTiles > 0) {
                            val pct = ((downloadState.progressTiles.toFloat() / downloadState.totalTiles.toFloat()) * 100f)
                                .roundToInt().coerceIn(0, 100)
                            "Caching $pct%"
                        } else {
                            "Download Offline Map"
                        }
                        Text(
                            text = btnText,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Bottom-End Pace Legend Pill
        if (points.size >= 2) {
            Surface(
                color = CarbonSurface.copy(alpha = 0.88f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .testTag("pace_legend_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    MapLegendDot("Fast", GpsEmerald)
                    MapLegendDot("Avg", ElectricOrange)
                    MapLegendDot("Slow", DangerRed)
                }
            }
        }
    }

    if (showDownloadDialog && mapView != null) {
        OfflineMapDownloadDialog(
            selectedRadiusKm = selectedRadiusKm,
            onSelectRadius = { selectedRadiusKm = it },
            targetLatLon = lastKnownLocation ?: (mapCenterLat to mapCenterLon),
            hasGpsLock = lastKnownLocation != null,
            isOnline = isOnline,
            cachedStorageMb = cachedStorageMb,
            estimatedTiles = estimatedTileCount,
            downloadState = downloadState,
            onStartDownload = {
                val lat = lastKnownLocation?.first ?: mapCenterLat
                val lon = lastKnownLocation?.second ?: mapCenterLon
                val bbox = OsmMapManager.computeBoundingBoxForRadiusKm(lat, lon, selectedRadiusKm)
                val cm = activeCacheManager ?: CacheManager(mapView).also { activeCacheManager = it }
                mapView.setUseDataConnection(true)

                downloadState = OfflineDownloadUiState(
                    isDownloading = true,
                    progressTiles = 0,
                    totalTiles = estimatedTileCount.coerceAtLeast(1),
                    currentZoom = OsmMapManager.OFFLINE_MIN_ZOOM,
                    errorCount = 0,
                    completedMessage = null
                )

                cm.downloadAreaAsyncNoUI(
                    context,
                    bbox,
                    OsmMapManager.OFFLINE_MIN_ZOOM,
                    OsmMapManager.OFFLINE_MAX_ZOOM,
                    object : CacheManager.CacheManagerCallback {
                        override fun downloadStarted() {
                            mainHandler.post {
                                downloadState = downloadState.copy(isDownloading = true, completedMessage = null)
                            }
                        }

                        override fun setPossibleTilesInArea(total: Int) {
                            mainHandler.post {
                                downloadState = downloadState.copy(totalTiles = total.coerceAtLeast(1))
                            }
                        }

                        override fun updateProgress(
                            progress: Int,
                            currentZoomLevel: Int,
                            zoomMin: Int,
                            zoomMax: Int
                        ) {
                            mainHandler.post {
                                downloadState = downloadState.copy(
                                    isDownloading = true,
                                    progressTiles = progress.coerceAtLeast(0),
                                    currentZoom = currentZoomLevel
                                )
                            }
                        }

                        override fun onTaskComplete() {
                            mainHandler.post {
                                mapView.setUseDataConnection(isOnline)
                                mapView.invalidate()
                                downloadState = downloadState.copy(
                                    isDownloading = false,
                                    progressTiles = downloadState.totalTiles,
                                    completedMessage = "Offline area downloaded successfully (Zoom 12–17)."
                                )
                                coroutineScope.launch {
                                    cachedStorageMb = OsmMapManager.getCachedStorageMb(context)
                                    hasCachedTilesForArea = OsmMapManager.hasCachedTilesForArea(
                                        context,
                                        lat,
                                        lon,
                                        mapZoomLevel.roundToInt().coerceIn(1, 19)
                                    )
                                }
                            }
                        }

                        override fun onTaskFailed(errors: Int) {
                            mainHandler.post {
                                mapView.setUseDataConnection(isOnline)
                                downloadState = downloadState.copy(
                                    isDownloading = false,
                                    errorCount = errors,
                                    completedMessage = "Finished with $errors tile error(s). Available tiles were cached."
                                )
                                coroutineScope.launch {
                                    cachedStorageMb = OsmMapManager.getCachedStorageMb(context)
                                    hasCachedTilesForArea = OsmMapManager.hasCachedTilesForArea(
                                        context,
                                        lat,
                                        lon,
                                        mapZoomLevel.roundToInt().coerceIn(1, 19)
                                    )
                                }
                            }
                        }
                    }
                )
            },
            onCancelDownload = {
                activeCacheManager?.cancelAllJobs()
                mapView.setUseDataConnection(isOnline)
                downloadState = downloadState.copy(
                    isDownloading = false,
                    completedMessage = "Download cancelled."
                )
            },
            onDismiss = { showDownloadDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfflineMapDownloadDialog(
    selectedRadiusKm: Double,
    onSelectRadius: (Double) -> Unit,
    targetLatLon: Pair<Double, Double>,
    hasGpsLock: Boolean,
    isOnline: Boolean,
    cachedStorageMb: Double,
    estimatedTiles: Int,
    downloadState: OfflineDownloadUiState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "Offline Map Pre-Downloader",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Cached storage size in MB
                Surface(
                    color = SlateElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = WalkCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Disk Tile Cache",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.1f MB / 500 MB", cachedStorageMb),
                            style = MetricCompactStyle,
                            color = WalkCyan,
                            modifier = Modifier.testTag("cached_storage_mb_text")
                        )
                    }
                }

                // Radius Selection (2 km, 5 km, 10 km)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Select Download Radius (Zoom 12–17)",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2.0, 5.0, 10.0).forEach { radiusKm ->
                            val selected = selectedRadiusKm == radiusKm
                            Surface(
                                color = if (selected) ElectricOrange.copy(alpha = 0.22f) else SlateElevated,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, if (selected) ElectricOrange else HairlineBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(enabled = !downloadState.isDownloading) {
                                        onSelectRadius(radiusKm)
                                    }
                                    .testTag("radius_${radiusKm.roundToInt()}km")
                            ) {
                                Text(
                                    text = "${radiusKm.roundToInt()} km",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) ElectricOrange else TextSecondary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                val centerMsg = String.format(
                    Locale.US,
                    "%s %.4f, %.4f • ~%d street tiles",
                    if (hasGpsLock) "GPS Center:" else "Map Center:",
                    targetLatLon.first,
                    targetLatLon.second,
                    estimatedTiles
                )
                Text(
                    text = centerMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (!isOnline) {
                    Text(
                        text = "No internet connection detected. Connect to Wi-Fi or cellular data to download new map areas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                }

                if (downloadState.isDownloading || downloadState.completedMessage != null) {
                    val total = downloadState.totalTiles.coerceAtLeast(1)
                    val fraction = (downloadState.progressTiles.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    val pct = (fraction * 100f).roundToInt()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .testTag("offline_download_progress"),
                            color = ElectricOrange,
                            trackColor = ObsidianBg
                        )
                        Text(
                            text = if (downloadState.isDownloading) {
                                "Zoom ${downloadState.currentZoom} (12–17) • ${downloadState.progressTiles} / $total tiles ($pct%)"
                            } else {
                                downloadState.completedMessage.orEmpty()
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (downloadState.errorCount > 0) WarningAmber else GpsEmerald
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (downloadState.isDownloading) {
                Button(
                    onClick = onCancelDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.testTag("cancel_download_tiles_button")
                ) {
                    Text("Cancel", color = TextPrimary)
                }
            } else {
                Button(
                    onClick = onStartDownload,
                    enabled = isOnline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricOrange,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier.testTag("confirm_download_tiles_button")
                ) {
                    Text("Download Tiles", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextPrimary)
            }
        }
    )
}

@Composable
private fun Dark100mGridCanvas(
    centerLat: Double,
    centerLon: Double,
    zoomLevel: Double,
    measured100mPx: Float,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val stepPx = if (measured100mPx > 4f) {
            measured100mPx
        } else {
            OsmMapManager.metersToPixelsAt(centerLat, zoomLevel, 100.0, density)
        }.coerceIn(10f, 320f)

        val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.15)
        val worldMetersX = centerLon * 111_320.0 * cosLat
        val worldMetersY = centerLat * 111_320.0
        val phaseX = ((((worldMetersX % 100.0) / 100.0) * stepPx).toFloat() % stepPx + stepPx) % stepPx
        val phaseY = ((((worldMetersY % 100.0) / 100.0) * stepPx).toFloat() % stepPx + stepPx) % stepPx

        val minorColor = if (isDarkMode) Color(0xFF1A2230) else Color(0xFFDCE3EC)
        val majorColor = if (isDarkMode) Color(0xFF253144) else Color(0xFFC5D0DE)
        val minorStroke = 1.dp.toPx()
        val majorStroke = 1.4.dp.toPx()

        val startGridX = (w / 2f) - phaseX
        var col = ((-startGridX) / stepPx).toInt() - 1
        while (true) {
            val x = startGridX + col * stepPx
            if (x > w) break
            if (x >= 0f) {
                val isMajor = col % 5 == 0
                drawLine(
                    color = if (isMajor) majorColor else minorColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = if (isMajor) majorStroke else minorStroke
                )
            }
            col++
        }

        val startGridY = (h / 2f) + phaseY
        var row = ((-startGridY) / stepPx).toInt() - 1
        while (true) {
            val y = startGridY + row * stepPx
            if (y > h) break
            if (y >= 0f) {
                val isMajor = row % 5 == 0
                drawLine(
                    color = if (isMajor) majorColor else minorColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (isMajor) majorStroke else minorStroke
                )
            }
            row++
        }
    }
}

@Composable
private fun MapLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

private class OsmRouteOverlay(
    private val densityPx: Float
) : Overlay() {
    private var points: List<CanvasPoint> = emptyList()
    private var accentArgb: Int = ElectricOrange.toArgb()
    private var avgPaceSecPerKm: Double = 0.0
    private var isLive: Boolean = true
    private var liveLocation: Pair<Double, Double>? = null
    private var gpsAccuracyMeters: Float? = null
    private var headingDegrees: Float? = null
    private var isUsingGpsBearing: Boolean = false

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.argb(190, 11, 13, 17)
        strokeWidth = 8.5f * densityPx
    }

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 4.8f * densityPx
    }

    private val accuracyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.argb(48, 38, 198, 218)
    }

    private val accuracyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * densityPx
        color = android.graphics.Color.argb(190, 38, 198, 218)
    }

    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val headingConePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val headingLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.6f * densityPx
    }

    private val p1Screen = Point()
    private val p2Screen = Point()
    private val liveScreen = Point()

    fun updateData(
        points: List<CanvasPoint>,
        accentColorArgb: Int,
        avgPaceSecPerKm: Double,
        isLive: Boolean,
        liveLocation: Pair<Double, Double>?,
        gpsAccuracyMeters: Float?,
        headingDegrees: Float?,
        isUsingGpsBearing: Boolean
    ) {
        this.points = points
        this.accentArgb = accentColorArgb
        this.avgPaceSecPerKm = avgPaceSecPerKm
        this.isLive = isLive
        this.liveLocation = liveLocation
        this.gpsAccuracyMeters = gpsAccuracyMeters
        this.headingDegrees = headingDegrees
        this.isUsingGpsBearing = isUsingGpsBearing
    }

    override fun draw(canvas: android.graphics.Canvas, projection: Projection) {
        // Note: OSMDroid's DefaultOverlayManager pre-applies Projection.save(canvas) with
        // mapView.mapOrientation (e.g., 180°). Drawing in projection pixel space keeps polylines,
        // the GPS accuracy circle, and the heading cone/arrow locked to geographic coordinates.

        // 1. Pace-Colored Route Polyline grouped by segmentIndex
        if (points.size >= 2) {
            val grouped = points.groupBy { it.segmentIndex }.toSortedMap()
            grouped.values.forEach { seg ->
                if (seg.size >= 2) {
                    for (i in 1 until seg.size) {
                        val a = seg[i - 1]
                        val b = seg[i]
                        projection.toPixels(OsmGeoPoint(a.latitude, a.longitude), p1Screen)
                        projection.toPixels(OsmGeoPoint(b.latitude, b.longitude), p2Screen)

                        canvas.drawLine(
                            p1Screen.x.toFloat(),
                            p1Screen.y.toFloat(),
                            p2Screen.x.toFloat(),
                            p2Screen.y.toFloat(),
                            haloPaint
                        )
                        val paceColor = resolvePaceSegmentColor(
                            speedMps = b.speedMps,
                            avgPaceSecPerKm = avgPaceSecPerKm,
                            fallbackColor = Color(accentArgb)
                        )
                        segmentPaint.color = paceColor.toArgb()
                        canvas.drawLine(
                            p1Screen.x.toFloat(),
                            p1Screen.y.toFloat(),
                            p2Screen.x.toFloat(),
                            p2Screen.y.toFloat(),
                            segmentPaint
                        )
                    }
                }
            }

            // Start Marker
            val startPt = points.first()
            projection.toPixels(OsmGeoPoint(startPt.latitude, startPt.longitude), p1Screen)
            markerFillPaint.color = ObsidianBg.toArgb()
            canvas.drawCircle(p1Screen.x.toFloat(), p1Screen.y.toFloat(), 7.5f * densityPx, markerFillPaint)
            markerFillPaint.color = GpsEmerald.toArgb()
            canvas.drawCircle(p1Screen.x.toFloat(), p1Screen.y.toFloat(), 5f * densityPx, markerFillPaint)

            if (!isLive) {
                val endPt = points.last()
                projection.toPixels(OsmGeoPoint(endPt.latitude, endPt.longitude), p2Screen)
                markerFillPaint.color = android.graphics.Color.WHITE
                canvas.drawCircle(p2Screen.x.toFloat(), p2Screen.y.toFloat(), 7.5f * densityPx, markerFillPaint)
                markerFillPaint.color = accentArgb
                canvas.drawCircle(p2Screen.x.toFloat(), p2Screen.y.toFloat(), 5f * densityPx, markerFillPaint)
            }
        }

        // 2. Live GPS Accuracy Circle + Heading Arrow + Position Dot (aligned to rotated map frame)
        if (isLive) {
            val activeCoord = liveLocation ?: points.lastOrNull()?.let { it.latitude to it.longitude }
            if (activeCoord != null) {
                projection.toPixels(OsmGeoPoint(activeCoord.first, activeCoord.second), liveScreen)
                val cx = liveScreen.x.toFloat()
                val cy = liveScreen.y.toFloat()

                val accMeters = gpsAccuracyMeters
                if (accMeters != null && accMeters > 0f) {
                    val radiusPx = projection.metersToPixels(accMeters).coerceAtLeast(10f * densityPx)
                    canvas.drawCircle(cx, cy, radiusPx, accuracyFillPaint)
                    canvas.drawCircle(cx, cy, radiusPx, accuracyStrokePaint)
                }

                val hDeg = headingDegrees
                if (hDeg != null) {
                    val arrowColor = if (isUsingGpsBearing) GpsEmerald else Color(accentArgb)
                    val coneLen = 26f * densityPx
                    val wingHalfWidth = 10.5f * densityPx
                    val wingBackOffset = coneLen * 0.62f

                    canvas.save()
                    // Rotate clockwise from geographic North (-Y in projection pixel space)
                    canvas.rotate(hDeg, cx, cy)
                    val conePath = Path().apply {
                        moveTo(cx, cy)
                        lineTo(cx - wingHalfWidth, cy - wingBackOffset)
                        lineTo(cx, cy - coneLen)
                        lineTo(cx + wingHalfWidth, cy - wingBackOffset)
                        close()
                    }
                    headingConePaint.color = arrowColor.copy(alpha = 0.36f).toArgb()
                    canvas.drawPath(conePath, headingConePaint)
                    headingLinePaint.color = arrowColor.toArgb()
                    canvas.drawLine(cx, cy, cx, cy - coneLen, headingLinePaint)
                    canvas.restore()
                }

                markerFillPaint.color = android.graphics.Color.WHITE
                canvas.drawCircle(cx, cy, 7.8f * densityPx, markerFillPaint)
                markerFillPaint.color = accentArgb
                canvas.drawCircle(cx, cy, 5.2f * densityPx, markerFillPaint)
            }
        }
    }
}
