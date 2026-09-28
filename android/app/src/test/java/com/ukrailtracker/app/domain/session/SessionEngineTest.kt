package com.ukrailtracker.app.domain.session

import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.WalkUpSample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEngineTest {

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
    fun userCommandsExecuteWithZeroLatency() = runTest {
        val appliedTransitions = mutableListOf<CommuteTransition>()
        val engine = SessionEngine(
            scope = backgroundScope,
            initialSession = CommuteSession.Idle,
            contextProvider = { SessionReduceContext() },
            onTransitionApplied = { _, transition -> appliedTransitions.add(transition) },
        )

        val origin = LockedOrigin(
            crs = "PAD",
            name = "London Paddington",
            latitude = 51.5,
            longitude = -0.1,
            lockedAtEpochMs = 0L,
            distanceMetres = 0.0,
            manualOverride = true,
        )

        engine.dispatch(CommuteEvent.SetOriginOverride(origin))

        val current = engine.sessionFlow.value
        assertTrue(current is CommuteSession.AtStation)
        assertEquals("PAD", (current as CommuteSession.AtStation).origin.crs)
        assertEquals(1, appliedTransitions.size)
    }

    @Test
    fun userDismissPreemptsAmbientLocationInBatch() = runTest {
        val appliedTransitions = mutableListOf<CommuteTransition>()
        val confirming = CommuteSession.Confirming(
            origin = testOrigin,
            assumed = testSnapshot,
            alternatives = emptyList(),
            triggeredAtEpochMs = 1_000L,
        )

        val engine = SessionEngine(
            scope = backgroundScope,
            initialSession = confirming,
            contextProvider = { SessionReduceContext() },
            onTransitionApplied = { _, transition -> appliedTransitions.add(transition) },
        )

        val loc = CommuteEvent.Location(
            sample = WalkUpSample(latitude = 51.5, longitude = -0.1, speedMps = 15.0, epochMs = 2_000L),
            nearest = null,
            speedMps = 15.0,
        )

        // Dispatch both before processing
        engine.dispatch(loc)
        engine.dispatch(CommuteEvent.Dismiss)

        assertEquals(CommuteSession.Idle, engine.sessionFlow.value)
    }
}
