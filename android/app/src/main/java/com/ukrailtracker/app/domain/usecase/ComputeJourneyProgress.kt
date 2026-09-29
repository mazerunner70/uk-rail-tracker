package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.model.CallingPoint
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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
    /** 0f at origin … 1f at destination on the spatial route axis. Null when coords missing. */
    val fractionAlong: Float? = null,
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
    /**
     * Predicted origin→destination duration in minutes (display only).
     * Null when clocks are missing.
     */
    val predictedDurationMinutes: Int?,
    /**
     * 0f at origin … 1f at / past destination. From GPS spatial math (session),
     * not Darwin clocks.
     */
    val journeyFraction: Float,
    /** True when [StopExpectation] is Unexpected — passed in from session. */
    val routeMismatch: Boolean,
    /** True when the next station on the trip is the favourite destination. */
    val approachingDestination: Boolean,
) {
    /** Alias for UI that still says fractionComplete. */
    val fractionComplete: Float get() = journeyFraction
}

object ComputeJourneyProgress {

    fun compute(
        callingPoints: List<CallingPoint>,
        originCrs: String,
        destinationCrs: String,
        destinationNameFallback: String,
        progressAnchorCrs: String?,
        atCallingPoint: Boolean,
        journeyFraction: Float,
        routeMismatch: Boolean,
        routeStations: List<RouteStation> = emptyList(),
        now: LocalDateTime = LocalDateTime.now(),
    ): JourneyProgress {
        val origin = originCrs.trim().uppercase()
        val dest = destinationCrs.trim().uppercase()
        val anchor = progressAnchorCrs?.trim()?.uppercase()

        val destIndex = callingPoints.indexOfLast { it.crs?.uppercase() == dest }
            .takeIf { it >= 0 }
            ?: (callingPoints.lastIndex.takeIf { callingPoints.isNotEmpty() } ?: -1)
        val originIndex = callingPoints.indexOfFirst { it.crs?.uppercase() == origin }
            .takeIf { it >= 0 }
            ?: 0

        val timetableNextIndex = inferExpectedNextIndex(
            callingPoints = callingPoints,
            originIndex = originIndex,
            destIndex = destIndex,
            confirmed = null,
            now = now,
        )

        val confirmedIndex = anchor?.let { crs ->
            callingPoints.indexOfFirst { it.crs?.uppercase() == crs }.takeIf { it >= 0 }
        }

        val currentIndex = when {
            atCallingPoint && confirmedIndex != null -> confirmedIndex
            confirmedIndex != null -> confirmedIndex
            timetableNextIndex > originIndex -> timetableNextIndex - 1
            else -> originIndex
        }

        val nextIndex = when {
            confirmedIndex != null && confirmedIndex < destIndex -> confirmedIndex + 1
            timetableNextIndex in 0..destIndex -> timetableNextIndex
            else -> null
        }

        val originPoint = callingPoints.getOrNull(originIndex)
        val destPoint = destIndex.takeIf { it >= 0 }?.let { callingPoints[it] }
        val originLabel = originPoint?.let { interpolationClockLabel(it, preferActual = true) }
        val destLabel = destPredictedClockLabel(destPoint)
        val durationSeconds = secondsBetweenLabels(originLabel, destLabel, now)
        val predictedDurationMinutes = durationSeconds
            ?.div(60L)
            ?.toInt()
            ?.takeIf { it > 0 }

        val spatialFractions = spatialFractionsAlongRoute(
            callingPoints = callingPoints,
            originIndex = originIndex,
            destIndex = destIndex,
            routeStations = routeStations,
        )

        val points = callingPoints.mapIndexed { index, cp ->
            val crs = cp.crs?.uppercase()
            val actualDisplay = JourneyTimeUtils.displayActualLabel(cp.actualTimeLabel)
            val observed = when {
                JourneyTimeUtils.parseableClockLabel(cp.actualTimeLabel) != null ->
                    JourneyTimeUtils.parseableClockLabel(cp.actualTimeLabel)
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
                fractionAlong = spatialFractions.getOrNull(index),
            )
        }

        val destinationArrived = JourneyTimeUtils.hasActualArrival(destPoint?.actualTimeLabel)
        val minutes = if (destinationArrived) {
            null
        } else {
            JourneyTimeUtils.minutesUntilExpectedArrival(
                scheduledArrivalLabel = destPoint?.scheduledTimeLabel,
                expectedArrivalLabel = destinationExpectedLabel(destPoint),
                now = now,
            )
        }

        val fraction = when {
            destinationArrived || (atCallingPoint && confirmedIndex != null && confirmedIndex >= destIndex) ->
                1f
            else -> journeyFraction.coerceIn(0f, 1f)
        }

        val nextPoint = nextIndex?.let { callingPoints.getOrNull(it) }
        val currentPoint = callingPoints.getOrNull(currentIndex)
        val timetableNextPoint = timetableNextIndex.takeIf { it >= 0 }
            ?.let { callingPoints.getOrNull(it) }
        val approaching = nextPoint?.crs?.uppercase() == dest

        return JourneyProgress(
            points = points,
            currentStopCrs = currentPoint?.crs?.uppercase() ?: anchor,
            currentStopName = currentPoint?.locationName,
            nextStopCrs = nextPoint?.crs?.uppercase(),
            nextStopName = nextPoint?.locationName,
            timetableNextStopName = timetableNextPoint?.locationName,
            destinationCrs = dest,
            destinationName = destPoint?.locationName ?: destinationNameFallback,
            minutesToDestination = minutes,
            destinationArrived = destinationArrived,
            predictedDurationMinutes = predictedDurationMinutes,
            journeyFraction = fraction,
            routeMismatch = routeMismatch,
            approachingDestination = approaching,
        )
    }

    /**
     * GPS journey fraction on the origin→destination spatial axis.
     * At a confirmed calling point: snap to that stop’s cumulative share.
     * En route: leg = progressAnchor → next calling point by route order;
     * `legFraction = dStart / (dStart + dEnd)`.
     */
    fun spatialJourneyFraction(
        latitude: Double,
        longitude: Double,
        progressAnchorCrs: String?,
        atCallingPointCrs: String?,
        originCrs: String,
        destinationCrs: String,
        callingPoints: List<CallingPoint>,
        routeStations: List<RouteStation>,
        previousFraction: Float,
    ): Float {
        val origin = originCrs.trim().uppercase()
        val dest = destinationCrs.trim().uppercase()
        val destIndex = callingPoints.indexOfLast { it.crs?.uppercase() == dest }
            .takeIf { it >= 0 }
            ?: return previousFraction.coerceIn(0f, 1f)
        val originIndex = callingPoints.indexOfFirst { it.crs?.uppercase() == origin }
            .takeIf { it >= 0 }
            ?: 0

        val fractions = spatialFractionsAlongRoute(
            callingPoints = callingPoints,
            originIndex = originIndex,
            destIndex = destIndex,
            routeStations = routeStations,
        )
        if (fractions.all { it == null }) {
            return indexFallbackFraction(
                callingPoints = callingPoints,
                originIndex = originIndex,
                destIndex = destIndex,
                progressAnchorCrs = progressAnchorCrs,
                atCallingPointCrs = atCallingPointCrs,
                previousFraction = previousFraction,
            )
        }

        val snapCrs = atCallingPointCrs?.trim()?.uppercase()
        if (snapCrs != null) {
            val idx = callingPoints.indexOfFirst { it.crs?.uppercase() == snapCrs }
            if (idx >= 0) {
                fractions[idx]?.let { return it.coerceIn(0f, 1f) }
                return indexShare(originIndex, destIndex, idx)
            }
        }

        val anchor = progressAnchorCrs?.trim()?.uppercase()
            ?: origin
        val anchorIndex = callingPoints.indexOfFirst { it.crs?.uppercase() == anchor }
            .takeIf { it >= 0 }
            ?: originIndex
        if (anchorIndex >= destIndex) {
            return 1f
        }
        val legEndIndex = (anchorIndex + 1..destIndex).firstOrNull { i ->
            callingPoints[i].crs != null &&
                routeStations.any { it.crs.equals(callingPoints[i].crs, ignoreCase = true) }
        } ?: (anchorIndex + 1).coerceAtMost(destIndex)

        val startStation = stationForCrs(callingPoints.getOrNull(anchorIndex)?.crs, routeStations)
            ?: stationForCrs(origin, routeStations)
        val endStation = stationForCrs(callingPoints.getOrNull(legEndIndex)?.crs, routeStations)
        if (startStation == null || endStation == null) {
            return fractions[anchorIndex] ?: previousFraction.coerceIn(0f, 1f)
        }

        val dStart = GeoUtils.haversineMetres(
            latitude, longitude, startStation.latitude, startStation.longitude,
        )
        val dEnd = GeoUtils.haversineMetres(
            latitude, longitude, endStation.latitude, endStation.longitude,
        )
        val denom = dStart + dEnd
        val legFraction = if (denom < 1.0) {
            if (dStart <= dEnd) 0f else 1f
        } else {
            (dStart / denom).toFloat().coerceIn(0f, 1f)
        }

        val startFrac = fractions[anchorIndex] ?: indexShare(originIndex, destIndex, anchorIndex)
        val endFrac = fractions[legEndIndex] ?: indexShare(originIndex, destIndex, legEndIndex)
        return (startFrac + legFraction * (endFrac - startFrac)).coerceIn(0f, 1f)
    }

    fun judgeStopExpectation(
        confirmedCrs: String,
        callingPoints: List<CallingPoint>,
        originCrs: String,
        destinationCrs: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): StopExpectation {
        if (callingPoints.isEmpty()) return StopExpectation.Unknown
        val origin = originCrs.trim().uppercase()
        val dest = destinationCrs.trim().uppercase()
        val confirmed = confirmedCrs.trim().uppercase()
        val destIndex = callingPoints.indexOfLast { it.crs?.uppercase() == dest }
            .takeIf { it >= 0 }
            ?: return StopExpectation.Unknown
        val originIndex = callingPoints.indexOfFirst { it.crs?.uppercase() == origin }
            .takeIf { it >= 0 }
            ?: 0
        val confirmedIndex = callingPoints.indexOfFirst { it.crs?.uppercase() == confirmed }
            .takeIf { it >= 0 }
            ?: return StopExpectation.Unexpected(timetableNextCrs = null)

        val timetableNextIndex = inferExpectedNextIndex(
            callingPoints = callingPoints,
            originIndex = originIndex,
            destIndex = destIndex,
            confirmed = null,
            now = now,
        )
        if (timetableNextIndex < 0) return StopExpectation.Unknown
        val timetableNextCrs = callingPoints.getOrNull(timetableNextIndex)?.crs?.uppercase()

        return when {
            confirmedIndex == timetableNextIndex -> StopExpectation.MatchesTimetableNext
            confirmedIndex == timetableNextIndex - 1 -> StopExpectation.StillAtPriorStop
            confirmedIndex < timetableNextIndex - 1 -> StopExpectation.BehindTimetable
            else -> StopExpectation.Unexpected(timetableNextCrs)
        }
    }

    private fun spatialFractionsAlongRoute(
        callingPoints: List<CallingPoint>,
        originIndex: Int,
        destIndex: Int,
        routeStations: List<RouteStation>,
    ): List<Float?> {
        if (callingPoints.isEmpty() || destIndex < originIndex) {
            return List(callingPoints.size) { null }
        }
        val coords = callingPoints.map { cp ->
            stationForCrs(cp.crs, routeStations)
        }
        if ((originIndex..destIndex).none { coords[it] != null }) {
            return List(callingPoints.size) { null }
        }
        val cumulative = DoubleArray(callingPoints.size) { 0.0 }
        var lastLat: Double? = null
        var lastLng: Double? = null
        var travelled = 0.0
        for (i in originIndex..destIndex) {
            val st = coords[i]
            if (st != null) {
                if (lastLat != null && lastLng != null) {
                    travelled += GeoUtils.haversineMetres(lastLat, lastLng, st.latitude, st.longitude)
                }
                lastLat = st.latitude
                lastLng = st.longitude
            }
            cumulative[i] = travelled
        }
        val total = cumulative[destIndex]
        if (total <= 0.0) {
            return callingPoints.indices.map { i ->
                when {
                    i < originIndex || i > destIndex -> null
                    i == originIndex -> 0f
                    i == destIndex -> 1f
                    else -> indexShare(originIndex, destIndex, i)
                }
            }
        }
        return callingPoints.indices.map { i ->
            when {
                i < originIndex || i > destIndex -> null
                i == originIndex -> 0f
                i == destIndex -> 1f
                coords[i] == null -> null
                else -> (cumulative[i] / total).toFloat().coerceIn(0f, 1f)
            }
        }
    }

    private fun indexFallbackFraction(
        callingPoints: List<CallingPoint>,
        originIndex: Int,
        destIndex: Int,
        progressAnchorCrs: String?,
        atCallingPointCrs: String?,
        previousFraction: Float,
    ): Float {
        val snap = atCallingPointCrs ?: progressAnchorCrs ?: return previousFraction.coerceIn(0f, 1f)
        val idx = callingPoints.indexOfFirst { it.crs.equals(snap, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: return previousFraction.coerceIn(0f, 1f)
        return indexShare(originIndex, destIndex, idx)
    }

    private fun indexShare(originIndex: Int, destIndex: Int, index: Int): Float {
        val span = (destIndex - originIndex).coerceAtLeast(1)
        return ((index - originIndex).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    private fun stationForCrs(crs: String?, routeStations: List<RouteStation>): RouteStation? {
        val key = crs?.trim()?.uppercase() ?: return null
        return routeStations.firstOrNull { it.crs.equals(key, ignoreCase = true) }
    }

    private fun destinationExpectedLabel(destPoint: CallingPoint?): String? {
        if (destPoint == null) return null
        if (JourneyTimeUtils.hasActualArrival(destPoint.actualTimeLabel)) return null
        return destPoint.expectedLabel
    }

    private fun destPredictedClockLabel(destPoint: CallingPoint?): String? {
        if (destPoint == null) return null
        if (JourneyTimeUtils.hasActualArrival(destPoint.actualTimeLabel)) {
            return JourneyTimeUtils.parseableClockLabel(destPoint.scheduledTimeLabel)
        }
        return JourneyTimeUtils.parseableClockLabel(destPoint.expectedLabel)
            ?: JourneyTimeUtils.parseableClockLabel(destPoint.scheduledTimeLabel)
    }

    private fun interpolationClockLabel(cp: CallingPoint, preferActual: Boolean): String? {
        if (preferActual && JourneyTimeUtils.hasActualArrival(cp.actualTimeLabel)) {
            JourneyTimeUtils.parseableClockLabel(cp.actualTimeLabel)?.let { return it }
            JourneyTimeUtils.parseableClockLabel(cp.scheduledTimeLabel)?.let { return it }
        }
        return JourneyTimeUtils.parseableClockLabel(cp.expectedLabel)
            ?: JourneyTimeUtils.parseableClockLabel(cp.scheduledTimeLabel)
    }

    private fun secondsBetweenLabels(
        fromLabel: String?,
        toLabel: String?,
        now: LocalDateTime,
    ): Long? {
        val fromUntil = secondsUntilLabel(fromLabel, now) ?: return null
        val toUntil = secondsUntilLabel(toLabel, now) ?: return null
        return (toUntil - fromUntil).takeIf { it > 0L }
    }

    private fun secondsUntilLabel(label: String?, now: LocalDateTime): Long? {
        val time = JourneyTimeUtils.parseTimeLabel(label) ?: return null
        val today = LocalDateTime.of(now.toLocalDate(), time)
        val candidates = listOf(today.minusDays(1), today, today.plusDays(1))
        val nearest = candidates.minBy { kotlin.math.abs(ChronoUnit.SECONDS.between(now, it)) }
        return ChronoUnit.SECONDS.between(now, nearest)
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
