package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.geo.GeoUtils

/**
 * Station-stop detection while on a train (M6).
 *
 * Confirms a stop when GPS shows low speed for [StationStopConfig.dwellMillis] and the
 * nearest route station is within [StationStopConfig.matchMetres].
 */
data class StationStopConfig(
    /** Proximity to a calling-point station centre. */
    val matchMetres: Double = 250.0,
    /** ~5.4 km/h — treat as stopped / dwelling. */
    val stopMaxMps: Double = 1.5,
    /** Sustained low speed before confirming the stop. */
    val dwellMillis: Long = 15_000L,
)

data class RouteStation(
    val crs: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

sealed interface StationStopPhase {
    data object Moving : StationStopPhase

    data class Dwelling(
        val crs: String,
        val name: String,
        val startedAtEpochMs: Long,
        val distanceMetres: Double,
    ) : StationStopPhase

    data class Confirmed(
        val crs: String,
        val name: String,
        val confirmedAtEpochMs: Long,
        val distanceMetres: Double,
    ) : StationStopPhase
}

object DetectStationStop {

    fun next(
        phase: StationStopPhase,
        sample: WalkUpSample,
        speedMps: Double?,
        routeStations: List<RouteStation>,
        config: StationStopConfig = StationStopConfig(),
    ): StationStopPhase {
        if (routeStations.isEmpty()) return StationStopPhase.Moving

        val nearest = nearestRouteStation(sample.latitude, sample.longitude, routeStations)
            ?: return StationStopPhase.Moving
        val (station, distance) = nearest
        val stopped = isStopped(speedMps, config) && distance <= config.matchMetres

        return when (phase) {
            StationStopPhase.Moving -> {
                if (!stopped) {
                    StationStopPhase.Moving
                } else {
                    StationStopPhase.Dwelling(
                        crs = station.crs,
                        name = station.name,
                        startedAtEpochMs = sample.epochMs,
                        distanceMetres = distance,
                    )
                }
            }

            is StationStopPhase.Dwelling -> {
                when {
                    !stopped -> StationStopPhase.Moving
                    !station.crs.equals(phase.crs, ignoreCase = true) -> {
                        StationStopPhase.Dwelling(
                            crs = station.crs,
                            name = station.name,
                            startedAtEpochMs = sample.epochMs,
                            distanceMetres = distance,
                        )
                    }
                    sample.epochMs - phase.startedAtEpochMs >= config.dwellMillis -> {
                        StationStopPhase.Confirmed(
                            crs = station.crs,
                            name = station.name,
                            confirmedAtEpochMs = sample.epochMs,
                            distanceMetres = distance,
                        )
                    }
                    else -> phase.copy(distanceMetres = distance)
                }
            }

            is StationStopPhase.Confirmed -> {
                when {
                    !stopped -> StationStopPhase.Moving
                    !station.crs.equals(phase.crs, ignoreCase = true) -> {
                        StationStopPhase.Dwelling(
                            crs = station.crs,
                            name = station.name,
                            startedAtEpochMs = sample.epochMs,
                            distanceMetres = distance,
                        )
                    }
                    else -> phase.copy(distanceMetres = distance)
                }
            }
        }
    }

    fun isStopped(speedMps: Double?, config: StationStopConfig = StationStopConfig()): Boolean {
        if (speedMps == null) return false
        return speedMps <= config.stopMaxMps
    }

    fun nearestRouteStation(
        latitude: Double,
        longitude: Double,
        routeStations: List<RouteStation>,
    ): Pair<RouteStation, Double>? {
        var best: RouteStation? = null
        var bestDist = Double.MAX_VALUE
        for (station in routeStations) {
            val d = GeoUtils.haversineMetres(
                latitude,
                longitude,
                station.latitude,
                station.longitude,
            )
            if (d < bestDist) {
                bestDist = d
                best = station
            }
        }
        return best?.let { it to bestDist }
    }
}
