package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.PacelyAppContainer
import com.example.data.local.ShoeEntity
import com.example.data.model.ActivityType
import com.example.data.model.TrackingStatus
import com.example.data.model.TrackingUiState
import com.example.data.repository.WorkoutRepository
import com.example.service.LocationTrackingService
import com.example.tracking.CompassHeadingManager
import com.example.tracking.WorkoutTrackingManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackingViewModel(
    private val trackingManager: WorkoutTrackingManager,
    private val compassHeadingManager: CompassHeadingManager,
    workoutRepository: WorkoutRepository
) : ViewModel() {

    val uiState: StateFlow<TrackingUiState> = trackingManager.uiState

    val shoes: StateFlow<List<ShoeEntity>> = workoutRepository.allShoes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startCompass() {
        compassHeadingManager.start()
    }

    fun stopCompass() {
        compassHeadingManager.stop()
    }

    fun toggleHeadingSourceMode() {
        trackingManager.toggleHeadingSourceMode()
    }

    fun selectActivityType(type: ActivityType) {
        trackingManager.setActivityType(type)
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        trackingManager.setAutoPauseEnabled(enabled)
    }

    fun selectShoe(shoeId: Long?) {
        trackingManager.selectShoe(shoeId)
    }

    fun warmUpGps(context: Context) {
        LocationTrackingService.startService(context, LocationTrackingService.ACTION_WARM_GPS)
    }

    fun startWorkout(context: Context) {
        trackingManager.startTracking()
        LocationTrackingService.startService(context, LocationTrackingService.ACTION_START)
    }

    fun togglePauseResume(context: Context) {
        when (uiState.value.status) {
            TrackingStatus.TRACKING -> {
                trackingManager.pauseTracking(isAuto = false)
                LocationTrackingService.startService(context, LocationTrackingService.ACTION_PAUSE)
            }
            TrackingStatus.PAUSED -> {
                trackingManager.resumeTracking()
                LocationTrackingService.startService(context, LocationTrackingService.ACTION_RESUME)
            }
            TrackingStatus.IDLE -> Unit
        }
    }

    fun stopAndSaveWorkout(context: Context, onSaved: (Long?) -> Unit) {
        viewModelScope.launch {
            LocationTrackingService.startService(context, LocationTrackingService.ACTION_STOP)
            val savedId = trackingManager.finishAndSaveWorkout()
            onSaved(savedId)
        }
    }

    fun discardWorkout(context: Context) {
        LocationTrackingService.startService(context, LocationTrackingService.ACTION_STOP)
        trackingManager.discardWorkout()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = PacelyAppContainer.get(context)
            return TrackingViewModel(
                container.trackingManager,
                container.compassHeadingManager,
                container.workoutRepository
            ) as T
        }
    }
}
