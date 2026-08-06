package com.ukrailtracker.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ukrailtracker.app.domain.model.PinnedJourney
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.pinnedJourneyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pinned_journey",
)

class PinnedJourneyStore(private val context: Context) {
    private val key = stringPreferencesKey("pinned_journey")

    val pinnedFlow: Flow<PinnedJourney?> = context.pinnedJourneyDataStore.data.map { prefs ->
        decode(prefs[key].orEmpty())
    }

    suspend fun getPinned(): PinnedJourney? = pinnedFlow.first()

    suspend fun pin(journey: PinnedJourney) {
        context.pinnedJourneyDataStore.edit { prefs ->
            prefs[key] = encode(journey)
        }
    }

    suspend fun clear() {
        context.pinnedJourneyDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    companion object {
        fun encode(journey: PinnedJourney): String =
            JSONObject()
                .put("serviceId", journey.serviceId)
                .put("originCrs", journey.originCrs)
                .put("destinationCrs", journey.destinationCrs)
                .put("originName", journey.originName)
                .put("destinationName", journey.destinationName)
                .put("operatorName", journey.operatorName)
                .put("scheduledDepartureLabel", journey.scheduledDepartureLabel)
                .put("pinnedAtEpochMs", journey.pinnedAtEpochMs)
                .toString()

        fun decode(raw: String): PinnedJourney? {
            if (raw.isBlank()) return null
            return runCatching {
                val obj = JSONObject(raw)
                val serviceId = obj.optString("serviceId").trim()
                val origin = obj.optString("originCrs").trim().uppercase()
                val dest = obj.optString("destinationCrs").trim().uppercase()
                if (serviceId.isBlank() || origin.isBlank() || dest.isBlank()) return null
                PinnedJourney(
                    serviceId = serviceId,
                    originCrs = origin,
                    destinationCrs = dest,
                    originName = obj.optString("originName"),
                    destinationName = obj.optString("destinationName"),
                    operatorName = obj.optString("operatorName"),
                    scheduledDepartureLabel = obj.optString("scheduledDepartureLabel"),
                    pinnedAtEpochMs = obj.optLong("pinnedAtEpochMs"),
                )
            }.getOrNull()
        }
    }
}
