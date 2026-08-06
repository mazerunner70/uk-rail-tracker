package com.ukrailtracker.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ukrailtracker.app.domain.model.JourneyPair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.favouriteJourneysDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "favourite_journeys",
)

class FavouriteJourneysStore(private val context: Context) {
    private val key = stringPreferencesKey("favourite_journeys")

    val journeysFlow: Flow<List<JourneyPair>> = context.favouriteJourneysDataStore.data.map { prefs ->
        decode(prefs[key].orEmpty())
    }

    suspend fun getJourneys(): List<JourneyPair> = journeysFlow.first()

    suspend fun toggle(originCrs: String, destinationCrs: String): Boolean {
        val pair = JourneyPair(
            originCrs = originCrs.trim().uppercase(),
            destinationCrs = destinationCrs.trim().uppercase(),
        )
        if (pair.originCrs.isBlank() || pair.destinationCrs.isBlank()) return false
        var added = false
        val current = getJourneys().toMutableList()
        val idx = current.indexOfFirst {
            it.originCrs == pair.originCrs && it.destinationCrs == pair.destinationCrs
        }
        if (idx >= 0) {
            current.removeAt(idx)
            added = false
        } else {
            current.add(0, pair)
            added = true
        }
        persist(current)
        return added
    }

    suspend fun remove(originCrs: String, destinationCrs: String) {
        val origin = originCrs.trim().uppercase()
        val dest = destinationCrs.trim().uppercase()
        persist(
            getJourneys().filterNot {
                it.originCrs == origin && it.destinationCrs == dest
            },
        )
    }

    private suspend fun persist(pairs: List<JourneyPair>) {
        context.favouriteJourneysDataStore.edit { prefs ->
            prefs[key] = encode(pairs)
        }
    }

    companion object {
        fun encode(pairs: List<JourneyPair>): String {
            val arr = JSONArray()
            pairs.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("origin", p.originCrs)
                        .put("destination", p.destinationCrs),
                )
            }
            return arr.toString()
        }

        fun decode(raw: String): List<JourneyPair> {
            if (raw.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val origin = obj.optString("origin").trim().uppercase()
                        val dest = obj.optString("destination").trim().uppercase()
                        if (origin.isNotBlank() && dest.isNotBlank()) {
                            add(JourneyPair(origin, dest))
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
