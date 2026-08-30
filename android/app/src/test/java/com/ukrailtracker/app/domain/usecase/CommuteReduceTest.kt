package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.CallingPoint
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import com.ukrailtracker.app.domain.model.TrainStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CommuteReduceTest {

    private val config = WalkUpConfig(
        geofenceMetres = 200.0,
        dwellMillis = 10_000L,
        lockHoldMetres = 350.0,
    )
    private val boardingConfig = BoardingConfig(autoAcceptMillis = 60_000L)
    private val zone = ZoneId.of("UTC")
    private val t0 = LocalDateTime.of(2026, 8, 16, 8, 5)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
    private val cached = listOf(snap("svc-1", dest = "RDG", rank = 0))

    private val pad = station("PAD", "London Paddington", 51.515, -0.175)

    @Test
    fun awayNearAtWalkAndDwell() {
        var session: CommuteSession = CommuteSession.Idle

        val away = reduceLocation(session, speed = 1.4, distance = 500.0, t = 0)
        assertEquals(CommuteSession.Idle, away.session)
        assertTrue(away.effects.isEmpty())
        session = away.session

        val near = reduceLocation(session, speed = 1.4, distance = 120.0, t = 1_000)
        assertTrue(near.session is CommuteSession.Near)
        assertEquals("PAD", (near.session as CommuteSession.Near).dwelling.crs)
        assertTrue(near.effects.isEmpty())
        session = near.session

        val at = reduceLocation(session, speed = 0.1, distance = 80.0, t = 12_000)
        val atStation = at.session as CommuteSession.AtStation
        assertEquals("PAD", atStation.origin.crs)
        assertEquals(BoardingPhase.Idle, atStation.boarding)
        assertEquals(
            listOf(
                CommuteEffect.LoadFavouriteBoundDepartures(originCrs = "PAD", forceRefresh = false),
            ),
            at.effects,
        )
    }

    @Test
    fun coldStartAlreadyAtStationLocksImmediately() {
        val result = reduceLocation(
            session = CommuteSession.Idle,
            speed = 0.0,
            distance = 40.0,
            t = 0,
            alreadyPresentOk = true,
        )
        val at = result.session as CommuteSession.AtStation
        assertEquals("PAD", at.origin.crs)
        assertEquals(
            listOf(CommuteEffect.LoadFavouriteBoundDepartures("PAD", forceRefresh = false)),
            result.effects,
        )
    }

    @Test
    fun vehiclePassStaysIdle() {
        val result = reduceLocation(
            session = CommuteSession.Idle,
            speed = 15.0,
            distance = 40.0,
            t = 0,
            alreadyPresentOk = true,
        )
        assertEquals(CommuteSession.Idle, result.session)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun setOriginOverrideLocksAtStation() {
        val origin = LockedOrigin(
            crs = "PAD",
            name = "London Paddington",
            latitude = pad.latitude,
            longitude = pad.longitude,
            lockedAtEpochMs = 0L,
            distanceMetres = 0.0,
            manualOverride = true,
        )
        val result = reduce(CommuteSession.Idle, CommuteEvent.SetOriginOverride(origin), config)
        val at = result.session as CommuteSession.AtStation
        assertTrue(at.origin.manualOverride)
        assertEquals(
            listOf(CommuteEffect.LoadFavouriteBoundDepartures("PAD", forceRefresh = true)),
            result.effects,
        )

        val ignored = reduceLocation(at, speed = 1.4, distance = 500.0, t = 5_000)
        assertEquals(at, ignored.session)
        assertTrue(ignored.effects.isEmpty())

        val cleared = reduce(at, CommuteEvent.ClearOverride, config)
        assertEquals(CommuteSession.Idle, cleared.session)
    }

    @Test
    fun openedForFrequentChecksElevatesPollWithoutLocking() {
        val until = 5 * 60_000L
        val result = reduce(
            CommuteSession.Idle,
            CommuteEvent.OpenedForFrequentChecks(untilEpochMs = until, hintCrs = "PAD"),
            config,
        )
        assertEquals(CommuteSession.Idle, result.session)
        assertEquals(listOf(CommuteEffect.ElevateFrequentChecks(until)), result.effects)

        val ridingOpened = reduce(onTrain(), CommuteEvent.OpenedForFrequentChecks(until), config)
        assertEquals(onTrain(), ridingOpened.session)
        assertTrue(ridingOpened.effects.isEmpty())
    }

    @Test
    fun osGeofenceEnterNotifiesWhenIdleAndIgnoresOnTrain() {
        val enter = CommuteEvent.OsGeofenceEnter(crs = "PAD", stationName = "London Paddington")
        val idle = reduce(CommuteSession.Idle, enter, config)
        assertEquals(CommuteSession.Idle, idle.session)
        assertEquals(
            listOf(CommuteEffect.NotifyProximity("PAD", "London Paddington")),
            idle.effects,
        )

        val riding = reduce(onTrain(), enter, config)
        assertEquals(onTrain(), riding.session)
        assertTrue(riding.effects.isEmpty())
    }

    @Test
    fun permissionLostReturnsIdle() {
        val near = reduceLocation(CommuteSession.Idle, speed = 1.4, distance = 120.0, t = 0)
        val lost = reduce(near.session, CommuteEvent.PermissionLost, config)
        assertEquals(CommuteSession.Idle, lost.session)
    }

    @Test
    fun leaveStationMotionSustainsThenConfirms() {
        var session: CommuteSession = atStation()
        val start = reduceFar(session, speed = 12.0, t = t0)
        assertTrue((start.session as CommuteSession.AtStation).boarding is BoardingPhase.Sustaining)
        assertTrue(start.effects.isEmpty())
        session = start.session

        val triggered = reduceFar(session, speed = 12.0, t = t0 + 8_000)
        val confirming = triggered.session as CommuteSession.Confirming
        assertEquals("svc-1", confirming.assumed.serviceId)
        assertEquals("RDG", confirming.assumed.destinationCrs)
        assertTrue(triggered.effects.isEmpty())

        val confirmed = reduce(
            confirming,
            CommuteEvent.Confirm(nowEpochMs = t0 + 8_000),
            config,
            cachedBoardingServices = cached,
            zoneId = zone,
        )
        assertTrue(confirmed.session is CommuteSession.OnTrain)
        val onTrain = confirmed.session as CommuteSession.OnTrain
        assertEquals("svc-1", onTrain.pin.serviceId)
        assertEquals("RDG", onTrain.pin.destinationCrs)
        assertEquals(
            listOf(
                CommuteEffect.Pin(onTrain.pin),
                CommuteEffect.StartTracking(destinationName = "RDG"),
            ),
            confirmed.effects,
        )
    }

    @Test
    fun boardingWithNoMatchReturnsIdle() {
        val at = atStation()
        reduceFar(at, speed = 12.0, t = t0).session.let { sustaining ->
            val result = reduceFar(sustaining, speed = 12.0, t = t0 + 8_000, services = emptyList())
            assertEquals(CommuteSession.Idle, result.session)
            assertTrue(result.effects.isEmpty())
        }
    }

    @Test
    fun dismissConfirmingReturnsIdleWithoutPin() {
        val confirming = reduceToConfirming()
        val dismissed = reduce(confirming, CommuteEvent.Dismiss, config, zoneId = zone)
        assertEquals(CommuteSession.Idle, dismissed.session)
        assertTrue(dismissed.effects.isEmpty())
    }

    @Test
    fun tickBeforeDeadlineKeepsConfirming() {
        val confirming = reduceToConfirming()
        val tick = reduce(
            confirming,
            CommuteEvent.Tick(nowEpochMs = t0 + 8_000 + 10_000),
            config,
            boardingConfig = boardingConfig,
            zoneId = zone,
        )
        assertEquals(confirming, tick.session)
        assertTrue(tick.effects.isEmpty())
    }

    @Test
    fun tickAfterDeadlinePinsAssumed() {
        val confirming = reduceToConfirming()
        val tick = reduce(
            confirming,
            CommuteEvent.Tick(nowEpochMs = t0 + 8_000 + 60_000),
            config,
            boardingConfig = boardingConfig,
            zoneId = zone,
        )
        assertTrue(tick.session is CommuteSession.OnTrain)
        assertEquals("svc-1", (tick.effects.filterIsInstance<CommuteEffect.Pin>().single()).journey.serviceId)
    }

    @Test
    fun locationFrozenWhileConfirming() {
        val confirming = reduceToConfirming()
        val ignored = reduceFar(confirming, speed = 12.0, t = t0 + 20_000)
        assertEquals(confirming, ignored.session)
    }

    @Test
    fun slowWalkAwayFromStationReturnsIdle() {
        val result = reduceFar(atStation(), speed = 1.0, t = t0)
        assertEquals(CommuteSession.Idle, result.session)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun selectServicePinsChosenAlternative() {
        val confirming = reduceToConfirming(
            services = listOf(
                snap("svc-1", dest = "RDG", rank = 0),
                snap("svc-2", dest = "DID", rank = 1),
            ),
        )
        val selected = reduce(
            confirming,
            CommuteEvent.SelectService(serviceId = "svc-2", nowEpochMs = t0 + 9_000),
            config,
            zoneId = zone,
        )
        val pin = selected.effects.filterIsInstance<CommuteEffect.Pin>().single()
        assertEquals("svc-2", pin.journey.serviceId)
        assertEquals("DID", pin.journey.destinationCrs)
        assertTrue(selected.session is CommuteSession.OnTrain)
    }

    @Test
    fun gpsConfirmsFavouriteDestinationFinishesTrip() {
        val session = onTrain(dest = "RDG")
        val route = listOf(RouteStation("RDG", "Reading", 51.458, -0.972))
        val dwell = reduceOnTrain(session, lat = 51.458, lng = -0.972, speed = 0.4, t = t0, route = route)
        assertTrue((dwell.session as CommuteSession.OnTrain).stop is StationStopPhase.Dwelling)
        val confirmed = reduceOnTrain(
            dwell.session,
            lat = 51.458,
            lng = -0.972,
            speed = 0.4,
            t = t0 + 15_000,
            route = route,
        )
        assertEquals(CommuteSession.Idle, confirmed.session)
        assertTrue(confirmed.effects.contains(CommuteEffect.WriteJourneyLog))
        assertTrue(confirmed.effects.contains(CommuteEffect.StopTracking))
        assertTrue(confirmed.effects.contains(CommuteEffect.ClearPin))
    }

    @Test
    fun gpsConfirmsIntermediateStopRefreshesDetails() {
        val session = onTrain(dest = "DID")
        val route = listOf(
            RouteStation("RDG", "Reading", 51.458, -0.972),
            RouteStation("DID", "Didcot Parkway", 51.611, -1.243),
        )
        val dwelling = reduceOnTrain(session, lat = 51.458, lng = -0.972, speed = 0.4, t = t0, route = route)
        val confirmed = reduceOnTrain(
            dwelling.session,
            lat = 51.458,
            lng = -0.972,
            speed = 0.4,
            t = t0 + 15_000,
            route = route,
        )
        val onTrain = confirmed.session as CommuteSession.OnTrain
        assertEquals("RDG", onTrain.confirmedStopCrs)
        assertEquals(listOf(CommuteEffect.RefreshServiceDetails), confirmed.effects)
    }

    @Test
    fun darwinActualAtDestinationFinishesTrip() {
        val session = onTrain(dest = "DID")
        val option = journeyOption(
            dest = "DID",
            destName = "Didcot Parkway",
            destActual = "10:38",
        )
        val result = reduce(
            session,
            CommuteEvent.ServiceDetails(option),
            config,
            progressNow = LocalDateTime.of(2026, 8, 16, 10, 0),
        )
        assertEquals(CommuteSession.Idle, result.session)
        assertTrue(result.effects.contains(CommuteEffect.WriteJourneyLog))
        assertTrue(result.effects.contains(CommuteEffect.ClearPin))
    }

    @Test
    fun endTripWritesLogAndClearsPin() {
        val result = reduce(onTrain(), CommuteEvent.EndTrip, config)
        assertEquals(CommuteSession.Idle, result.session)
        assertEquals(
            listOf(
                CommuteEffect.WriteJourneyLog,
                CommuteEffect.StopTracking,
                CommuteEffect.ClearPin,
            ),
            result.effects,
        )
    }

    @Test
    fun restorePinEntersOnTrainWithStartTracking() {
        val pin = onTrain().pin
        val result = reduce(CommuteSession.Idle, CommuteEvent.RestorePin(pin), config)
        assertTrue(result.session is CommuteSession.OnTrain)
        assertEquals(
            listOf(CommuteEffect.StartTracking(destinationName = pin.destinationName)),
            result.effects,
        )
    }

    @Test
    fun watchPlatformNestsUnderAtStation() {
        val result = reduce(atStation(), watchEvent(), config)
        val at = result.session as CommuteSession.AtStation
        val watch = at.platformWatch as PlatformWatchPhase.Watching
        assertEquals("svc-1", watch.serviceId)
        assertEquals("RDG", watch.destinationCrs)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun watchPlatformIgnoredWhenIdle() {
        val result = reduce(CommuteSession.Idle, watchEvent(), config)
        assertEquals(CommuteSession.Idle, result.session)
    }

    @Test
    fun clearPlatformWatchReturnsToBrowsing() {
        val watching = reduce(atStation(), watchEvent(), config).session
        val cleared = reduce(watching, CommuteEvent.ClearPlatformWatch, config)
        val at = cleared.session as CommuteSession.AtStation
        assertEquals(PlatformWatchPhase.Idle, at.platformWatch)
        assertEquals("PAD", at.origin.crs)
    }

    @Test
    fun boardingSustainPreservesPlatformWatch() {
        val watching = reduce(atStation(), watchEvent(), config).session as CommuteSession.AtStation
        val sustaining = reduceFar(watching, speed = 12.0, t = t0)
        val at = sustaining.session as CommuteSession.AtStation
        assertTrue(at.boarding is BoardingPhase.Sustaining)
        assertEquals("svc-1", (at.platformWatch as PlatformWatchPhase.Watching).serviceId)
    }

    private fun watchEvent() = CommuteEvent.WatchPlatform(
        serviceId = "svc-1",
        originCrs = "PAD",
        destinationCrs = "RDG",
        destinationName = "Reading",
        scheduledDepartureLabel = "08:05",
        expectedDepartureLabel = "On time",
        platform = "1",
    )

    private fun reduceToConfirming(
        services: List<BoardingServiceSnapshot> = cached,
    ): CommuteSession.Confirming {
        val sustaining = reduceFar(atStation(), speed = 12.0, t = t0, services = services).session
        return reduceFar(sustaining, speed = 12.0, t = t0 + 8_000, services = services)
            .session as CommuteSession.Confirming
    }

    private fun onTrain(dest: String = "RDG"): CommuteSession.OnTrain =
        CommuteSession.OnTrain(
            pin = PinnedJourney(
                serviceId = "svc-1",
                originCrs = "PAD",
                destinationCrs = dest,
                originName = "London Paddington",
                destinationName = dest,
                operatorName = "GWR",
                scheduledDepartureLabel = "08:05",
                pinnedAtEpochMs = t0,
            ),
        )

    private fun reduceOnTrain(
        session: CommuteSession,
        lat: Double,
        lng: Double,
        speed: Double,
        t: Long,
        route: List<RouteStation>,
    ): CommuteTransition {
        return reduce(
            session = session,
            event = CommuteEvent.Location(
                sample = WalkUpSample(lat, lng, speed, t),
                nearest = null,
                speedMps = speed,
            ),
            config = config,
            routeStations = route,
        )
    }

    private fun journeyOption(
        dest: String,
        destName: String,
        destActual: String?,
    ) = JourneyOption(
        serviceId = "svc-1",
        originCrs = "PAD",
        destinationCrs = dest,
        destinationName = destName,
        scheduledDepartureLabel = "08:05",
        expectedDepartureLabel = "On time",
        scheduledArrivalLabel = "10:30",
        expectedArrivalLabel = "10:35",
        platform = "1",
        operatorName = "GWR",
        status = TrainStatus.OnTime,
        delayMinutes = 0,
        callingPoints = listOf(
            CallingPoint(
                "Paddington",
                "PAD",
                "08:05",
                "On time",
                actualTimeLabel = "08:05",
            ),
            CallingPoint(
                destName,
                dest,
                "10:30",
                "10:35",
                actualTimeLabel = destActual,
            ),
        ),
        isCancelled = false,
    )

    private fun atStation(): CommuteSession.AtStation =
        CommuteSession.AtStation(
            origin = LockedOrigin(
                crs = "PAD",
                name = "London Paddington",
                latitude = pad.latitude,
                longitude = pad.longitude,
                lockedAtEpochMs = t0,
                distanceMetres = 40.0,
            ),
        )

    private fun reduceFar(
        session: CommuteSession,
        speed: Double,
        t: Long,
        services: List<BoardingServiceSnapshot> = cached,
    ): CommuteTransition {
        val sample = WalkUpSample(
            latitude = pad.latitude + 0.0045,
            longitude = pad.longitude,
            speedMps = speed,
            epochMs = t,
        )
        val nearest = StationWithDistance(pad, 500.0)
        return reduce(
            session = session,
            event = CommuteEvent.Location(
                sample = sample,
                nearest = nearest,
                speedMps = speed,
            ),
            config = config,
            boardingConfig = boardingConfig,
            cachedBoardingServices = services,
            zoneId = zone,
        )
    }

    private fun snap(
        serviceId: String,
        dest: String,
        rank: Int,
        scheduled: String = "08:05",
    ) = BoardingServiceSnapshot(
        serviceId = serviceId,
        originCrs = "PAD",
        originName = "London Paddington",
        destinationCrs = dest,
        destinationName = dest,
        operatorName = "GWR",
        scheduledDepartureLabel = scheduled,
        expectedDepartureLabel = "On time",
        platform = "1",
        favouriteRank = rank,
    )

    private fun reduceLocation(
        session: CommuteSession,
        speed: Double?,
        distance: Double,
        t: Long,
        alreadyPresentOk: Boolean = false,
    ): CommuteTransition {
        val nearest = StationWithDistance(pad, distance)
        val sample = WalkUpSample(
            latitude = pad.latitude,
            longitude = pad.longitude,
            speedMps = speed,
            epochMs = t,
        )
        return reduce(
            session = session,
            event = CommuteEvent.Location(
                sample = sample,
                nearest = nearest,
                speedMps = speed,
                alreadyPresentOk = alreadyPresentOk,
            ),
            config = config,
        )
    }

    private fun station(crs: String, name: String, lat: Double, lng: Double): Station =
        Station(
            crsCode = crs,
            name = name,
            latitude = lat,
            longitude = lng,
            operatorName = "GWR",
            operatorCode = "GW",
        )
}
