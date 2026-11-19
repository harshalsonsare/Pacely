package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.BackupSettingsPayload
import com.example.data.export.WorkoutExporter
import com.example.data.export.WorkoutImporter
import com.example.data.local.LapSplitEntity
import com.example.data.local.PacelyDatabase
import com.example.data.local.TrackPointEntity
import com.example.data.local.WorkoutEntity
import com.example.data.model.ActivityType
import com.example.data.model.WorkoutWithDetails
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupImportRobolectricTest {

    private lateinit var database: PacelyDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PacelyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutRepository(database.workoutDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun jsonRoundTrip_andDuplicatePrevention_worksEndToEnd() = runTest {
        val existingStart = 1_700_000_000_500L
        val existingWorkout = WorkoutEntity(
            id = 1L,
            title = "Morning Run",
            activityType = ActivityType.RUN.name,
            startTimeMillis = existingStart,
            endTimeMillis = existingStart + 600_000L,
            elapsedMillis = 600_000L,
            distanceMeters = 2000.0,
            avgPaceSecPerKm = 300.0,
            caloriesBurned = 175,
            elevationGainMeters = 18.0,
            elevationLossMeters = 12.0,
            encodedPreviewPoints = ""
        )
        val existingPoints = listOf(
            TrackPointEntity(1L, 1L, 37.7749, -122.4194, 10.0, existingStart, 0L, 3.3f, 0.0, 0),
            TrackPointEntity(2L, 1L, 37.7929, -122.4194, 28.0, existingStart + 600_000L, 600_000L, 3.3f, 2000.0, 0)
        )
        val existingSplits = listOf(
            LapSplitEntity(1L, 1L, 1, 1000.0, 300_000L, 300.0, 9.0),
            LapSplitEntity(2L, 1L, 2, 1000.0, 300_000L, 300.0, 9.0)
        )

        val newStart = 1_700_100_000_000L
        val secondWorkout = existingWorkout.copy(
            id = 2L,
            title = "Evening Walk",
            activityType = ActivityType.WALK.name,
            startTimeMillis = newStart,
            endTimeMillis = newStart + 900_000L,
            elapsedMillis = 900_000L,
            distanceMeters = 1200.0
        )

        val backupJson = WorkoutExporter.toJson(
            listOf(
                WorkoutWithDetails(existingWorkout, existingPoints, existingSplits),
                WorkoutWithDetails(secondWorkout, emptyList(), emptyList())
            )
        )

        // Seed only the first workout into Room
        database.workoutDao().insertCompleteWorkout(
            existingWorkout.copy(id = 0L),
            existingPoints.map { it.copy(id = 0L, workoutId = 0L) },
            existingSplits.map { it.copy(id = 0L, workoutId = 0L) }
        )

        val parsed = WorkoutImporter.parseRawContent(backupJson, "pacely_backup.json", 70f)
        assertEquals(2, parsed.size)

        val summary = repository.importWorkouts(parsed)
        assertEquals(1, summary.importedCount)
        assertEquals(1, summary.skippedDuplicatesCount)
        assertEquals("Imported 1 new workout (1 duplicate skipped)", summary.toUserMessage())

        val stored = repository.allWorkouts.first()
        assertEquals(2, stored.size)
    }

    @Test
    fun gpxImport_parsesTrackPointsElevationAndSkipsSecondRoundedDuplicate() = runTest {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Test">
              <metadata>
                <name>Coastal Trail Run</name>
                <time>2026-11-10T08:00:00Z</time>
              </metadata>
              <trk>
                <name>Coastal Trail Run</name>
                <type>running</type>
                <trkseg>
                  <trkpt lat="37.7749" lon="-122.4194">
                    <ele>10.0</ele>
                    <time>2026-11-10T08:00:00Z</time>
                  </trkpt>
                  <trkpt lat="37.7849" lon="-122.4194">
                    <ele>24.0</ele>
                    <time>2026-11-10T08:05:30Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val parsed = WorkoutImporter.parseRawContent(gpx, "coastal_trail.gpx", 72f)
        assertEquals(1, parsed.size)
        val item = parsed.first()
        assertEquals("Coastal Trail Run", item.workout.title)
        assertEquals(ActivityType.RUN.name, item.workout.activityType)
        assertTrue(item.workout.distanceMeters > 1000.0)
        assertEquals(14.0, item.workout.elevationGainMeters, 0.5)
        assertEquals(2, item.splits.size)

        val firstSummary = repository.importWorkouts(parsed)
        assertEquals(1, firstSummary.importedCount)

        // Re-importing same GPX should be detected as duplicate
        val secondSummary = repository.importWorkouts(parsed)
        assertEquals(0, secondSummary.importedCount)
        assertEquals(1, secondSummary.skippedDuplicatesCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun corruptedFile_throwsClearValidationException() {
        WorkoutImporter.parseRawContent("{ not valid json", "broken.json", 70f)
    }

    @Test
    fun singleWorkoutZipBundle_containsGpxAndJson_andImportsCleanly() = runTest {
        val start = 1_763_450_000_000L
        val workout = WorkoutEntity(
            id = 5L,
            title = "Sunset Tempo",
            activityType = ActivityType.RUN.name,
            startTimeMillis = start,
            endTimeMillis = start + 1_200_000L,
            elapsedMillis = 1_200_000L,
            distanceMeters = 4200.0,
            avgPaceSecPerKm = 285.7,
            caloriesBurned = 310,
            elevationGainMeters = 32.0,
            elevationLossMeters = 28.0,
            encodedPreviewPoints = ""
        )
        val pts = listOf(
            TrackPointEntity(1L, 5L, 37.7749, -122.4194, 15.0, start, 0L, 3.5f, 0.0, 0),
            TrackPointEntity(2L, 5L, 37.7850, -122.4194, 30.0, start + 600_000L, 600_000L, 3.5f, 2100.0, 0),
            TrackPointEntity(3L, 5L, 37.7950, -122.4194, 47.0, start + 1_200_000L, 1_200_000L, 3.5f, 4200.0, 0)
        )
        val splits = listOf(
            LapSplitEntity(1L, 5L, 1, 1000.0, 285_000L, 285.0, 8.0),
            LapSplitEntity(2L, 5L, 2, 1000.0, 286_000L, 286.0, 7.0)
        )
        val details = WorkoutWithDetails(workout, pts, splits)
        val expectedBase = WorkoutExporter.singleWorkoutBaseName(start)

        val baos = ByteArrayOutputStream()
        WorkoutExporter.writeSingleWorkoutZip(details, baos)
        val zipBytes = baos.toByteArray()

        val entryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        assertTrue(entryNames.contains("$expectedBase.gpx"))
        assertTrue(entryNames.contains("$expectedBase.json"))

        // Import ZIP into fresh DB -> 1 imported, 0 duplicate skipped (internal .gpx + .json deduplicated)
        val parsed = WorkoutImporter.parseRawBytes(zipBytes, "$expectedBase.zip", 70f)
        assertEquals(1, parsed.size)
        assertEquals("Sunset Tempo", parsed.first().workout.title)
        assertEquals(2, parsed.first().splits.size)

        val firstSummary = repository.importWorkouts(parsed)
        assertEquals(1, firstSummary.importedCount)
        assertEquals(0, firstSummary.skippedDuplicatesCount)

        // Second import detects existing workout in DB
        val secondSummary = repository.importWorkouts(parsed)
        assertEquals(0, secondSummary.importedCount)
        assertEquals(1, secondSummary.skippedDuplicatesCount)
    }

    @Test
    fun fullBackupZipBundle_containsFullBackupJsonWithSettingsAndPerWorkoutGpxFiles() = runTest {
        val start1 = 1_763_450_000_000L
        val start2 = 1_763_460_000_000L // same date to verify unique GPX naming
        val w1 = WorkoutEntity(
            id = 1L,
            title = "Morning Run",
            activityType = ActivityType.RUN.name,
            startTimeMillis = start1,
            endTimeMillis = start1 + 600_000L,
            elapsedMillis = 600_000L,
            distanceMeters = 2000.0,
            avgPaceSecPerKm = 300.0,
            caloriesBurned = 175,
            elevationGainMeters = 18.0,
            elevationLossMeters = 12.0,
            encodedPreviewPoints = ""
        )
        val w2 = w1.copy(
            id = 2L,
            title = "Afternoon Walk",
            activityType = ActivityType.WALK.name,
            startTimeMillis = start2,
            endTimeMillis = start2 + 900_000L,
            elapsedMillis = 900_000L,
            distanceMeters = 1500.0
        )
        val pts1 = listOf(
            TrackPointEntity(1L, 1L, 37.7749, -122.4194, 10.0, start1, 0L, 3.3f, 0.0, 0),
            TrackPointEntity(2L, 1L, 37.7849, -122.4194, 20.0, start1 + 600_000L, 600_000L, 3.3f, 2000.0, 0)
        )
        val pts2 = listOf(
            TrackPointEntity(3L, 2L, 37.7700, -122.4100, 12.0, start2, 0L, 1.6f, 0.0, 0),
            TrackPointEntity(4L, 2L, 37.7750, -122.4100, 14.0, start2 + 900_000L, 900_000L, 1.6f, 1500.0, 0)
        )
        val settings = BackupSettingsPayload(
            weightKg = 74f,
            autoPauseEnabled = true,
            autoPauseSpeedThresholdMps = 0.6f,
            voiceSplitCuesEnabled = true,
            splitIntervalKm = 1.0f,
            headingSourceMode = "AUTO",
            darkMapModeEnabled = true,
            mapOrientationDegrees = 180f
        )

        val baos = ByteArrayOutputStream()
        WorkoutExporter.writeFullBackupZip(
            workouts = listOf(
                WorkoutWithDetails(w1, pts1, emptyList()),
                WorkoutWithDetails(w2, pts2, emptyList())
            ),
            settings = settings,
            outputStream = baos
        )
        val zipBytes = baos.toByteArray()

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue(entries.containsKey("full_backup.json"))
        val gpxEntries = entries.keys.filter { it.endsWith(".gpx") }
        assertEquals(2, gpxEntries.size)
        val backupJsonText = entries.getValue("full_backup.json")
        assertTrue(backupJsonText.contains("\"settings\""))
        assertTrue(backupJsonText.contains("\"stats\""))
        assertTrue(backupJsonText.contains("\"mapOrientationDegrees\": 180"))

        val parsed = WorkoutImporter.parseRawBytes(zipBytes, "pacely_full_backup.zip", 70f)
        assertEquals(2, parsed.size)
        val summary = repository.importWorkouts(parsed)
        assertEquals(2, summary.importedCount)
        assertEquals(0, summary.skippedDuplicatesCount)
    }
}
