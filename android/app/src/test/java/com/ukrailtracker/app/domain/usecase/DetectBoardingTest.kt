package com.ukrailtracker.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectBoardingTest {

    private val config = BoardingConfig(
        leaveMetres = 400.0,
        boardMinMps = 5.0,
        sustainMillis = 8_000L,
    )

    // Paddington
    private val originLat = 51.515
    private val originLng = -0.175

    @Test
    fun stillInsideFootprintDoesNotTrigger() {
        var phase: BoardingPhase = BoardingPhase.Idle
        // ~111 m north, train speed
        phase = next(phase, lat = 51.516, speed = 12.0, t = 0)
        assertEquals(BoardingPhase.Idle, phase)

        phase = next(phase, lat = 51.516, speed = 12.0, t = 10_000)
        assertEquals(BoardingPhase.Idle, phase)
    }

    @Test
    fun walkAwayBeyondLeaveDoesNotTrigger() {
        var phase: BoardingPhase = BoardingPhase.Idle
        // ~444 m north at walking pace
        phase = next(phase, lat = 51.519, speed = 1.4, t = 0)
        assertEquals(BoardingPhase.Idle, phase)

        phase = next(phase, lat = 51.519, speed = 1.2, t = 10_000)
        assertEquals(BoardingPhase.Idle, phase)
    }

    @Test
    fun trainSpeedBeyondLeaveSustainsThenTriggers() {
        var phase: BoardingPhase = BoardingPhase.Idle
        phase = next(phase, lat = 51.519, speed = 12.0, t = 0)
        assertTrue(phase is BoardingPhase.Sustaining)

        phase = next(phase, lat = 51.520, speed = 14.0, t = 4_000)
        assertTrue(phase is BoardingPhase.Sustaining)

        phase = next(phase, lat = 51.521, speed = 15.0, t = 8_000)
        val triggered = phase as BoardingPhase.Triggered
        assertEquals(8_000L, triggered.triggeredAtEpochMs)
    }

    @Test
    fun brokenSustainResetsToIdle() {
        var phase: BoardingPhase = BoardingPhase.Idle
        phase = next(phase, lat = 51.519, speed = 12.0, t = 0)
        assertTrue(phase is BoardingPhase.Sustaining)

        // Drop to walk pace — cancel sustain
        phase = next(phase, lat = 51.519, speed = 1.0, t = 3_000)
        assertEquals(BoardingPhase.Idle, phase)
    }

    @Test
    fun unknownSpeedDoesNotQualify() {
        val phase = next(BoardingPhase.Idle, lat = 51.519, speed = null, t = 0)
        assertEquals(BoardingPhase.Idle, phase)
    }

    @Test
    fun triggeredSticks() {
        var phase: BoardingPhase = BoardingPhase.Triggered(
            triggeredAtEpochMs = 8_000L,
            distanceMetres = 500.0,
            speedMps = 12.0,
        )
        phase = next(phase, lat = 51.522, speed = 1.0, t = 20_000)
        assertTrue(phase is BoardingPhase.Triggered)
        assertEquals(8_000L, (phase as BoardingPhase.Triggered).triggeredAtEpochMs)
    }

    private fun next(
        phase: BoardingPhase,
        lat: Double,
        speed: Double?,
        t: Long,
    ): BoardingPhase {
        val sample = WalkUpSample(
            latitude = lat,
            longitude = originLng,
            speedMps = speed,
            epochMs = t,
        )
        return DetectBoarding.next(
            phase = phase,
            sample = sample,
            originLatitude = originLat,
            originLongitude = originLng,
            speedMps = speed,
            config = config,
        )
    }
}
