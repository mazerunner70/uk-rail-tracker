package com.ukrailtracker.app.ui.home

import com.ukrailtracker.app.domain.session.host.AtStationHost
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformWatchPollIntervalTest {

    @Test
    fun `polls faster as departure approaches`() {
        assertEquals(60_000L, AtStationHost.platformPollIntervalMs(25))
        assertEquals(30_000L, AtStationHost.platformPollIntervalMs(15))
        assertEquals(15_000L, AtStationHost.platformPollIntervalMs(8))
        assertEquals(10_000L, AtStationHost.platformPollIntervalMs(3))
        assertEquals(10_000L, AtStationHost.platformPollIntervalMs(1))
        assertEquals(10_000L, AtStationHost.platformPollIntervalMs(0))
        assertEquals(30_000L, AtStationHost.platformPollIntervalMs(-2))
        assertEquals(20_000L, AtStationHost.platformPollIntervalMs(null))
    }
}
