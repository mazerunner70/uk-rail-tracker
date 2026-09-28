package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.CallingPoint
import java.time.LocalDateTime

/** Timetable-derived state while waiting on the platform for the pinned service. */
data class TrainApproachState(
    val originStationName: String,
    val minutesUntilOriginArrival: Long?,
    /** When the train has not reached the boarding stop yet — last/upstream stop on the route. */
    val approachingFromStopName: String?,
    /** True when the pinned origin is the first calling point on the service. */
    val originIsFirstOnRoute: Boolean,
    val minutesUntilDeparture: Long?,
    /** Auto-mark boarded when departure from the boarding stop is within [AUTO_BOARD_MINUTES]. */
    val shouldAutoBoard: Boolean,
) {
    companion object {
        const val AUTO_BOARD_MINUTES = 2L
    }
}

object ComputeTrainApproach {

    fun compute(
        callingPoints: List<CallingPoint>,
        originCrs: String,
        destinationCrs: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): TrainApproachState {
        if (callingPoints.isEmpty()) {
            return TrainApproachState(
                originStationName = originCrs,
                minutesUntilOriginArrival = null,
                approachingFromStopName = null,
                originIsFirstOnRoute = true,
                minutesUntilDeparture = null,
                shouldAutoBoard = false,
            )
        }

        val origin = originCrs.trim().uppercase()
        val originIdx = callingPoints.indexOfFirst { it.crs?.uppercase() == origin }
            .takeIf { it >= 0 }
            ?: 0
        val originCp = callingPoints[originIdx]

        val firstRouteIdx = callingPoints.indexOfFirst { !it.crs.isNullOrBlank() }.coerceAtLeast(0)
        val originIsFirstOnRoute = originIdx <= firstRouteIdx

        val minutesUntilOriginArrival = JourneyTimeUtils.minutesUntilExpectedArrival(
            scheduledArrivalLabel = originCp.scheduledTimeLabel,
            expectedArrivalLabel = originCp.expectedLabel,
            now = now,
        )

        val depLabel = JourneyTimeUtils.effectiveDepartureLabel(
            scheduledDepartureLabel = originCp.scheduledTimeLabel.orEmpty(),
            expectedDepartureLabel = originCp.expectedLabel,
        )
        val minutesUntilDeparture = JourneyTimeUtils.minutesUntilDeparture(depLabel, now)

        val approachingFromStopName = if (originIsFirstOnRoute) {
            null
        } else {
            resolveApproachingFromStop(
                callingPoints = callingPoints,
                originIdx = originIdx,
                firstRouteIdx = firstRouteIdx,
                now = now,
            )
        }

        val shouldAutoBoard = minutesUntilDeparture != null &&
            minutesUntilDeparture <= TrainApproachState.AUTO_BOARD_MINUTES

        return TrainApproachState(
            originStationName = originCp.locationName,
            minutesUntilOriginArrival = minutesUntilOriginArrival,
            approachingFromStopName = approachingFromStopName,
            originIsFirstOnRoute = originIsFirstOnRoute,
            minutesUntilDeparture = minutesUntilDeparture,
            shouldAutoBoard = shouldAutoBoard,
        )
    }

    private fun resolveApproachingFromStop(
        callingPoints: List<CallingPoint>,
        originIdx: Int,
        firstRouteIdx: Int,
        now: LocalDateTime,
    ): String? {
        if (originIdx <= firstRouteIdx) return null
        var lastDeparted: String? = null
        for (i in originIdx - 1 downTo firstRouteIdx) {
            val cp = callingPoints[i]
            val name = cp.locationName.ifBlank { cp.crs.orEmpty() }
            val depLabel = JourneyTimeUtils.effectiveDepartureLabel(
                scheduledDepartureLabel = cp.scheduledTimeLabel.orEmpty(),
                expectedDepartureLabel = cp.expectedLabel,
            )
            val minsUntilDep = JourneyTimeUtils.minutesUntilDeparture(depLabel, now)
            if (minsUntilDep != null && minsUntilDep > 0) {
                return name
            }
            lastDeparted = name
        }
        return lastDeparted
    }
}

/** Train is moving fast enough to leave the platform (not walking pace). */
fun isTrainMovingSpeed(speedMps: Double?): Boolean =
    speedMps != null && speedMps >= TRAIN_MOVING_MIN_MPS

const val TRAIN_MOVING_MIN_MPS = 2.5
