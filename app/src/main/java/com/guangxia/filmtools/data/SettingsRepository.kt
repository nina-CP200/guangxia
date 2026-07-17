package com.guangxia.filmtools.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guangxia.filmtools.core.DistanceUnit
import com.guangxia.filmtools.core.MeteringMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

data class AppSettings(
    val evCalibration: Double = 0.0,
    val meteringMode: MeteringMode = MeteringMode.AVERAGE,
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val calibration = doublePreferencesKey("ev_calibration")
        val meteringMode = stringPreferencesKey("metering_mode")
        val distanceUnit = stringPreferencesKey("distance_unit")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            evCalibration = prefs[Keys.calibration] ?: 0.0,
            meteringMode = prefs[Keys.meteringMode]?.let { runCatching { MeteringMode.valueOf(it) }.getOrNull() } ?: MeteringMode.AVERAGE,
            distanceUnit = prefs[Keys.distanceUnit]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: DistanceUnit.METERS,
        )
    }

    suspend fun setCalibration(value: Double) = context.settingsDataStore.edit { it[Keys.calibration] = value.coerceIn(-3.0, 3.0) }
    suspend fun setMeteringMode(value: MeteringMode) = context.settingsDataStore.edit { it[Keys.meteringMode] = value.name }
    suspend fun setDistanceUnit(value: DistanceUnit) = context.settingsDataStore.edit { it[Keys.distanceUnit] = value.name }
}
