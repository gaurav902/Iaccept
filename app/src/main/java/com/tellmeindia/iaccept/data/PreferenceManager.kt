package com.tellmeindia.iaccept.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    companion object {
        val MIN_FARE = intPreferencesKey("min_fare")
        val MAX_DISTANCE = doublePreferencesKey("max_distance")
        val AUTO_ACCEPT_ENABLED = booleanPreferencesKey("auto_accept_enabled")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val PARCEL_FILTER = booleanPreferencesKey("parcel_filter")
        val AUTOMATION_MASTER = booleanPreferencesKey("automation_master")
        val UPI_SAFE_MODE = booleanPreferencesKey("upi_safe_mode")
        val SCREEN_INTERACTION = booleanPreferencesKey("screen_interaction")
        val RAPIDO_ENABLED = booleanPreferencesKey("rapido_enabled")
        val UBER_ENABLED = booleanPreferencesKey("uber_enabled")
        val DISCLOSURE_ACCEPTED = booleanPreferencesKey("disclosure_accepted")
        val THEME_MODE = intPreferencesKey("theme_mode") // 0: Auto, 1: Light, 2: Dark
    }

    val themeMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: 0
    }

    suspend fun updateThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    val disclosureAccepted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DISCLOSURE_ACCEPTED] ?: false
    }

    suspend fun updateDisclosureAccepted(accepted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DISCLOSURE_ACCEPTED] = accepted
        }
    }

    val automationMaster: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTOMATION_MASTER] ?: true
    }

    val rapidoEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[RAPIDO_ENABLED] ?: true
    }

    val uberEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[UBER_ENABLED] ?: true
    }

    val upiSafeMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[UPI_SAFE_MODE] ?: false
    }

    val screenInteraction: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SCREEN_INTERACTION] ?: true
    }

    val minFare: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MIN_FARE] ?: 0
    }

    val maxDistance: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[MAX_DISTANCE] ?: 100.0
    }

    val parcelFilter: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PARCEL_FILTER] ?: true
    }

    val autoAcceptEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_ACCEPT_ENABLED] ?: false
    }

    val serviceEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SERVICE_ENABLED] ?: false
    }

    suspend fun updateMinFare(fare: Int) {
        context.dataStore.edit { preferences ->
            preferences[MIN_FARE] = fare
        }
    }

    suspend fun updateMaxDistance(distance: Double) {
        context.dataStore.edit { preferences ->
            preferences[MAX_DISTANCE] = distance
        }
    }

    suspend fun updateAutoAccept(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_ACCEPT_ENABLED] = enabled
        }
    }

    suspend fun updateServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SERVICE_ENABLED] = enabled
        }
    }

    suspend fun updateParcelFilter(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PARCEL_FILTER] = enabled
        }
    }

    suspend fun updateRapidoEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RAPIDO_ENABLED] = enabled
        }
    }

    suspend fun updateUberEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[UBER_ENABLED] = enabled
        }
    }

    suspend fun updateAutomationMaster(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTOMATION_MASTER] = enabled
            preferences[SCREEN_INTERACTION] = enabled
            preferences[SERVICE_ENABLED] = enabled
            if (enabled) {
                preferences[UPI_SAFE_MODE] = false
            }
        }
    }

    suspend fun updateUpiSafeMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[UPI_SAFE_MODE] = enabled
            if (enabled) {
                preferences[AUTOMATION_MASTER] = false
                preferences[SCREEN_INTERACTION] = false
                preferences[SERVICE_ENABLED] = false
            }
        }
    }

    suspend fun updateScreenInteraction(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCREEN_INTERACTION] = enabled
            if (enabled) {
                preferences[UPI_SAFE_MODE] = false
            }
        }
    }
}
