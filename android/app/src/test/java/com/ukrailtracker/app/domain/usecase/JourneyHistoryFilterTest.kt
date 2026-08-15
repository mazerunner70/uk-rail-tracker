package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.JourneyLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JourneyHistoryFilterTest {

    private val today = LocalDate.of(2026, 8, 9)

    @Test
    fun claimableIncludesDelayAndCancellation() {
        assertTrue(JourneyHistoryFilter.isClaimable(entry(delay = 15)))
        assertTrue(JourneyHistoryFilter.isClaimable(entry(cancelled = true)))
        assertTrue(JourneyHistoryFilter.isClaimable(entry(claimStatus = "eligible")))
        assertFalse(JourneyHistoryFilter.isClaimable(entry(delay = 10)))
    }

    @Test
    fun claimWindowIs56Days() {
        assertTrue(JourneyHistoryFilter.inClaimWindow("2026-08-09", today))
        assertTrue(JourneyHistoryFilter.inClaimWindow("2026-06-14", today))
        assertFalse(JourneyHistoryFilter.inClaimWindow("2026-06-13", today))
        assertFalse(JourneyHistoryFilter.inClaimWindow("2026-08-10", today))
    }

    @Test
    fun visibleClaimableFiltersAndKeepsWindow() {
        val entries = listOf(
            entry(id = 1, date = "2026-08-08", delay = 20),
            entry(id = 2, date = "2026-08-07", delay = 5),
            entry(id = 3, date = "2026-06-01", delay = 40), // outside window
        )
        val claimable = JourneyHistoryFilter.visible(
            entries = entries,
            mode = JourneyHistoryMode.Claimable,
            today = today,
        )
        assertEquals(listOf(1L), claimable.map { it.id })

        val all = JourneyHistoryFilter.visible(
            entries = entries,
            mode = JourneyHistoryMode.All,
            today = today,
        )
        assertEquals(listOf(1L, 2L), all.map { it.id })
    }

    private fun entry(
        id: Long = 1,
        date: String = "2026-08-09",
        delay: Int? = null,
        cancelled: Boolean = false,
        claimStatus: String = "not_eligible",
    ): JourneyLogEntry = JourneyLogEntry(
        id = id,
        dateIso = date,
        originCrs = "PAD",
        destinationCrs = "RDG",
        operatorName = "GWR",
        operatorCode = "GW",
        scheduledDepartureLabel = "08:00",
        scheduledArrivalLabel = "09:00",
        expectedArrivalLabel = "09:00",
        delayMinutes = delay,
        wasCancelled = cancelled,
        serviceId = "svc-$id",
        claimStatus = claimStatus,
    )
}
