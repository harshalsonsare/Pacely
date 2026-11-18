package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.HeadingSourceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pacely_prefs")

class UserPreferencesRepository(private val context: Context) {
    private val weightKgKey = floatPreferencesKey("user_weight_kg")
    private val autoPauseKey = booleanPreferencesKey("auto_pause_enabled")
    private val autoPauseSpeedThresholdKey = floatPreferencesKey("auto_pause_speed_threshold_mps")
    private val voiceSplitCuesKey = booleanPreferencesKey("voice_split_cues_enabled")
    private val splitIntervalKmKey = floatPreferencesKey("split_interval_km")
    private val headingSourceModeKey = stringPreferencesKey("heading_source_mode")
    private val darkMapModeEnabledKey = booleanPreferencesKey("dark_map_mode_enabled")

    val weightKgFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        (prefs[weightKgKey] ?: 70f).coerceIn(35f, 200f)
    }

    val autoPauseEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[autoPauseKey] ?: true
    }

    val autoPauseSpeedThresholdMpsFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        (prefs[autoPauseSpeedThresholdKey] ?: 0.5f).coerceIn(0.2f, 2.0f)
    }

    val voiceSplitCuesEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[voiceSplitCuesKey] ?: true
    }

    val splitIntervalKmFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        (prefs[splitIntervalKmKey] ?: 1.0f).coerceIn(0.1f, 50.0f)
    }

    val headingSourceModeFlow: Flow<HeadingSourceMode> = context.dataStore.data.map { prefs ->
        HeadingSourceMode.fromString(prefs[headingSourceModeKey])
    }

    val darkMapModeEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[darkMapModeEnabledKey] ?: true
    }

    suspend fun setWeightKg(weightKg: Float) {
        context.dataStore.edit { prefs ->
            prefs[weightKgKey] = weightKg.coerceIn(35f, 200f)
        }
    }

    suspend fun setAutoPauseEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[autoPauseKey] = enabled
        }
    }

    suspend fun setAutoPauseSpeedThresholdMps(speedMps: Float) {
        context.dataStore.edit { prefs ->
            prefs[autoPauseSpeedThresholdKey] = speedMps.coerceIn(0.2f, 2.0f)
        }
    }

    suspend fun setVoiceSplitCuesEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[voiceSplitCuesKey] = enabled
        }
    }

    suspend fun setSplitIntervalKm(intervalKm: Float) {
        context.dataStore.edit { prefs ->
            prefs[splitIntervalKmKey] = intervalKm.coerceIn(0.1f, 50.0f)
        }
    }

    suspend fun setHeadingSourceMode(mode: HeadingSourceMode) {
        context.dataStore.edit { prefs ->
            prefs[headingSourceModeKey] = mode.name
        }
    }

    suspend fun setDarkMapModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[darkMapModeEnabledKey] = enabled
        }
    }
}
