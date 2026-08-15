package com.ukrailtracker.app.location

import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.model.CallingPoint
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.UserLocation
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.JourneyTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Public GPS-simulator phase for UI (app-bar icon / blink). */
enum class SimPhase {
    Idle,
    Walking,
    AtStation,
    OnTrain,
    ReadyDisembark,
    Disembarking,
}

/**
 * Debug-only multi-phase location simulator.
 *
 * Idle → Walking (approach) → AtStation → OnTrain (real `st` gaps + dwell) →
 * ReadyDisembark → Disembarking (300 m north) → Idle.
 */
class WalkUpLocationSimulator {

    private val _phase = MutableStateFlow(SimPhase.Idle)
    val phase: StateFlow<SimPhase> = _phase.asStateFlow()

    @Volatile
    private var engine: Engine? = null

    val isActive: Boolean get() = _phase.value != SimPhase.Idle

    fun startWalkUp(
        targetLatitude: Double,
        targetLongitude: Double,
        startOffsetMetres: Double = DEFAULT_START_OFFSET_METRES,
        durationMs: Long = DEFAULT_DURATION_MS,
        bearingDegrees: Double = DEFAULT_APPROACH_BEARING_DEGREES,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val start = offsetByMetres(
            latitude = targetLatitude,
            longitude = targetLongitude,
            distanceMetres = startOffsetMetres,
            bearingDegrees = bearingDegrees,
        )
        engine = Engine.Walking(
            startLatitude = start.first,
            startLongitude = start.second,
            targetLatitude = targetLatitude,
            targetLongitude = targetLongitude,
            startedAtEpochMs = nowEpochMs,
            durationMs = durationMs.coerceAtLeast(1L),
        )
        _phase.value = SimPhase.Walking
    }

    /**
     * Board a service: GPS follows calling-point legs using real timetable gaps from [ScheduleWaypoint.scheduleOffsetMs],
     * with [dwellMs] at each intermediate stop.
     */
    fun startOnTrain(
        waypoints: List<ScheduleWaypoint>,
        dwellMs: Long = DEFAULT_STOP_DWELL_MS,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        require(waypoints.size >= 2) { "Need at least origin and one further calling point" }
        val timeline = buildRealtimeTimeline(
            waypoints = waypoints,
            dwellMs = dwellMs.coerceAtLeast(0L),
        )
        val last = timeline.last()
        engine = Engine.OnTrain(
            points = timeline,
            startedAtEpochMs = nowEpochMs,
            finalLatitude = last.latitude,
            finalLongitude = last.longitude,
            finalCrs = last.crs,
        )
        _phase.value = SimPhase.OnTrain
    }

    fun startDisembark(
        durationMs: Long = DEFAULT_DISEMBARK_DURATION_MS,
        distanceMetres: Double = DEFAULT_DISEMBARK_METRES,
        bearingDegrees: Double = DEFAULT_DISEMBARK_BEARING_DEGREES,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val hold = when (val e = engine) {
            is Engine.ReadyDisembark -> e.latitude to e.longitude
            is Engine.OnTrain -> e.finalLatitude to e.finalLongitude
            is Engine.AtStation -> e.latitude to e.longitude
            else -> return
        }
        val end = offsetByMetres(
            latitude = hold.first,
            longitude = hold.second,
            distanceMetres = distanceMetres,
            bearingDegrees = bearingDegrees,
        )
        engine = Engine.Disembarking(
            startLatitude = hold.first,
            startLongitude = hold.second,
            endLatitude = end.first,
            endLongitude = end.second,
            startedAtEpochMs = nowEpochMs,
            durationMs = durationMs.coerceAtLeast(1L),
        )
        _phase.value = SimPhase.Disembarking
    }

    fun stop() {
        engine = null
        _phase.value = SimPhase.Idle
    }

    fun currentLocation(nowEpochMs: Long = System.currentTimeMillis()): UserLocation? {
        return when (val e = engine) {
            null -> null
            is Engine.Walking -> walkUpLocation(e, nowEpochMs)
            is Engine.AtStation -> holdLocation(e.latitude, e.longitude, nowEpochMs, REPORTED_STOP_SPEED_MPS)
            is Engine.OnTrain -> onTrainLocation(e, nowEpochMs)
            is Engine.ReadyDisembark -> holdLocation(
                e.latitude,
                e.longitude,
                nowEpochMs,
                REPORTED_STOP_SPEED_MPS,
            )
            is Engine.Disembarking -> disembarkLocation(e, nowEpochMs)
        }
    }

    private fun walkUpLocation(s: Engine.Walking, nowEpochMs: Long): UserLocation {
        val elapsed = (nowEpochMs - s.startedAtEpochMs).coerceAtLeast(0L)
        val t = (elapsed.toDouble() / s.durationMs.toDouble()).coerceIn(0.0, 1.0)
        if (t >= 1.0) {
            engine = Engine.AtStation(
                latitude = s.targetLatitude,
                longitude = s.targetLongitude,
            )
            _phase.value = SimPhase.AtStation
            return holdLocation(
                s.targetLatitude,
                s.targetLongitude,
                nowEpochMs,
                REPORTED_STOP_SPEED_MPS,
            )
        }
        val lat = s.startLatitude + (s.targetLatitude - s.startLatitude) * t
        val lng = s.startLongitude + (s.targetLongitude - s.startLongitude) * t
        return UserLocation(
            latitude = lat,
            longitude = lng,
            accuracyMetres = 8f,
            speedMetresPerSecond = REPORTED_WALK_SPEED_MPS,
            epochMs = nowEpochMs,
        )
    }

    private fun onTrainLocation(s: Engine.OnTrain, nowEpochMs: Long): UserLocation {
        val elapsed = (nowEpochMs - s.startedAtEpochMs).coerceAtLeast(0L)
        val points = s.points
        val last = points.last()
        if (elapsed >= last.offsetMs) {
            engine = Engine.ReadyDisembark(
                latitude = s.finalLatitude,
                longitude = s.finalLongitude,
                crs = s.finalCrs,
            )
            _phase.value = SimPhase.ReadyDisembark
            return holdLocation(
                s.finalLatitude,
                s.finalLongitude,
                nowEpochMs,
                REPORTED_STOP_SPEED_MPS,
            )
        }
        if (elapsed <= points.first().offsetMs) {
            val p = points.first()
            return holdLocation(p.latitude, p.longitude, nowEpochMs, REPORTED_STOP_SPEED_MPS)
        }
        val nextIdx = points.indexOfFirst { it.offsetMs >= elapsed }.coerceAtLeast(1)
        val prev = points[nextIdx - 1]
        val next = points[nextIdx]
        val span = (next.offsetMs - prev.offsetMs).coerceAtLeast(1L)
        val t = ((elapsed - prev.offsetMs).toDouble() / span.toDouble()).coerceIn(0.0, 1.0)
        // At the exact Arrive instant (t == 1) report stop speed so dwell detection can arm.
        val moving = prev.kind == TimelineKind.Depart &&
            next.kind == TimelineKind.Arrive &&
            t < 1.0
        return UserLocation(
            latitude = prev.latitude + (next.latitude - prev.latitude) * t,
            longitude = prev.longitude + (next.longitude - prev.longitude) * t,
            accuracyMetres = 12f,
            speedMetresPerSecond = if (moving) REPORTED_TRAIN_SPEED_MPS else REPORTED_STOP_SPEED_MPS,
            epochMs = nowEpochMs,
        )
    }

    private fun disembarkLocation(s: Engine.Disembarking, nowEpochMs: Long): UserLocation {
        val elapsed = (nowEpochMs - s.startedAtEpochMs).coerceAtLeast(0L)
        val t = (elapsed.toDouble() / s.durationMs.toDouble()).coerceIn(0.0, 1.0)
        val lat = s.startLatitude + (s.endLatitude - s.startLatitude) * t
        val lng = s.startLongitude + (s.endLongitude - s.startLongitude) * t
        if (t >= 1.0) {
            engine = null
            _phase.value = SimPhase.Idle
        }
        return UserLocation(
            latitude = lat,
            longitude = lng,
            accuracyMetres = 8f,
            speedMetresPerSecond = REPORTED_WALK_SPEED_MPS,
            epochMs = nowEpochMs,
        )
    }

    private fun holdLocation(
        latitude: Double,
        longitude: Double,
        nowEpochMs: Long,
        speedMps: Float,
    ): UserLocation = UserLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMetres = 12f,
        speedMetresPerSecond = speedMps,
        epochMs = nowEpochMs,
    )

    fun offsetByMetres(
        latitude: Double,
        longitude: Double,
        distanceMetres: Double,
        bearingDegrees: Double,
    ): Pair<Double, Double> {
        val angular = distanceMetres / EARTH_RADIUS_METRES
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = asin(
            sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing),
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }

    fun distanceFromTargetMetres(nowEpochMs: Long = System.currentTimeMillis()): Double? {
        val s = engine as? Engine.Walking ?: return null
        val loc = currentLocation(nowEpochMs) ?: return null
        // After auto-promote, engine may no longer be Walking.
        val targetLat = s.targetLatitude
        val targetLng = s.targetLongitude
        return GeoUtils.haversineMetres(
            loc.latitude,
            loc.longitude,
            targetLat,
            targetLng,
        )
    }

    private sealed class Engine {
        data class Walking(
            val startLatitude: Double,
            val startLongitude: Double,
            val targetLatitude: Double,
            val targetLongitude: Double,
            val startedAtEpochMs: Long,
            val durationMs: Long,
        ) : Engine()

        data class AtStation(
            val latitude: Double,
            val longitude: Double,
        ) : Engine()

        data class OnTrain(
            val points: List<TimelinePoint>,
            val startedAtEpochMs: Long,
            val finalLatitude: Double,
            val finalLongitude: Double,
            val finalCrs: String,
        ) : Engine()

        data class ReadyDisembark(
            val latitude: Double,
            val longitude: Double,
            val crs: String,
        ) : Engine()

        data class Disembarking(
            val startLatitude: Double,
            val startLongitude: Double,
            val endLatitude: Double,
            val endLongitude: Double,
            val startedAtEpochMs: Long,
            val durationMs: Long,
        ) : Engine()
    }

    data class ScheduleWaypoint(
        val crs: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        /** Milliseconds after origin departure on the real timetable. */
        val scheduleOffsetMs: Long,
    )

    enum class TimelineKind { Arrive, Depart }

    data class TimelinePoint(
        val crs: String,
        val latitude: Double,
        val longitude: Double,
        val offsetMs: Long,
        val kind: TimelineKind,
    )

    companion object {
        const val DEFAULT_TARGET_CRS = "APS"
        const val DEFAULT_START_OFFSET_METRES = 300.0
        const val DEFAULT_DURATION_MS = 120_000L
        const val DEFAULT_APPROACH_BEARING_DEGREES = 180.0
        /** Fixed dwell at intermediate stops (wall-clock). */
        const val DEFAULT_STOP_DWELL_MS = 60_000L
        const val DEFAULT_DISEMBARK_METRES = 300.0
        const val DEFAULT_DISEMBARK_DURATION_MS = 240_000L
        const val DEFAULT_DISEMBARK_BEARING_DEGREES = 0.0
        const val REPORTED_WALK_SPEED_MPS = 1.4f
        const val REPORTED_TRAIN_SPEED_MPS = 22f
        const val REPORTED_STOP_SPEED_MPS = 0.4f
        private const val EARTH_RADIUS_METRES = 6_371_000.0

        /**
         * Real-time timeline: travel between stops uses timetable `st` deltas;
         * intermediate stops add [dwellMs] before departure.
         */
        fun buildRealtimeTimeline(
            waypoints: List<ScheduleWaypoint>,
            dwellMs: Long,
        ): List<TimelinePoint> {
            require(waypoints.size >= 2)
            val sorted = waypoints.sortedBy { it.scheduleOffsetMs }
            val originOffset = sorted.first().scheduleOffsetMs
            val relative = sorted.map {
                it.copy(scheduleOffsetMs = (it.scheduleOffsetMs - originOffset).coerceAtLeast(0L))
            }
            val out = ArrayList<TimelinePoint>()
            relative.forEachIndexed { index, wp ->
                if (index == 0) {
                    out += TimelinePoint(wp.crs, wp.latitude, wp.longitude, 0L, TimelineKind.Arrive)
                    out += TimelinePoint(wp.crs, wp.latitude, wp.longitude, 0L, TimelineKind.Depart)
                } else {
                    val prev = relative[index - 1]
                    val travel = (wp.scheduleOffsetMs - prev.scheduleOffsetMs).coerceAtLeast(1L)
                    val departFromPrev = out.last().offsetMs
                    val arriveAt = departFromPrev + travel
                    out += TimelinePoint(wp.crs, wp.latitude, wp.longitude, arriveAt, TimelineKind.Arrive)
                    if (index < relative.lastIndex) {
                        out += TimelinePoint(
                            wp.crs,
                            wp.latitude,
                            wp.longitude,
                            arriveAt + dwellMs,
                            TimelineKind.Depart,
                        )
                    }
                }
            }
            return out
        }

        suspend fun waypointsFromOption(
            option: JourneyOption,
            stationRepository: StationRepository,
        ): List<ScheduleWaypoint> {
            val origin = option.originCrs.uppercase()
            val dest = option.destinationCrs.uppercase()
            val points = option.callingPoints.mapNotNull { it.toResolved(stationRepository) }
            if (points.isEmpty()) return emptyList()

            var originIdx = points.indexOfFirst { it.crs == origin }
            if (originIdx < 0) originIdx = 0
            var destIdx = points.indexOfLast { it.crs == dest }
            if (destIdx < originIdx) destIdx = points.lastIndex

            val slice = points.subList(originIdx, destIdx + 1)
            val originMinutes = slice.first().minutesFromMidnight
                ?: JourneyTimeUtils.parseTimeLabel(option.scheduledDepartureLabel)
                    ?.toSecondOfDay()?.div(60)
                ?: return emptyList()

            return slice.mapNotNull { p ->
                val mins = p.minutesFromMidnight ?: return@mapNotNull null
                var delta = mins - originMinutes
                if (delta < 0) delta += 24 * 60
                ScheduleWaypoint(
                    crs = p.crs,
                    name = p.name,
                    latitude = p.latitude,
                    longitude = p.longitude,
                    scheduleOffsetMs = delta * 60_000L,
                )
            }.distinctBy { it.crs }
                .sortedBy { it.scheduleOffsetMs }
        }

        private data class ResolvedPoint(
            val crs: String,
            val name: String,
            val latitude: Double,
            val longitude: Double,
            val minutesFromMidnight: Int?,
        )

        private suspend fun CallingPoint.toResolved(stationRepository: StationRepository): ResolvedPoint? {
            val crs = this.crs?.trim()?.uppercase().orEmpty()
            if (crs.isEmpty()) return null
            val station = stationRepository.getByCrs(crs) ?: return null
            val timeLabel = JourneyTimeUtils.effectiveDepartureLabel(
                scheduledDepartureLabel = scheduledTimeLabel.orEmpty(),
                expectedDepartureLabel = expectedLabel,
            ).ifBlank { scheduledTimeLabel.orEmpty() }
            val minutes = JourneyTimeUtils.parseTimeLabel(timeLabel)
                ?.toSecondOfDay()
                ?.div(60)
            return ResolvedPoint(
                crs = crs,
                name = station.name.ifBlank { locationName },
                latitude = station.latitude,
                longitude = station.longitude,
                minutesFromMidnight = minutes,
            )
        }
    }
}
