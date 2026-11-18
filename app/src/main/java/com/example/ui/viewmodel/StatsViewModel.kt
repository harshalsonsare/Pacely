package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.PacelyAppContainer
import com.example.data.local.ShoeEntity
import com.example.data.model.PersonalRecordsSummary
import com.example.data.model.ShoeMileageStats
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StatsUiState(
    val personalRecords: PersonalRecordsSummary = PersonalRecordsSummary(),
    val shoes: List<ShoeMileageStats> = emptyList(),
    val totalWorkoutsCount: Int = 0,
    val totalLifetimeKm: Double = 0.0
)

class StatsViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        repository.observePersonalRecords(),
        repository.observeShoeMileageStats(),
        repository.allWorkouts
    ) { prs, shoes, workouts ->
        StatsUiState(
            personalRecords = prs,
            shoes = shoes,
            totalWorkoutsCount = workouts.size,
            totalLifetimeKm = workouts.sumOf { it.distanceMeters } / 1000.0
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState()
    )

    fun addShoe(
        brand: String,
        name: String,
        initialMileageKm: Double,
        targetMaxKm: Double,
        setAsDefault: Boolean
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addShoe(
                brand = brand.ifBlank { "Running" },
                name = name,
                initialMileageKm = initialMileageKm,
                targetMaxKm = targetMaxKm,
                setAsDefault = setAsDefault
            )
        }
    }

    fun setDefaultShoe(shoeId: Long) {
        viewModelScope.launch {
            repository.setDefaultShoe(shoeId)
        }
    }

    fun toggleShoeRetired(shoe: ShoeEntity) {
        viewModelScope.launch {
            repository.toggleShoeRetired(shoe)
        }
    }

    fun deleteShoe(shoeId: Long) {
        viewModelScope.launch {
            repository.deleteShoe(shoeId)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = PacelyAppContainer.get(context)
            return StatsViewModel(container.workoutRepository) as T
        }
    }
}
