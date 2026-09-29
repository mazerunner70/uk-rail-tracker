package com.ukrailtracker.app.domain.location

import com.ukrailtracker.app.domain.model.StationWithDistance
import com.ukrailtracker.app.domain.usecase.CommuteEvent

/**
 * Distance bands for station presence. Enter at [atMetres] / [nearMetres];
 * leave only after [exitMarginMetres] past that radius so a fix on the boundary
 * does not bounce hosts.
 */
data class StationPresenceConfig(
    val nearMetres: Double = 350.0,
    val atMetres: Double = 200.0,
    val exitMarginMetres: Double = 40.0,
)

sealed interface PresenceBand {
    data object Away : PresenceBand

    data class Near(val station: StationWithDistance) : PresenceBand

    data class At(val station: StationWithDistance) : PresenceBand
}

object StationPresenceClassifier {

    fun next(
        previous: PresenceBand,
        nearest: StationWithDistance?,
        config: StationPresenceConfig = StationPresenceConfig(),
    ): PresenceBand {
        if (nearest == null) return PresenceBand.Away
        val distance = nearest.distanceMetres
        val atEnter = config.atMetres
        val atExit = config.atMetres + config.exitMarginMetres
        val nearEnter = config.nearMetres
        val nearExit = config.nearMetres + config.exitMarginMetres
        val sameStation = previous.crs()?.equals(nearest.station.crsCode, ignoreCase = true) == true
        if (!sameStation) return classifyFresh(nearest, distance, atEnter, nearEnter)
        return when (previous) {
            PresenceBand.Away -> classifyFresh(nearest, distance, atEnter, nearEnter)
            is PresenceBand.At -> when {
                distance <= atExit -> PresenceBand.At(nearest)
                distance <= nearExit -> PresenceBand.Near(nearest)
                else -> PresenceBand.Away
            }
            is PresenceBand.Near -> when {
                distance <= atEnter -> PresenceBand.At(nearest)
                distance <= nearExit -> PresenceBand.Near(nearest)
                else -> PresenceBand.Away
            }
        }
    }

    /**
     * Edge event when the band or CRS changes. Same band and CRS yields null
     * even if the distance moved.
     */
    fun edgeEvent(previous: PresenceBand, next: PresenceBand, epochMs: Long): CommuteEvent? {
        if (previous.samePlace(next)) return null
        return when (next) {
            PresenceBand.Away -> CommuteEvent.LeftStation
            is PresenceBand.Near -> next.station.toNearEvent(epochMs)
            is PresenceBand.At -> next.station.toAtEvent(epochMs)
        }
    }

    private fun classifyFresh(
        nearest: StationWithDistance,
        distance: Double,
        atEnter: Double,
        nearEnter: Double,
    ): PresenceBand = when {
        distance <= atEnter -> PresenceBand.At(nearest)
        distance <= nearEnter -> PresenceBand.Near(nearest)
        else -> PresenceBand.Away
    }

    private fun PresenceBand.crs(): String? = when (this) {
        PresenceBand.Away -> null
        is PresenceBand.Near -> station.station.crsCode
        is PresenceBand.At -> station.station.crsCode
    }

    private fun PresenceBand.samePlace(other: PresenceBand): Boolean = when {
        this is PresenceBand.Away && other is PresenceBand.Away -> true
        this is PresenceBand.Near && other is PresenceBand.Near ->
            station.station.crsCode.equals(other.station.station.crsCode, ignoreCase = true)
        this is PresenceBand.At && other is PresenceBand.At ->
            station.station.crsCode.equals(other.station.station.crsCode, ignoreCase = true)
        else -> false
    }
}

internal fun StationWithDistance.toNearEvent(epochMs: Long) = CommuteEvent.NearStation(
    crs = station.crsCode,
    name = station.name,
    latitude = station.latitude,
    longitude = station.longitude,
    distanceMetres = distanceMetres,
    epochMs = epochMs,
)

internal fun StationWithDistance.toAtEvent(epochMs: Long) = CommuteEvent.AtStation(
    crs = station.crsCode,
    name = station.name,
    latitude = station.latitude,
    longitude = station.longitude,
    distanceMetres = distanceMetres,
    epochMs = epochMs,
)
