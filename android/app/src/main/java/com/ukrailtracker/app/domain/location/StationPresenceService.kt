package com.ukrailtracker.app.domain.location

import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import com.ukrailtracker.app.domain.model.UserLocation
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.WalkUpSample
import com.ukrailtracker.app.domain.usecase.resolveSpeed
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One GPS fix plus any presence edge that crossed a distance band. */
data class PresenceSample(
    val events: List<CommuteEvent>,
    val speedMps: Double?,
)

/**
 * Holds the station catalogue and turns each GPS fix into a [CommuteEvent.Location]
 * plus, when the distance band or station changes, a Near / At / Left event.
 */
class StationPresenceService(
    private val stationRepository: StationRepository,
    private val config: StationPresenceConfig = StationPresenceConfig(),
) {
    private val mutex = Mutex()
    private var catalogue: List<Station> = emptyList()
    private var band: PresenceBand = PresenceBand.Away
    private var previousSample: WalkUpSample? = null

    suspend fun onLocation(location: UserLocation): PresenceSample = mutex.withLock {
        val stations = loadCatalogue()
        val nearest = nearest(stations, location.latitude, location.longitude)
        val sample = WalkUpSample(
            latitude = location.latitude,
            longitude = location.longitude,
            speedMps = location.speedMetresPerSecond?.toDouble(),
            epochMs = location.epochMs,
        )
        val speed = resolveSpeed(sample, previousSample)
        previousSample = sample
        val next = StationPresenceClassifier.next(band, nearest, config)
        val edge = StationPresenceClassifier.edgeEvent(band, next, location.epochMs)
        band = next
        val events = buildList {
            add(
                CommuteEvent.Location(
                    sample = sample,
                    nearest = nearest,
                    speedMps = speed,
                ),
            )
            if (edge != null) add(edge)
        }
        PresenceSample(events = events, speedMps = speed)
    }

    suspend fun reset() {
        mutex.withLock {
            band = PresenceBand.Away
            previousSample = null
        }
    }

    private suspend fun loadCatalogue(): List<Station> {
        if (catalogue.isNotEmpty()) return catalogue
        stationRepository.ensureImported()
        catalogue = stationRepository.getAll()
        return catalogue
    }

    private fun nearest(stations: List<Station>, lat: Double, lng: Double): StationWithDistance? {
        var best: StationWithDistance? = null
        for (station in stations) {
            val distance = GeoUtils.haversineMetres(lat, lng, station.latitude, station.longitude)
            val current = best
            if (current == null || distance < current.distanceMetres) {
                best = StationWithDistance(station = station, distanceMetres = distance)
            }
        }
        return best
    }
}
