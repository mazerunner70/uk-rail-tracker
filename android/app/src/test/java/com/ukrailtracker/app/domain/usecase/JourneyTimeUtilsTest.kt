package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.TrainStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class JourneyTimeUtilsTest {

    @Test
    fun durationMinutesHandlesSameDay() {
        assertEquals(90, JourneyTimeUtils.durationMinutes("08:00", "09:30"))
    }

    @Test
    fun durationMinutesHandlesMidnightWrap() {
        assertEquals(45, JourneyTimeUtils.durationMinutes("23:40", "00:25"))
    }

    @Test
    fun durationMinutesFromOptionPrefersExpected() {
        val option = option(
            scheduledDeparture = "08:00",
            expectedDeparture = "08:10",
            scheduledArrival = "09:00",
            expectedArrival = "09:20",
        )
        assertEquals(70, JourneyTimeUtils.durationMinutes(option))
    }

    @Test
    fun filterNextHourKeepsServicesInWindow() {
        val now = LocalDateTime.of(2026, 8, 6, 8, 0)
        val options = listOf(
            option(scheduledDeparture = "08:15", scheduledArrival = "09:00"),
            option(scheduledDeparture = "09:30", scheduledArrival = "10:10"), // outside 60m
            option(scheduledDeparture = "07:50", scheduledArrival = "08:40"), // just past grace
        )
        val filtered = JourneyTimeUtils.filterNextHour(options, now = now, windowMinutes = 60)
        assertEquals(1, filtered.size)
        assertEquals("08:15", filtered[0].scheduledDepartureLabel)
    }

    @Test
    fun isDepartureWithinWindow() {
        val now = LocalDateTime.of(2026, 8, 6, 12, 0)
        assertTrue(JourneyTimeUtils.isDepartureWithinWindow("12:45", now))
        assertFalse(JourneyTimeUtils.isDepartureWithinWindow("14:00", now))
        assertNull(JourneyTimeUtils.parseTimeLabel("On time"))
    }

    private fun option(
        scheduledDeparture: String,
        scheduledArrival: String? = null,
        expectedDeparture: String = "On time",
        expectedArrival: String? = null,
    ): JourneyOption = JourneyOption(
        serviceId = "svc-$scheduledDeparture",
        originCrs = "PAD",
        destinationCrs = "RDG",
        destinationName = "Reading",
        scheduledDepartureLabel = scheduledDeparture,
        expectedDepartureLabel = expectedDeparture,
        scheduledArrivalLabel = scheduledArrival,
        expectedArrivalLabel = expectedArrival,
        platform = "5",
        operatorName = "GWR",
        status = TrainStatus.OnTime,
        delayMinutes = null,
        callingPoints = emptyList(),
        isCancelled = false,
    )
}
