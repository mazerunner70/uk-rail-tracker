package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.geo.GeoUtils

/**
 * Boarding detection after M4 walk-up (M5).
 *
 * While watching a locked origin station, require distance beyond [BoardingConfig.leaveMetres]
 * and sustained speed at/above [BoardingConfig.boardMinMps] before triggering a boarding prompt.
 * Slow walk-away does not count as boarding.
 */
data class BoardingConfig(
    /** Beyond walk-up lock hold — must leave the station footprint. */
    val leaveMetres: Double = 400.0,
    /** ~18 km/h — above walking / jogging; train-like motion. */
    val boardMinMps: Double = 5.0,
    /** Qualifying samples must hold for this long. */
    val sustainMillis: Long = 8_000L,
    /** Departure match: minutes before board signal. */
    val matchPastMinutes: Long = 8,
    /** Departure match: minutes after board signal. */
    val matchFutureMinutes: Long = 3,
)

sealed interface BoardingPhase {
    data object Idle : BoardingPhase

    data class Sustaining(
        val startedAtEpochMs: Long,
        val distanceMetres: Double,
        val speedMps: Double,
    ) : BoardingPhase

    data class Triggered(
        val triggeredAtEpochMs: Long,
        val distanceMetres: Double,
        val speedMps: Double,
    ) : BoardingPhase
}

object DetectBoarding {

    /**
     * Advance boarding detection while the user was previously at [originLat]/[originLng].
     *
     * Call on each location sample after walk-up [WalkUpPhase.Arrived]. Reset to [Idle]
     * when returning to a normal Away path without a prompt.
     */
    fun next(
        phase: BoardingPhase,
        sample: WalkUpSample,
        originLatitude: Double,
        originLongitude: Double,
        speedMps: Double?,
        config: BoardingConfig = BoardingConfig(),
    ): BoardingPhase {
        val distance = GeoUtils.haversineMetres(
            originLatitude,
            originLongitude,
            sample.latitude,
            sample.longitude,
        )
        val qualifies = qualifiesAsBoardingMotion(distance, speedMps, config)

        return when (phase) {
            BoardingPhase.Idle -> {
                if (!qualifies) {
                    BoardingPhase.Idle
                } else {
                    BoardingPhase.Sustaining(
                        startedAtEpochMs = sample.epochMs,
                        distanceMetres = distance,
                        speedMps = speedMps ?: 0.0,
                    )
                }
            }

            is BoardingPhase.Sustaining -> {
                if (!qualifies) {
                    BoardingPhase.Idle
                } else if (sample.epochMs - phase.startedAtEpochMs >= config.sustainMillis) {
                    BoardingPhase.Triggered(
                        triggeredAtEpochMs = sample.epochMs,
                        distanceMetres = distance,
                        speedMps = speedMps ?: phase.speedMps,
                    )
                } else {
                    phase.copy(
                        distanceMetres = distance,
                        speedMps = speedMps ?: phase.speedMps,
                    )
                }
            }

            is BoardingPhase.Triggered -> phase
        }
    }

    fun qualifiesAsBoardingMotion(
        distanceMetres: Double,
        speedMps: Double?,
        config: BoardingConfig = BoardingConfig(),
    ): Boolean {
        if (distanceMetres < config.leaveMetres) return false
        if (speedMps == null) return false
        return speedMps >= config.boardMinMps
    }
}
