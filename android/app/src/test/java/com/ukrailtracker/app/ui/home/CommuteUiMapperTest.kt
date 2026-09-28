package com.ukrailtracker.app.ui.home

import com.ukrailtracker.app.domain.model.CallingPoint
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.PlatformWatchPhase
import com.ukrailtracker.app.ui.station.StatusKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CommuteUiMapperTest {

    @Test
    fun needsPermissionOverlayTakesPrecedence() {
        val ui = mapCommutePresence(
            session = CommuteSession.Idle,
            aux = CommuteAuxState(overlay = PresenceOverlay.NeedsPermission),
        )
        assertEquals(PresenceChromeUi.NeedsPermission, ui.chrome)
        assertNull(ui.tripBody)
    }

    @Test
    fun locationUnavailableWhenIdle() {
        val ui = mapCommutePresence(
            session = CommuteSession.Idle,
            aux = CommuteAuxState(overlay = PresenceOverlay.LocationUnavailable),
        )
        assertEquals(PresenceChromeUi.LocationUnavailable, ui.chrome)
        assertNull(ui.tripBody)
    }

    @Test
    fun locationUnavailableIgnoredAtStation() {
        val session = atStation()
        val ui = mapCommutePresence(
            session = session,
            aux = CommuteAuxState(overlay = PresenceOverlay.LocationUnavailable),
        )
        assertTrue(ui.chrome is PresenceChromeUi.AtStation)
        assertTrue(ui.tripBody is HomeBodyUi.AtStation)
        assertEquals("PAD", (ui.tripBody as HomeBodyUi.AtStation).crsCode)
    }

    @Test
    fun idleMapsToNotAtStation() {
        val ui = mapCommutePresence(CommuteSession.Idle, CommuteAuxState())
        assertEquals(PresenceChromeUi.NotAtStation, ui.chrome)
        assertNull(ui.tripBody)
    }

    @Test
    fun nearMapsToNearStation() {
        val ui = mapCommutePresence(
            CommuteSession.Near(
                crs = "PAD",
                name = "Paddington",
                latitude = 51.515,
                longitude = -0.175,
                distanceMetres = 120.0,
            ),
            CommuteAuxState(),
        )
        val near = ui.chrome as PresenceChromeUi.NearStation
        assertEquals("PAD", near.crsCode)
        assertEquals("120 m", near.distanceLabel)
        assertNull(ui.tripBody)
    }

    @Test
    fun atStationUsesAuxDestinations() {
        val dest = WalkUpDestinationUi(
            destinationCrs = "RDG",
            destinationName = "Reading",
            services = emptyList(),
        )
        val ui = mapCommutePresence(
            atStation(),
            CommuteAuxState(
                atStation = AtStationAux(
                    originCrs = "PAD",
                    destinations = listOf(dest),
                    destinationsLoading = false,
                ),
            ),
            lastLoadedOriginCrs = "PAD",
        )
        val body = ui.tripBody as HomeBodyUi.AtStation
        assertEquals(listOf(dest), body.destinations)
        assertFalse(body.destinationsLoading)
        assertTrue(ui.chrome is PresenceChromeUi.AtStation)
    }

    @Test
    fun atStationShowsLoadingWhenOriginNotYetLoaded() {
        val ui = mapCommutePresence(
            atStation(),
            CommuteAuxState(atStation = AtStationAux(originCrs = "PAD")),
            lastLoadedOriginCrs = null,
        )
        val body = ui.tripBody as HomeBodyUi.AtStation
        assertTrue(body.destinationsLoading)
    }

    @Test
    fun confirmingMapsBoardingPrompt() {
        val assumed = boardingSnapshot()
        val ui = mapCommutePresence(
            CommuteSession.Confirming(
                origin = lockedOrigin(),
                assumed = assumed,
                alternatives = emptyList(),
                triggeredAtEpochMs = 0L,
                showPicker = true,
            ),
            CommuteAuxState(),
        )
        val body = ui.tripBody as HomeBodyUi.BoardingPrompt
        assertEquals("PAD", body.originCrs)
        assertEquals(assumed.serviceId, body.assumed.serviceId)
        assertTrue(body.showPicker)
        val chrome = ui.chrome as PresenceChromeUi.Boarding
        assertEquals("Paddington", chrome.originName)
        assertEquals(assumed.scheduledDepartureLabel, chrome.assumedDepartureLabel)
    }

    @Test
    fun onTrainSkeletonFromAux() {
        val pin = pinned()
        val ui = mapCommutePresence(
            CommuteSession.OnTrain(pin = pin),
            CommuteAuxState(onTrain = onTrainSkeletonAux()),
        )
        val body = ui.tripBody as HomeBodyUi.OnJourney
        assertEquals(pin.serviceId, body.serviceId)
        assertTrue(body.refreshing)
        assertEquals(pin.destinationName, body.destinationName)
        assertTrue(ui.chrome is PresenceChromeUi.OnJourney)
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
        val ui = mapCommutePresence(
            atStation().copy(platformWatch = watching),
            CommuteAuxState(atStation = AtStationAux(originCrs = "PAD")),
            lastLoadedOriginCrs = "PAD",
        )
        val watch = (ui.tripBody as HomeBodyUi.AtStation).platformWatch!!
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
        val ui = mapCommutePresence(
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
        )
        val watch = (ui.tripBody as HomeBodyUi.AtStation).platformWatch!!
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
