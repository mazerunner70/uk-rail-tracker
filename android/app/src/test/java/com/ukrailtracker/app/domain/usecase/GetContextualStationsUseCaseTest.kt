package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.CommuteWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

class GetContextualStationsUseCaseTest {

    private val mondayMorning = Clock.fixed(
        Instant.parse("2026-08-03T07:45:00Z"),
        ZoneId.of("UTC"),
    )

    @Test
    fun prefersActiveCommuteWindowThenFavourites() {
        val useCase = GetContextualStationsUseCase(mondayMorning)
        val windows = listOf(
            CommuteWindow(
                stationCrs = "PAD",
                days = setOf(DayOfWeek.MONDAY),
                startMinutes = 7 * 60 + 30,
                endMinutes = 9 * 60,
                label = "Morning",
            ),
            CommuteWindow(
                stationCrs = "RDG",
                days = setOf(DayOfWeek.MONDAY),
                startMinutes = 17 * 60,
                endMinutes = 19 * 60,
                label = "Evening",
            ),
        )
        val selected = useCase.select(
            windows = windows,
            favouriteCrs = listOf("KGX", "PAD"),
            limit = 3,
        )
        assertEquals(listOf("PAD", "KGX"), selected)
    }

    @Test
    fun fallsBackToFavouritesOutsideWindows() {
        val evening = Clock.fixed(
            Instant.parse("2026-08-03T12:00:00Z"),
            ZoneId.of("UTC"),
        )
        val useCase = GetContextualStationsUseCase(evening)
        val windows = listOf(
            CommuteWindow(
                stationCrs = "PAD",
                days = setOf(DayOfWeek.MONDAY),
                startMinutes = 7 * 60 + 30,
                endMinutes = 9 * 60,
            ),
        )
        val selected = useCase.select(windows, favouriteCrs = listOf("KGX", "STP"))
        assertEquals(listOf("KGX", "STP"), selected)
    }
}
