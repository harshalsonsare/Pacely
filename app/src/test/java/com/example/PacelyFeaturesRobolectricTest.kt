package com.example

import android.content.Context
import android.location.Location
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PacelyDatabase
import com.example.data.local.TrackPointEntity
import com.example.data.local.WorkoutEntity
import com.example.data.model.ActivityType
import com.example.data.model.HeadingSourceMode
import com.example.data.model.LapSplit
import com.example.data.model.TrackingStatus
import com.example.data.model.WorkoutWithDetails
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.WorkoutRepository
import com.example.tracking.CompassMath
import com.example.tracking.PersonalRecordsCalculator
import com.example.tracking.SplitFeedbackEngine
import com.example.tracking.SplitFeedbackListener
import com.example.tracking.WorkoutTrackingManager
import com.example.ui.components.resolvePaceSegmentColor
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.GpsEmerald
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PacelyFeaturesRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: PacelyDatabase
    private lateinit var repository: WorkoutRepository
    private lateinit var prefs: UserPreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PacelyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutRepository(database.workoutDao())
        prefs = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun trackingManager_filtersInaccurateGpsPointsOver15m_andAnnouncesConfigurableSplit() {
        val announced = mutableListOf<Pair<LapSplit, Double>>()
        val fakeFeedback = object : SplitFeedbackListener {
            override fun announceSplit(split: LapSplit, cumulativeDistanceMeters: Double) {
                announced.add(split to cumulativeDistanceMeters)
            }
            override fun shutdown() = Unit
        }
        val manager = WorkoutTrackingManager(repository, prefs, fakeFeedback)
        manager.setSplitIntervalKm(0.5f)
        manager.startTracking()

        // Noisy point (> 15m accuracy) must be filtered out
        val noisyLoc = Location("gps").apply {
            latitude = 37.7749
            longitude = -122.4194
            accuracy = 22f
            speed = 3.0f
            time = 1_700_000_000_000L
        }
        manager.onLocationUpdate(noisyLoc)
        assertEquals(0, manager.uiState.value.livePoints.size)

        // Valid start point (accuracy <= 15m)
        val p1 = Location("gps").apply {
            latitude = 37.7749
            longitude = -122.4194
            altitude = 10.0
            accuracy = 6f
            speed = 3.3f
            time = 1_700_000_000_000L
        }
        manager.onLocationUpdate(p1)
        assertEquals(1, manager.uiState.value.livePoints.size)

        // Move ~1.11 km north in 330 seconds -> crosses both 0.5 km and 1.0 km splits
        val p2 = Location("gps").apply {
            latitude = 37.7849
            longitude = -122.4194
            altitude = 16.0
            accuracy = 5f
            speed = 3.37f
            time = 1_700_000_330_000L
        }
        manager.onLocationUpdate(p2)

        assertEquals(2, manager.uiState.value.livePoints.size)
        assertTrue(manager.uiState.value.distanceMeters > 1000.0)
        assertEquals(2, announced.size)
        assertEquals(500.0, announced[0].second, 0.1)
        assertEquals(1000.0, announced[1].second, 0.1)

        val utterance = SplitFeedbackEngine.formatSplitUtterance(
            split = LapSplit(1, 500.0, 150_000L, 300.0, 2.0),
            cumulativeDistanceMeters = 500.0
        )
        assertEquals("0.5 kilometers completed, split pace 5 minutes 0 seconds", utterance)
    }

    @Test
    fun compassMath_handlesWrapAround_declination_andAutoVsMagOnlyMode() {
        // Wrap-around across 359° -> 1° with alpha=0.15 moves forward to 359.3°
        val wrappedForward = CompassMath.smoothAngleDegrees(current = 359f, target = 1f, alpha = 0.15f)
        assertEquals(359.3f, wrappedForward, 0.05f)

        // Wrap-around across 1° -> 359° moves backward to 0.7°
        val wrappedBackward = CompassMath.smoothAngleDegrees(current = 1f, target = 359f, alpha = 0.15f)
        assertEquals(0.7f, wrappedBackward, 0.05f)

        // Declination shifts Magnetic North to True North
        assertEquals(12.5f, CompassMath.applyDeclination(358.5f, 14.0f), 0.01f)

        // AUTO mode uses Magnetometer <= 1.2 m/s and GPS bearing > 1.2 m/s
        val slowAuto = CompassMath.resolveHeading(
            mode = HeadingSourceMode.AUTO,
            magnetometerHeadingDeg = 45f,
            gpsBearingDeg = 180f,
            speedMps = 0.8f
        )
        assertFalse(slowAuto.isUsingGpsBearing)
        assertEquals(45f, slowAuto.headingDegrees ?: -1f, 0.01f)

        val fastAuto = CompassMath.resolveHeading(
            mode = HeadingSourceMode.AUTO,
            magnetometerHeadingDeg = 45f,
            gpsBearingDeg = 180f,
            speedMps = 2.5f
        )
        assertTrue(fastAuto.isUsingGpsBearing)
        assertEquals(180f, fastAuto.headingDegrees ?: -1f, 0.01f)

        val magOnly = CompassMath.resolveHeading(
            mode = HeadingSourceMode.MAGNETOMETER_ONLY,
            magnetometerHeadingDeg = 45f,
            gpsBearingDeg = 180f,
            speedMps = 2.5f
        )
        assertFalse(magOnly.isUsingGpsBearing)
        assertEquals(45f, magOnly.headingDegrees ?: -1f, 0.01f)
    }

    @Test
    fun configurableAutoPauseThreshold_pausesAfter4sBelowThreshold_andResumesAboveThreshold() {
        val manager = WorkoutTrackingManager(repository, prefs, null)
        manager.setAutoPauseEnabled(true)
        manager.setAutoPauseSpeedThresholdMps(1.4f)
        manager.startTracking()

        val startLoc = Location("gps").apply {
            latitude = 37.7749
            longitude = -122.4194
            accuracy = 5f
            speed = 1.0f // Below 1.4 m/s threshold
            time = 1_700_000_000_000L
        }
        manager.onLocationUpdate(startLoc)
        assertEquals(TrackingStatus.TRACKING, manager.uiState.value.status)

        // Advance realtime by 4.2 seconds while still moving below 1.4 m/s
        ShadowSystemClock.advanceBy(Duration.ofMillis(4200L))
        val slowLoc = Location("gps").apply {
            latitude = 37.77493
            longitude = -122.4194
            accuracy = 5f
            speed = 1.1f
            time = 1_700_000_004_200L
        }
        manager.onLocationUpdate(slowLoc)
        assertEquals(TrackingStatus.PAUSED, manager.uiState.value.status)
        assertTrue(manager.uiState.value.isAutoPaused)

        // Now speed rises back above 1.4 m/s -> auto-resumes
        ShadowSystemClock.advanceBy(Duration.ofMillis(2000L))
        val fastLoc = Location("gps").apply {
            latitude = 37.7750
            longitude = -122.4194
            accuracy = 5f
            speed = 2.2f
            time = 1_700_000_006_200L
        }
        manager.onLocationUpdate(fastLoc)
        assertEquals(TrackingStatus.TRACKING, manager.uiState.value.status)
        assertFalse(manager.uiState.value.isAutoPaused)
    }

    @Test
    fun paceColorResolver_mapsFastAvgSlowCorrectly() {
        val avgPace = 300.0 // 5:00 /km -> 3.33 m/s
        assertEquals(GpsEmerald, resolvePaceSegmentColor(speedMps = 4.0f, avgPaceSecPerKm = avgPace))
        assertEquals(ElectricOrange, resolvePaceSegmentColor(speedMps = 3.33f, avgPaceSecPerKm = avgPace))
        assertEquals(DangerRed, resolvePaceSegmentColor(speedMps = 2.5f, avgPaceSecPerKm = avgPace))
    }

    @Test
    fun personalRecordsCalculator_findsFastest1K_5K_andLongest() {
        val w1 = WorkoutEntity(
            id = 10L,
            title = "Tempo 6K",
            activityType = ActivityType.RUN.name,
            startTimeMillis = 1_700_000_000_000L,
            endTimeMillis = 1_700_001_500_000L,
            elapsedMillis = 1_500_000L,
            distanceMeters = 6000.0,
            avgPaceSecPerKm = 250.0,
            caloriesBurned = 420,
            elevationGainMeters = 40.0,
            elevationLossMeters = 35.0,
            encodedPreviewPoints = ""
        )
        val pts = listOf(
            TrackPointEntity(1L, 10L, 37.77, -122.41, 10.0, 1_700_000_000_000L, 0L, 4.0f, 0.0, 0),
            TrackPointEntity(2L, 10L, 37.78, -122.41, 12.0, 1_700_000_220_000L, 220_000L, 4.5f, 1000.0, 0),
            TrackPointEntity(3L, 10L, 37.82, -122.41, 20.0, 1_700_001_220_000L, 1_220_000L, 4.0f, 5000.0, 0),
            TrackPointEntity(4L, 10L, 37.83, -122.41, 25.0, 1_700_001_500_000L, 1_500_000L, 3.6f, 6000.0, 0)
        )

        val prs = PersonalRecordsCalculator.compute(
            listOf(WorkoutWithDetails(w1, pts, emptyList()))
        )

        assertNotNull(prs.fastest1K)
        assertEquals(220_000L, prs.fastest1K?.durationMillis)
        assertNotNull(prs.fastest5K)
        assertEquals(1_220_000L, prs.fastest5K?.durationMillis)
        assertNull(prs.fastest10K)
        assertEquals(6000.0, prs.longestDistance?.distanceMeters ?: 0.0, 0.1)
    }

    @Test
    fun shoeMileageTracker_aggregatesInitialAndTrackedMileage() = runTest {
        val shoeId = repository.addShoe(
            brand = "Nike",
            name = "Pegasus 41",
            initialMileageKm = 45.0,
            targetMaxKm = 800.0,
            setAsDefault = true
        )

        repository.saveCompletedWorkout(
            activityType = ActivityType.RUN,
            startTimeMillis = 1_700_000_000_000L,
            endTimeMillis = 1_700_001_800_000L,
            elapsedMillis = 1_800_000L,
            distanceMeters = 5200.0,
            caloriesBurned = 380.0,
            elevationGainMeters = 25.0,
            elevationLossMeters = 20.0,
            points = emptyList(),
            splits = emptyList(),
            shoeId = shoeId
        )

        val stats = repository.observeShoeMileageStats().first()
        assertEquals(1, stats.size)
        val peg = stats.first()
        assertEquals(5.2, peg.loggedDistanceKm, 0.01)
        assertEquals(50.2, peg.totalDistanceKm, 0.01)
        assertEquals(1, peg.workoutCount)
    }
}
