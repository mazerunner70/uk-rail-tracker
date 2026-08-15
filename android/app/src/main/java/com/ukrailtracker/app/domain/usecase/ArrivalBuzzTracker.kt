package com.ukrailtracker.app.domain.usecase

/**
 * Final-approach haptic thresholds for M6 (3 / 2 / 1 minutes before ETA).
 *
 * Each threshold fires at most once per trip. If ETA slips past a threshold that
 * already fired, it is not re-armed.
 */
object ArrivalBuzzTracker {

    val THRESHOLDS_MINUTES: List<Int> = listOf(3, 2, 1)

    /**
     * Returns thresholds that should vibrate now, given [minutesRemaining] and the set of
     * thresholds already fired this trip.
     *
     * A threshold T fires when approaching the destination and
     * `0 < minutesRemaining <= T` (or minutesRemaining == 0 for the 1-min buzz window edge).
     */
    fun thresholdsToFire(
        minutesRemaining: Long?,
        alreadyFired: Set<Int>,
        approachingDestination: Boolean,
    ): List<Int> {
        if (!approachingDestination) return emptyList()
        if (minutesRemaining == null) return emptyList()
        if (minutesRemaining < 0) return emptyList()

        return THRESHOLDS_MINUTES.filter { threshold ->
            threshold !in alreadyFired && minutesRemaining <= threshold
        }
    }

    fun withFired(alreadyFired: Set<Int>, newlyFired: Collection<Int>): Set<Int> =
        alreadyFired + newlyFired
}
