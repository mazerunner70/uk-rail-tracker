package com.ukrailtracker.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ukrailtracker.app.domain.model.CommuteWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

private val Context.commuteDataStore: DataStore<Preferences> by preferencesDataStore(name = "commute_windows")

class CommuteWindowStore(private val context: Context) {
    private val key = stringPreferencesKey("commute_windows")

    val windowsFlow: Flow<List<CommuteWindow>> = context.commuteDataStore.data.map { prefs ->
        decode(prefs[key].orEmpty())
    }

    suspend fun getWindows(): List<CommuteWindow> = windowsFlow.first()

    suspend fun upsert(window: CommuteWindow) {
        val normalised = window.copy(stationCrs = window.stationCrs.trim().uppercase())
        val current = getWindows().toMutableList()
        val idx = current.indexOfFirst { it.id == normalised.id }
        if (idx >= 0) current[idx] = normalised else current.add(normalised)
        persist(current)
    }

    suspend fun delete(id: String) {
        persist(getWindows().filterNot { it.id == id })
    }

    private suspend fun persist(windows: List<CommuteWindow>) {
        context.commuteDataStore.edit { prefs ->
            prefs[key] = encode(windows)
        }
    }

    companion object {
        fun encode(windows: List<CommuteWindow>): String {
            val arr = JSONArray()
            windows.forEach { w ->
                arr.put(
                    JSONObject().apply {
                        put("id", w.id)
                        put("stationCrs", w.stationCrs)
                        put("startMinutes", w.startMinutes)
                        put("endMinutes", w.endMinutes)
                        put("label", w.label)
                        put(
                            "days",
                            JSONArray(w.days.map { it.name }.sorted()),
                        )
                    },
                )
            }
            return arr.toString()
        }

        fun decode(raw: String): List<CommuteWindow> {
            if (raw.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val daysArr = obj.optJSONArray("days") ?: JSONArray()
                        val days = buildSet {
                            for (d in 0 until daysArr.length()) {
                                runCatching {
                                    DayOfWeek.valueOf(daysArr.getString(d))
                                }.getOrNull()?.let(::add)
                            }
                        }
                        val crs = obj.optString("stationCrs").trim().uppercase()
                        if (crs.isBlank() || days.isEmpty()) continue
                        add(
                            CommuteWindow(
                                id = obj.optString("id").ifBlank {
                                    java.util.UUID.randomUUID().toString()
                                },
                                stationCrs = crs,
                                days = days,
                                startMinutes = obj.optInt("startMinutes", 0).coerceIn(0, 1439),
                                endMinutes = obj.optInt("endMinutes", 0).coerceIn(0, 1439),
                                label = obj.optString("label"),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
