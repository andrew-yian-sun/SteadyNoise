package com.steady.noise

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsDataStore by preferencesDataStore(name = "settings")

object UserPrefs {
    val noiseType = stringPreferencesKey("noise_type")
    val durationMinutes = intPreferencesKey("duration_minutes")
    val stopMode = stringPreferencesKey("stop_mode")
    val untilHour = intPreferencesKey("until_hour")
    val untilMinute = intPreferencesKey("until_minute")
}
