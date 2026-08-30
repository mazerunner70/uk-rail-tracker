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
    fun predictedDurationUsesOriginActualAndDestExpected() {
        val progress = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "SLO",
            now = now,
        )
        // PAD actual 09:31 → DID expected 10:35.
        assertEquals(64, progress.predictedDurationMinutes)
    }

    @Test
    fun fractionCompleteIsElapsedOverPredictedDuration() {
        val early = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "SLO",
            now = LocalDateTime.of(2026, 8, 6, 10, 0),
        )
        val later = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "SLO",
            now = LocalDateTime.of(2026, 8, 6, 10, 8),
        )
        // 09:31 → 10:00 = 29 min / 64; 09:31 → 10:08 = 37 / 64.
        assertEquals(29f / 64f, early.fractionComplete, 0.002f)
        assertEquals(37f / 64f, later.fractionComplete, 0.002f)
        assertTrue(later.fractionComplete > early.fractionComplete)
    }

    @Test
    fun fractionAlongPlacesStopsOnPredictedAxis() {
        val progress = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "SLO",
            now = now,
        )
        assertEquals(0f, progress.points[0].fractionAlong!!, 0.001f)
        // SLO is current — expected 09:52, not actual. 09:31 → 09:52 = 21 / 64.
        assertEquals(21f / 64f, progress.points[1].fractionAlong!!, 0.002f)
        // RDG expected 10:15. 09:31 → 10:15 = 44 / 64.
        assertEquals(44f / 64f, progress.points[2].fractionAlong!!, 0.002f)
        assertEquals(1f, progress.points[3].fractionAlong!!, 0.001f)
    }

    @Test
    fun laterDestEtaLowersFill() {
        val slipped = points.dropLast(1) + CallingPoint(
            locationName = "Didcot Parkway",
            crs = "DID",
            scheduledTimeLabel = "10:30",
            expectedLabel = "10:45",
        )
        val onTime = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "SLO",
            now = now,
        )
        val delayed = ComputeJourneyProgress.compute(
            callingPoints = slipped,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "SLO",
            now = now,
        )
        assertEquals(74, delayed.predictedDurationMinutes)
        assertTrue(delayed.fractionComplete < onTime.fractionComplete)
        assertEquals(29f / 74f, delayed.fractionComplete, 0.002f)
    }

    @Test
    fun fractionStaysAtStopBeforeHopStart() {
        val waiting = ComputeJourneyProgress.compute(
            callingPoints = points,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "PAD",
            now = LocalDateTime.of(2026, 8, 6, 9, 20),
        )
        assertEquals(0f, waiting.fractionComplete, 0.001f)
        assertEquals(64, waiting.predictedDurationMinutes)
    }

    @Test
    fun fractionAtDestinationIsOne() {
        val arrived = points.dropLast(1) + CallingPoint(
            locationName = "Didcot Parkway",
            crs = "DID",
            scheduledTimeLabel = "10:30",
            expectedLabel = "10:35",
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
        assertEquals(1f, progress.fractionComplete, 0.001f)
        assertEquals(1f, progress.points.last().fractionAlong!!, 0.001f)
        // Dest AT is ignored for duration — still PAD 09:31 → DID ST 10:30.
        assertEquals(59, progress.predictedDurationMinutes)
    }

    @Test
    fun missingClocksLeaveDurationNullAndSkipAlong() {
        val noTimes = listOf(
            CallingPoint("Paddington", "PAD", null, null),
            CallingPoint("Didcot Parkway", "DID", null, null),
        )
        val progress = ComputeJourneyProgress.compute(
            callingPoints = noTimes,
            originCrs = "PAD",
            destinationCrs = "DID",
            destinationNameFallback = "Didcot",
            confirmedStopCrs = "PAD",
            now = now,
        )
        assertNull(progress.predictedDurationMinutes)
        assertTrue(progress.points.all { it.fractionAlong == null })
        assertEquals(0f, progress.fractionComplete, 0.001f)
    }
}
