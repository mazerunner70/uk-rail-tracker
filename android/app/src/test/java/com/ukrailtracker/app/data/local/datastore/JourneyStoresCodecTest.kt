package com.ukrailtracker.app.data.local.datastore

import com.ukrailtracker.app.domain.model.JourneyPair
import com.ukrailtracker.app.domain.model.PinnedJourney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JourneyStoresCodecTest {

    @Test
    fun favouriteJourneysRoundTrip() {
        val pairs = listOf(JourneyPair("PAD", "BRI"), JourneyPair("KGX", "EDB"))
        val decoded = FavouriteJourneysStore.decode(FavouriteJourneysStore.encode(pairs))
        assertEquals(pairs, decoded)
    }

    @Test
    fun recentJourneysRoundTrip() {
        val pairs = listOf(JourneyPair("PAD", "BRI"))
        val decoded = RecentJourneysStore.decode(RecentJourneysStore.encode(pairs))
        assertEquals(pairs, decoded)
    }

    @Test
    fun pinnedJourneyRoundTrip() {
        val pinned = PinnedJourney(
            serviceId = "abc+/=",
            originCrs = "PAD",
            destinationCrs = "BRI",
            originName = "Paddington",
            destinationName = "Bristol Temple Meads",
            operatorName = "GWR",
            scheduledDepartureLabel = "08:00",
            pinnedAtEpochMs = 123L,
        )
        val decoded = PinnedJourneyStore.decode(PinnedJourneyStore.encode(pinned))
        assertEquals(pinned, decoded)
    }

    @Test
    fun pinnedJourneyDecodeBlank() {
        assertNull(PinnedJourneyStore.decode(""))
    }
}
