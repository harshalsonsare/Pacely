package com.example.tracking

import com.example.data.local.TrackPointEntity
import com.example.data.model.PersonalRecord
import com.example.data.model.PersonalRecordsSummary
import com.example.data.model.WorkoutWithDetails

object PersonalRecordsCalculator {

    fun compute(workouts: List<WorkoutWithDetails>): PersonalRecordsSummary {
        if (workouts.isEmpty()) return PersonalRecordsSummary()

        val fastest1K = findFastestDistance(workouts, 1000.0, "Fastest 1K")
        val fastest5K = findFastestDistance(workouts, 5000.0, "Fastest 5K")
        val fastest10K = findFastestDistance(workouts, 10000.0, "Fastest 10K")

        val longest = workouts
            .filter { it.workout.distanceMeters >= 50.0 }
            .maxByOrNull { it.workout.distanceMeters }
            ?.let { item ->
                val w = item.workout
                PersonalRecord(
                    categoryLabel = "Longest Distance",
                    workoutId = w.id,
                    workoutTitle = w.title,
                    dateMillis = w.startTimeMillis,
                    durationMillis = w.elapsedMillis,
                    distanceMeters = w.distanceMeters,
                    paceSecPerKm = w.avgPaceSecPerKm
                )
            }

        return PersonalRecordsSummary(
            fastest1K = fastest1K,
            fastest5K = fastest5K,
            fastest10K = fastest10K,
            longestDistance = longest
        )
    }

    private fun findFastestDistance(
        workouts: List<WorkoutWithDetails>,
        targetMeters: Double,
        label: String
    ): PersonalRecord? {
        var best: PersonalRecord? = null

        workouts.forEach { details ->
            val w = details.workout
            if (w.distanceMeters < targetMeters || w.elapsedMillis <= 0L) return@forEach

            val bestSegmentMillis = findFastestWindowMillis(details.trackPoints, targetMeters)
                ?: ((w.elapsedMillis * (targetMeters / w.distanceMeters)).toLong()).coerceAtLeast(1000L)

            val paceSecPerKm = (bestSegmentMillis / 1000.0) / (targetMeters / 1000.0)
            if (best == null || bestSegmentMillis < best!!.durationMillis) {
                best = PersonalRecord(
                    categoryLabel = label,
                    workoutId = w.id,
                    workoutTitle = w.title,
                    dateMillis = w.startTimeMillis,
                    durationMillis = bestSegmentMillis,
                    distanceMeters = targetMeters,
                    paceSecPerKm = paceSecPerKm
                )
            }
        }
        return best
    }

    private fun findFastestWindowMillis(
        points: List<TrackPointEntity>,
        targetMeters: Double
    ): Long? {
        if (points.size < 2 || points.last().distanceFromStartMeters < targetMeters) return null

        var bestDuration = Long.MAX_VALUE
        var left = 0

        for (right in 1 until points.size) {
            val endPt = points[right]
            while (left < right && (endPt.distanceFromStartMeters - points[left].distanceFromStartMeters) >= targetMeters) {
                val startPt = points[left]
                val distSpan = endPt.distanceFromStartMeters - startPt.distanceFromStartMeters
                val timeSpan = (endPt.elapsedMillis - startPt.elapsedMillis).coerceAtLeast(0L)
                if (distSpan > 0.0 && timeSpan > 0L) {
                    val interpolatedMillis = (timeSpan * (targetMeters / distSpan)).toLong().coerceAtLeast(1000L)
                    if (interpolatedMillis < bestDuration) {
                        bestDuration = interpolatedMillis
                    }
                }
                left++
            }
        }

        return if (bestDuration == Long.MAX_VALUE) null else bestDuration
    }
}
