package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.model.StationWithDistance

/**
 * Walk-up arrival state machine (M4).
 *
 * - Cold start already inside a station geofence (not vehicle speed) → [WalkUpPhase.Arrived]
 *   immediately — no extra walking required.
 * - Later approach: pedestrian entry + short dwell → Arrived.
 * - Vehicle / train speeds inside the footprint do not count as walk-up.
 */
data class WalkUpConfig(
    val geofenceMetres: Double = 200.0,
    /** ~1 km/h — below this counts as stopped / GPS noise. */
    val walkMinMps: Double = 0.28,
    /** ~7.2 km/h — above this is jogging / cycling / vehicle for entry. */
    val walkMaxMps: Double = 2.0,
    /** Clearly not on foot (~29 km/h). */
    val vehicleRejectMps: Double = 8.0,
    val dwellMillis: Long = 12_000L,
    /** Hysteresis so a locked arrival does not flap at the geofence edge. */
    val lockHoldMetres: Double = 350.0,
)

data class WalkUpSample(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Double?,
    val epochMs: Long,
)

sealed interface WalkUpPhase {
    data object Away : WalkUpPhase

    data class Dwelling(
        val crs: String,
        val stationName: String,
        val enteredAtEpochMs: Long,
        val distanceMetres: Double,
        val sawWalkingPace: Boolean,
        /** False when OS never reported speed (common on emulator geo fix). */
        val hadSpeedReading: Boolean,
    ) : WalkUpPhase

    data class Arrived(
        val crs: String,
        val stationName: String,
        val distanceMetres: Double,
        val lockedAtEpochMs: Long,
    ) : WalkUpPhase
}

object DetectWalkUpArrival {

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

    /**
     * @param alreadyPresentOk when true (app just started / first fix), being inside the
     * geofence at non-vehicle speed locks [WalkUpPhase.Arrived] without a walk+dwell sequence.
     */
    fun next(
        phase: WalkUpPhase,
        sample: WalkUpSample,
        nearest: StationWithDistance?,
        speedMps: Double?,
        config: WalkUpConfig = WalkUpConfig(),
        alreadyPresentOk: Boolean = false,
    ): WalkUpPhase {
        if (nearest == null) return WalkUpPhase.Away

        val crs = nearest.station.crsCode.uppercase()
        val name = nearest.station.name
        val distance = nearest.distanceMetres
        val vehicle = isVehicle(speedMps, config)
        val walking = isClearlyWalking(speedMps, config)

        if (vehicle) return WalkUpPhase.Away

        return when (phase) {
            WalkUpPhase.Away -> {
                if (distance > config.geofenceMetres) {
                    WalkUpPhase.Away
                } else if (alreadyPresentOk) {
                    WalkUpPhase.Arrived(
                        crs = crs,
                        stationName = name,
                        distanceMetres = distance,
                        lockedAtEpochMs = sample.epochMs,
                    )
                } else {
                    // Drive-and-stop (speed 0 after a vehicle sample already reset to Away):
                    // require walking or unknown speed to start a walk-up dwell.
                    val stoppedWithKnownSpeed =
                        speedMps != null && speedMps < config.walkMinMps
                    if (stoppedWithKnownSpeed && !walking) {
                        WalkUpPhase.Away
                    } else {
                        WalkUpPhase.Dwelling(
                            crs = crs,
                            stationName = name,
                            enteredAtEpochMs = sample.epochMs,
                            distanceMetres = distance,
                            sawWalkingPace = walking,
                            hadSpeedReading = speedMps != null,
                        )
                    }
                }
            }

            is WalkUpPhase.Dwelling -> {
                if (distance > config.geofenceMetres) {
                    return WalkUpPhase.Away
                }
                if (crs != phase.crs) {
                    return WalkUpPhase.Dwelling(
                        crs = crs,
                        stationName = name,
                        enteredAtEpochMs = sample.epochMs,
                        distanceMetres = distance,
                        sawWalkingPace = walking,
                        hadSpeedReading = speedMps != null,
                    )
                }
                val sawWalking = phase.sawWalkingPace || walking
                val hadSpeed = phase.hadSpeedReading || speedMps != null
                val dwellElapsed = sample.epochMs - phase.enteredAtEpochMs >= config.dwellMillis
                // Promote after dwell if we saw walking, or speed was never reported (emulator).
                val canPromote = sawWalking || !hadSpeed
                if (dwellElapsed && canPromote) {
                    WalkUpPhase.Arrived(
                        crs = crs,
                        stationName = name,
                        distanceMetres = distance,
                        lockedAtEpochMs = sample.epochMs,
                    )
                } else {
                    phase.copy(
                        distanceMetres = distance,
                        sawWalkingPace = sawWalking,
                        hadSpeedReading = hadSpeed,
                    )
                }
            }

            is WalkUpPhase.Arrived -> {
                if (distance > config.lockHoldMetres) {
                    return if (distance <= config.geofenceMetres) {
                        WalkUpPhase.Dwelling(
                            crs = crs,
                            stationName = name,
                            enteredAtEpochMs = sample.epochMs,
                            distanceMetres = distance,
                            sawWalkingPace = walking,
                            hadSpeedReading = speedMps != null,
                        )
                    } else {
                        WalkUpPhase.Away
                    }
                }
                if (crs != phase.crs && distance <= config.geofenceMetres) {
                    return WalkUpPhase.Dwelling(
                        crs = crs,
                        stationName = name,
                        enteredAtEpochMs = sample.epochMs,
                        distanceMetres = distance,
                        sawWalkingPace = walking,
                        hadSpeedReading = speedMps != null,
                    )
                }
                if (crs == phase.crs) {
                    phase.copy(distanceMetres = distance)
                } else {
                    phase
                }
            }
        }
    }

    private fun isVehicle(speedMps: Double?, config: WalkUpConfig): Boolean =
        speedMps != null && speedMps >= config.vehicleRejectMps

    private fun isClearlyWalking(speedMps: Double?, config: WalkUpConfig): Boolean =
        speedMps != null && speedMps >= config.walkMinMps && speedMps <= config.walkMaxMps
}
