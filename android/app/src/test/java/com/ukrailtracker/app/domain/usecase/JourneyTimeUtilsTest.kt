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
    fun minutesUntilArrivalPrefersExpected() {
        val now = LocalDateTime.of(2026, 8, 6, 10, 0)
        assertEquals(
            35L,
            JourneyTimeUtils.minutesUntilArrival(
                scheduledArrivalLabel = "10:30",
                expectedArrivalLabel = "10:35",
                now = now,
            ),
        )
    }

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

    @Test
    fun effectiveDepartureLabelPrefersParseableExpected() {
        assertEquals(
            "08:10",
            JourneyTimeUtils.effectiveDepartureLabel("08:00", "08:10"),
        )
        assertEquals(
            "08:00",
            JourneyTimeUtils.effectiveDepartureLabel("08:00", "On time"),
        )
    }

    @Test
    fun isDepartureAroundNowAllowsRecentPast() {
        val now = LocalDateTime.of(2026, 8, 6, 8, 5)
        assertTrue(JourneyTimeUtils.isDepartureAroundNow("08:00", now, pastMinutes = 8, futureMinutes = 3))
        assertFalse(JourneyTimeUtils.isDepartureAroundNow("07:50", now, pastMinutes = 8, futureMinutes = 3))
        assertFalse(JourneyTimeUtils.isDepartureAroundNow("08:20", now, pastMinutes = 8, futureMinutes = 3))
    }

    @Test
    fun scheduleVarianceMinutesSigned() {
        assertEquals(1, JourneyTimeUtils.scheduleVarianceMinutes("12:00", "12:01"))
        assertEquals(-2, JourneyTimeUtils.scheduleVarianceMinutes("12:00", "11:58"))
        assertEquals(0, JourneyTimeUtils.scheduleVarianceMinutes("12:00", "12:00"))
        assertNull(JourneyTimeUtils.scheduleVarianceMinutes("12:00", "On time"))
    }

    @Test
    fun hasActualArrivalAcceptsOnTimePhrase() {
        assertTrue(JourneyTimeUtils.hasActualArrival("On time"))
        assertTrue(JourneyTimeUtils.hasActualArrival("19:58"))
        assertTrue(JourneyTimeUtils.hasActualArrival("19:58*"))
        assertFalse(JourneyTimeUtils.hasActualArrival("No report"))
        assertFalse(JourneyTimeUtils.hasActualArrival(null))
        assertFalse(JourneyTimeUtils.hasActualArrival("Delayed"))
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
