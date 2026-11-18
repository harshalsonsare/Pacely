package com.example

import android.content.Context
import com.example.data.local.PacelyDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.WorkoutRepository
import com.example.map.OsmMapManager
import com.example.tracking.CompassHeadingManager
import com.example.tracking.SplitFeedbackEngine
import com.example.tracking.WorkoutTrackingManager

class PacelyAppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext
    init {
        OsmMapManager.ensureInitialized(appContext)
    }
    private val database = PacelyDatabase.getInstance(appContext)
    val workoutRepository = WorkoutRepository(database.workoutDao())
    val preferencesRepository = UserPreferencesRepository(appContext)
    val splitFeedbackEngine = SplitFeedbackEngine(appContext)
    val trackingManager = WorkoutTrackingManager(
        workoutRepository = workoutRepository,
        preferencesRepository = preferencesRepository,
        splitFeedbackListener = splitFeedbackEngine
    )
    val compassHeadingManager = CompassHeadingManager(appContext) { headingDeg, needsCalibration ->
        trackingManager.updateCompassHeading(headingDeg, needsCalibration)
    }.also { compass ->
        trackingManager.locationUpdateHook = { lat, lon, alt ->
            compass.updateLocation(lat, lon, alt)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PacelyAppContainer? = null

        fun get(context: Context): PacelyAppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PacelyAppContainer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
