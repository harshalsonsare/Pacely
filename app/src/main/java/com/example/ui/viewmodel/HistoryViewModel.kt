package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.PacelyAppContainer
import com.example.data.export.ImportSummary
import com.example.data.export.WorkoutExporter
import com.example.data.export.WorkoutImporter
import com.example.data.local.WorkoutEntity
import com.example.data.model.ActivityType
import com.example.data.model.WeeklySummary
import com.example.data.model.WorkoutWithDetails
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class HistoryFilter(val label: String) {
    ALL("All"),
    RUNS("Runs"),
    WALKS("Walks")
}

data class HistoryUiState(
    val workouts: List<WorkoutEntity> = emptyList(),
    val totalWorkoutCount: Int = 0,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val weeklySummary: WeeklySummary = WeeklySummary(),
    val isTransferring: Boolean = false
)

class HistoryViewModel(
    private val repository: WorkoutRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(HistoryFilter.ALL)
    private val transferringFlow = MutableStateFlow(false)

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.allWorkouts,
        filterFlow,
        repository.observeWeeklySummary(),
        transferringFlow
    ) { workouts, filter, summary, isTransferring ->
        val filtered = when (filter) {
            HistoryFilter.ALL -> workouts
            HistoryFilter.RUNS -> workouts.filter { ActivityType.fromString(it.activityType) == ActivityType.RUN }
            HistoryFilter.WALKS -> workouts.filter { ActivityType.fromString(it.activityType) == ActivityType.WALK }
        }
        HistoryUiState(
            workouts = filtered,
            totalWorkoutCount = workouts.size,
            filter = filter,
            weeklySummary = summary,
            isTransferring = isTransferring
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    val shoes: StateFlow<List<com.example.data.local.ShoeEntity>> = repository.allShoes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setFilter(filter: HistoryFilter) {
        filterFlow.value = filter
    }

    fun observeWorkoutDetail(workoutId: Long): Flow<WorkoutWithDetails?> =
        repository.observeWorkoutWithDetails(workoutId)

    fun assignShoeToWorkout(workoutId: Long, shoeId: Long?) {
        viewModelScope.launch {
            repository.assignShoeToWorkout(workoutId, shoeId)
        }
    }

    fun deleteWorkout(workoutId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteWorkout(workoutId)
            onDeleted()
        }
    }

    fun exportAllToUri(
        context: Context,
        uri: Uri,
        isJson: Boolean,
        onComplete: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            transferringFlow.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val all = repository.getAllWorkoutsWithDetailsOnce()
                    val content = if (isJson) {
                        WorkoutExporter.toJson(all)
                    } else {
                        WorkoutExporter.toCsv(all.map { it.workout })
                    }
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open output stream")
                    all.size
                }
            }
            transferringFlow.value = false
            onComplete(result)
        }
    }

    fun importFromUri(
        context: Context,
        uri: Uri,
        onComplete: (Result<ImportSummary>) -> Unit
    ) {
        viewModelScope.launch {
            transferringFlow.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val weightKg = preferencesRepository.weightKgFlow.first()
                    val parsed = WorkoutImporter.parseFromUri(
                        contentResolver = context.contentResolver,
                        uri = uri,
                        defaultWeightKg = weightKg
                    )
                    repository.importWorkouts(parsed)
                }
            }
            transferringFlow.value = false
            onComplete(result)
        }
    }

    fun exportWorkoutGpxToUri(
        context: Context,
        workoutId: Long,
        uri: Uri,
        onComplete: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            transferringFlow.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val details = repository.getWorkoutWithDetailsOnce(workoutId)
                        ?: error("Workout not found")
                    val gpx = WorkoutExporter.toGpx(details)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(gpx.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open output stream")
                    Unit
                }
            }
            transferringFlow.value = false
            onComplete(result)
        }
    }

    fun prepareShareFile(
        context: Context,
        workoutId: Long,
        onReady: (Result<File>) -> Unit
    ) {
        viewModelScope.launch {
            transferringFlow.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val details = repository.getWorkoutWithDetailsOnce(workoutId)
                        ?: error("Workout not found")
                    val gpx = WorkoutExporter.toGpx(details)
                    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(exportDir, "pacely_workout_${workoutId}.gpx")
                    file.writeText(gpx, Charsets.UTF_8)
                    file
                }
            }
            transferringFlow.value = false
            onReady(result)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = PacelyAppContainer.get(context)
            return HistoryViewModel(
                container.workoutRepository,
                container.preferencesRepository
            ) as T
        }
    }
}
