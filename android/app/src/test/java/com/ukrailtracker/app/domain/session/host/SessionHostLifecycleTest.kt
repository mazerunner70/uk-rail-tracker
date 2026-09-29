package com.ukrailtracker.app.domain.session.host

import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.PlatformWatchPhase
import com.ukrailtracker.app.domain.usecase.initialWaitingAtOrigin
import com.ukrailtracker.app.ui.home.AtStationAux
import com.ukrailtracker.app.ui.home.CommuteAuxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lifecycle behaviour for hosts without I/O (services = null).
 * Job start/stop with services is covered via [com.ukrailtracker.app.domain.commute.CommuteRuntimeTest].
 */
class SessionHostLifecycleTest {

    private val origin = LockedOrigin(
        crs = "PAD",
        name = "London Paddington",
        latitude = 51.515,
        longitude = -0.175,
        lockedAtEpochMs = 1_000L,
        distanceMetres = 0.0,
    )

    private val otherOrigin = origin.copy(crs = "RDG", name = "Reading")

    private val watching = PlatformWatchPhase.Watching(
        serviceId = "svc-1",
        originCrs = "PAD",
        destinationCrs = "RDG",
        destinationName = "Reading",
        scheduledDepartureLabel = "10:00",
        expectedDepartureLabel = "On time",
        platform = "1",
    )

    @Test
    fun confirmingEntryThenExitReturnsHandoff() {
        val host = ConfirmingHost()
        val confirming = CommuteSession.Confirming(
            origin = origin,
            assumed = BoardingServiceSnapshot(
                serviceId = "svc-1",
                originCrs = "PAD",
                originName = "London Paddington",
                destinationCrs = "RDG",
                destinationName = "Reading",
                operatorName = "GWR",
                scheduledDepartureLabel = "10:00",
                expectedDepartureLabel = "On time",
                platform = null,
                favouriteRank = 0,
            ),
            alternatives = emptyList(),
            triggeredAtEpochMs = 0L,
        )
        val entered = host.onEntry(SessionTypeId.AtStation, SessionHandoff(), confirming)
        assertEquals(SessionHandoff(), entered)
        val exited = host.onExit(SessionTypeId.OnTrain)
        assertEquals(entered, exited)
    }

    @Test
    fun atStationExitClearsAuxAndBoardingCache() {
        val host = AtStationHost()
        val at = CommuteSession.AtStation(origin = origin, platformWatch = watching)
        val seed = SessionHandoff(
            aux = CommuteAuxState(
                atStation = AtStationAux(originCrs = "PAD", destinationsLoading = false),
            ),
            cachedBoardingServices = listOf(
                BoardingServiceSnapshot(
                    serviceId = "svc-1",
                    originCrs = "PAD",
                    originName = "London Paddington",
                    destinationCrs = "RDG",
                    destinationName = "Reading",
                    operatorName = "GWR",
                    scheduledDepartureLabel = "10:00",
                    expectedDepartureLabel = "On time",
                    platform = null,
                    favouriteRank = 0,
                ),
            ),
            platformNotified = true,
        )
        host.onEntry(SessionTypeId.Idle, seed, at)
        val exited = host.onExit(SessionTypeId.Idle)
        assertTrue(exited.cachedBoardingServices.isEmpty())
        assertNull(exited.aux.atStation.originCrs)
        assertNull(exited.aux.atStation.platformWatch)
        assertEquals(false, exited.platformNotified)
    }

    @Test
    fun atStationActiveChangeClearsWatchOnCrsChange() {
        val host = AtStationHost()
        val previous = CommuteSession.AtStation(origin = origin, platformWatch = watching)
        val next = CommuteSession.AtStation(origin = otherOrigin)
        val seed = SessionHandoff(
            aux = CommuteAuxState(
                atStation = AtStationAux(
                    originCrs = "PAD",
                    platformWatch = com.ukrailtracker.app.ui.home.PlatformWatchUi(
                        serviceId = "svc-1",
                        originCrs = "PAD",
                        destinationCrs = "RDG",
                        destinationName = "Reading",
                        scheduledDepartureLabel = "10:00",
                        expectedDepartureLabel = "On time",
                        platform = "1",
                        checkedAtLabel = null,
                        polling = false,
                        errorMessage = null,
                    ),
                ),
            ),
            platformNotified = true,
        )
        host.onEntry(SessionTypeId.Near, seed, previous)
        val changed = host.onActiveChange(previous, next, seed)
        assertEquals(false, changed.platformNotified)
        assertEquals("RDG", changed.aux.atStation.originCrs)
    }

    @Test
    fun onTrainEntryClearsBoardingAndSkeletonsAux() {
        val host = OnTrainHost()
        val onTrain = CommuteSession.OnTrain(
            pin = PinnedJourney(
                serviceId = "svc-1",
                originCrs = "PAD",
                destinationCrs = "RDG",
                originName = "London Paddington",
                destinationName = "Reading",
                operatorName = "GWR",
                scheduledDepartureLabel = "10:00",
                pinnedAtEpochMs = 0L,
            ),
            ridePhase = initialWaitingAtOrigin("London Paddington"),
        )
        val seed = SessionHandoff(
            cachedBoardingServices = listOf(
                BoardingServiceSnapshot(
                    serviceId = "svc-1",
                    originCrs = "PAD",
                    originName = "London Paddington",
                    destinationCrs = "RDG",
                    destinationName = "Reading",
                    operatorName = "GWR",
                    scheduledDepartureLabel = "10:00",
                    expectedDepartureLabel = "On time",
                    platform = null,
                    favouriteRank = 0,
                ),
            ),
            aux = CommuteAuxState(atStation = AtStationAux(originCrs = "PAD")),
        )
        val entered = host.onEntry(SessionTypeId.Confirming, seed, onTrain)
        assertTrue(entered.cachedBoardingServices.isEmpty())
        assertNull(entered.aux.atStation.originCrs)
        assertTrue(entered.aux.onTrain.refreshing)
        val exited = host.onExit(SessionTypeId.Idle)
        assertNull(exited.latestOnTrainOption)
        assertTrue(exited.routeStations.isEmpty())
        assertNull(exited.aux.onTrain.destinationName)
    }

    @Test
    fun onTrainActiveChangeRestartsContextOnPinChange() {
        val host = OnTrainHost()
        val pin1 = PinnedJourney(
            serviceId = "svc-1",
            originCrs = "PAD",
            destinationCrs = "RDG",
            originName = "London Paddington",
            destinationName = "Reading",
            operatorName = "GWR",
            scheduledDepartureLabel = "10:00",
            pinnedAtEpochMs = 0L,
        )
        val previous = CommuteSession.OnTrain(pin = pin1)
        val next = CommuteSession.OnTrain(pin = pin1.copy(serviceId = "svc-2"))
        host.onEntry(SessionTypeId.Idle, SessionHandoff(), previous)
        val changed = host.onActiveChange(previous, next, SessionHandoff())
        assertTrue(changed.aux.onTrain.refreshing)
        assertNull(changed.latestOnTrainOption)
    }
}
