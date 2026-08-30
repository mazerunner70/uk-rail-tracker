package com.ukrailtracker.app.ui.home

import com.ukrailtracker.app.domain.commute.CommuteRuntime
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformWatchPollIntervalTest {

    @Test
    fun `polls faster as departure approaches`() {
        assertEquals(60_000L, CommuteRuntime.platformPollIntervalMs(25))
        assertEquals(30_000L, CommuteRuntime.platformPollIntervalMs(15))
        assertEquals(15_000L, CommuteRuntime.platformPollIntervalMs(8))
        assertEquals(10_000L, CommuteRuntime.platformPollIntervalMs(3))
        assertEquals(10_000L, CommuteRuntime.platformPollIntervalMs(1))
        assertEquals(10_000L, CommuteRuntime.platformPollIntervalMs(0))
        assertEquals(30_000L, CommuteRuntime.platformPollIntervalMs(-2))
        assertEquals(20_000L, CommuteRuntime.platformPollIntervalMs(null))
    }
}
