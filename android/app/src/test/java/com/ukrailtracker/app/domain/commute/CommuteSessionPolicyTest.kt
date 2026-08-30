package com.ukrailtracker.app.domain.commute

import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.PlatformWatchPhase
import com.ukrailtracker.app.domain.usecase.WalkUpPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommuteSessionPolicyTest {

    private val origin = LockedOrigin(
        crs = "PAD",
        name = "London Paddington",
        latitude = 51.515,
        longitude = -0.175,
        lockedAtEpochMs = 0L,
        distanceMetres = 40.0,
    )

    private val variants: List<Pair<String, CommuteSession>> = listOf(
        "Idle" to CommuteSession.Idle,
        "Near" to CommuteSession.Near(
            WalkUpPhase.Dwelling(
                crs = "PAD",
                stationName = "London Paddington",
                enteredAtEpochMs = 0L,
                distanceMetres = 120.0,
                sawWalkingPace = true,
                hadSpeedReading = true,
            ),
        ),
        "AtStation" to CommuteSession.AtStation(origin = origin),
        "AtStationWatching" to CommuteSession.AtStation(
            origin = origin,
            platformWatch = PlatformWatchPhase.Watching(
                serviceId = "svc-1",
                originCrs = "PAD",
                destinationCrs = "RDG",
                destinationName = "Reading",
                scheduledDepartureLabel = "08:05",
                expectedDepartureLabel = "On time",
                platform = null,
            ),
        ),
        "AtStationManual" to CommuteSession.AtStation(
            origin = origin.copy(manualOverride = true),
        ),
        "Confirming" to CommuteSession.Confirming(
            origin = origin,
            assumed = snap(),
            alternatives = emptyList(),
            triggeredAtEpochMs = 0L,
        ),
        "OnTrain" to CommuteSession.OnTrain(
            pin = PinnedJourney(
                serviceId = "svc-1",
                originCrs = "PAD",
                destinationCrs = "RDG",
                originName = "London Paddington",
                destinationName = "Reading",
                operatorName = "GWR",
                scheduledDepartureLabel = "08:05",
                pinnedAtEpochMs = 0L,
            ),
        ),
    )

    @Test
    fun acceptsWalkUpLocationEvents() {
        assertEquals(
            mapOf(
                "Idle" to true,
                "Near" to true,
                "AtStation" to true,
                "AtStationWatching" to true,
                "AtStationManual" to false,
                "Confirming" to false,
                "OnTrain" to true,
            ),
            variants.associate { (name, session) -> name to session.acceptsWalkUpLocationEvents() },
        )
    }

    @Test
    fun shouldResolveNearbyStations() {
        variants.forEach { (name, session) ->
            assertEquals(
                name,
                name != "OnTrain",
                session.shouldResolveNearbyStations(),
            )
        }
    }

    @Test
    fun lockedOriginCrs() {
        assertNull(CommuteSession.Idle.lockedOriginCrs())
        assertEquals("PAD", CommuteSession.AtStation(origin).lockedOriginCrs())
        assertEquals(
            "PAD",
            CommuteSession.Confirming(
                origin = origin,
                assumed = snap(),
                alternatives = emptyList(),
                triggeredAtEpochMs = 0L,
            ).lockedOriginCrs(),
        )
        assertNull(
            CommuteSession.OnTrain(
                pin = PinnedJourney(
                    serviceId = "svc-1",
                    originCrs = "PAD",
                    destinationCrs = "RDG",
                    originName = "London Paddington",
                    destinationName = "Reading",
                    operatorName = "GWR",
                    scheduledDepartureLabel = "08:05",
                    pinnedAtEpochMs = 0L,
                ),
            ).lockedOriginCrs(),
        )
    }

    @Test
    fun destinationLoadOriginCrsSkipsConfirming() {
        assertEquals("PAD", CommuteSession.AtStation(origin).destinationLoadOriginCrs())
        assertNull(
            CommuteSession.Confirming(
                origin = origin,
                assumed = snap(),
                alternatives = emptyList(),
                triggeredAtEpochMs = 0L,
            ).destinationLoadOriginCrs(),
        )
    }

    @Test
    fun locationPollIntervalMs() {
        val now = 100_000L
        assertEquals(LOCATION_POLL_MS, CommuteSession.Idle.locationPollIntervalMs(now, 0L))
        assertEquals(
            LOCATION_POLL_WATCHING_MS,
            CommuteSession.AtStation(origin).locationPollIntervalMs(now, 0L),
        )
        assertEquals(
            LOCATION_POLL_WATCHING_MS,
            CommuteSession.Confirming(
                origin = origin,
                assumed = snap(),
                alternatives = emptyList(),
                triggeredAtEpochMs = 0L,
            ).locationPollIntervalMs(now, 0L),
        )
        assertEquals(
            LOCATION_POLL_ON_TRAIN_MS,
            CommuteSession.OnTrain(
                pin = PinnedJourney(
                    serviceId = "svc-1",
                    originCrs = "PAD",
                    destinationCrs = "RDG",
                    originName = "London Paddington",
                    destinationName = "Reading",
                    operatorName = "GWR",
                    scheduledDepartureLabel = "08:05",
                    pinnedAtEpochMs = 0L,
                ),
            ).locationPollIntervalMs(now, 0L),
        )
        assertEquals(
            LOCATION_POLL_WATCHING_MS,
            CommuteSession.Idle.locationPollIntervalMs(now, elevatedUntilEpochMs = now + 1),
        )
    }

    @Test
    fun forceRefreshAndOverlayPolicies() {
        assertTrue(CommuteSession.Idle.clearsPresenceOverlayOnWatchStart())
        assertFalse(CommuteSession.AtStation(origin).clearsPresenceOverlayOnWatchStart())
        assertTrue(CommuteSession.Idle.showsLocationUnavailableOverlay())
        assertFalse(CommuteSession.AtStation(origin).showsLocationUnavailableOverlay())
        assertTrue(CommuteSession.Idle.allowsColdStartAlreadyPresentLock())
        assertFalse(CommuteSession.AtStation(origin).allowsColdStartAlreadyPresentLock())
        assertTrue(
            CommuteSession.AtStation(origin).forceRefreshLocationSample(0L, 0L),
        )
        assertFalse(
            CommuteSession.Idle.forceRefreshLocationSample(100L, elevatedUntilEpochMs = 0L),
        )
        assertTrue(
            CommuteSession.Idle.forceRefreshLocationSample(100L, elevatedUntilEpochMs = 200L),
        )
    }

    @Test
    fun phaseAndFocusFlags() {
        assertEquals(CommutePhase.Idle, CommuteSession.Idle.phase())
        assertTrue(CommuteSession.AtStation(origin).focusesAtStation())
        assertTrue(CommuteSession.AtStation(origin).focusesTripUi())
        assertFalse(CommuteSession.Idle.focusesAtStation())
        assertTrue(
            CommuteSession.OnTrain(
                pin = PinnedJourney(
                    serviceId = "svc-1",
                    originCrs = "PAD",
                    destinationCrs = "RDG",
                    originName = "London Paddington",
                    destinationName = "Reading",
                    operatorName = "GWR",
                    scheduledDepartureLabel = "08:05",
                    pinnedAtEpochMs = 0L,
                ),
            ).focusesTripUi(),
        )
    }

    private fun snap() = BoardingServiceSnapshot(
        serviceId = "svc-1",
        originCrs = "PAD",
        originName = "London Paddington",
        destinationCrs = "RDG",
        destinationName = "Reading",
        operatorName = "GWR",
        scheduledDepartureLabel = "08:05",
        expectedDepartureLabel = "On time",
        platform = "1",
        favouriteRank = 0,
    )
}
