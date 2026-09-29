package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.CallingPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ComputeJourneyProgressTest {

    private val now = LocalDateTime.of(2026, 8, 6, 10, 0)

    private val points = listOf(
        CallingPoint("Paddington", "PAD", "09:30", "On time", actualTimeLabel = "09:31"),
        CallingPoint("Slough", "SLO", "09:50", "09:52", actualTimeLabel = "09:53"),
        CallingPoint("Reading", "RDG", "10:10", "10:15"),
        CallingPoint("Didcot Parkway", "DID", "10:30", "10:35"),
    )

    private val routeStations = listOf(
        RouteStation("PAD", "Paddington", 51.515, -0.175),
        RouteStation("SLO", "Slough", 51.512, -0.592),
        RouteStation("RDG", "Reading", 51.458, -0.972),
        RouteStation("DID", "Didcot Parkway", 51.611, -1.243),
    )

    private fun progress(
        confirmed: String? = null,
        atCallingPoint: Boolean = confirmed != null,
        journeyFraction: Float = 0f,
        routeMismatch: Boolean = false,
        callingPoints: List<CallingPoint> = points,
        stations: List<RouteStation> = routeStations,
        clock: LocalDateTime = now,
    ) = ComputeJourneyProgress.compute(
        callingPoints = callingPoints,
        originCrs = "PAD",
        destinationCrs = callingPoints.last().crs ?: "DID",
        destinationNameFallback = "Didcot",
        progressAnchorCrs = confirmed,
        atCallingPoint = atCallingPoint,
        journeyFraction = journeyFraction,
        routeMismatch = routeMismatch,
        routeStations = stations,
        now = clock,
    )

    @Test
    fun countdownUsesExpectedArrivalAtDestination() {
        val result = progress()
        assertEquals(35L, result.minutesToDestination)
        assertFalse(result.destinationArrived)
        assertEquals("DID", result.destinationCrs)
    }

    @Test
    fun destinationActualMarksArrivedAndClearsCountdown() {
        val arrived = points.dropLast(1) + CallingPoint(
            locationName = "Didcot Parkway",
            crs = "DID",
            scheduledTimeLabel = "10:30",
            expectedLabel = "10:38",
            actualTimeLabel = "10:38",
        )
        val result = progress(confirmed = "DID", callingPoints = arrived, journeyFraction = 1f)
        assertTrue(result.destinationArrived)
        assertNull(result.minutesToDestination)
    }

    @Test
    fun destinationActualOnTimePhraseMarksArrived() {
        val arrived = points.dropLast(1) + CallingPoint(
            locationName = "London Euston",
            crs = "EUS",
            scheduledTimeLabel = "19:58",
            expectedLabel = "On time",
            actualTimeLabel = "On time",
        )
        val result = progress(
            confirmed = null,
            atCallingPoint = false,
            callingPoints = arrived,
            stations = emptyList(),
        )
        assertTrue(result.destinationArrived)
        assertNull(result.minutesToDestination)
        assertEquals("On time", result.points.last().actualLabel)
        assertEquals(0, result.points.last().scheduleVarianceMinutes)
    }

    @Test
    fun onTimeExpectedUsesScheduledForCountdown() {
        val onTimeDest = points.dropLast(1) + CallingPoint(
            locationName = "Didcot Parkway",
            crs = "DID",
            scheduledTimeLabel = "10:30",
            expectedLabel = "On time",
        )
        val result = progress(callingPoints = onTimeDest)
        assertEquals(30L, result.minutesToDestination)
        assertFalse(result.destinationArrived)
    }

    @Test
    fun confirmedStopAdvancesCurrentAndNext() {
        val result = progress(confirmed = "SLO", journeyFraction = 0.3f)
        assertEquals("SLO", result.currentStopCrs)
        assertEquals("RDG", result.nextStopCrs)
        assertTrue(result.points[1].isCurrent)
        assertTrue(result.points[2].isNext)
    }

    @Test
    fun judgeUnexpectedStop() {
        val expectation = ComputeJourneyProgress.judgeStopExpectation(
            confirmedCrs = "DID",
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            now = now,
        )
        assertTrue(expectation.isUnexpected())
    }

    @Test
    fun judgeArrivingAtExpectedNext() {
        val expectation = ComputeJourneyProgress.judgeStopExpectation(
            confirmedCrs = "RDG",
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            now = now,
        )
        assertEquals(StopExpectation.MatchesTimetableNext, expectation)
        val result = progress(confirmed = "RDG", journeyFraction = 0.7f)
        assertEquals("RDG", result.currentStopCrs)
        assertEquals("DID", result.nextStopCrs)
        assertTrue(result.approachingDestination)
    }

    @Test
    fun scheduleVarianceUsesActualWhenPresent() {
        val result = progress(confirmed = "SLO")
        assertEquals("09:31", result.points[0].actualLabel)
        assertEquals(1, result.points[0].scheduleVarianceMinutes)
        assertEquals("09:53", result.points[1].actualLabel)
        assertEquals(3, result.points[1].scheduleVarianceMinutes)
        assertEquals(null, result.points[2].actualLabel)
        assertEquals(5, result.points[2].scheduleVarianceMinutes)
    }

    @Test
    fun predictedDurationUsesOriginActualAndDestExpected() {
        val result = progress(confirmed = "SLO")
        assertEquals(64, result.predictedDurationMinutes)
    }

    @Test
    fun journeyFractionPassthroughFromSession() {
        val result = progress(confirmed = "SLO", journeyFraction = 0.42f)
        assertEquals(0.42f, result.journeyFraction, 0.001f)
        assertEquals(0.42f, result.fractionComplete, 0.001f)
    }

    @Test
    fun spatialFractionSnapsAtCallingPoint() {
        val atSlough = ComputeJourneyProgress.spatialJourneyFraction(
            latitude = 51.512,
            longitude = -0.592,
            progressAnchorCrs = "SLO",
            atCallingPointCrs = "SLO",
            originCrs = "PAD",
            destinationCrs = "DID",
            callingPoints = points,
            routeStations = routeStations,
            previousFraction = 0f,
        )
        val midLeg = ComputeJourneyProgress.spatialJourneyFraction(
            latitude = 51.485,
            longitude = -0.782,
            progressAnchorCrs = "SLO",
            atCallingPointCrs = null,
            originCrs = "PAD",
            destinationCrs = "DID",
            callingPoints = points,
            routeStations = routeStations,
            previousFraction = atSlough,
        )
        assertTrue(atSlough in 0.15f..0.55f)
        assertTrue(midLeg > atSlough)
        assertTrue(midLeg < 1f)
    }

    @Test
    fun spatialFractionAtOriginIsZero() {
        val frac = ComputeJourneyProgress.spatialJourneyFraction(
            latitude = 51.515,
            longitude = -0.175,
            progressAnchorCrs = "PAD",
            atCallingPointCrs = "PAD",
            originCrs = "PAD",
            destinationCrs = "DID",
            callingPoints = points,
            routeStations = routeStations,
            previousFraction = 0f,
        )
        assertEquals(0f, frac, 0.001f)
    }

    @Test
    fun spatialFractionAtDestinationIsOne() {
        val frac = ComputeJourneyProgress.spatialJourneyFraction(
            latitude = 51.611,
            longitude = -1.243,
            progressAnchorCrs = "DID",
            atCallingPointCrs = "DID",
            originCrs = "PAD",
            destinationCrs = "DID",
            callingPoints = points,
            routeStations = routeStations,
            previousFraction = 0.9f,
        )
        assertEquals(1f, frac, 0.001f)
    }

    @Test
    fun fractionAlongUsesSpatialAxis() {
        val result = progress(confirmed = "SLO", journeyFraction = 0.3f)
        assertEquals(0f, result.points[0].fractionAlong!!, 0.001f)
        assertEquals(1f, result.points[3].fractionAlong!!, 0.001f)
        assertTrue(result.points[1].fractionAlong!! > 0f)
        assertTrue(result.points[2].fractionAlong!! > result.points[1].fractionAlong!!)
    }

    @Test
    fun fractionAtDestinationForcesOne() {
        val arrived = points.dropLast(1) + CallingPoint(
            locationName = "Didcot Parkway",
            crs = "DID",
            scheduledTimeLabel = "10:30",
            expectedLabel = "10:35",
            actualTimeLabel = "10:38",
        )
        val result = progress(
            confirmed = "DID",
            journeyFraction = 0.5f,
            callingPoints = arrived,
        )
        assertEquals(1f, result.journeyFraction, 0.001f)
        assertEquals(59, result.predictedDurationMinutes)
    }

    @Test
    fun missingCoordsFallsBackToIndexShare() {
        val frac = ComputeJourneyProgress.spatialJourneyFraction(
            latitude = 51.512,
            longitude = -0.592,
            progressAnchorCrs = "SLO",
            atCallingPointCrs = "SLO",
            originCrs = "PAD",
            destinationCrs = "DID",
            callingPoints = points,
            routeStations = emptyList(),
            previousFraction = 0f,
        )
        // PAD=0, SLO=1, RDG=2, DID=3 → 1/3
        assertEquals(1f / 3f, frac, 0.001f)
    }

    @Test
    fun missingClocksLeaveDurationNull() {
        val noTimes = listOf(
            CallingPoint("Paddington", "PAD", null, null),
            CallingPoint("Didcot Parkway", "DID", null, null),
        )
        val result = progress(
            confirmed = "PAD",
            callingPoints = noTimes,
            stations = emptyList(),
        )
        assertNull(result.predictedDurationMinutes)
    }
}
