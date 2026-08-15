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

    @Test
    fun countdownUsesExpectedArrivalAtDestination() {
        val progress = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = null,
            now = now,
        )
        assertEquals(35L, progress.minutesToDestination)
        assertFalse(progress.destinationArrived)
        assertEquals("DID", progress.destinationCrs)
    }

    @Test
    fun destinationActualMarksArrivedAndClearsCountdown() {
        val arrived = points.dropLast(1) + CallingPoint(
            locationName = "Didcot Parkway",
            crs = "DID",
            scheduledTimeLabel = "10:30",
            expectedLabel = "10:38", // echoed AT into expected in some payloads
            actualTimeLabel = "10:38",
        )
        val progress = ComputeJourneyProgress.compute(
            callingPoints = arrived,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "DID",
            now = now,
        )
        assertTrue(progress.destinationArrived)
        assertNull(progress.minutesToDestination)
    }

    @Test
    fun destinationActualOnTimePhraseMarksArrived() {
        // Darwin commonly publishes AT as "On time" rather than HH:mm.
        val arrived = points.dropLast(1) + CallingPoint(
            locationName = "London Euston",
            crs = "EUS",
            scheduledTimeLabel = "19:58",
            expectedLabel = "On time",
            actualTimeLabel = "On time",
        )
        val progress = ComputeJourneyProgress.compute(
            callingPoints = arrived,
            originCrs = "PAD",
            destinationCrs = "EUS",
            destinationNameFallback = "Euston",
            confirmedStopCrs = null,
            now = now,
        )
        assertTrue(progress.destinationArrived)
        assertNull(progress.minutesToDestination)
        assertEquals("On time", progress.points.last().actualLabel)
        assertEquals(0, progress.points.last().scheduleVarianceMinutes)
    }

    @Test
    fun onTimeExpectedUsesScheduledForCountdown() {
        val onTimeDest = points.dropLast(1) + CallingPoint(
            locationName = "Didcot Parkway",
            crs = "DID",
            scheduledTimeLabel = "10:30",
            expectedLabel = "On time",
        )
        val progress = ComputeJourneyProgress.compute(
            callingPoints = onTimeDest,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = null,
            now = now,
        )
        assertEquals(30L, progress.minutesToDestination)
        assertFalse(progress.destinationArrived)
    }

    @Test
    fun confirmedStopAdvancesCurrentAndNext() {
        val progress = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "SLO",
            now = now,
        )
        assertEquals("SLO", progress.currentStopCrs)
        assertEquals("RDG", progress.nextStopCrs)
        assertFalse(progress.routeMismatch)
        assertTrue(progress.points[1].isCurrent)
        assertTrue(progress.points[2].isNext)
    }

    @Test
    fun unexpectedStopFlagsMismatch() {
        // Timetable next at 10:00 is Slough (09:52 expected still in past? 09:52 is past)
        // Actually at 10:00, SLO expected 09:52 is past, so next is RDG (10:15).
        // Confirming DID early → ahead of expected → mismatch.
        val progress = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "DID",
            now = now,
        )
        assertTrue(progress.routeMismatch)
    }

    @Test
    fun arrivingAtExpectedNextIsNotMismatch() {
        val progress = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "RDG",
            now = now,
        )
        // Timetable next is RDG; confirming RDG is OK.
        assertFalse(progress.routeMismatch)
        assertEquals("RDG", progress.currentStopCrs)
        assertEquals("DID", progress.nextStopCrs)
        assertTrue(progress.approachingDestination)
    }

    @Test
    fun scheduleVarianceUsesActualWhenPresent() {
        val progress = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "SLO",
            now = now,
        )
        assertEquals("09:31", progress.points[0].actualLabel)
        assertEquals(1, progress.points[0].scheduleVarianceMinutes)
        assertEquals("09:53", progress.points[1].actualLabel)
        assertEquals(3, progress.points[1].scheduleVarianceMinutes)
        // Future stop: expected vs scheduled (no actual yet).
        assertEquals(null, progress.points[2].actualLabel)
        assertEquals(5, progress.points[2].scheduleVarianceMinutes)
    }

    @Test
    fun fractionIncreasesAlongRoute() {
        val atOrigin = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "PAD",
            now = now,
        )
        val mid = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "RDG",
            now = now,
        )
        assertTrue(mid.fractionComplete > atOrigin.fractionComplete)
    }
}
