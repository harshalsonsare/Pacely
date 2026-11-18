package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val activityType: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val elapsedMillis: Long,
    val distanceMeters: Double,
    val avgPaceSecPerKm: Double,
    val caloriesBurned: Int,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val encodedPreviewPoints: String,
    val shoeId: Long? = null
)

@Entity(tableName = "shoes")
data class ShoeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val brand: String,
    val name: String,
    val initialMileageKm: Double = 0.0,
    val targetMaxKm: Double = 800.0,
    val isDefault: Boolean = false,
    val isRetired: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId")]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workoutId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val timestampMillis: Long,
    val elapsedMillis: Long,
    val speedMps: Float,
    val distanceFromStartMeters: Double,
    val segmentIndex: Int
)

@Entity(
    tableName = "lap_splits",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId")]
)
data class LapSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workoutId: Long,
    val lapNumber: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val paceSecPerKm: Double,
    val elevationDeltaMeters: Double
)

data class WorkoutWithRelations(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutId"
    )
    val trackPoints: List<TrackPointEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutId"
    )
    val splits: List<LapSplitEntity>
)
