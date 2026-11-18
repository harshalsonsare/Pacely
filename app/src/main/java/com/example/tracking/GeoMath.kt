package com.example.tracking

import com.example.data.model.ActivityType
import com.example.data.model.GeoPoint
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(radLat1) * cos(radLat2) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun calculateDynamicMet(activityType: ActivityType, speedMps: Double): Double {
        val speedKmh = (speedMps * 3.6).coerceAtLeast(0.0)
        return when (activityType) {
            ActivityType.RUN -> {
                if (speedKmh < 5.0) 7.0
                else (8.3 + ((speedKmh - 8.0).coerceIn(0.0, 9.0) * 0.55)).coerceIn(7.0, 13.5)
            }
            ActivityType.WALK -> {
                if (speedKmh < 2.0) 2.5
                else (2.8 + ((speedKmh - 3.0).coerceIn(0.0, 4.5) * 0.52)).coerceIn(2.5, 5.2)
            }
        }
    }

    fun formatElapsedTime(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatPace(secPerKm: Double?): String {
        if (secPerKm == null || secPerKm.isNaN() || secPerKm.isInfinite() || secPerKm <= 0.0 || secPerKm > 3599.0) {
            return "--:--"
        }
        val rounded = secPerKm.roundToInt()
        val mins = rounded / 60
        val secs = rounded % 60
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }

    fun formatDistanceKm(meters: Double): String {
        val km = (meters / 1000.0).coerceAtLeast(0.0)
        return String.format(Locale.US, "%.2f", km)
    }

    fun encodePreviewPoints(points: List<GeoPoint>, maxPoints: Int = 64): String {
        if (points.isEmpty()) return ""
        val sampled = if (points.size <= maxPoints) {
            points
        } else {
            val step = (points.size - 1).toDouble() / (maxPoints - 1).toDouble()
            List(maxPoints) { idx ->
                points[(idx * step).roundToInt().coerceIn(0, points.lastIndex)]
            }
        }
        return sampled.joinToString(";") { pt ->
            String.format(Locale.US, "%.5f,%.5f,%d", pt.latitude, pt.longitude, pt.segmentIndex)
        }
    }

    data class PreviewCoord(
        val latitude: Double,
        val longitude: Double,
        val segmentIndex: Int
    )

    fun decodePreviewPoints(encoded: String): List<PreviewCoord> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(";").mapNotNull { token ->
            val parts = token.split(",")
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                val seg = parts.getOrNull(2)?.toIntOrNull() ?: 0
                if (lat != null && lon != null) PreviewCoord(lat, lon, seg) else null
            } else {
                null
            }
        }
    }
}
