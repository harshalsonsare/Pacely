package com.example.tracking

import android.location.Location
import android.os.SystemClock
import com.example.data.model.ActivityType
import com.example.data.model.GeoPoint
import com.example.data.model.HeadingSourceMode
import com.example.data.model.LapSplit
import com.example.data.model.TrackingStatus
import com.example.data.model.TrackingUiState
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class WorkoutTrackingManager(
    private val workoutRepository: WorkoutRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val splitFeedbackListener: SplitFeedbackListener? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    var locationUpdateHook: ((latitude: Double, longitude: Double, altitudeMeters: Double) -> Unit)? = null

    private var timerJob: Job? = null
    private var startWallTimeMillis: Long = 0L
    private var accumulatedElapsedMillis: Long = 0L
    private var lastResumeRealtimeMillis: Long = 0L
    private var currentSegmentIndex: Int = 0

    private var referenceAltitudeMeters: Double? = null
    private var lastSplitDistanceMeters: Double = 0.0
    private var lastSplitElapsedMillis: Long = 0L
    private var lastSplitAltitudeMeters: Double? = null

    private var lastPointTimestampMillis: Long = 0L
    private var lowSpeedSinceRealtimeMillis: Long? = null
    private var hasUserOverriddenShoe = false

    init {
        scope.launch {
            preferencesRepository.weightKgFlow.collect { weight ->
                _uiState.update { it.copy(weightKg = weight) }
            }
        }
        scope.launch {
            preferencesRepository.autoPauseEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(autoPauseEnabled = enabled) }
                if (!enabled) {
                    lowSpeedSinceRealtimeMillis = null
                }
            }
        }
        scope.launch {
            preferencesRepository.autoPauseSpeedThresholdMpsFlow.collect { threshold ->
                _uiState.update { it.copy(autoPauseSpeedThresholdMps = threshold) }
            }
        }
        scope.launch {
            preferencesRepository.voiceSplitCuesEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(voiceSplitCuesEnabled = enabled) }
            }
        }
        scope.launch {
            preferencesRepository.splitIntervalKmFlow.collect { intervalKm ->
                _uiState.update { it.copy(splitIntervalKm = intervalKm) }
            }
        }
        scope.launch {
            preferencesRepository.headingSourceModeFlow.collect { mode ->
                _uiState.update { current ->
                    val resolved = CompassMath.resolveHeading(
                        mode = mode,
                        magnetometerHeadingDeg = current.magnetometerHeadingDegrees,
                        gpsBearingDeg = current.gpsBearingDegrees,
                        speedMps = current.currentSpeedMps
                    )
                    current.copy(
                        headingSourceMode = mode,
                        activeHeadingDegrees = resolved.headingDegrees,
                        isUsingGpsBearing = resolved.isUsingGpsBearing
                    )
                }
            }
        }
        scope.launch {
            preferencesRepository.darkMapModeEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(darkMapModeEnabled = enabled) }
            }
        }
        scope.launch {
            workoutRepository.allShoes.collect { shoes ->
                if (!hasUserOverriddenShoe) {
                    val defaultId = shoes.firstOrNull { it.isDefault && !it.isRetired }?.id
                        ?: shoes.firstOrNull { !it.isRetired }?.id
                    _uiState.update { current ->
                        if (current.selectedShoeId == null || shoes.none { it.id == current.selectedShoeId }) {
                            current.copy(selectedShoeId = defaultId)
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    fun updateCompassHeading(headingDegrees: Float?, needsCalibration: Boolean) {
        _uiState.update { current ->
            val resolved = CompassMath.resolveHeading(
                mode = current.headingSourceMode,
                magnetometerHeadingDeg = headingDegrees,
                gpsBearingDeg = current.gpsBearingDegrees,
                speedMps = current.currentSpeedMps
            )
            current.copy(
                magnetometerHeadingDegrees = headingDegrees,
                needsCompassCalibration = needsCalibration,
                activeHeadingDegrees = resolved.headingDegrees,
                isUsingGpsBearing = resolved.isUsingGpsBearing
            )
        }
    }

    fun toggleHeadingSourceMode() {
        val nextMode = when (_uiState.value.headingSourceMode) {
            HeadingSourceMode.AUTO -> HeadingSourceMode.MAGNETOMETER_ONLY
            HeadingSourceMode.MAGNETOMETER_ONLY -> HeadingSourceMode.AUTO
        }
        _uiState.update { current ->
            val resolved = CompassMath.resolveHeading(
                mode = nextMode,
                magnetometerHeadingDeg = current.magnetometerHeadingDegrees,
                gpsBearingDeg = current.gpsBearingDegrees,
                speedMps = current.currentSpeedMps
            )
            current.copy(
                headingSourceMode = nextMode,
                activeHeadingDegrees = resolved.headingDegrees,
                isUsingGpsBearing = resolved.isUsingGpsBearing
            )
        }
        scope.launch {
            preferencesRepository.setHeadingSourceMode(nextMode)
        }
    }

    fun setActivityType(type: ActivityType) {
        if (_uiState.value.status == TrackingStatus.IDLE) {
            _uiState.update { it.copy(activityType = type) }
        }
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoPauseEnabled = enabled) }
        if (!enabled) {
            lowSpeedSinceRealtimeMillis = null
        }
        scope.launch {
            preferencesRepository.setAutoPauseEnabled(enabled)
        }
    }

    fun setAutoPauseSpeedThresholdMps(speedMps: Float) {
        val clamped = speedMps.coerceIn(0.2f, 2.0f)
        _uiState.update { it.copy(autoPauseSpeedThresholdMps = clamped) }
        scope.launch {
            preferencesRepository.setAutoPauseSpeedThresholdMps(clamped)
        }
    }

    fun setSplitIntervalKm(intervalKm: Float) {
        val clamped = intervalKm.coerceIn(0.1f, 50.0f)
        _uiState.update { it.copy(splitIntervalKm = clamped) }
        scope.launch {
            preferencesRepository.setSplitIntervalKm(clamped)
        }
    }

    fun setDarkMapModeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(darkMapModeEnabled = enabled) }
        scope.launch {
            preferencesRepository.setDarkMapModeEnabled(enabled)
        }
    }

    fun toggleDarkMapMode() {
        setDarkMapModeEnabled(!_uiState.value.darkMapModeEnabled)
    }

    fun selectShoe(shoeId: Long?) {
        hasUserOverriddenShoe = true
        _uiState.update { it.copy(selectedShoeId = shoeId) }
    }

    fun startTracking() {
        val nowWall = System.currentTimeMillis()
        val nowRealtime = SystemClock.elapsedRealtime()

        startWallTimeMillis = nowWall
        accumulatedElapsedMillis = 0L
        lastResumeRealtimeMillis = nowRealtime
        currentSegmentIndex = 0
        referenceAltitudeMeters = null
        lastSplitDistanceMeters = 0.0
        lastSplitElapsedMillis = 0L
        lastSplitAltitudeMeters = null
        lastPointTimestampMillis = nowWall
        lowSpeedSinceRealtimeMillis = null

        _uiState.update { current ->
            current.copy(
                status = TrackingStatus.TRACKING,
                isAutoPaused = false,
                elapsedMillis = 0L,
                distanceMeters = 0.0,
                currentPaceSecPerKm = null,
                avgPaceSecPerKm = null,
                caloriesBurned = 0.0,
                elevationGainMeters = 0.0,
                elevationLossMeters = 0.0,
                livePoints = emptyList(),
                completedSplits = emptyList(),
                currentLapNumber = 1,
                currentLapDistanceMeters = 0.0,
                currentLapElapsedMillis = 0L
            )
        }
        startTicker()
    }

    fun pauseTracking(isAuto: Boolean = false) {
        if (_uiState.value.status != TrackingStatus.TRACKING) return
        val nowRealtime = SystemClock.elapsedRealtime()
        accumulatedElapsedMillis += (nowRealtime - lastResumeRealtimeMillis).coerceAtLeast(0L)
        timerJob?.cancel()
        currentSegmentIndex += 1
        lowSpeedSinceRealtimeMillis = null
        _uiState.update {
            it.copy(
                status = TrackingStatus.PAUSED,
                isAutoPaused = isAuto,
                elapsedMillis = accumulatedElapsedMillis,
                currentPaceSecPerKm = null,
                currentLapElapsedMillis = (accumulatedElapsedMillis - lastSplitElapsedMillis).coerceAtLeast(0L)
            )
        }
    }

    fun resumeTracking() {
        if (_uiState.value.status != TrackingStatus.PAUSED) return
        lastResumeRealtimeMillis = SystemClock.elapsedRealtime()
        lastPointTimestampMillis = System.currentTimeMillis()
        lowSpeedSinceRealtimeMillis = null
        _uiState.update { it.copy(status = TrackingStatus.TRACKING, isAutoPaused = false) }
        startTicker()
    }

    suspend fun finishAndSaveWorkout(): Long? {
        val current = _uiState.value
        if (current.status == TrackingStatus.IDLE) return null

        val totalElapsed = computeCurrentElapsedMillis()
        timerJob?.cancel()

        val savedId = if (current.distanceMeters >= 10.0 || current.livePoints.size >= 2) {
            val finalSplits = buildFinalSplits(current, totalElapsed)
            workoutRepository.saveCompletedWorkout(
                activityType = current.activityType,
                startTimeMillis = if (startWallTimeMillis > 0L) startWallTimeMillis else System.currentTimeMillis() - totalElapsed,
                endTimeMillis = System.currentTimeMillis(),
                elapsedMillis = totalElapsed,
                distanceMeters = current.distanceMeters,
                caloriesBurned = current.caloriesBurned,
                elevationGainMeters = current.elevationGainMeters,
                elevationLossMeters = current.elevationLossMeters,
                points = current.livePoints,
                splits = finalSplits,
                shoeId = current.selectedShoeId
            )
        } else {
            null
        }

        resetToIdle()
        return savedId
    }

    fun discardWorkout() {
        timerJob?.cancel()
        resetToIdle()
    }

    private fun resetToIdle() {
        accumulatedElapsedMillis = 0L
        currentSegmentIndex = 0
        referenceAltitudeMeters = null
        lastSplitDistanceMeters = 0.0
        lastSplitElapsedMillis = 0L
        lastSplitAltitudeMeters = null
        lowSpeedSinceRealtimeMillis = null
        _uiState.update { current ->
            TrackingUiState(
                activityType = current.activityType,
                autoPauseEnabled = current.autoPauseEnabled,
                autoPauseSpeedThresholdMps = current.autoPauseSpeedThresholdMps,
                voiceSplitCuesEnabled = current.voiceSplitCuesEnabled,
                splitIntervalKm = current.splitIntervalKm,
                selectedShoeId = current.selectedShoeId,
                headingSourceMode = current.headingSourceMode,
                magnetometerHeadingDegrees = current.magnetometerHeadingDegrees,
                gpsBearingDegrees = current.gpsBearingDegrees,
                currentSpeedMps = current.currentSpeedMps,
                activeHeadingDegrees = current.activeHeadingDegrees,
                isUsingGpsBearing = current.isUsingGpsBearing,
                needsCompassCalibration = current.needsCompassCalibration,
                gpsAccuracyMeters = current.gpsAccuracyMeters,
                lastKnownLocation = current.lastKnownLocation,
                currentAltitudeMeters = current.currentAltitudeMeters,
                weightKg = current.weightKg,
                darkMapModeEnabled = current.darkMapModeEnabled
            )
        }
    }

    fun onLocationUpdate(location: Location) {
        val accuracy = if (location.hasAccuracy()) location.accuracy else 10f
        val alt = if (location.hasAltitude()) location.altitude else (_uiState.value.currentAltitudeMeters ?: 0.0)
        val rawBearing = if (location.hasBearing()) CompassMath.normalizeDegrees(location.bearing) else null
        val instantSpeed = if (location.hasSpeed()) location.speed.coerceAtLeast(0f) else _uiState.value.currentSpeedMps

        locationUpdateHook?.invoke(location.latitude, location.longitude, alt)

        _uiState.update { current ->
            val bearingToUse = rawBearing ?: current.gpsBearingDegrees
            val resolved = CompassMath.resolveHeading(
                mode = current.headingSourceMode,
                magnetometerHeadingDeg = current.magnetometerHeadingDegrees,
                gpsBearingDeg = bearingToUse,
                speedMps = instantSpeed
            )
            current.copy(
                gpsAccuracyMeters = accuracy,
                lastKnownLocation = location.latitude to location.longitude,
                currentAltitudeMeters = alt,
                gpsBearingDegrees = bearingToUse,
                currentSpeedMps = instantSpeed,
                activeHeadingDegrees = resolved.headingDegrees,
                isUsingGpsBearing = resolved.isUsingGpsBearing
            )
        }

        // Reject noisy GPS points with accuracy worse than 15 meters
        if (accuracy > MAX_ALLOWED_ACCURACY_METERS) return

        val current = _uiState.value
        val nowWall = if (location.time > 0L) location.time else System.currentTimeMillis()
        val nowRealtime = SystemClock.elapsedRealtime()
        val autoPauseThreshold = current.autoPauseSpeedThresholdMps.coerceIn(0.2f, 2.0f)

        // Auto-Resume if currently Auto-Paused and speed rises back above the configured threshold
        if (current.status == TrackingStatus.PAUSED && current.isAutoPaused && current.autoPauseEnabled) {
            val prevPoint = current.livePoints.lastOrNull()
            val movedMeters = if (prevPoint != null) {
                GeoMath.haversineMeters(prevPoint.latitude, prevPoint.longitude, location.latitude, location.longitude)
            } else {
                0.0
            }
            val dtSec = if (prevPoint != null) ((nowWall - prevPoint.timestampMillis) / 1000.0).coerceAtLeast(1.0) else 1.0
            val impliedSpeed = movedMeters / dtSec
            val reportedSpeed = if (location.hasSpeed()) location.speed else impliedSpeed.toFloat()

            if (reportedSpeed > autoPauseThreshold || (movedMeters >= 4.0 && impliedSpeed > autoPauseThreshold)) {
                resumeTracking()
            } else {
                return
            }
        }

        val activeState = _uiState.value
        if (activeState.status != TrackingStatus.TRACKING) return

        val elapsedNow = computeCurrentElapsedMillis()
        val existingPoints = activeState.livePoints
        val prevPoint = existingPoints.lastOrNull()

        var distanceDelta = 0.0
        var impliedSpeedMps = 0.0
        if (prevPoint != null && prevPoint.segmentIndex == currentSegmentIndex) {
            val delta = GeoMath.haversineMeters(
                prevPoint.latitude,
                prevPoint.longitude,
                location.latitude,
                location.longitude
            )
            val dtSec = ((nowWall - prevPoint.timestampMillis) / 1000.0).coerceAtLeast(0.5)
            impliedSpeedMps = delta / dtSec

            // Reject GPS teleport spikes (> 13 m/s ~ 46.8 km/h)
            if (impliedSpeedMps > 13.0) return
            distanceDelta = delta
        }

        val reportedSpeed = if (location.hasSpeed()) location.speed else impliedSpeedMps.toFloat()
        val effectiveSpeed = if (location.hasSpeed() && location.speed > 0f) location.speed else impliedSpeedMps.toFloat()

        // Update speed + heading source resolution with effectiveSpeed
        _uiState.update { state ->
            val resolved = CompassMath.resolveHeading(
                mode = state.headingSourceMode,
                magnetometerHeadingDeg = state.magnetometerHeadingDegrees,
                gpsBearingDeg = state.gpsBearingDegrees,
                speedMps = effectiveSpeed
            )
            state.copy(
                currentSpeedMps = effectiveSpeed,
                activeHeadingDegrees = resolved.headingDegrees,
                isUsingGpsBearing = resolved.isUsingGpsBearing
            )
        }

        // Auto-Pause check when GPS speed stays below configured threshold for 4 seconds
        if (activeState.autoPauseEnabled) {
            if (effectiveSpeed < autoPauseThreshold) {
                if (lowSpeedSinceRealtimeMillis == null) {
                    lowSpeedSinceRealtimeMillis = nowRealtime
                } else if (nowRealtime - lowSpeedSinceRealtimeMillis!! >= AUTO_PAUSE_DURATION_MS) {
                    pauseTracking(isAuto = true)
                    return
                }
            } else {
                lowSpeedSinceRealtimeMillis = null
            }
        }

        // Reject stationary GPS jitter (< 2.5m when speed is very low)
        if (prevPoint != null && prevPoint.segmentIndex == currentSegmentIndex && distanceDelta < 2.5 && reportedSpeed < 0.6f) {
            return
        }

        val newDistanceMeters = activeState.distanceMeters + distanceDelta

        // Elevation accumulation with 3.0m hysteresis filter
        var gainDelta = 0.0
        var lossDelta = 0.0
        if (location.hasAltitude()) {
            val refAlt = referenceAltitudeMeters
            if (refAlt == null) {
                referenceAltitudeMeters = alt
                if (lastSplitAltitudeMeters == null) {
                    lastSplitAltitudeMeters = alt
                }
            } else {
                val diff = alt - refAlt
                if (abs(diff) >= 3.0) {
                    if (diff > 0) gainDelta = diff else lossDelta = abs(diff)
                    referenceAltitudeMeters = alt
                }
            }
        }

        val newPoint = GeoPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = alt,
            timestampMillis = nowWall,
            elapsedMillis = elapsedNow,
            speedMps = effectiveSpeed,
            distanceFromStartMeters = newDistanceMeters,
            segmentIndex = currentSegmentIndex
        )
        val updatedPoints = existingPoints + newPoint

        // Check for completed configurable split intervals & trigger Voice + Haptic cue
        val splitStepMeters = (activeState.splitIntervalKm.toDouble().coerceIn(0.1, 50.0)) * 1000.0
        val updatedSplits = activeState.completedSplits.toMutableList()
        var nextThreshold = lastSplitDistanceMeters + splitStepMeters
        while (newDistanceMeters >= nextThreshold) {
            val splitDuration = (elapsedNow - lastSplitElapsedMillis).coerceAtLeast(1000L)
            val splitPace = (splitDuration / 1000.0) / (splitStepMeters / 1000.0)
            val elevDelta = alt - (lastSplitAltitudeMeters ?: alt)
            val newSplit = LapSplit(
                lapNumber = updatedSplits.size + 1,
                distanceMeters = splitStepMeters,
                durationMillis = splitDuration,
                paceSecPerKm = splitPace,
                elevationDeltaMeters = elevDelta
            )
            updatedSplits.add(newSplit)
            if (activeState.voiceSplitCuesEnabled) {
                splitFeedbackListener?.announceSplit(newSplit, nextThreshold)
            }
            lastSplitDistanceMeters = nextThreshold
            lastSplitElapsedMillis = elapsedNow
            lastSplitAltitudeMeters = alt
            nextThreshold = lastSplitDistanceMeters + splitStepMeters
        }

        val currentPace = computeRollingPaceSecPerKm(updatedPoints, currentSegmentIndex)
        val avgPace = if (newDistanceMeters >= 15.0 && elapsedNow > 0L) {
            (elapsedNow / 1000.0) / (newDistanceMeters / 1000.0)
        } else {
            null
        }

        val dtCalMillis = (nowWall - lastPointTimestampMillis).coerceIn(0L, 15_000L)
        lastPointTimestampMillis = nowWall
        val met = GeoMath.calculateDynamicMet(activeState.activityType, effectiveSpeed.toDouble())
        val calDelta = if (distanceDelta > 0.0) {
            met * activeState.weightKg * (dtCalMillis / 3_600_000.0)
        } else {
            0.0
        }

        _uiState.update {
            it.copy(
                elapsedMillis = elapsedNow,
                distanceMeters = newDistanceMeters,
                currentPaceSecPerKm = currentPace,
                avgPaceSecPerKm = avgPace,
                caloriesBurned = it.caloriesBurned + calDelta,
                elevationGainMeters = it.elevationGainMeters + gainDelta,
                elevationLossMeters = it.elevationLossMeters + lossDelta,
                livePoints = updatedPoints,
                completedSplits = updatedSplits,
                currentLapNumber = updatedSplits.size + 1,
                currentLapDistanceMeters = (newDistanceMeters - lastSplitDistanceMeters).coerceAtLeast(0.0),
                currentLapElapsedMillis = (elapsedNow - lastSplitElapsedMillis).coerceAtLeast(0L)
            )
        }
    }

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(500L)
                val state = _uiState.value
                if (state.status != TrackingStatus.TRACKING) break

                val nowRealtime = SystemClock.elapsedRealtime()
                val lowSince = lowSpeedSinceRealtimeMillis
                if (state.autoPauseEnabled && lowSince != null && nowRealtime - lowSince >= AUTO_PAUSE_DURATION_MS) {
                    pauseTracking(isAuto = true)
                    break
                }

                val elapsed = computeCurrentElapsedMillis()
                val avgPace = if (state.distanceMeters >= 15.0 && elapsed > 0L) {
                    (elapsed / 1000.0) / (state.distanceMeters / 1000.0)
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        elapsedMillis = elapsed,
                        avgPaceSecPerKm = avgPace,
                        currentLapElapsedMillis = (elapsed - lastSplitElapsedMillis).coerceAtLeast(0L)
                    )
                }
            }
        }
    }

    private fun computeCurrentElapsedMillis(): Long {
        val state = _uiState.value
        return when (state.status) {
            TrackingStatus.TRACKING -> accumulatedElapsedMillis + (SystemClock.elapsedRealtime() - lastResumeRealtimeMillis).coerceAtLeast(0L)
            TrackingStatus.PAUSED -> accumulatedElapsedMillis
            TrackingStatus.IDLE -> 0L
        }
    }

    private fun computeRollingPaceSecPerKm(points: List<GeoPoint>, activeSegment: Int): Double? {
        val segPoints = points.filter { it.segmentIndex == activeSegment }
        if (segPoints.size < 2) return null
        val latest = segPoints.last()
        val windowStart = segPoints.firstOrNull { latest.elapsedMillis - it.elapsedMillis <= 15_000L } ?: segPoints[segPoints.size - 2]
        val dist = latest.distanceFromStartMeters - windowStart.distanceFromStartMeters
        val timeSec = (latest.elapsedMillis - windowStart.elapsedMillis) / 1000.0
        if (dist < 6.0 || timeSec < 2.0) {
            val speed = latest.speedMps.toDouble()
            return if (speed >= 0.4) 1000.0 / speed else null
        }
        return timeSec / (dist / 1000.0)
    }

    private fun buildFinalSplits(state: TrackingUiState, totalElapsed: Long): List<LapSplit> {
        val result = state.completedSplits.toMutableList()
        val remainingDist = (state.distanceMeters - lastSplitDistanceMeters).coerceAtLeast(0.0)
        val remainingTime = (totalElapsed - lastSplitElapsedMillis).coerceAtLeast(0L)
        if (remainingDist >= 20.0 && remainingTime > 0L) {
            val pace = (remainingTime / 1000.0) / (remainingDist / 1000.0)
            val lastAlt = state.livePoints.lastOrNull()?.altitudeMeters ?: 0.0
            val elevDelta = lastAlt - (lastSplitAltitudeMeters ?: lastAlt)
            result.add(
                LapSplit(
                    lapNumber = result.size + 1,
                    distanceMeters = remainingDist,
                    durationMillis = remainingTime,
                    paceSecPerKm = pace,
                    elevationDeltaMeters = elevDelta
                )
            )
        }
        return result
    }

    companion object {
        const val MAX_ALLOWED_ACCURACY_METERS = 15f
        const val DEFAULT_AUTO_PAUSE_SPEED_THRESHOLD_MPS = 0.5f
        const val AUTO_PAUSE_DURATION_MS = 4000L
    }
}
