package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY startTimeMillis DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY startTimeMillis DESC")
    fun observeAllWorkoutsWithDetails(): Flow<List<WorkoutWithRelations>>

    @Query("SELECT * FROM workouts WHERE startTimeMillis >= :sinceMillis ORDER BY startTimeMillis DESC")
    fun getWorkoutsSince(sinceMillis: Long): Flow<List<WorkoutEntity>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    fun observeWorkoutWithDetails(workoutId: Long): Flow<WorkoutWithRelations?>

    @Query("SELECT startTimeMillis FROM workouts")
    suspend fun getAllStartTimestamps(): List<Long>

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY startTimeMillis DESC")
    suspend fun getAllWorkoutsWithDetailsOnce(): List<WorkoutWithRelations>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutWithDetailsOnce(workoutId: Long): WorkoutWithRelations?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLapSplits(splits: List<LapSplitEntity>)

    @Transaction
    suspend fun insertCompleteWorkout(
        workout: WorkoutEntity,
        points: List<TrackPointEntity>,
        splits: List<LapSplitEntity>
    ): Long {
        val workoutId = insertWorkout(workout)
        if (points.isNotEmpty()) {
            insertTrackPoints(points.map { it.copy(workoutId = workoutId) })
        }
        if (splits.isNotEmpty()) {
            insertLapSplits(splits.map { it.copy(workoutId = workoutId) })
        }
        return workoutId
    }

    @Query("UPDATE workouts SET shoeId = :shoeId WHERE id = :workoutId")
    suspend fun assignShoeToWorkout(workoutId: Long, shoeId: Long?)

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    suspend fun deleteWorkoutById(workoutId: Long)

    @Query("DELETE FROM workouts")
    suspend fun deleteAllWorkouts()

    // Shoe Mileage Tracker queries
    @Query("SELECT * FROM shoes ORDER BY isRetired ASC, isDefault DESC, createdAtMillis DESC")
    fun getAllShoes(): Flow<List<ShoeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoe(shoe: ShoeEntity): Long

    @Update
    suspend fun updateShoe(shoe: ShoeEntity)

    @Query("UPDATE shoes SET isDefault = 0")
    suspend fun clearDefaultShoes()

    @Query("UPDATE shoes SET isDefault = 1 WHERE id = :shoeId")
    suspend fun markShoeAsDefault(shoeId: Long)

    @Transaction
    suspend fun setDefaultShoe(shoeId: Long) {
        clearDefaultShoes()
        markShoeAsDefault(shoeId)
    }

    @Query("UPDATE workouts SET shoeId = NULL WHERE shoeId = :shoeId")
    suspend fun clearShoeFromWorkouts(shoeId: Long)

    @Transaction
    suspend fun deleteShoeAndUnlink(shoeId: Long) {
        clearShoeFromWorkouts(shoeId)
        deleteShoeById(shoeId)
    }

    @Query("DELETE FROM shoes WHERE id = :shoeId")
    suspend fun deleteShoeById(shoeId: Long)
}
