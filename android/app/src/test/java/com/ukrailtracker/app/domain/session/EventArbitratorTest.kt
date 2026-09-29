package com.ukrailtracker.app.domain.session

import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.WalkUpSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventArbitratorTest {

    private val testStation = Station(
        crsCode = "PAD",
        name = "London Paddington",
        latitude = 51.5,
        longitude = -0.1,
        operatorName = "GWR",
        operatorCode = "GW",
    )

    private fun sampleLocation(timestampMs: Long, speed: Double = 1.0): CommuteEvent.Location =
        CommuteEvent.Location(
            sample = WalkUpSample(
                latitude = 51.5,
                longitude = -0.1,
                speedMps = speed,
                epochMs = timestampMs,
            ),
            nearest = StationWithDistance(testStation, 50.0),
            speedMps = speed,
        )

    @Test
    fun emptyListReturnsNull() {
        assertNull(EventArbitrator.arbitrate(emptyList()))
    }

    @Test
    fun singleEventReturnsItself() {
        val event = CommuteEvent.Dismiss
        assertEquals(event, EventArbitrator.arbitrate(listOf(event)))
    }

    @Test
    fun userDismissTrumpsAmbientGpsLocation() {
        val gps = sampleLocation(1_000L)
        val dismiss = CommuteEvent.Dismiss
        val winner = EventArbitrator.arbitrate(listOf(gps, dismiss))
        assertEquals(dismiss, winner)
    }

    @Test
    fun userDismissTrumpsTickTimeout() {
        val tick = CommuteEvent.Tick(nowEpochMs = 30_000L)
        val dismiss = CommuteEvent.Dismiss
        val winner = EventArbitrator.arbitrate(listOf(tick, dismiss))
        assertEquals(dismiss, winner)
    }

    @Test
    fun userConfirmTrumpsAmbientGpsAndTick() {
        val gps = sampleLocation(1_000L)
        val tick = CommuteEvent.Tick(nowEpochMs = 30_000L)
        val confirm = CommuteEvent.Confirm(nowEpochMs = 30_001L)

        val winner = EventArbitrator.arbitrate(listOf(gps, tick, confirm))
        assertEquals(confirm, winner)
    }

    @Test
    fun systemCriticalPermissionLostTrumpsAmbientGpsAndTimeout() {
        val gps = sampleLocation(1_000L)
        val tick = CommuteEvent.Tick(nowEpochMs = 30_000L)
        val permissionLost = CommuteEvent.PermissionLost

        val winner = EventArbitrator.arbitrate(listOf(gps, tick, permissionLost))
        assertEquals(permissionLost, winner)
    }

    @Test
    fun userIntentTrumpsSystemCritical() {
        // e.g. User tapped end trip right as permission was revoked
        val permissionLost = CommuteEvent.PermissionLost
        val endTrip = CommuteEvent.EndTrip

        val winner = EventArbitrator.arbitrate(listOf(permissionLost, endTrip))
        assertEquals(endTrip, winner)
    }

    @Test
    fun multipleGpsLocationsCoalesceToNewest() {
        val loc1 = sampleLocation(1_000L, speed = 1.0)
        val loc2 = sampleLocation(2_000L, speed = 2.0)
        val loc3 = sampleLocation(3_000L, speed = 3.0)

        val winner = EventArbitrator.arbitrate(listOf(loc1, loc2, loc3))
        assertEquals(loc3, winner)
    }

    @Test
    fun presenceEdgeBeatsLocationInTheSameSlice() {
        val location = sampleLocation(1_000L)
        val at = CommuteEvent.AtStation(
            crs = "PAD",
            name = "London Paddington",
            latitude = 51.5,
            longitude = -0.1,
            distanceMetres = 40.0,
            epochMs = 1_000L,
        )
        assertEquals(at, EventArbitrator.arbitrate(listOf(location, at)))
    }

    @Test
    fun multipleUserIntentsResolvesToLatest() {
        val showPicker = CommuteEvent.ShowPicker
        val select = CommuteEvent.SelectService("svc-1", nowEpochMs = 500L)

        val winner = EventArbitrator.arbitrate(listOf(showPicker, select))
        assertEquals(select, winner)
    }
}
