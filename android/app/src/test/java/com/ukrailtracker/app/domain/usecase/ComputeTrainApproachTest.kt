package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.CallingPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ComputeTrainApproachTest {

    private val now = LocalDateTime.of(2026, 8, 16, 8, 0)

    @Test
    fun originFirstOnRouteWhenNoPriorStops() {
        val points = listOf(
            cp("Apsley", "APS", "08:10", "On time"),
            cp("London Euston", "EUS", "08:45", "On time"),
        )
        val state = ComputeTrainApproach.compute(
            callingPoints = points,
            originCrs = "APS",
            destinationCrs = "EUS",
            now = now,
        )
        assertTrue(state.originIsFirstOnRoute)
        assertNull(state.approachingFromStopName)
        assertEquals(10L, state.minutesUntilOriginArrival)
    }

    @Test
    fun intermediateOriginApproachingFromEarlierStop() {
        val points = listOf(
            cp("Paddington", "PAD", "08:00", "On time"),
            cp("Reading", "RDG", "08:30", "On time"),
            cp("Didcot", "DID", "08:45", "On time"),
        )
        val state = ComputeTrainApproach.compute(
            callingPoints = points,
            originCrs = "RDG",
            destinationCrs = "DID",
            now = now,
        )
        assertFalse(state.originIsFirstOnRoute)
        assertEquals("Paddington", state.approachingFromStopName)
        assertEquals(30L, state.minutesUntilOriginArrival)
    }

    @Test
    fun autoBoardWhenDepartureWithinTwoMinutes() {
        val points = listOf(
            cp("Apsley", "APS", "08:01", "On time"),
            cp("London Euston", "EUS", "08:45", "On time"),
        )
        val state = ComputeTrainApproach.compute(
            callingPoints = points,
            originCrs = "APS",
            destinationCrs = "EUS",
            now = now,
        )
        assertTrue(state.shouldAutoBoard)
    }

    @Test
    fun autoBoardAtIntermediateOriginWithinTwoMinutesOfDeparture() {
        val points = listOf(
            cp("Milton Keynes Central", "MKC", "07:50", "On time"),
            cp("Apsley", "APS", "08:01", "On time"),
            cp("London Euston", "EUS", "08:45", "On time"),
        )
        val state = ComputeTrainApproach.compute(
            callingPoints = points,
            originCrs = "APS",
            destinationCrs = "EUS",
            now = now,
        )
        assertFalse(state.originIsFirstOnRoute)
        assertEquals("Milton Keynes Central", state.approachingFromStopName)
        assertTrue(state.shouldAutoBoard)
    }

    private fun cp(
        name: String,
        crs: String,
        scheduled: String,
        expected: String,
    ) = CallingPoint(
        locationName = name,
        crs = crs,
        scheduledTimeLabel = scheduled,
        expectedLabel = expected,
    )
}
