package com.ukrailtracker.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_meta")

class RecentSearchStore(private val context: Context) {
    private val key = stringPreferencesKey("recent_station_searches")

    suspend fun getRecent(): List<String> {
        val raw = context.searchDataStore.data.map { it[key].orEmpty() }.first()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val crs = arr.optString(i).trim().uppercase()
                    if (crs.isNotBlank()) add(crs)
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun record(crs: String) {
        val normalised = crs.trim().uppercase()
        if (normalised.isBlank()) return
        val current = getRecent().filterNot { it == normalised }.toMutableList()
        current.add(0, normalised)
        val trimmed = current.take(MAX_RECENT)
        context.searchDataStore.edit { prefs ->
            prefs[key] = JSONArray(trimmed).toString()
        }
    }

    companion object {
        private const val MAX_RECENT = 8
    }
}
