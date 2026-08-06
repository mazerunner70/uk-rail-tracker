package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectWalkUpArrivalTest {

    private val config = WalkUpConfig(
        geofenceMetres = 200.0,
        dwellMillis = 10_000L,
        lockHoldMetres = 350.0,
    )

    private val pad = station("PAD", "London Paddington", 51.515, -0.175)

    @Test
    fun coldStartAlreadyAtStationLocksImmediately() {
        val phase = next(
            phase = WalkUpPhase.Away,
            speed = 0.0,
            distance = 40.0,
            t = 0,
            alreadyPresentOk = true,
        )
        val arrived = phase as WalkUpPhase.Arrived
        assertEquals("PAD", arrived.crs)
    }

    @Test
    fun coldStartAtStationWithUnknownSpeedLocksImmediately() {
        val phase = next(
            phase = WalkUpPhase.Away,
            speed = null,
            distance = 90.0,
            t = 0,
            alreadyPresentOk = true,
        )
        assertTrue(phase is WalkUpPhase.Arrived)
    }

    @Test
    fun coldStartDoesNotLockAtVehicleSpeed() {
        val phase = next(
            phase = WalkUpPhase.Away,
            speed = 15.0,
            distance = 40.0,
            t = 0,
            alreadyPresentOk = true,
        )
        assertEquals(WalkUpPhase.Away, phase)
    }

    @Test
    fun walkAndDwellLocksArrival() {
        var phase: WalkUpPhase = WalkUpPhase.Away
        phase = next(phase, speed = 1.4, distance = 120.0, t = 0)
        assertTrue(phase is WalkUpPhase.Dwelling)

        phase = next(phase, speed = 0.1, distance = 80.0, t = 11_000)
        val arrived = phase as WalkUpPhase.Arrived
        assertEquals("PAD", arrived.crs)
    }

    @Test
    fun vehiclePassDoesNotCountAsWalkUp() {
        var phase: WalkUpPhase = WalkUpPhase.Away
        phase = next(phase, speed = 15.0, distance = 50.0, t = 0)
        assertEquals(WalkUpPhase.Away, phase)

        phase = next(phase, speed = 12.0, distance = 40.0, t = 5_000)
        assertEquals(WalkUpPhase.Away, phase)
    }

    @Test
    fun driveAndStopDoesNotPromoteWithoutWalking() {
        var phase: WalkUpPhase = WalkUpPhase.Away
        phase = next(phase, speed = 14.0, distance = 40.0, t = 0)
        assertEquals(WalkUpPhase.Away, phase)

        phase = next(phase, speed = 0.0, distance = 30.0, t = 2_000)
        assertEquals(WalkUpPhase.Away, phase)

        phase = next(phase, speed = 0.0, distance = 25.0, t = 20_000)
        assertEquals(WalkUpPhase.Away, phase)
    }

    @Test
    fun unknownSpeedCanPromoteAfterDwell() {
        var phase: WalkUpPhase = WalkUpPhase.Away
        phase = next(phase, speed = null, distance = 90.0, t = 0)
        assertTrue(phase is WalkUpPhase.Dwelling)

        phase = next(phase, speed = null, distance = 70.0, t = 12_000)
        assertTrue(phase is WalkUpPhase.Arrived)
    }

    @Test
    fun leavingLockHoldClearsArrival() {
        var phase: WalkUpPhase = WalkUpPhase.Away
        phase = next(phase, speed = 1.2, distance = 100.0, t = 0)
        phase = next(phase, speed = 0.2, distance = 80.0, t = 12_000)
        assertTrue(phase is WalkUpPhase.Arrived)

        phase = next(phase, speed = 1.0, distance = 400.0, t = 20_000)
        assertEquals(WalkUpPhase.Away, phase)
    }

    @Test
    fun resolveSpeedFallsBackToHaversine() {
        val prev = WalkUpSample(51.515, -0.175, null, 0)
        // ~111 m north in ~10 s → ~11.1 m/s
        val cur = WalkUpSample(51.516, -0.175, null, 10_000)
        val speed = DetectWalkUpArrival.resolveSpeed(cur, prev)
        assertTrue(speed != null && speed > 8.0)
    }

    private fun next(
        phase: WalkUpPhase,
        speed: Double?,
        distance: Double,
        t: Long,
        alreadyPresentOk: Boolean = false,
    ): WalkUpPhase {
        val nearest = StationWithDistance(pad, distance)
        val sample = WalkUpSample(
            latitude = pad.latitude,
            longitude = pad.longitude,
            speedMps = speed,
            epochMs = t,
        )
        return DetectWalkUpArrival.next(
            phase = phase,
            sample = sample,
            nearest = nearest,
            speedMps = speed,
            config = config,
            alreadyPresentOk = alreadyPresentOk,
        )
    }

    private fun station(crs: String, name: String, lat: Double, lng: Double): Station =
        Station(
            crsCode = crs,
            name = name,
            latitude = lat,
            longitude = lng,
            operatorName = "GWR",
            operatorCode = "GW",
        )
}
