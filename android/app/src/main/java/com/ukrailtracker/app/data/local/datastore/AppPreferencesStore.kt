package com.ukrailtracker.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppPreferencesStore(private val context: Context) {
    private val refreshEnabledKey = booleanPreferencesKey("background_refresh_enabled")
    private val refreshIntervalKey = intPreferencesKey("background_refresh_interval")

    val backgroundRefreshEnabledFlow: Flow<Boolean> =
        context.appPrefsDataStore.data.map { it[refreshEnabledKey] ?: true }

    suspend fun isBackgroundRefreshEnabled(): Boolean = backgroundRefreshEnabledFlow.first()

    suspend fun setBackgroundRefreshEnabled(enabled: Boolean) {
        context.appPrefsDataStore.edit { prefs ->
            prefs[refreshEnabledKey] = enabled
        }
    }

    suspend fun getBackgroundRefreshIntervalMinutes(): Int =
        context.appPrefsDataStore.data.map {
            it[refreshIntervalKey] ?: DEFAULT_INTERVAL_MINUTES
        }.first()

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 15
    }
}
