package com.ukrailtracker.app.ui.home

import com.ukrailtracker.app.domain.model.CallingPoint
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.PlatformWatchPhase
import com.ukrailtracker.app.domain.usecase.WalkUpPhase
import com.ukrailtracker.app.ui.station.StatusKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CommuteUiMapperTest {

    @Test
    fun needsPermissionOverlayTakesPrecedence() {
        val ui = mapCommuteWalkUpUi(
            session = CommuteSession.Idle,
            aux = CommuteAuxState(overlay = PresenceOverlay.NeedsPermission),
        )
        assertEquals(WalkUpHomeUi.NeedsPermission, ui)
    }

    @Test
    fun locationUnavailableWhenIdle() {
        val ui = mapCommuteWalkUpUi(
            session = CommuteSession.Idle,
            aux = CommuteAuxState(overlay = PresenceOverlay.LocationUnavailable),
        )
        assertEquals(WalkUpHomeUi.LocationUnavailable, ui)
    }

    @Test
    fun locationUnavailableIgnoredAtStation() {
        val session = atStation()
        val ui = mapCommuteWalkUpUi(
            session = session,
            aux = CommuteAuxState(overlay = PresenceOverlay.LocationUnavailable),
        )
        assertTrue(ui is WalkUpHomeUi.AtStation)
        assertEquals("PAD", (ui as WalkUpHomeUi.AtStation).crsCode)
    }

    @Test
    fun idleMapsToNotAtStation() {
        val ui = mapCommuteWalkUpUi(CommuteSession.Idle, CommuteAuxState())
        assertEquals(WalkUpHomeUi.NotAtStation, ui)
    }

    @Test
    fun nearMapsToNearStation() {
        val ui = mapCommuteWalkUpUi(
            CommuteSession.Near(
                WalkUpPhase.Dwelling(
                    crs = "PAD",
                    stationName = "Paddington",
                    distanceMetres = 120.0,
                    enteredAtEpochMs = 0L,
                    sawWalkingPace = true,
                    hadSpeedReading = true,
                ),
            ),
            CommuteAuxState(),
        )
        val near = ui as WalkUpHomeUi.NearStation
        assertEquals("PAD", near.crsCode)
        assertEquals("120 m", near.distanceLabel)
    }

    @Test
    fun atStationUsesAuxDestinations() {
        val dest = WalkUpDestinationUi(
            destinationCrs = "RDG",
            destinationName = "Reading",
            services = emptyList(),
        )
        val ui = mapCommuteWalkUpUi(
            atStation(),
            CommuteAuxState(
                atStation = AtStationAux(
                    originCrs = "PAD",
                    destinations = listOf(dest),
                    destinationsLoading = false,
                ),
            ),
            lastLoadedOriginCrs = "PAD",
        ) as WalkUpHomeUi.AtStation
        assertEquals(listOf(dest), ui.destinations)
        assertFalse(ui.destinationsLoading)
    }

    @Test
    fun atStationShowsLoadingWhenOriginNotYetLoaded() {
        val ui = mapCommuteWalkUpUi(
            atStation(),
            CommuteAuxState(atStation = AtStationAux(originCrs = "PAD")),
            lastLoadedOriginCrs = null,
        ) as WalkUpHomeUi.AtStation
        assertTrue(ui.destinationsLoading)
    }

    @Test
    fun confirmingMapsBoardingPrompt() {
        val assumed = boardingSnapshot()
        val ui = mapCommuteWalkUpUi(
            CommuteSession.Confirming(
                origin = lockedOrigin(),
                assumed = assumed,
                alternatives = emptyList(),
                triggeredAtEpochMs = 0L,
                showPicker = true,
            ),
            CommuteAuxState(),
        ) as WalkUpHomeUi.BoardingPrompt
        assertEquals("PAD", ui.originCrs)
        assertEquals(assumed.serviceId, ui.assumed.serviceId)
        assertTrue(ui.showPicker)
    }

    @Test
    fun onTrainSkeletonFromAux() {
        val pin = pinned()
        val ui = mapCommuteWalkUpUi(
            CommuteSession.OnTrain(pin = pin),
            CommuteAuxState(onTrain = onTrainSkeletonAux()),
        ) as WalkUpHomeUi.OnJourney
        assertEquals(pin.serviceId, ui.serviceId)
        assertTrue(ui.refreshing)
        assertEquals(pin.destinationName, ui.destinationName)
    }

    @Test
    fun mergeAtStationKeepsDestinationsForSameCrs() {
        val session = atStation()
        val merged = mergeAtStationAuxForArrival(
            session = session,
            current = AtStationAux(
                originCrs = "PAD",
                destinations = listOf(
                    WalkUpDestinationUi("RDG", "Reading", emptyList()),
                ),
                destinationsLoading = false,
            ),
            lastLoadedOriginCrs = "PAD",
        )
        assertEquals(1, merged.destinations.size)
        assertFalse(merged.destinationsLoading)
    }

    @Test
    fun mergeAtStationClearsDestinationsForNewCrs() {
        val merged = mergeAtStationAuxForArrival(
            session = atStation(crs = "RDG"),
            current = AtStationAux(
                originCrs = "PAD",
                destinations = listOf(
                    WalkUpDestinationUi("BRI", "Bristol", emptyList()),
                ),
            ),
            lastLoadedOriginCrs = "PAD",
        )
        assertEquals("RDG", merged.originCrs)
        assertTrue(merged.destinations.isEmpty())
        assertTrue(merged.destinationsLoading)
    }

    @Test
    fun buildOnTrainUiAuxPopulatesProgress() {
        val pin = pinned()
        val option = JourneyOption(
            serviceId = pin.serviceId,
            originCrs = pin.originCrs,
            destinationCrs = pin.destinationCrs,
            destinationName = pin.destinationName,
            scheduledDepartureLabel = pin.scheduledDepartureLabel,
            expectedDepartureLabel = pin.scheduledDepartureLabel,
            scheduledArrivalLabel = "09:30",
            expectedArrivalLabel = "09:30",
            platform = "1",
            operatorName = pin.operatorName,
            status = TrainStatus.OnTime,
            delayMinutes = 0,
            callingPoints = listOf(
                CallingPoint(
                    locationName = "Paddington",
                    crs = "PAD",
                    scheduledTimeLabel = "08:05",
                    expectedLabel = "On time",
                    actualTimeLabel = "08:05",
                ),
                CallingPoint(
                    locationName = "Reading",
                    crs = "RDG",
                    scheduledTimeLabel = "09:30",
                    expectedLabel = "On time",
                ),
            ),
            isCancelled = false,
        )
        val aux = buildOnTrainUiAux(
            option = option,
            onTrain = CommuteSession.OnTrain(pin = pin),
            now = LocalDateTime.of(2026, 8, 16, 9, 0),
        )
        assertEquals(StatusKind.OnTime, aux.statusKind)
        assertFalse(aux.refreshing)
        assertEquals("Reading", aux.destinationName)
    }

    @Test
    fun atStationMapsPlatformWatchFromSession() {
        val watching = PlatformWatchPhase.Watching(
            serviceId = "svc-1",
            originCrs = "PAD",
            destinationCrs = "RDG",
            destinationName = "Reading",
            scheduledDepartureLabel = "08:05",
            expectedDepartureLabel = "On time",
            platform = null,
        )
        val ui = mapCommuteWalkUpUi(
            atStation().copy(platformWatch = watching),
            CommuteAuxState(atStation = AtStationAux(originCrs = "PAD")),
            lastLoadedOriginCrs = "PAD",
        ) as WalkUpHomeUi.AtStation
        val watch = ui.platformWatch!!
        assertEquals("svc-1", watch.serviceId)
        assertEquals("RDG", watch.destinationCrs)
        assertTrue(watch.polling)
        assertEquals(null, watch.platform)
    }

    @Test
    fun atStationMergesAuxPlatformSnapshotOntoSessionWatch() {
        val watching = PlatformWatchPhase.Watching(
            serviceId = "svc-1",
            originCrs = "PAD",
            destinationCrs = "RDG",
            destinationName = "Reading",
            scheduledDepartureLabel = "08:05",
            expectedDepartureLabel = "On time",
            platform = null,
        )
        val ui = mapCommuteWalkUpUi(
            atStation().copy(platformWatch = watching),
            CommuteAuxState(
                atStation = AtStationAux(
                    originCrs = "PAD",
                    platformWatch = PlatformWatchUi(
                        serviceId = "svc-1",
                        originCrs = "PAD",
                        destinationCrs = "RDG",
                        destinationName = "Reading",
                        scheduledDepartureLabel = "08:05",
                        expectedDepartureLabel = "08:07",
                        platform = "12",
                        checkedAtLabel = "08:01:00",
                        polling = false,
                    ),
                ),
            ),
            lastLoadedOriginCrs = "PAD",
        ) as WalkUpHomeUi.AtStation
        val watch = ui.platformWatch!!
        assertEquals("12", watch.platform)
        assertEquals("08:07", watch.expectedDepartureLabel)
        assertFalse(watch.polling)
    }

    private fun atStation(crs: String = "PAD"): CommuteSession.AtStation =
        CommuteSession.AtStation(origin = lockedOrigin(crs = crs))

    private fun lockedOrigin(crs: String = "PAD"): LockedOrigin =
        LockedOrigin(
            crs = crs,
            name = if (crs == "PAD") "Paddington" else crs,
            latitude = 51.5,
            longitude = -0.17,
            lockedAtEpochMs = 0L,
            distanceMetres = 80.0,
            manualOverride = false,
        )

    private fun boardingSnapshot(): BoardingServiceSnapshot =
        BoardingServiceSnapshot(
            serviceId = "svc-1",
            originCrs = "PAD",
            destinationCrs = "RDG",
            originName = "Paddington",
            destinationName = "Reading",
            operatorName = "GWR",
            scheduledDepartureLabel = "08:05",
            expectedDepartureLabel = "08:05",
            platform = "1",
            favouriteRank = 0,
        )

    private fun pinned(): PinnedJourney =
        PinnedJourney(
            serviceId = "svc-1",
            originCrs = "PAD",
            destinationCrs = "RDG",
            originName = "Paddington",
            destinationName = "Reading",
            operatorName = "GWR",
            scheduledDepartureLabel = "08:05",
            pinnedAtEpochMs = 0L,
        )
}
