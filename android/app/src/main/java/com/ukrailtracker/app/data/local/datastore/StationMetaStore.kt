package com.ukrailtracker.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.metaDataStore: DataStore<Preferences> by preferencesDataStore(name = "station_meta")

class StationMetaStore(private val context: Context) {
    private val versionKey = intPreferencesKey("stations_db_version")

    suspend fun getImportedVersion(): Int =
        context.metaDataStore.data.map { it[versionKey] ?: 0 }.first()

    suspend fun setImportedVersion(version: Int) {
        context.metaDataStore.edit { prefs ->
            prefs[versionKey] = version
        }
    }
}
