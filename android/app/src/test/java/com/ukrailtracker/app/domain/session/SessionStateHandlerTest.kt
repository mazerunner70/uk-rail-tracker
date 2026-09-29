package com.ukrailtracker.app.domain.session

import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.session.host.AtStationHost
import com.ukrailtracker.app.domain.session.host.ConfirmingHost
import com.ukrailtracker.app.domain.session.host.IdleHost
import com.ukrailtracker.app.domain.session.host.OnTrainHost
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateHandlerTest {

    private val context = SessionReduceContext()
    private val idleHost = IdleHost()
    private val confirmingHost = ConfirmingHost()
    private val onTrainHost = OnTrainHost()
    private val atStationHost = AtStationHost()

    private val testOrigin = LockedOrigin(
        crs = "PAD",
        name = "London Paddington",
        latitude = 51.515,
        longitude = -0.175,
        lockedAtEpochMs = 1_000L,
        distanceMetres = 0.0,
    )

    private val testSnapshot = BoardingServiceSnapshot(
        serviceId = "svc-123",
        originCrs = "PAD",
        originName = "London Paddington",
        destinationCrs = "RDG",
        destinationName = "Reading",
        operatorName = "GWR",
        scheduledDepartureLabel = "10:00",
        expectedDepartureLabel = "On time",
        platform = "1",
        favouriteRank = 0,
    )

    @Test
    fun idleIgnoresConfirmOrSelectService() {
        val idle = CommuteSession.Idle
        val transition = idleHost.handle(idle, CommuteEvent.Confirm(10_000L), context)
        assertEquals(idle, transition.session)
        assertTrue(transition.effects.isEmpty())
    }

    @Test
    fun confirmingSelectsAlternativeService() {
        val alt = testSnapshot.copy(serviceId = "svc-456", destinationCrs = "BRI")
        val confirming = CommuteSession.Confirming(
            origin = testOrigin,
            assumed = testSnapshot,
            alternatives = listOf(alt),
            triggeredAtEpochMs = 10_000L,
        )

        val result = confirmingHost.handle(
            confirming,
            CommuteEvent.SelectService("svc-456", nowEpochMs = 15_000L),
            context,
        )

        val onTrain = result.session as CommuteSession.OnTrain
        assertEquals("svc-456", onTrain.pin.serviceId)
        assertEquals("BRI", onTrain.pin.destinationCrs)
    }

    @Test
    fun confirmingDismissReturnsToIdle() {
        val confirming = CommuteSession.Confirming(
            origin = testOrigin,
            assumed = testSnapshot,
            alternatives = emptyList(),
            triggeredAtEpochMs = 10_000L,
        )

        val result = confirmingHost.handle(confirming, CommuteEvent.Dismiss, context)
        assertEquals(CommuteSession.Idle, result.session)
    }

    @Test
    fun onTrainIgnoresDismissAndLocationLeave() {
        val onTrain = CommuteSession.OnTrain(
            pin = PinnedJourney(
                serviceId = "svc-123",
                originCrs = "PAD",
                destinationCrs = "RDG",
                originName = "London Paddington",
                destinationName = "Reading",
                operatorName = "GWR",
                scheduledDepartureLabel = "10:00",
                pinnedAtEpochMs = 10_000L,
            ),
        )

        val result = onTrainHost.handle(onTrain, CommuteEvent.Dismiss, context)
        assertEquals(onTrain, result.session)
    }

    @Test
    fun atStationClearsPlatformWatch() {
        val atStation = CommuteSession.AtStation(origin = testOrigin)
        val watching = atStationHost.handle(
            atStation,
            CommuteEvent.WatchPlatform(
                serviceId = "svc-123",
                originCrs = "PAD",
                destinationCrs = "RDG",
                destinationName = "Reading",
                scheduledDepartureLabel = "10:00",
                expectedDepartureLabel = "On time",
                platform = "1",
            ),
            context,
        )

        val cleared = atStationHost.handle(
            watching.session as CommuteSession.AtStation,
            CommuteEvent.ClearPlatformWatch,
            context,
        )

        val clearedAtStation = cleared.session as CommuteSession.AtStation
        assertEquals(com.ukrailtracker.app.domain.usecase.PlatformWatchPhase.Idle, clearedAtStation.platformWatch)
    }
}
