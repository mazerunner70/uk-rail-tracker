package com.ukrailtracker.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformWatchPollIntervalTest {

    @Test
    fun `polls faster as departure approaches`() {
        assertEquals(60_000L, HomeViewModel.platformPollIntervalMs(25))
        assertEquals(30_000L, HomeViewModel.platformPollIntervalMs(15))
        assertEquals(15_000L, HomeViewModel.platformPollIntervalMs(8))
        assertEquals(10_000L, HomeViewModel.platformPollIntervalMs(3))
        assertEquals(10_000L, HomeViewModel.platformPollIntervalMs(1))
        assertEquals(10_000L, HomeViewModel.platformPollIntervalMs(0))
        assertEquals(30_000L, HomeViewModel.platformPollIntervalMs(-2))
        assertEquals(20_000L, HomeViewModel.platformPollIntervalMs(null))
    }
}
