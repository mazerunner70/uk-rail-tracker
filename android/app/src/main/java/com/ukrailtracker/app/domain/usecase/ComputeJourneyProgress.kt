package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.CallingPoint
import java.time.LocalDateTime

data class ProgressPointUiModel(
    val name: String,
    val crs: String?,
    val scheduledLabel: String?,
    val expectedLabel: String?,
    val actualLabel: String?,
    /** Signed minutes vs timetable (positive = late). Null when unknown / on-time phrase. */
    val scheduleVarianceMinutes: Int?,
    val isOrigin: Boolean,
    val isDestination: Boolean,
    val isPast: Boolean,
    val isCurrent: Boolean,
    val isNext: Boolean,
    val isCancelled: Boolean,
)

data class JourneyProgress(
    val points: List<ProgressPointUiModel>,
    val currentStopCrs: String?,
    val currentStopName: String?,
    val nextStopCrs: String?,
    val nextStopName: String?,
    /** Timetable-only next stop (ignores GPS) — used for mismatch copy. */
    val timetableNextStopName: String?,
    val destinationCrs: String,
    val destinationName: String,
    /**
     * Minutes until destination **expected** arrival (ET clock, or ST when ET is
     * "On time"). Null when unknown or when Darwin has published an actual (AT).
     */
    val minutesToDestination: Long?,
    /** True when Darwin has published an actual arrival time for the destination. */
    val destinationArrived: Boolean,
    /** 0f at origin … 1f at / past destination. */
    val fractionComplete: Float,
    /**
     * True when GPS confirmed a stop that is not the timetable's next expected calling
     * point (and not the stop immediately before it).
     */
    val routeMismatch: Boolean,
    /** True when the next station on the trip is the favourite destination. */
    val approachingDestination: Boolean,
)

object ComputeJourneyProgress {

    fun compute(
        callingPoints: List<CallingPoint>,
        originCrs: String,
        destinationCrs: String,
        destinationNameFallback: String,
        confirmedStopCrs: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ): JourneyProgress {
        val origin = originCrs.trim().uppercase()
        val dest = destinationCrs.trim().uppercase()
        val confirmed = confirmedStopCrs?.trim()?.uppercase()

        val destIndex = callingPoints.indexOfLast { it.crs?.uppercase() == dest }
            .takeIf { it >= 0 }
            ?: (callingPoints.lastIndex.takeIf { callingPoints.isNotEmpty() } ?: -1)
        val originIndex = callingPoints.indexOfFirst { it.crs?.uppercase() == origin }
            .takeIf { it >= 0 }
            ?: 0

        // Timetable-only next — used for mismatch (ignore GPS confirmation).
        val timetableNextIndex = inferExpectedNextIndex(
            callingPoints = callingPoints,
            originIndex = originIndex,
            destIndex = destIndex,
            confirmed = null,
            now = now,
        )

        val confirmedIndex = confirmed?.let { crs ->
            callingPoints.indexOfFirst { it.crs?.uppercase() == crs }.takeIf { it >= 0 }
        }

        val currentIndex = when {
            confirmedIndex != null -> confirmedIndex
            timetableNextIndex > originIndex -> timetableNextIndex - 1
            else -> originIndex
        }

        val nextIndex = when {
            confirmedIndex != null && confirmedIndex < destIndex -> confirmedIndex + 1
            timetableNextIndex in 0..destIndex -> timetableNextIndex
            else -> null
        }

        val routeMismatch = isRouteMismatch(
            confirmedIndex = confirmedIndex,
            timetableNextIndex = timetableNextIndex,
            destIndex = destIndex,
        )

        val points = callingPoints.mapIndexed { index, cp ->
            val crs = cp.crs?.uppercase()
            val actualDisplay = JourneyTimeUtils.displayActualLabel(cp.actualTimeLabel)
            val observed = when {
                JourneyTimeUtils.parseableClockLabel(cp.actualTimeLabel) != null ->
                    JourneyTimeUtils.parseableClockLabel(cp.actualTimeLabel)
                // Darwin often reports AT as "On time" — treat as zero variance vs ST.
                JourneyTimeUtils.hasActualArrival(cp.actualTimeLabel) -> cp.scheduledTimeLabel
                else -> JourneyTimeUtils.parseableClockLabel(cp.expectedLabel)
            }
            ProgressPointUiModel(
                name = cp.locationName,
                crs = cp.crs,
                scheduledLabel = cp.scheduledTimeLabel,
                expectedLabel = cp.expectedLabel,
                actualLabel = actualDisplay,
                scheduleVarianceMinutes = JourneyTimeUtils.scheduleVarianceMinutes(
                    scheduledLabel = cp.scheduledTimeLabel,
                    observedLabel = observed,
                ),
                isOrigin = index == originIndex || crs == origin,
                isDestination = index == destIndex || crs == dest,
                isPast = index < currentIndex,
                isCurrent = index == currentIndex,
                isNext = nextIndex != null && index == nextIndex,
                isCancelled = cp.isCancelled,
            )
        }

        val destPoint = destIndex.takeIf { it >= 0 }?.let { callingPoints[it] }
        // AT may be HH:mm or the phrase "On time" (arrived to timetable).
        val destinationArrived = JourneyTimeUtils.hasActualArrival(destPoint?.actualTimeLabel)
        // Countdown is always destination ET (ST when ET is "On time") — never AT.
        val minutes = if (destinationArrived) {
            null
        } else {
            JourneyTimeUtils.minutesUntilExpectedArrival(
                scheduledArrivalLabel = destPoint?.scheduledTimeLabel,
                expectedArrivalLabel = destinationExpectedLabel(destPoint),
                now = now,
            )
        }

        val span = (destIndex - originIndex).coerceAtLeast(1)
        val progressed = when {
            currentIndex <= originIndex -> 0
            currentIndex >= destIndex -> span
            else -> currentIndex - originIndex
        }
        val fraction = (progressed.toFloat() / span.toFloat()).coerceIn(0f, 1f)

        val nextPoint = nextIndex?.let { callingPoints.getOrNull(it) }
        val currentPoint = callingPoints.getOrNull(currentIndex)
        val timetableNextPoint = timetableNextIndex.takeIf { it >= 0 }
            ?.let { callingPoints.getOrNull(it) }
        val approaching = nextPoint?.crs?.uppercase() == dest

        return JourneyProgress(
            points = points,
            currentStopCrs = currentPoint?.crs?.uppercase() ?: confirmed,
            currentStopName = currentPoint?.locationName,
            nextStopCrs = nextPoint?.crs?.uppercase(),
            nextStopName = nextPoint?.locationName,
            timetableNextStopName = timetableNextPoint?.locationName,
            destinationCrs = dest,
            destinationName = destPoint?.locationName ?: destinationNameFallback,
            minutesToDestination = minutes,
            destinationArrived = destinationArrived,
            fractionComplete = fraction,
            routeMismatch = routeMismatch,
            approachingDestination = approaching,
        )
    }

    /**
     * ET for the destination stop. When Darwin has only published AT, [CallingPoint.expectedLabel]
     * may have been filled from AT — ignore that and fall back to scheduled for countdown.
     */
    private fun destinationExpectedLabel(destPoint: CallingPoint?): String? {
        if (destPoint == null) return null
        // When AT is present, expectedLabel may be AT echoed via et ?: at — not a live ETA.
        if (JourneyTimeUtils.hasActualArrival(destPoint.actualTimeLabel)) return null
        return destPoint.expectedLabel
    }

    /**
     * Next expected stop: first calling point after origin whose arrival is still in the
     * future (or unknown). When [confirmed] matches a point, next is the following one.
     */
    internal fun inferExpectedNextIndex(
        callingPoints: List<CallingPoint>,
        originIndex: Int,
        destIndex: Int,
        confirmed: String?,
        now: LocalDateTime,
    ): Int {
        if (callingPoints.isEmpty() || destIndex < 0) return -1
        if (confirmed != null) {
            val confirmedIdx = callingPoints.indexOfFirst { it.crs?.uppercase() == confirmed }
            if (confirmedIdx >= 0) {
                return (confirmedIdx + 1).coerceAtMost(destIndex)
            }
        }
        for (i in (originIndex + 1)..destIndex) {
            val cp = callingPoints[i]
            val label = effectiveArrivalLabel(cp)
            val minutes = JourneyTimeUtils.minutesUntilArrivalLabel(label, now)
            if (minutes == null || minutes >= 0) return i
        }
        return destIndex
    }

    private fun isRouteMismatch(
        confirmedIndex: Int?,
        timetableNextIndex: Int,
        destIndex: Int,
    ): Boolean {
        if (confirmedIndex == null || destIndex < 0 || timetableNextIndex < 0) return false
        // Arrived at the expected next stop — OK.
        if (confirmedIndex == timetableNextIndex) return false
        // Still dwelling at the previous stop — OK.
        if (confirmedIndex == timetableNextIndex - 1) return false
        // Behind timetable (earlier stop) — tolerate; not a diversion signal.
        if (confirmedIndex < timetableNextIndex - 1) return false
        // Ahead of / off the expected sequence.
        return true
    }

    private fun effectiveArrivalLabel(cp: CallingPoint): String? {
        val expected = cp.expectedLabel?.trim().orEmpty()
        if (expected.isNotEmpty() &&
            !expected.equals("On time", ignoreCase = true) &&
            !expected.equals("Delayed", ignoreCase = true) &&
            JourneyTimeUtils.parseTimeLabel(expected) != null
        ) {
            return expected
        }
        return cp.scheduledTimeLabel
    }
}
