package com.ukrailtracker.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectStationStopTest {

    private val config = StationStopConfig(
        matchMetres = 250.0,
        stopMaxMps = 1.5,
        dwellMillis = 15_000L,
    )

    // Rough coords for test stations
    private val reading = RouteStation("RDG", "Reading", 51.458, -0.972)
    private val slough = RouteStation("SLO", "Slough", 51.512, -0.592)
    private val route = listOf(reading, slough)

    @Test
    fun movingAtSpeedStaysMoving() {
        val phase = DetectStationStop.next(
            phase = StationStopPhase.Moving,
            sample = sample(lat = 51.458, lng = -0.972, speed = 20.0, t = 0),
            speedMps = 20.0,
            routeStations = route,
            config = config,
        )
        assertEquals(StationStopPhase.Moving, phase)
    }

    @Test
    fun stoppedNearStationStartsDwelling() {
        val phase = DetectStationStop.next(
            phase = StationStopPhase.Moving,
            sample = sample(lat = 51.458, lng = -0.972, speed = 0.5, t = 0),
            speedMps = 0.5,
            routeStations = route,
            config = config,
        )
        val dwelling = phase as StationStopPhase.Dwelling
        assertEquals("RDG", dwelling.crs)
    }

    @Test
    fun dwellThenConfirm() {
        var phase: StationStopPhase = StationStopPhase.Moving
        phase = DetectStationStop.next(
            phase = phase,
            sample = sample(lat = 51.458, lng = -0.972, speed = 0.4, t = 0),
            speedMps = 0.4,
            routeStations = route,
            config = config,
        )
        assertTrue(phase is StationStopPhase.Dwelling)

        phase = DetectStationStop.next(
            phase = phase,
            sample = sample(lat = 51.4581, lng = -0.972, speed = 0.3, t = 16_000),
            speedMps = 0.3,
            routeStations = route,
            config = config,
        )
        val confirmed = phase as StationStopPhase.Confirmed
        assertEquals("RDG", confirmed.crs)
    }

    @Test
    fun leaveConfirmResetsToMoving() {
        var phase: StationStopPhase = StationStopPhase.Confirmed(
            crs = "RDG",
            name = "Reading",
            confirmedAtEpochMs = 16_000L,
            distanceMetres = 40.0,
        )
        phase = DetectStationStop.next(
            phase = phase,
            sample = sample(lat = 51.48, lng = -0.85, speed = 18.0, t = 30_000),
            speedMps = 18.0,
            routeStations = route,
            config = config,
        )
        assertEquals(StationStopPhase.Moving, phase)
    }

    @Test
    fun farFromRouteStationsDoesNotDwell() {
        // Far from both Reading and Slough
        val phase = DetectStationStop.next(
            phase = StationStopPhase.Moving,
            sample = sample(lat = 52.0, lng = -1.0, speed = 0.2, t = 0),
            speedMps = 0.2,
            routeStations = route,
            config = config,
        )
        assertEquals(StationStopPhase.Moving, phase)
    }

    private fun sample(lat: Double, lng: Double, speed: Double?, t: Long) =
        WalkUpSample(
            latitude = lat,
            longitude = lng,
            speedMps = speed,
            epochMs = t,
        )
}
