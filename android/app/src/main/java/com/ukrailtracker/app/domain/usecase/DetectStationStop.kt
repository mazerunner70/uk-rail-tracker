package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.geo.GeoUtils

/**
 * Station-stop detection while on a train (M6).
 *
 * Confirms a stop when GPS shows low speed for [StationStopConfig.dwellMillis] and the
 * nearest route station is within [StationStopConfig.matchMetres].
 *
 * Raw GPS motion only — ride phase [OnTrainRidePhase.AtCallingPoint] is derived in reduce
 * when motion becomes [GpsMotion.Confirmed].
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

/** Raw GPS stop detector output (Moving / Dwelling / Confirmed). */
sealed interface GpsMotion {
    data object Moving : GpsMotion

    data class Dwelling(
        val crs: String,
        val name: String,
        val startedAtEpochMs: Long,
        val distanceMetres: Double,
    ) : GpsMotion

    data class Confirmed(
        val crs: String,
        val name: String,
        val confirmedAtEpochMs: Long,
        val distanceMetres: Double,
    ) : GpsMotion
}

@Deprecated("Use GpsMotion", ReplaceWith("GpsMotion"))
typealias StationStopPhase = GpsMotion

object DetectStationStop {

    fun next(
        phase: GpsMotion,
        sample: WalkUpSample,
        speedMps: Double?,
        routeStations: List<RouteStation>,
        config: StationStopConfig = StationStopConfig(),
    ): GpsMotion {
        if (routeStations.isEmpty()) return GpsMotion.Moving

        val nearest = nearestRouteStation(sample.latitude, sample.longitude, routeStations)
            ?: return GpsMotion.Moving
        val (station, distance) = nearest
        val stopped = isStopped(speedMps, config) && distance <= config.matchMetres

        return when (phase) {
            GpsMotion.Moving -> {
                if (!stopped) {
                    GpsMotion.Moving
                } else {
                    GpsMotion.Dwelling(
                        crs = station.crs,
                        name = station.name,
                        startedAtEpochMs = sample.epochMs,
                        distanceMetres = distance,
                    )
                }
            }

            is GpsMotion.Dwelling -> {
                when {
                    !stopped -> GpsMotion.Moving
                    !station.crs.equals(phase.crs, ignoreCase = true) -> {
                        GpsMotion.Dwelling(
                            crs = station.crs,
                            name = station.name,
                            startedAtEpochMs = sample.epochMs,
                            distanceMetres = distance,
                        )
                    }
                    sample.epochMs - phase.startedAtEpochMs >= config.dwellMillis -> {
                        GpsMotion.Confirmed(
                            crs = station.crs,
                            name = station.name,
                            confirmedAtEpochMs = sample.epochMs,
                            distanceMetres = distance,
                        )
                    }
                    else -> phase.copy(distanceMetres = distance)
                }
            }

            is GpsMotion.Confirmed -> {
                when {
                    !stopped -> GpsMotion.Moving
                    !station.crs.equals(phase.crs, ignoreCase = true) -> {
                        GpsMotion.Dwelling(
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
