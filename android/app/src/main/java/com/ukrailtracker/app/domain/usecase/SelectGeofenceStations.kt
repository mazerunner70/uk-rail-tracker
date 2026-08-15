package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance

/**
 * Builds the set of stations to register as OS geofences:
 * nearest [nearbyLimit] plus always-on favourites / commute CRS (capped at [maxTotal]).
 */
object SelectGeofenceStations {

    data class Candidate(
        val crs: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
    )

    fun select(
        nearby: List<StationWithDistance>,
        alwaysOn: List<Station>,
        nearbyLimit: Int = 5,
        maxTotal: Int = 20,
    ): List<Candidate> {
        val byCrs = LinkedHashMap<String, Candidate>()

        nearby.take(nearbyLimit.coerceAtLeast(0)).forEach { row ->
            val s = row.station
            val crs = s.crsCode.trim().uppercase()
            if (crs.isNotEmpty()) {
                byCrs[crs] = Candidate(
                    crs = crs,
                    name = s.name.ifBlank { crs },
                    latitude = s.latitude,
                    longitude = s.longitude,
                )
            }
        }

        alwaysOn.forEach { s ->
            val crs = s.crsCode.trim().uppercase()
            if (crs.isEmpty() || byCrs.containsKey(crs)) return@forEach
            byCrs[crs] = Candidate(
                crs = crs,
                name = s.name.ifBlank { crs },
                latitude = s.latitude,
                longitude = s.longitude,
            )
        }

        return byCrs.values.take(maxTotal.coerceAtLeast(0))
    }
}
