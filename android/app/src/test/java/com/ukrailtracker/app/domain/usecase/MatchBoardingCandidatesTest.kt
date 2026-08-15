package com.ukrailtracker.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MatchBoardingCandidatesTest {

    private val zone = ZoneId.of("Europe/London")

    @Test
    fun singleMatchBecomesAssumed() {
        val boardedAt = epoch(2026, 8, 6, 8, 5)
        val cached = listOf(
            snap("svc-1", dest = "RDG", rank = 0, scheduled = "08:02"),
        )
        val result = MatchBoardingCandidates.match(cached, boardedAt, zoneId = zone)!!
        assertEquals("svc-1", result.assumed.serviceId)
        assertTrue(result.alternatives.isEmpty())
    }

    @Test
    fun sameSlotPrefersHigherPriorityFavourite() {
        val boardedAt = epoch(2026, 8, 6, 8, 5)
        val cached = listOf(
            snap("svc-rdg", dest = "RDG", rank = 1, scheduled = "08:00"),
            snap("svc-did", dest = "DID", rank = 0, scheduled = "08:00"),
        )
        val result = MatchBoardingCandidates.match(cached, boardedAt, zoneId = zone)!!
        assertEquals("svc-did", result.assumed.serviceId)
        assertEquals(listOf("svc-rdg"), result.alternatives.map { it.serviceId })
    }

    @Test
    fun emptyOrOutOfWindowReturnsNull() {
        val boardedAt = epoch(2026, 8, 6, 8, 5)
        assertNull(MatchBoardingCandidates.match(emptyList(), boardedAt, zoneId = zone))

        val far = listOf(snap("svc-far", dest = "RDG", rank = 0, scheduled = "10:00"))
        assertNull(MatchBoardingCandidates.match(far, boardedAt, zoneId = zone))
    }

    @Test
    fun snapshotsFromDestinationsUsesFavouriteOrder() {
        val destinations = listOf(
            FavouriteDestinationServices(
                destinationCrs = "RDG",
                destinationName = "Reading",
                services = listOf(
                    FavouriteServiceUiModel(
                        serviceId = "s1",
                        scheduledDepartureLabel = "08:00",
                        expectedDepartureLabel = "On time",
                        platform = "5",
                        statusLabel = "On time",
                        delayMinutes = null,
                        isCancelled = false,
                        durationMinutes = 30,
                        destinationName = "Reading",
                    ),
                ),
            ),
            FavouriteDestinationServices(
                destinationCrs = "DID",
                destinationName = "Didcot Parkway",
                services = listOf(
                    FavouriteServiceUiModel(
                        serviceId = "s2",
                        scheduledDepartureLabel = "08:05",
                        expectedDepartureLabel = "08:07",
                        platform = "2",
                        statusLabel = "08:07",
                        delayMinutes = 2,
                        isCancelled = false,
                        durationMinutes = 40,
                        destinationName = "Didcot Parkway",
                    ),
                ),
            ),
        )
        val snaps = MatchBoardingCandidates.snapshotsFromDestinations(
            originCrs = "PAD",
            originName = "London Paddington",
            destinations = destinations,
            favouriteDestinationOrder = listOf("DID", "RDG"),
            operatorNameFallback = "GWR",
        )
        assertEquals(2, snaps.size)
        assertEquals(0, snaps.first { it.serviceId == "s2" }.favouriteRank)
        assertEquals(1, snaps.first { it.serviceId == "s1" }.favouriteRank)
        assertEquals("GWR", snaps[0].operatorName)
    }

    @Test
    fun isDepartureAroundNowUsesExtendedPastWindow() {
        val now = LocalDateTime.of(2026, 8, 6, 8, 5)
        assertTrue(JourneyTimeUtils.isDepartureAroundNow("08:00", now, pastMinutes = 8, futureMinutes = 3))
        assertTrue(JourneyTimeUtils.isDepartureAroundNow("08:08", now, pastMinutes = 8, futureMinutes = 3))
    }

    private fun snap(
        serviceId: String,
        dest: String,
        rank: Int,
        scheduled: String,
        expected: String = "On time",
    ) = BoardingServiceSnapshot(
        serviceId = serviceId,
        originCrs = "PAD",
        originName = "London Paddington",
        destinationCrs = dest,
        destinationName = dest,
        operatorName = "GWR",
        scheduledDepartureLabel = scheduled,
        expectedDepartureLabel = expected,
        platform = "1",
        favouriteRank = rank,
    )

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
}
