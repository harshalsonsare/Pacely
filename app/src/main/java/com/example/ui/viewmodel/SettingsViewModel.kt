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
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val weightKg: Float = 70f,
    val autoPauseEnabled: Boolean = true,
    val autoPauseSpeedThresholdMps: Float = 0.5f,
    val voiceSplitCuesEnabled: Boolean = true,
    val splitIntervalKm: Float = 1.0f,
    val darkMapModeEnabled: Boolean = true,
    val totalWorkoutsCount: Int = 0,
    val isTransferring: Boolean = false
)

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val transferringFlow = MutableStateFlow(false)

    private val automationPrefsFlow = combine(
        preferencesRepository.autoPauseEnabledFlow,
        preferencesRepository.autoPauseSpeedThresholdMpsFlow,
        preferencesRepository.voiceSplitCuesEnabledFlow,
        preferencesRepository.splitIntervalKmFlow,
        preferencesRepository.darkMapModeEnabledFlow
    ) { autoPause, threshold, voiceCues, splitKm, darkMap ->
        AutomationPrefs(autoPause, threshold, voiceCues, splitKm, darkMap)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.weightKgFlow,
        automationPrefsFlow,
        workoutRepository.allWorkouts,
        transferringFlow
    ) { weight, autoPrefs, workouts, isTransferring ->
        SettingsUiState(
            weightKg = weight,
            autoPauseEnabled = autoPrefs.autoPauseEnabled,
            autoPauseSpeedThresholdMps = autoPrefs.autoPauseSpeedThresholdMps,
            voiceSplitCuesEnabled = autoPrefs.voiceSplitCuesEnabled,
            splitIntervalKm = autoPrefs.splitIntervalKm,
            darkMapModeEnabled = autoPrefs.darkMapModeEnabled,
            totalWorkoutsCount = workouts.size,
            isTransferring = isTransferring
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private data class AutomationPrefs(
        val autoPauseEnabled: Boolean,
        val autoPauseSpeedThresholdMps: Float,
        val voiceSplitCuesEnabled: Boolean,
        val splitIntervalKm: Float,
        val darkMapModeEnabled: Boolean
    )

    fun updateWeightKg(weightKg: Float) {
        viewModelScope.launch {
            preferencesRepository.setWeightKg(weightKg)
        }
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoPauseEnabled(enabled)
        }
    }

    fun setAutoPauseSpeedThresholdMps(speedMps: Float) {
        viewModelScope.launch {
            preferencesRepository.setAutoPauseSpeedThresholdMps(speedMps)
        }
    }

    fun setVoiceSplitCuesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setVoiceSplitCuesEnabled(enabled)
        }
    }

    fun setSplitIntervalKm(intervalKm: Float) {
        viewModelScope.launch {
            preferencesRepository.setSplitIntervalKm(intervalKm)
        }
    }

    fun setDarkMapModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDarkMapModeEnabled(enabled)
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
                    val all = workoutRepository.getAllWorkoutsWithDetailsOnce()
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
            val weightKg = uiState.value.weightKg
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val parsed = WorkoutImporter.parseFromUri(
                        contentResolver = context.contentResolver,
                        uri = uri,
                        defaultWeightKg = weightKg
                    )
                    workoutRepository.importWorkouts(parsed)
                }
            }
            transferringFlow.value = false
            onComplete(result)
        }
    }

    fun clearAllWorkouts(onCleared: () -> Unit) {
        viewModelScope.launch {
            workoutRepository.deleteAllWorkouts()
            onCleared()
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = PacelyAppContainer.get(context)
            return SettingsViewModel(
                container.preferencesRepository,
                container.workoutRepository
            ) as T
        }
    }
}
