package com.example.data.model

import com.example.data.local.LapSplitEntity
import com.example.data.local.ShoeEntity
import com.example.data.local.TrackPointEntity
import com.example.data.local.WorkoutEntity

enum class ActivityType(val label: String, val baseMet: Double) {
    RUN("Run", 9.8),
    WALK("Walk", 3.8);

    companion object {
        fun fromString(value: String): ActivityType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: RUN
    }
}

enum class TrackingStatus {
    IDLE,
    TRACKING,
    PAUSED
}

enum class HeadingSourceMode(val label: String) {
    AUTO("Auto"),
    MAGNETOMETER_ONLY("Mag Only");

    companion object {
        fun fromString(value: String?): HeadingSourceMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
    }
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val timestampMillis: Long,
    val elapsedMillis: Long,
    val speedMps: Float,
    val distanceFromStartMeters: Double,
    val segmentIndex: Int = 0
)

data class LapSplit(
    val lapNumber: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val paceSecPerKm: Double,
    val elevationDeltaMeters: Double
)

data class WorkoutWithDetails(
    val workout: WorkoutEntity,
    val trackPoints: List<TrackPointEntity>,
    val splits: List<LapSplitEntity>
)

data class WeeklySummary(
    val totalDistanceMeters: Double = 0.0,
    val totalDurationMillis: Long = 0L,
    val workoutCount: Int = 0,
    val runCount: Int = 0,
    val walkCount: Int = 0,
    val totalCalories: Int = 0,
    val totalElevationGainMeters: Double = 0.0
)

data class TrackingUiState(
    val status: TrackingStatus = TrackingStatus.IDLE,
    val activityType: ActivityType = ActivityType.RUN,
    val isAutoPaused: Boolean = false,
    val autoPauseEnabled: Boolean = true,
    val autoPauseSpeedThresholdMps: Float = 0.5f,
    val voiceSplitCuesEnabled: Boolean = true,
    val splitIntervalKm: Float = 1.0f,
    val selectedShoeId: Long? = null,
    val headingSourceMode: HeadingSourceMode = HeadingSourceMode.AUTO,
    val magnetometerHeadingDegrees: Float? = null,
    val gpsBearingDegrees: Float? = null,
    val currentSpeedMps: Float = 0f,
    val activeHeadingDegrees: Float? = null,
    val isUsingGpsBearing: Boolean = false,
    val needsCompassCalibration: Boolean = false,
    val elapsedMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val currentPaceSecPerKm: Double? = null,
    val avgPaceSecPerKm: Double? = null,
    val caloriesBurned: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val currentAltitudeMeters: Double? = null,
    val gpsAccuracyMeters: Float? = null,
    val livePoints: List<GeoPoint> = emptyList(),
    val completedSplits: List<LapSplit> = emptyList(),
    val currentLapNumber: Int = 1,
    val currentLapDistanceMeters: Double = 0.0,
    val currentLapElapsedMillis: Long = 0L,
    val lastKnownLocation: Pair<Double, Double>? = null,
    val weightKg: Float = 70f,
    val darkMapModeEnabled: Boolean = true
)

data class PersonalRecord(
    val categoryLabel: String,
    val workoutId: Long,
    val workoutTitle: String,
    val dateMillis: Long,
    val durationMillis: Long,
    val distanceMeters: Double,
    val paceSecPerKm: Double
)

data class PersonalRecordsSummary(
    val fastest1K: PersonalRecord? = null,
    val fastest5K: PersonalRecord? = null,
    val fastest10K: PersonalRecord? = null,
    val longestDistance: PersonalRecord? = null
)

data class ShoeMileageStats(
    val shoe: ShoeEntity,
    val loggedDistanceKm: Double,
    val totalDistanceKm: Double,
    val workoutCount: Int
)
