package com.zerotap.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zerotap_preferences")

class UserPreferences(private val context: Context) {

    private object PreferencesKeys {
        val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        val MOTION_SAMPLING_RATE_MS = longPreferencesKey("motion_sampling_rate_ms")
        val LOCATION_INTERVAL_MS = longPreferencesKey("location_interval_ms")
        val AUDIO_SAMPLING_INTERVAL_MS = longPreferencesKey("audio_sampling_interval_ms")
        val DEBUG_MODE_ENABLED = booleanPreferencesKey("debug_mode_enabled")
        val ALERT_ENDPOINT_URL = stringPreferencesKey("alert_endpoint_url")
        val DEMO_MODE_ENABLED = booleanPreferencesKey("demo_mode_enabled")
        val DEMO_EMERGENCY_CALL_ENABLED = booleanPreferencesKey("demo_emergency_call_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode") // "SYSTEM", "LIGHT", "DARK"
        val HOME_LATITUDE = stringPreferencesKey("home_latitude")
        val HOME_LONGITUDE = stringPreferencesKey("home_longitude")
        val HOME_LABEL = stringPreferencesKey("home_label")
    }

    val protectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PROTECTION_ENABLED] ?: false
    }

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROTECTION_ENABLED] = enabled
        }
    }

    val motionSamplingRateMs: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.MOTION_SAMPLING_RATE_MS] ?: 100L
    }

    suspend fun setMotionSamplingRateMs(rate: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MOTION_SAMPLING_RATE_MS] = rate
        }
    }

    val locationIntervalMs: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LOCATION_INTERVAL_MS] ?: 5000L
    }

    suspend fun setLocationIntervalMs(interval: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCATION_INTERVAL_MS] = interval
        }
    }

    val audioSamplingIntervalMs: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUDIO_SAMPLING_INTERVAL_MS] ?: 500L
    }

    suspend fun setAudioSamplingIntervalMs(interval: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUDIO_SAMPLING_INTERVAL_MS] = interval
        }
    }

    val debugModeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEBUG_MODE_ENABLED] ?: false
    }

    suspend fun setDebugModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEBUG_MODE_ENABLED] = enabled
        }
    }

    val alertEndpointUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ALERT_ENDPOINT_URL] ?: ""
    }

    suspend fun setAlertEndpointUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALERT_ENDPOINT_URL] = url
        }
    }

    val demoModeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEMO_MODE_ENABLED] ?: true
    }

    suspend fun setDemoModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEMO_MODE_ENABLED] = enabled
        }
    }

    // Default to false for critical safety - 112 call is simulated only by default
    val demoEmergencyCallEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEMO_EMERGENCY_CALL_ENABLED] ?: false
    }

    suspend fun setDemoEmergencyCallEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEMO_EMERGENCY_CALL_ENABLED] = enabled
        }
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.THEME_MODE] ?: "SYSTEM"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    val homeCoordinates: Flow<Pair<Double, Double>?> = context.dataStore.data.map { preferences ->
        val latStr = preferences[PreferencesKeys.HOME_LATITUDE]
        val lngStr = preferences[PreferencesKeys.HOME_LONGITUDE]
        if (latStr != null && lngStr != null) {
            val lat = latStr.toDoubleOrNull()
            val lng = lngStr.toDoubleOrNull()
            if (lat != null && lng != null) Pair(lat, lng) else null
        } else null
    }

    suspend fun setHomeCoordinates(latitude: Double, longitude: Double, label: String = "Home") {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HOME_LATITUDE] = latitude.toString()
            preferences[PreferencesKeys.HOME_LONGITUDE] = longitude.toString()
            preferences[PreferencesKeys.HOME_LABEL] = label
        }
    }

    suspend fun clearHomeCoordinates() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.HOME_LATITUDE)
            preferences.remove(PreferencesKeys.HOME_LONGITUDE)
            preferences.remove(PreferencesKeys.HOME_LABEL)
        }
    }
}
