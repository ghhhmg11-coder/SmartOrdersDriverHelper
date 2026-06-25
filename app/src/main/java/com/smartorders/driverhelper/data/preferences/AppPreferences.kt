package com.smartorders.driverhelper.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        val AUTO_ACCEPT_ENABLED = booleanPreferencesKey("auto_accept_enabled")
        val MIN_PRICE = doublePreferencesKey("min_price")
        val MAX_PRICE = doublePreferencesKey("max_price")
        val MAX_PICKUP_DISTANCE = doublePreferencesKey("max_pickup_distance")
        val MAX_TRIP_DISTANCE = doublePreferencesKey("max_trip_distance")
        val TARGET_APP = stringPreferencesKey("target_app")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val DELAY_MS = longPreferencesKey("delay_ms")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
    }

    val autoAcceptEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_ACCEPT_ENABLED] ?: false }
    val minPrice: Flow<Double> = context.dataStore.data.map { it[MIN_PRICE] ?: 0.0 }
    val maxPrice: Flow<Double> = context.dataStore.data.map { it[MAX_PRICE] ?: 9999.0 }
    val maxPickupDistance: Flow<Double> = context.dataStore.data.map { it[MAX_PICKUP_DISTANCE] ?: 10.0 }
    val maxTripDistance: Flow<Double> = context.dataStore.data.map { it[MAX_TRIP_DISTANCE] ?: 100.0 }
    val targetApp: Flow<String> = context.dataStore.data.map { it[TARGET_APP] ?: "all" }
    val soundEnabled: Flow<Boolean> = context.dataStore.data.map { it[SOUND_ENABLED] ?: true }
    val vibrationEnabled: Flow<Boolean> = context.dataStore.data.map { it[VIBRATION_ENABLED] ?: true }
    val delayMs: Flow<Long> = context.dataStore.data.map { it[DELAY_MS] ?: 500L }
    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[IS_LOGGED_IN] ?: false }

    suspend fun setAutoAccept(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_ACCEPT_ENABLED] = enabled }
    }
    suspend fun setMinPrice(value: Double) { context.dataStore.edit { it[MIN_PRICE] = value } }
    suspend fun setMaxPrice(value: Double) { context.dataStore.edit { it[MAX_PRICE] = value } }
    suspend fun setMaxPickupDistance(value: Double) { context.dataStore.edit { it[MAX_PICKUP_DISTANCE] = value } }
    suspend fun setMaxTripDistance(value: Double) { context.dataStore.edit { it[MAX_TRIP_DISTANCE] = value } }
    suspend fun setTargetApp(value: String) { context.dataStore.edit { it[TARGET_APP] = value } }
    suspend fun setSoundEnabled(value: Boolean) { context.dataStore.edit { it[SOUND_ENABLED] = value } }
    suspend fun setVibrationEnabled(value: Boolean) { context.dataStore.edit { it[VIBRATION_ENABLED] = value } }
    suspend fun setDelayMs(value: Long) { context.dataStore.edit { it[DELAY_MS] = value } }
    suspend fun setLoggedIn(value: Boolean) { context.dataStore.edit { it[IS_LOGGED_IN] = value } }

    suspend fun resetAll() {
        context.dataStore.edit { prefs ->
            prefs[AUTO_ACCEPT_ENABLED] = false
            prefs[MIN_PRICE] = 0.0
            prefs[MAX_PRICE] = 9999.0
            prefs[MAX_PICKUP_DISTANCE] = 10.0
            prefs[MAX_TRIP_DISTANCE] = 100.0
            prefs[TARGET_APP] = "all"
            prefs[SOUND_ENABLED] = true
            prefs[VIBRATION_ENABLED] = true
            prefs[DELAY_MS] = 500L
        }
    }
}
