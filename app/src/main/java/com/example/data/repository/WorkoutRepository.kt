package com.example.data.repository

import com.example.data.export.ImportSummary
import com.example.data.export.ImportedWorkoutData
import com.example.data.local.LapSplitEntity
import com.example.data.local.ShoeEntity
import com.example.data.local.TrackPointEntity
import com.example.data.local.WorkoutDao
import com.example.data.local.WorkoutEntity
import com.example.data.local.WorkoutWithRelations
import com.example.data.model.ActivityType
import com.example.data.model.GeoPoint
import com.example.data.model.LapSplit
import com.example.data.model.PersonalRecordsSummary
import com.example.data.model.ShoeMileageStats
import com.example.data.model.WeeklySummary
import com.example.data.model.WorkoutWithDetails
import com.example.tracking.GeoMath
import com.example.tracking.PersonalRecordsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

class WorkoutRepository(private val workoutDao: WorkoutDao) {

    val allWorkouts: Flow<List<WorkoutEntity>> = workoutDao.getAllWorkouts()
    val allShoes: Flow<List<ShoeEntity>> = workoutDao.getAllShoes()

    fun observeWeeklySummary(): Flow<WeeklySummary> {
        val weekStart = startOfCurrentWeekMillis()
        return workoutDao.getWorkoutsSince(weekStart).map { workouts ->
            var distance = 0.0
            var duration = 0L
            var runs = 0
            var walks = 0
            var calories = 0
            var elevation = 0.0
            workouts.forEach { w ->
                distance += w.distanceMeters
                duration += w.elapsedMillis
                calories += w.caloriesBurned
                elevation += w.elevationGainMeters
                if (ActivityType.fromString(w.activityType) == ActivityType.RUN) {
                    runs++
                } else {
                    walks++
                }
            }
            WeeklySummary(
                totalDistanceMeters = distance,
                totalDurationMillis = duration,
                workoutCount = workouts.size,
                runCount = runs,
                walkCount = walks,
                totalCalories = calories,
                totalElevationGainMeters = elevation
            )
        }
    }

    fun observePersonalRecords(): Flow<PersonalRecordsSummary> {
        return workoutDao.observeAllWorkoutsWithDetails().map { relations ->
            PersonalRecordsCalculator.compute(relations.map { it.toDomain() })
        }
    }

    fun observeShoeMileageStats(): Flow<List<ShoeMileageStats>> {
        return combine(workoutDao.getAllShoes(), workoutDao.getAllWorkouts()) { shoes, workouts ->
            val grouped = workouts.filter { it.shoeId != null }.groupBy { it.shoeId!! }
            shoes.map { shoe ->
                val linked = grouped[shoe.id].orEmpty()
                val loggedKm = linked.sumOf { it.distanceMeters } / 1000.0
                ShoeMileageStats(
                    shoe = shoe,
                    loggedDistanceKm = loggedKm,
                    totalDistanceKm = shoe.initialMileageKm + loggedKm,
                    workoutCount = linked.size
                )
            }
        }
    }

    fun observeWorkoutWithDetails(workoutId: Long): Flow<WorkoutWithDetails?> {
        return workoutDao.observeWorkoutWithDetails(workoutId).map { relation ->
            relation?.toDomain()
        }
    }

    suspend fun getAllWorkoutsWithDetailsOnce(): List<WorkoutWithDetails> {
        return workoutDao.getAllWorkoutsWithDetailsOnce().map { it.toDomain() }
    }

    suspend fun getWorkoutWithDetailsOnce(workoutId: Long): WorkoutWithDetails? {
        return workoutDao.getWorkoutWithDetailsOnce(workoutId)?.toDomain()
    }

    suspend fun saveCompletedWorkout(
        activityType: ActivityType,
        startTimeMillis: Long,
        endTimeMillis: Long,
        elapsedMillis: Long,
        distanceMeters: Double,
        caloriesBurned: Double,
        elevationGainMeters: Double,
        elevationLossMeters: Double,
        points: List<GeoPoint>,
        splits: List<LapSplit>,
        shoeId: Long? = null
    ): Long {
        val avgPaceSecPerKm = if (distanceMeters >= 5.0 && elapsedMillis > 0L) {
            (elapsedMillis / 1000.0) / (distanceMeters / 1000.0)
        } else {
            0.0
        }

        val workoutEntity = WorkoutEntity(
            title = buildDefaultTitle(activityType, startTimeMillis),
            activityType = activityType.name,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            elapsedMillis = elapsedMillis,
            distanceMeters = distanceMeters,
            avgPaceSecPerKm = avgPaceSecPerKm,
            caloriesBurned = caloriesBurned.roundToInt(),
            elevationGainMeters = elevationGainMeters,
            elevationLossMeters = elevationLossMeters,
            encodedPreviewPoints = GeoMath.encodePreviewPoints(points),
            shoeId = shoeId
        )

        val pointEntities = points.map { pt ->
            TrackPointEntity(
                workoutId = 0L,
                latitude = pt.latitude,
                longitude = pt.longitude,
                altitudeMeters = pt.altitudeMeters,
                timestampMillis = pt.timestampMillis,
                elapsedMillis = pt.elapsedMillis,
                speedMps = pt.speedMps,
                distanceFromStartMeters = pt.distanceFromStartMeters,
                segmentIndex = pt.segmentIndex
            )
        }

        val splitEntities = splits.map { sp ->
            LapSplitEntity(
                workoutId = 0L,
                lapNumber = sp.lapNumber,
                distanceMeters = sp.distanceMeters,
                durationMillis = sp.durationMillis,
                paceSecPerKm = sp.paceSecPerKm,
                elevationDeltaMeters = sp.elevationDeltaMeters
            )
        }

        return workoutDao.insertCompleteWorkout(workoutEntity, pointEntities, splitEntities)
    }

    suspend fun importWorkouts(importedList: List<ImportedWorkoutData>): ImportSummary {
        val existingTimestamps = workoutDao.getAllStartTimestamps().toMutableList()
        var importedCount = 0
        var skippedDuplicates = 0

        importedList.forEach { item ->
            val candidateStart = item.workout.startTimeMillis
            val isDuplicate = existingTimestamps.any { existing ->
                abs(existing - candidateStart) < 1000L
            }
            if (isDuplicate) {
                skippedDuplicates++
            } else {
                workoutDao.insertCompleteWorkout(
                    workout = item.workout.copy(id = 0L),
                    points = item.trackPoints.map { it.copy(id = 0L, workoutId = 0L) },
                    splits = item.splits.map { it.copy(id = 0L, workoutId = 0L) }
                )
                existingTimestamps.add(candidateStart)
                importedCount++
            }
        }

        return ImportSummary(
            importedCount = importedCount,
            skippedDuplicatesCount = skippedDuplicates
        )
    }

    suspend fun addShoe(
        brand: String,
        name: String,
        initialMileageKm: Double,
        targetMaxKm: Double,
        setAsDefault: Boolean
    ): Long {
        if (setAsDefault) {
            workoutDao.clearDefaultShoes()
        }
        return workoutDao.insertShoe(
            ShoeEntity(
                brand = brand.trim(),
                name = name.trim(),
                initialMileageKm = initialMileageKm.coerceAtLeast(0.0),
                targetMaxKm = targetMaxKm.coerceIn(100.0, 5000.0),
                isDefault = setAsDefault,
                isRetired = false
            )
        )
    }

    suspend fun setDefaultShoe(shoeId: Long) {
        workoutDao.setDefaultShoe(shoeId)
    }

    suspend fun toggleShoeRetired(shoe: ShoeEntity) {
        workoutDao.updateShoe(
            shoe.copy(
                isRetired = !shoe.isRetired,
                isDefault = if (!shoe.isRetired) false else shoe.isDefault
            )
        )
    }

    suspend fun deleteShoe(shoeId: Long) {
        workoutDao.deleteShoeAndUnlink(shoeId)
    }

    suspend fun assignShoeToWorkout(workoutId: Long, shoeId: Long?) {
        workoutDao.assignShoeToWorkout(workoutId, shoeId)
    }

    suspend fun deleteWorkout(workoutId: Long) {
        workoutDao.deleteWorkoutById(workoutId)
    }

    suspend fun deleteAllWorkouts() {
        workoutDao.deleteAllWorkouts()
    }

    private fun WorkoutWithRelations.toDomain(): WorkoutWithDetails = WorkoutWithDetails(
        workout = workout,
        trackPoints = trackPoints.sortedBy { it.timestampMillis },
        splits = splits.sortedBy { it.lapNumber }
    )

    private fun startOfCurrentWeekMillis(): Long {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis > System.currentTimeMillis()) {
            cal.add(Calendar.WEEK_OF_YEAR, -1)
        }
        return cal.timeInMillis
    }

    private fun buildDefaultTitle(activityType: ActivityType, timestampMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val period = when (hour) {
            in 4..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
        return "$period ${activityType.label}"
    }
}
