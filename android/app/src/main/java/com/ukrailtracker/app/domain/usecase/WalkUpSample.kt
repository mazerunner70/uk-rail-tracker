package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.geo.GeoUtils

data class WalkUpSample(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Double?,
    val epochMs: Long,
)

/** Prefer the OS speed. Fall back to distance over time when the fix has none. */
fun resolveSpeed(current: WalkUpSample, previous: WalkUpSample?): Double? {
    val reported = current.speedMps
    if (reported != null && reported >= 0.0) return reported
    if (previous == null) return null
    val dtSec = (current.epochMs - previous.epochMs) / 1_000.0
    if (dtSec < 0.5) return previous.speedMps
    val dist = GeoUtils.haversineMetres(
        previous.latitude,
        previous.longitude,
        current.latitude,
        current.longitude,
    )
    return dist / dtSec
}
