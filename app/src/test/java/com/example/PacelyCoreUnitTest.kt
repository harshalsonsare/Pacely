package com.example

import com.example.data.export.WorkoutExporter
import com.example.data.local.LapSplitEntity
import com.example.data.local.TrackPointEntity
import com.example.data.local.WorkoutEntity
import com.example.data.model.ActivityType
import com.example.data.model.GeoPoint
import com.example.data.model.WorkoutWithDetails
import com.example.tracking.GeoMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacelyCoreUnitTest {

    @Test
    fun haversineDistance_isAccurateForShortRunSegment() {
        // ~111m per 0.001 degree latitude
        val dist = GeoMath.haversineMeters(37.7749, -122.4194, 37.7759, -122.4194)
        assertTrue("Expected ~111.2m, got $dist", dist in 108.0..114.0)
    }

    @Test
    fun formatters_renderExpectedPaceAndElapsed() {
        assertEquals("05:30", GeoMath.formatElapsedTime(330_000L))
        assertEquals("1:02:05", GeoMath.formatElapsedTime(3_725_000L))
        assertEquals("4:35", GeoMath.formatPace(275.0))
        assertEquals("--:--", GeoMath.formatPace(null))
        assertEquals("--:--", GeoMath.formatPace(0.0))
    }

    @Test
    fun metCalories_runHigherThanWalk() {
        val runMet = GeoMath.calculateDynamicMet(ActivityType.RUN, 3.2)
        val walkMet = GeoMath.calculateDynamicMet(ActivityType.WALK, 1.4)
        assertTrue(runMet > walkMet)
    }

    @Test
    fun previewEncoding_roundTripsCoordinates() {
        val points = listOf(
            GeoPoint(37.77490, -122.41940, 12.0, 1000L, 0L, 3.0f, 0.0, 0),
            GeoPoint(37.77550, -122.41890, 14.0, 15000L, 14000L, 3.1f, 80.0, 0),
            GeoPoint(37.77600, -122.41850, 15.0, 30000L, 29000L, 3.2f, 150.0, 1)
        )
        val encoded = GeoMath.encodePreviewPoints(points)
        val decoded = GeoMath.decodePreviewPoints(encoded)
        assertEquals(3, decoded.size)
        assertEquals(37.7749, decoded[0].latitude, 0.0001)
        assertEquals(1, decoded[2].segmentIndex)
    }

    @Test
    fun exporters_generateValidCsvAndGpx() {
        val workout = WorkoutEntity(
            id = 7L,
            title = "Morning Run",
            activityType = ActivityType.RUN.name,
            startTimeMillis = 1_700_000_000_000L,
            endTimeMillis = 1_700_000_600_000L,
            elapsedMillis = 600_000L,
            distanceMeters = 2000.0,
            avgPaceSecPerKm = 300.0,
            caloriesBurned = 160,
            elevationGainMeters = 24.0,
            elevationLossMeters = 18.0,
            encodedPreviewPoints = ""
        )
        val trackPoints = listOf(
            TrackPointEntity(1L, 7L, 37.7749, -122.4194, 10.0, 1_700_000_000_000L, 0L, 3.3f, 0.0, 0),
            TrackPointEntity(2L, 7L, 37.7839, -122.4194, 22.0, 1_700_000_300_000L, 300_000L, 3.3f, 1000.0, 0)
        )
        val splits = listOf(
            LapSplitEntity(1L, 7L, 1, 1000.0, 300_000L, 300.0, 12.0)
        )
        val details = WorkoutWithDetails(workout, trackPoints, splits)

        val csv = WorkoutExporter.toCsv(listOf(workout))
        assertTrue(csv.contains("Morning Run"))
        assertTrue(csv.contains("2.000"))
        assertTrue(csv.contains("5:00"))

        val gpx = WorkoutExporter.toGpx(details)
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("<trkpt lat=\"37.7749000\" lon=\"-122.4194000\">"))
        assertTrue(gpx.contains("<ele>10.00</ele>"))
        assertTrue(gpx.contains("<type>running</type>"))
    }
}
