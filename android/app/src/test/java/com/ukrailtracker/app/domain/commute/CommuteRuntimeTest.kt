package com.ukrailtracker.app.domain.commute

import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.model.UserLocation
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.LoadFavouriteBoundDepartures
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.ui.home.PresenceOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Long-lived pin-restore / on-train jobs are started on [backgroundScope] so
 * [runTest] does not treat them as uncompleted work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CommuteRuntimeTest {

    private val pad = Station(
        crsCode = "PAD",
        name = "London Paddington",
        latitude = 51.515,
        longitude = -0.175,
        operatorName = "GWR",
        operatorCode = "GW",
    )

    @Test
    fun startsIdle() = runTest {
        val runtime = createRuntime(backgroundScope)
        assertEquals(CommuteSession.Idle, runtime.sessionFlow.value)
    }

    @Test
    fun permissionDeniedSetsOverlayAndIdle() = runTest {
        val runtime = createRuntime(backgroundScope)
        runtime.onPermissionDenied()
        assertEquals(PresenceOverlay.NeedsPermission, runtime.auxFlow.value.overlay)
        assertEquals(CommuteSession.Idle, runtime.sessionFlow.value)
    }

    @Test
    fun setOriginOverrideLocksAtStation() = runTest {
        val runtime = createRuntime(backgroundScope)
        // Dispatch synchronously — avoid scope.launch + advanceUntilIdle races with
        // the background pin collector.
        runtime.dispatch(
            CommuteEvent.SetOriginOverride(
                LockedOrigin(
                    crs = "PAD",
                    name = "London Paddington",
                    latitude = pad.latitude,
                    longitude = pad.longitude,
                    lockedAtEpochMs = 0L,
                    distanceMetres = 0.0,
                    manualOverride = true,
                ),
            ),
        )
        val session = runtime.sessionFlow.value
        assertTrue(session is CommuteSession.AtStation)
        assertTrue((session as CommuteSession.AtStation).origin.manualOverride)
        assertEquals("PAD", session.origin.crs)
    }

    @Test
    fun dismissWhileIdleStaysIdle() = runTest {
        val runtime = createRuntime(backgroundScope)
        runtime.dispatch(CommuteEvent.Dismiss)
        assertEquals(CommuteSession.Idle, runtime.sessionFlow.value)
    }

    @Test
    fun openedForFrequentChecksKeepsIdle() = runTest {
        val runtime = createRuntime(backgroundScope)
        runtime.dispatch(
            CommuteEvent.OpenedForFrequentChecks(
                untilEpochMs = System.currentTimeMillis() + 60_000,
                hintCrs = "PAD",
            ),
        )
        assertEquals(CommuteSession.Idle, runtime.sessionFlow.value)
    }

    @Test
    fun pinRestoreFromStoreDispatchesOnTrain() = runTest(UnconfinedTestDispatcher()) {
        val pinStore = FakePinStore()
        val runtime = createRuntime(backgroundScope, pinStore = pinStore)
        val pinned = PinnedJourney(
            serviceId = "svc-1",
            originCrs = "PAD",
            destinationCrs = "RDG",
            originName = "London Paddington",
            destinationName = "Reading",
            operatorName = "GWR",
            scheduledDepartureLabel = "08:05",
            pinnedAtEpochMs = 0L,
        )
        pinStore.emit(pinned)
        assertTrue(runtime.sessionFlow.value is CommuteSession.OnTrain)
        pinStore.emit(null)
        assertEquals(CommuteSession.Idle, runtime.sessionFlow.value)
    }

    @Test
    fun confirmThenEndTripReturnsIdle() = runTest {
        val runtime = createRuntime(backgroundScope)
        val pinned = PinnedJourney(
            serviceId = "svc-1",
            originCrs = "PAD",
            destinationCrs = "RDG",
            originName = "London Paddington",
            destinationName = "Reading",
            operatorName = "GWR",
            scheduledDepartureLabel = "08:05",
            pinnedAtEpochMs = 0L,
        )
        runtime.dispatch(CommuteEvent.RestorePin(pinned))
        assertTrue(runtime.sessionFlow.value is CommuteSession.OnTrain)
        runtime.dispatch(CommuteEvent.EndTrip)
        advanceUntilIdle()
        assertEquals(CommuteSession.Idle, runtime.sessionFlow.value)
    }

    private fun createRuntime(
        scope: CoroutineScope,
        pinStore: FakePinStore = FakePinStore(),
    ): CommuteRuntime {
        val stations = FakeStationRepository(pad)
        val journeys = FakeJourneyRepository()
        return CommuteRuntime(
            scope = scope,
            appContext = RuntimeEnvironment.getApplication(),
            stationRepository = stations,
            journeyRepository = journeys,
            locationProvider = FakeLocationProvider(),
            favouritesStore = FakeFavouritesAccess(emptyList()),
            pinnedJourneyStore = pinStore,
            journeyLogRepository = FakeJourneyLogger(),
            loadFavouriteBoundDepartures = LoadFavouriteBoundDepartures(journeys, stations),
        )
    }

    private class FakeStationRepository(private val pad: Station) : StationRepository {
        override suspend fun ensureImported() = Unit
        override suspend fun count(): Int = 1
        override suspend fun getByCrs(crs: String): Station? =
            if (crs.equals("PAD", ignoreCase = true)) pad else null
        override suspend fun search(query: String): List<Station> = emptyList()
        override suspend fun getNearby(lat: Double, lng: Double, limit: Int): List<StationWithDistance> =
            emptyList()
    }

    private class FakeJourneyRepository : JourneyRepository {
        override suspend fun getOptions(
            originCrs: String,
            destinationCrs: String,
            forceRefresh: Boolean,
        ) = emptyList<JourneyOption>()

        override suspend fun getOption(
            originCrs: String,
            destinationCrs: String,
            serviceId: String,
            forceRefresh: Boolean,
        ): JourneyOption? = null

        override suspend fun getServiceDetails(
            serviceId: String,
            originCrs: String,
            destinationCrs: String,
            destinationNameFallback: String,
        ): JourneyOption = JourneyOption(
            serviceId = serviceId,
            originCrs = originCrs,
            destinationCrs = destinationCrs,
            destinationName = destinationNameFallback,
            scheduledDepartureLabel = "08:05",
            expectedDepartureLabel = "On time",
            scheduledArrivalLabel = "08:30",
            expectedArrivalLabel = "08:30",
            platform = "1",
            operatorName = "GWR",
            status = TrainStatus.OnTime,
            delayMinutes = 0,
            callingPoints = emptyList(),
            isCancelled = false,
        )
    }

    private class FakeLocationProvider : LocationProvider {
        override suspend fun currentLocation(forceRefresh: Boolean): UserLocation? = null
    }

    private class FakeFavouritesAccess(
        private val favourites: List<String>,
    ) : CommuteFavouritesAccess {
        override suspend fun getFavourites(): List<String> = favourites
    }

    private class FakePinStore : CommutePinStore {
        private val state = MutableStateFlow<PinnedJourney?>(null)
        override val pinnedFlow: Flow<PinnedJourney?> = state

        fun emit(journey: PinnedJourney?) {
            state.value = journey
        }

        override suspend fun pin(journey: PinnedJourney) {
            state.value = journey
        }

        override suspend fun clear() {
            state.value = null
        }
    }

    private class FakeJourneyLogger : CommuteJourneyLogger {
        val logged = mutableListOf<JourneyOption>()
        override suspend fun logFromOption(option: JourneyOption) {
            logged += option
        }
    }
}
