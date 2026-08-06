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

private val Context.recentJourneysDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_journeys",
)

class RecentJourneysStore(private val context: Context) {
    private val key = stringPreferencesKey("recent_journeys")

    val recentFlow: Flow<List<JourneyPair>> = context.recentJourneysDataStore.data.map { prefs ->
        decode(prefs[key].orEmpty())
    }

    suspend fun getRecent(): List<JourneyPair> = recentFlow.first()

    suspend fun record(originCrs: String, destinationCrs: String) {
        val pair = JourneyPair(
            originCrs = originCrs.trim().uppercase(),
            destinationCrs = destinationCrs.trim().uppercase(),
        )
        if (pair.originCrs.isBlank() || pair.destinationCrs.isBlank()) return
        if (pair.originCrs == pair.destinationCrs) return
        val next = buildList {
            add(pair)
            getRecent()
                .filterNot { it.originCrs == pair.originCrs && it.destinationCrs == pair.destinationCrs }
                .take(MAX - 1)
                .forEach(::add)
        }
        context.recentJourneysDataStore.edit { prefs ->
            prefs[key] = encode(next)
        }
    }

    companion object {
        const val MAX = 8

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
