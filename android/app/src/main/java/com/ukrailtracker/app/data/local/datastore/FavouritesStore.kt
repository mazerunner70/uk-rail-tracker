package com.ukrailtracker.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.favouritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favourites")

class FavouritesStore(private val context: Context) {
    private val key = stringSetPreferencesKey("favourite_stations")

    val favouritesFlow: Flow<List<String>> = context.favouritesDataStore.data.map { prefs ->
        prefs[key].orEmpty()
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .sorted()
    }

    suspend fun getFavourites(): List<String> = favouritesFlow.first()

    suspend fun add(crs: String) {
        val normalised = crs.trim().uppercase()
        if (normalised.isBlank()) return
        context.favouritesDataStore.edit { prefs ->
            val next = prefs[key].orEmpty().toMutableSet()
            next.add(normalised)
            prefs[key] = next
        }
    }

    suspend fun remove(crs: String) {
        val normalised = crs.trim().uppercase()
        context.favouritesDataStore.edit { prefs ->
            val next = prefs[key].orEmpty().toMutableSet()
            next.remove(normalised)
            prefs[key] = next
        }
    }

    suspend fun toggle(crs: String): Boolean {
        val normalised = crs.trim().uppercase()
        if (normalised.isBlank()) return false
        var added = false
        context.favouritesDataStore.edit { prefs ->
            val next = prefs[key].orEmpty().toMutableSet()
            if (next.contains(normalised)) {
                next.remove(normalised)
                added = false
            } else {
                next.add(normalised)
                added = true
            }
            prefs[key] = next
        }
        return added
    }
}
