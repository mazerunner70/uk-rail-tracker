package com.ukrailtracker.app.location

import com.ukrailtracker.app.domain.geo.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkUpLocationSimulatorTest {

    private val sim = WalkUpLocationSimulator()

    @Test
    fun `walk starts about 300m from target and promotes to AtStation`() {
        val targetLat = 51.73252418
        val targetLng = -0.462924369
        val startMs = 1_000_000L
        sim.startWalkUp(
            targetLatitude = targetLat,
            targetLongitude = targetLng,
            startOffsetMetres = 300.0,
            durationMs = 120_000L,
            nowEpochMs = startMs,
        )
        assertEquals(SimPhase.Walking, sim.phase.value)

        val start = sim.currentLocation(startMs)!!
        val startDist = GeoUtils.haversineMetres(
            start.latitude, start.longitude, targetLat, targetLng,
        )
        assertEquals(300.0, startDist, 5.0)

        val mid = sim.currentLocation(startMs + 60_000L)!!
        val midDist = GeoUtils.haversineMetres(
            mid.latitude, mid.longitude, targetLat, targetLng,
        )
        assertEquals(150.0, midDist, 5.0)

        val end = sim.currentLocation(startMs + 120_000L)!!
        val endDist = GeoUtils.haversineMetres(
            end.latitude, end.longitude, targetLat, targetLng,
        )
        assertTrue(endDist < 1.0)
        assertEquals(SimPhase.AtStation, sim.phase.value)
        assertEquals(
            WalkUpLocationSimulator.REPORTED_STOP_SPEED_MPS,
            end.speedMetresPerSecond!!,
            0.01f,
        )

        // Hold at station thereafter.
        val held = sim.currentLocation(startMs + 200_000L)!!
        assertEquals(targetLat, held.latitude, 0.0001)
        assertEquals(targetLng, held.longitude, 0.0001)
    }

    @Test
    fun `on-train uses real st gaps plus one-minute dwells`() {
        val startMs = 2_000_000L
        val waypoints = listOf(
            WalkUpLocationSimulator.ScheduleWaypoint(
                crs = "APS",
                name = "Apsley",
                latitude = 51.73,
                longitude = -0.46,
                scheduleOffsetMs = 0L,
            ),
            WalkUpLocationSimulator.ScheduleWaypoint(
                crs = "WAT",
                name = "Watford",
                latitude = 51.65,
                longitude = -0.40,
                scheduleOffsetMs = 10 * 60_000L,
            ),
            WalkUpLocationSimulator.ScheduleWaypoint(
                crs = "EUS",
                name = "Euston",
                latitude = 51.53,
                longitude = -0.13,
                scheduleOffsetMs = 40 * 60_000L,
            ),
        )
        sim.startOnTrain(
            waypoints = waypoints,
            dwellMs = 60_000L,
            nowEpochMs = startMs,
        )
        assertEquals(SimPhase.OnTrain, sim.phase.value)

        // Midpoint of first leg (10 min): 5 min in.
        val midFirst = sim.currentLocation(startMs + 5 * 60_000L)!!
        assertEquals(51.69, midFirst.latitude, 0.02)
        assertEquals(
            WalkUpLocationSimulator.REPORTED_TRAIN_SPEED_MPS,
            midFirst.speedMetresPerSecond!!,
            0.01f,
        )

        // Arrive WAT at 10 min.
        val atWat = sim.currentLocation(startMs + 10 * 60_000L)!!
        assertEquals(51.65, atWat.latitude, 0.01)
        assertEquals(-0.40, atWat.longitude, 0.01)
        assertEquals(
            WalkUpLocationSimulator.REPORTED_STOP_SPEED_MPS,
            atWat.speedMetresPerSecond!!,
            0.01f,
        )

        // Still dwelling at 10.5 min.
        val dwelling = sim.currentLocation(startMs + 10 * 60_000L + 30_000L)!!
        assertEquals(51.65, dwelling.latitude, 0.01)

        // Final arrive: 10 min + 1 min dwell + 30 min = 41 min.
        val atEus = sim.currentLocation(startMs + 41 * 60_000L)!!
        assertEquals(51.53, atEus.latitude, 0.01)
        assertEquals(-0.13, atEus.longitude, 0.01)
        assertEquals(SimPhase.ReadyDisembark, sim.phase.value)
    }

    @Test
    fun `realtime timeline preserves st deltas and dwells`() {
        val waypoints = listOf(
            WalkUpLocationSimulator.ScheduleWaypoint("A", "A", 0.0, 0.0, 0L),
            WalkUpLocationSimulator.ScheduleWaypoint("B", "B", 1.0, 1.0, 10 * 60_000L),
            WalkUpLocationSimulator.ScheduleWaypoint("C", "C", 2.0, 2.0, 30 * 60_000L),
        )
        val timeline = WalkUpLocationSimulator.buildRealtimeTimeline(
            waypoints = waypoints,
            dwellMs = 60_000L,
        )
        // A arrive/depart 0; B arrive 10m, depart 11m; C arrive 11m+20m = 31m
        assertEquals(0L, timeline[0].offsetMs)
        assertEquals(0L, timeline[1].offsetMs)
        assertEquals(10 * 60_000L, timeline[2].offsetMs)
        assertEquals(11 * 60_000L, timeline[3].offsetMs)
        assertEquals(31 * 60_000L, timeline[4].offsetMs)
    }

    @Test
    fun `disembark walks 300m north then returns to Idle`() {
        val startMs = 3_000_000L
        sim.startWalkUp(
            targetLatitude = 51.73,
            targetLongitude = -0.46,
            durationMs = 1_000L,
            nowEpochMs = startMs,
        )
        sim.currentLocation(startMs + 1_000L) // → AtStation
        assertEquals(SimPhase.AtStation, sim.phase.value)

        // Force ReadyDisembark via a tiny on-train trip.
        sim.startOnTrain(
            waypoints = listOf(
                WalkUpLocationSimulator.ScheduleWaypoint("A", "A", 51.73, -0.46, 0L),
                WalkUpLocationSimulator.ScheduleWaypoint("B", "B", 51.74, -0.46, 60_000L),
            ),
            dwellMs = 0L,
            nowEpochMs = startMs + 2_000L,
        )
        sim.currentLocation(startMs + 2_000L + 60_000L)
        assertEquals(SimPhase.ReadyDisembark, sim.phase.value)

        val stationLat = 51.74
        val stationLng = -0.46
        sim.startDisembark(
            durationMs = 240_000L,
            distanceMetres = 300.0,
            bearingDegrees = 0.0,
            nowEpochMs = startMs + 100_000L,
        )
        assertEquals(SimPhase.Disembarking, sim.phase.value)

        val start = sim.currentLocation(startMs + 100_000L)!!
        assertEquals(stationLat, start.latitude, 0.001)

        val end = sim.currentLocation(startMs + 100_000L + 240_000L)!!
        val northDist = GeoUtils.haversineMetres(
            stationLat, stationLng, end.latitude, end.longitude,
        )
        assertEquals(300.0, northDist, 5.0)
        assertTrue(end.latitude > stationLat)
        assertEquals(SimPhase.Idle, sim.phase.value)
        assertNull(sim.currentLocation(startMs + 100_000L + 240_001L))
    }
}
