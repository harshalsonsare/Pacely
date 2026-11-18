package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.HeadingSourceMode
import com.example.tracking.CompassMath
import com.example.tracking.GeoMath
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WalkCyan
import com.example.ui.theme.WarningAmber
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class CanvasPoint(
    val latitude: Double,
    val longitude: Double,
    val segmentIndex: Int = 0,
    val altitudeMeters: Double = 0.0,
    val speedMps: Float = 0f
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveRouteCanvas(
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
    onToggleHeadingSource: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var userZoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var isAutoFit by remember { mutableStateOf(true) }

    val pulseTransition = rememberInfiniteTransition(label = "gps_pulse")
    val pulseRadiusFactor by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    val effectivePoints = remember(points, lastKnownLocation) {
        if (points.isNotEmpty()) points
        else if (lastKnownLocation != null) listOf(CanvasPoint(lastKnownLocation.first, lastKnownLocation.second))
        else emptyList()
    }

    val referenceAvgPaceSecPerKm = remember(points, avgPaceSecPerKm) {
        if (avgPaceSecPerKm != null && avgPaceSecPerKm > 0.0) {
            avgPaceSecPerKm
        } else {
            val movingSpeeds = points.mapNotNull { pt ->
                if (pt.speedMps > 0.4f) pt.speedMps.toDouble() else null
            }
            if (movingSpeeds.isNotEmpty()) {
                1000.0 / movingSpeeds.average()
            } else {
                0.0
            }
        }
    }

    val scaleInfo = remember(effectivePoints, userZoom) {
        computeRouteScaleInfo(effectivePoints)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF141B26), ObsidianBg)
                )
            )
            .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    isAutoFit = false
                    userZoom = (userZoom * zoom).coerceIn(0.4f, 12f)
                    panOffset += pan
                }
            }
            .testTag("live_route_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Subtle telemetry coordinate grid
            val gridStep = 48.dp.toPx()
            val gridColor = Color(0xFF1B2332)
            var gx = (panOffset.x % gridStep + gridStep) % gridStep
            while (gx < w) {
                drawLine(
                    color = gridColor,
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = 1.dp.toPx()
                )
                gx += gridStep
            }
            var gy = (panOffset.y % gridStep + gridStep) % gridStep
            while (gy < h) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = 1.dp.toPx()
                )
                gy += gridStep
            }

            if (effectivePoints.isEmpty()) {
                val center = Offset(w / 2f, h / 2f)
                listOf(0.14f, 0.28f, 0.42f).forEach { factor ->
                    drawCircle(
                        color = accentColor.copy(alpha = 0.14f),
                        radius = min(w, h) * factor,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                if (isLive) {
                    drawCircle(
                        color = accentColor.copy(alpha = pulseAlpha * 0.6f),
                        radius = 12.dp.toPx() * pulseRadiusFactor,
                        center = center
                    )
                    if (headingDegrees != null) {
                        drawHeadingIndicator(
                            center = center,
                            headingDegrees = headingDegrees,
                            arrowColor = if (isUsingGpsBearing) GpsEmerald else accentColor,
                            coneLengthPx = 28.dp.toPx()
                        )
                    }
                    drawCircle(
                        color = accentColor,
                        radius = 6.dp.toPx(),
                        center = center
                    )
                }
                return@Canvas
            }

            val pad = 42.dp.toPx()
            val usableW = (w - pad * 2f).coerceAtLeast(10f)
            val usableH = (h - pad * 2f).coerceAtLeast(10f)
            val centerScreen = Offset(w / 2f, h / 2f)

            val refLat = scaleInfo.centerLat
            val refLon = scaleInfo.centerLon
            val cosLat = cos(Math.toRadians(refLat)).coerceAtLeast(0.15)
            val metersPerDegLat = 111_320.0
            val metersPerDegLon = 111_320.0 * cosLat

            val spanMetersX = scaleInfo.spanMetersX.coerceAtLeast(60.0)
            val spanMetersY = scaleInfo.spanMetersY.coerceAtLeast(60.0)
            val basePxPerMeter = min(usableW / spanMetersX.toFloat(), usableH / spanMetersY.toFloat())
            val pxPerMeter = basePxPerMeter * (if (isAutoFit) 1f else userZoom)
            val activePan = if (isAutoFit) Offset.Zero else panOffset

            fun toScreen(pt: CanvasPoint): Offset {
                val dxMeters = (pt.longitude - refLon) * metersPerDegLon
                val dyMeters = (pt.latitude - refLat) * metersPerDegLat
                return Offset(
                    x = centerScreen.x + activePan.x + (dxMeters.toFloat() * pxPerMeter),
                    y = centerScreen.y + activePan.y - (dyMeters.toFloat() * pxPerMeter)
                )
            }

            val projected = effectivePoints.map { it to toScreen(it) }

            // Draw pace-colored route polyline grouped by segmentIndex (no lines across pause jumps)
            val segments = projected.groupBy { it.first.segmentIndex }.toSortedMap()
            segments.values.forEach { segList ->
                if (segList.size >= 2) {
                    // First pass: soft glow per segment
                    for (i in 1 until segList.size) {
                        val p1 = segList[i - 1]
                        val p2 = segList[i]
                        val segColor = resolvePaceSegmentColor(
                            speedMps = p2.first.speedMps,
                            avgPaceSecPerKm = referenceAvgPaceSecPerKm,
                            fallbackColor = accentColor
                        )
                        drawLine(
                            color = segColor.copy(alpha = 0.24f),
                            start = p1.second,
                            end = p2.second,
                            strokeWidth = 11.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    // Second pass: crisp foreground stroke
                    for (i in 1 until segList.size) {
                        val p1 = segList[i - 1]
                        val p2 = segList[i]
                        val segColor = resolvePaceSegmentColor(
                            speedMps = p2.first.speedMps,
                            avgPaceSecPerKm = referenceAvgPaceSecPerKm,
                            fallbackColor = accentColor
                        )
                        drawLine(
                            color = segColor,
                            start = p1.second,
                            end = p2.second,
                            strokeWidth = 4.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Start marker
            val startPos = projected.first().second
            if (points.size >= 2) {
                drawCircle(
                    color = ObsidianBg,
                    radius = 7.5.dp.toPx(),
                    center = startPos
                )
                drawCircle(
                    color = GpsEmerald,
                    radius = 5.dp.toPx(),
                    center = startPos
                )
            }

            // End / Current marker + Heading Arrow
            val endPos = projected.last().second
            if (isLive) {
                if (isTracking || effectivePoints.size == 1) {
                    drawCircle(
                        color = accentColor.copy(alpha = pulseAlpha),
                        radius = 10.dp.toPx() * pulseRadiusFactor,
                        center = endPos
                    )
                }
                if (headingDegrees != null) {
                    drawHeadingIndicator(
                        center = endPos,
                        headingDegrees = headingDegrees,
                        arrowColor = if (isUsingGpsBearing) GpsEmerald else accentColor,
                        coneLengthPx = 26.dp.toPx()
                    )
                }
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = endPos
                )
                drawCircle(
                    color = accentColor,
                    radius = 5.2.dp.toPx(),
                    center = endPos
                )
            } else if (points.size >= 2) {
                drawCircle(
                    color = Color.White,
                    radius = 7.5.dp.toPx(),
                    center = endPos
                )
                drawCircle(
                    color = accentColor,
                    radius = 5.dp.toPx(),
                    center = endPos
                )
            }
        }

        // Top-left GPS accuracy + Tappable Heading Pill + Calibration Warning Badge
        if (isLive) {
            val (badgeColor, badgeText, gpsLocked) = when {
                gpsAccuracyMeters == null -> Triple(WarningAmber, "Searching GPS", false)
                gpsAccuracyMeters <= 15f -> Triple(GpsEmerald, "GPS ±${gpsAccuracyMeters.roundToInt()}m", true)
                else -> Triple(WarningAmber, "Noisy GPS ±${gpsAccuracyMeters.roundToInt()}m", false)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 10.dp, end = 64.dp)
            ) {
                Surface(
                    color = CarbonSurface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (gpsLocked) Icons.Rounded.GpsFixed else Icons.Rounded.GpsNotFixed,
                            contentDescription = badgeText,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }

                // Tappable Heading Pill (Auto vs Magnetometer Only)
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
                                    rotationZ = headingDegrees ?: 0f
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

        // Top-right Recenter button
        if (effectivePoints.isNotEmpty()) {
            Surface(
                color = CarbonSurface.copy(alpha = 0.9f),
                shape = CircleShape,
                border = BorderStroke(
                    1.dp,
                    if (isAutoFit) HairlineBorder else accentColor
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            ) {
                IconButton(
                    onClick = {
                        isAutoFit = true
                        userZoom = 1f
                        panOffset = Offset.Zero
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("recenter_map_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Recenter route",
                        tint = if (isAutoFit) TextSecondary else accentColor
                    )
                }
            }
        }

        // Bottom-left Scale Bar & Bottom-right Pace Legend
        if (effectivePoints.size >= 2) {
            val activeSpan = (max(scaleInfo.spanMetersX, scaleInfo.spanMetersY) / (if (isAutoFit) 1f else userZoom))
            val scaleLabel = chooseNiceScaleLabel(activeSpan)
            Surface(
                color = CarbonSurface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .background(accentColor, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = scaleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Pace Legend Pill
            Surface(
                color = CarbonSurface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .testTag("pace_legend_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    LegendDot("Fast", GpsEmerald)
                    LegendDot("Avg", ElectricOrange)
                    LegendDot("Slow", DangerRed)
                }
            }
        } else if (isLive) {
            Surface(
                color = CarbonSurface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            ) {
                Text(
                    text = if (isTracking) "Recording GPS trail (filtering accuracy > 15m)..." else "Tap Start to plot your pace-colored route",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
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

private fun DrawScope.drawHeadingIndicator(
    center: Offset,
    headingDegrees: Float,
    arrowColor: Color,
    coneLengthPx: Float
) {
    val rad = Math.toRadians(headingDegrees.toDouble() - 90.0)
    val spreadRad = Math.toRadians(24.0)
    val tip = Offset(
        x = center.x + (cos(rad) * coneLengthPx).toFloat(),
        y = center.y + (sin(rad) * coneLengthPx).toFloat()
    )
    val leftWing = Offset(
        x = center.x + (cos(rad - spreadRad) * (coneLengthPx * 0.62f)).toFloat(),
        y = center.y + (sin(rad - spreadRad) * (coneLengthPx * 0.62f)).toFloat()
    )
    val rightWing = Offset(
        x = center.x + (cos(rad + spreadRad) * (coneLengthPx * 0.62f)).toFloat(),
        y = center.y + (sin(rad + spreadRad) * (coneLengthPx * 0.62f)).toFloat()
    )
    val conePath = Path().apply {
        moveTo(center.x, center.y)
        lineTo(leftWing.x, leftWing.y)
        lineTo(tip.x, tip.y)
        lineTo(rightWing.x, rightWing.y)
        close()
    }
    drawPath(
        path = conePath,
        brush = Brush.radialGradient(
            colors = listOf(arrowColor.copy(alpha = 0.45f), arrowColor.copy(alpha = 0.08f)),
            center = center,
            radius = coneLengthPx
        )
    )
    drawLine(
        color = arrowColor,
        start = center,
        end = tip,
        strokeWidth = 2.6.dp.toPx(),
        cap = StrokeCap.Round
    )
}

fun resolvePaceSegmentColor(
    speedMps: Float,
    avgPaceSecPerKm: Double,
    fallbackColor: Color = ElectricOrange
): Color {
    if (speedMps <= 0.35f || avgPaceSecPerKm <= 0.0) return fallbackColor
    val segPaceSecPerKm = 1000.0 / speedMps.toDouble()
    return when {
        segPaceSecPerKm < avgPaceSecPerKm * 0.93 -> GpsEmerald
        segPaceSecPerKm > avgPaceSecPerKm * 1.07 -> DangerRed
        else -> ElectricOrange
    }
}

private data class RouteScaleInfo(
    val centerLat: Double,
    val centerLon: Double,
    val spanMetersX: Double,
    val spanMetersY: Double
)

private fun computeRouteScaleInfo(points: List<CanvasPoint>): RouteScaleInfo {
    if (points.isEmpty()) return RouteScaleInfo(0.0, 0.0, 100.0, 100.0)
    var minLat = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var minLon = Double.POSITIVE_INFINITY
    var maxLon = Double.NEGATIVE_INFINITY
    points.forEach { p ->
        if (p.latitude < minLat) minLat = p.latitude
        if (p.latitude > maxLat) maxLat = p.latitude
        if (p.longitude < minLon) minLon = p.longitude
        if (p.longitude > maxLon) maxLon = p.longitude
    }
    val centerLat = (minLat + maxLat) / 2.0
    val centerLon = (minLon + maxLon) / 2.0
    val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.15)
    val spanY = (maxLat - minLat) * 111_320.0
    val spanX = (maxLon - minLon) * 111_320.0 * cosLat
    return RouteScaleInfo(
        centerLat = centerLat,
        centerLon = centerLon,
        spanMetersX = spanX.coerceAtLeast(45.0),
        spanMetersY = spanY.coerceAtLeast(45.0)
    )
}

private fun chooseNiceScaleLabel(spanMeters: Double): String {
    val target = spanMeters * 0.25
    return when {
        target < 35 -> "25 m"
        target < 75 -> "50 m"
        target < 175 -> "100 m"
        target < 375 -> "250 m"
        target < 750 -> "500 m"
        target < 1500 -> "1 km"
        target < 3500 -> "2 km"
        else -> "5 km"
    }
}

@Composable
fun MiniRoutePreview(
    encodedPoints: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val coords = remember(encodedPoints) { GeoMath.decodePreviewPoints(encodedPoints) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianBg)
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            if (coords.size < 2) {
                drawLine(
                    color = HairlineBorder,
                    start = Offset(0f, size.height * 0.6f),
                    end = Offset(size.width, size.height * 0.4f),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
                return@Canvas
            }

            val minLat = coords.minOf { it.latitude }
            val maxLat = coords.maxOf { it.latitude }
            val minLon = coords.minOf { it.longitude }
            val maxLon = coords.maxOf { it.longitude }
            val centerLat = (minLat + maxLat) / 2.0
            val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.15)
            val spanY = ((maxLat - minLat) * 111_320.0).coerceAtLeast(25.0)
            val spanX = ((maxLon - minLon) * 111_320.0 * cosLat).coerceAtLeast(25.0)
            val scale = min(size.width / spanX.toFloat(), size.height / spanY.toFloat())
            val centerScreen = Offset(size.width / 2f, size.height / 2f)
            val centerLon = (minLon + maxLon) / 2.0

            val projected = coords.map { pt ->
                val dx = ((pt.longitude - centerLon) * 111_320.0 * cosLat).toFloat() * scale
                val dy = ((pt.latitude - centerLat) * 111_320.0).toFloat() * scale
                pt.segmentIndex to Offset(centerScreen.x + dx, centerScreen.y - dy)
            }

            projected.groupBy { it.first }.values.forEach { seg ->
                if (seg.size >= 2) {
                    val path = Path().apply {
                        moveTo(seg.first().second.x, seg.first().second.y)
                        for (i in 1 until seg.size) {
                            lineTo(seg[i].second.x, seg[i].second.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = accentColor.copy(alpha = 0.25f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = path,
                        color = accentColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            drawCircle(color = GpsEmerald, radius = 3.2.dp.toPx(), center = projected.first().second)
            drawCircle(color = Color.White, radius = 3.2.dp.toPx(), center = projected.last().second)
        }
    }
}

@Composable
fun ElevationProfileChart(
    points: List<CanvasPoint>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    val altitudes = remember(points) { points.map { it.altitudeMeters } }
    val minAlt = remember(altitudes) { altitudes.minOrNull() ?: 0.0 }
    val maxAlt = remember(altitudes) { altitudes.maxOrNull() ?: 0.0 }
    val range = (maxAlt - minAlt).coerceAtLeast(5.0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SlateElevated)
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ELEVATION PROFILE",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Text(
                text = "${minAlt.roundToInt()}m – ${maxAlt.roundToInt()}m",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val strokePath = Path()
            val fillPath = Path()

            altitudes.forEachIndexed { idx, alt ->
                val x = (idx.toFloat() / (altitudes.lastIndex).toFloat()) * w
                val normY = ((alt - minAlt) / range).toFloat().coerceIn(0f, 1f)
                val y = h - (normY * (h - 8.dp.toPx())) - 4.dp.toPx()
                if (idx == 0) {
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(w, h)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.35f), Color.Transparent)
                )
            )
            drawPath(
                path = strokePath,
                color = accentColor,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
