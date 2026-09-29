package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.CallingPoint
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.session.SessionReduceContext
import com.ukrailtracker.app.domain.session.SessionReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CommuteReduceTest {

    private val boardingConfig = BoardingConfig(autoAcceptMillis = 60_000L)
    private val zone = ZoneId.of("UTC")
    private val t0 = LocalDateTime.of(2026, 8, 16, 8, 5)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
    private val cached = listOf(snap("svc-1", dest = "RDG", rank = 0))

    private val pad = station("PAD", "London Paddington", 51.515, -0.175)

    private fun reduce(
        session: CommuteSession,
        event: CommuteEvent,
        boardingConfig: BoardingConfig = this.boardingConfig,
        stationStopConfig: StationStopConfig = StationStopConfig(),
        cachedBoardingServices: List<BoardingServiceSnapshot> = emptyList(),
        routeStations: List<RouteStation> = emptyList(),
        onTrainCallingPoints: List<CallingPoint> = emptyList(),
        zoneId: ZoneId = zone,
        progressNow: LocalDateTime = LocalDateTime.now(),
    ): CommuteTransition = SessionReducer.reduce(
        session = session,
        event = event,
        context = SessionReduceContext(
            boardingConfig = boardingConfig,
            stationStopConfig = stationStopConfig,
            cachedBoardingServices = cachedBoardingServices,
            routeStations = routeStations,
            onTrainCallingPoints = onTrainCallingPoints,
            zoneId = zoneId,
            progressNow = progressNow,
        ),
    )

    @Test
    fun nearThenAtFromDistanceEvents() {
        var session: CommuteSession = CommuteSession.Idle

        val fix = reduceLocation(session, speed = 1.4, distance = 120.0, t = 1_000)
        assertEquals(CommuteSession.Idle, fix.session)

        val near = reduce(session, nearEvent(distance = 280.0))
        val nearSession = near.session as CommuteSession.Near
        assertEquals("PAD", nearSession.crs)
        assertTrue(near.effects.isEmpty())
        session = near.session

        val at = reduce(session, atEvent(distance = 80.0, epochMs = 12_000))
        val atStation = at.session as CommuteSession.AtStation
        assertEquals("PAD", atStation.origin.crs)
        assertEquals(BoardingPhase.Idle, atStation.boarding)
        assertTrue(at.effects.isEmpty())
    }

    @Test
    fun atStationEventLocksImmediately() {
        val result = reduce(CommuteSession.Idle, atEvent(distance = 40.0))
        val at = result.session as CommuteSession.AtStation
        assertEquals("PAD", at.origin.crs)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun onTrainIgnoresPresenceEdges() {
        val riding = onTrain()
        assertEquals(riding, reduce(riding, atEvent()).session)
        assertEquals(riding, reduce(riding, nearEvent()).session)
        assertEquals(riding, reduce(riding, CommuteEvent.LeftStation).session)
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
        val result = reduce(CommuteSession.Idle, CommuteEvent.SetOriginOverride(origin))
        val at = result.session as CommuteSession.AtStation
        assertTrue(at.origin.manualOverride)
        assertTrue(result.effects.isEmpty())

        val ignored = reduceLocation(at, speed = 1.4, distance = 500.0, t = 5_000)
        assertEquals(at, ignored.session)
        assertTrue(ignored.effects.isEmpty())

        val ignoredLeave = reduce(at, CommuteEvent.LeftStation)
        assertEquals(at, ignoredLeave.session)

        val cleared = reduce(at, CommuteEvent.ClearOverride)
        assertEquals(CommuteSession.Idle, cleared.session)
    }

    @Test
    fun leftStationReturnsIdleUnlessBoarding() {
        assertEquals(CommuteSession.Idle, reduce(atStation(), CommuteEvent.LeftStation).session)

        val sustaining = reduceFar(atStation(), speed = 12.0, t = t0).session
        val held = reduce(sustaining, CommuteEvent.LeftStation)
        assertTrue(held.session is CommuteSession.AtStation)
    }

    @Test
    fun nearBandStepsDownFromAtStation() {
        val result = reduce(atStation(), nearEvent(distance = 280.0))
        val near = result.session as CommuteSession.Near
        assertEquals("PAD", near.crs)
        assertEquals(280.0, near.distanceMetres, 0.0)
    }

    @Test
    fun osGeofenceEnterNotifiesWhenIdleAndIgnoresOnTrain() {
        val enter = CommuteEvent.OsGeofenceEnter(crs = "PAD", stationName = "London Paddington")
        val idle = reduce(CommuteSession.Idle, enter)
        assertEquals(CommuteSession.Idle, idle.session)
        assertEquals(
            listOf(CommuteEffect.NotifyProximity("PAD", "London Paddington")),
            idle.effects,
        )

        val riding = reduce(onTrain(), enter)
        assertEquals(onTrain(), riding.session)
        assertTrue(riding.effects.isEmpty())
    }

    @Test
    fun permissionLostReturnsIdle() {
        val near = reduce(CommuteSession.Idle, nearEvent(distance = 120.0))
        val lost = reduce(near.session, CommuteEvent.PermissionLost)
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
            cachedBoardingServices = cached,
            zoneId = zone,
        )
        assertTrue(confirmed.session is CommuteSession.OnTrain)
        val onTrain = confirmed.session as CommuteSession.OnTrain
        assertEquals("svc-1", onTrain.pin.serviceId)
        assertEquals("RDG", onTrain.pin.destinationCrs)
        assertEquals(
            listOf(CommuteEffect.Pin(onTrain.pin)),
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
        val dismissed = reduce(confirming, CommuteEvent.Dismiss, zoneId = zone)
        assertEquals(CommuteSession.Idle, dismissed.session)
        assertTrue(dismissed.effects.isEmpty())
    }

    @Test
    fun tickBeforeDeadlineKeepsConfirming() {
        val confirming = reduceToConfirming()
        val tick = reduce(
            confirming,
            CommuteEvent.Tick(nowEpochMs = t0 + 8_000 + 10_000),
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
    fun slowWalkAwayDoesNotDropAtStationUntilLeftEvent() {
        val result = reduceFar(atStation(), speed = 1.0, t = t0)
        assertTrue(result.session is CommuteSession.AtStation)
        assertEquals(CommuteSession.Idle, reduce(result.session, CommuteEvent.LeftStation).session)
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
            zoneId = zone,
        )
        val pin = selected.effects.filterIsInstance<CommuteEffect.Pin>().single()
        assertEquals("svc-2", pin.journey.serviceId)
        assertEquals("DID", pin.journey.destinationCrs)
        assertTrue(selected.session is CommuteSession.OnTrain)
        val onTrain = selected.session as CommuteSession.OnTrain
        assertTrue(onTrain.ridePhase is OnTrainRidePhase.WaitingAtOrigin)
    }

    @Test
    fun gpsConfirmsFavouriteDestinationFinishesTrip() {
        val session = onTrain(dest = "RDG")
        val route = listOf(RouteStation("RDG", "Reading", 51.458, -0.972))
        val dwell = reduceOnTrain(session, lat = 51.458, lng = -0.972, speed = 0.4, t = t0, route = route)
        assertTrue((dwell.session as CommuteSession.OnTrain).gpsMotion is GpsMotion.Dwelling)
        assertEquals(OnTrainRidePhase.EnRoute, (dwell.session as CommuteSession.OnTrain).ridePhase)
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
        assertEquals("RDG", onTrain.progressAnchorCrs)
        assertTrue(onTrain.ridePhase is OnTrainRidePhase.AtCallingPoint)
        assertTrue(onTrain.gpsMotion is GpsMotion.Confirmed)
        assertEquals(listOf(CommuteEffect.RefreshServiceDetails), confirmed.effects)
    }

    @Test
    fun dwellingDoesNotEnterAtCallingPoint() {
        val session = onTrain(dest = "DID")
        val route = listOf(RouteStation("RDG", "Reading", 51.458, -0.972))
        val dwell = reduceOnTrain(session, lat = 51.458, lng = -0.972, speed = 0.4, t = t0, route = route)
        val onTrain = dwell.session as CommuteSession.OnTrain
        assertTrue(onTrain.gpsMotion is GpsMotion.Dwelling)
        assertEquals(OnTrainRidePhase.EnRoute, onTrain.ridePhase)
        assertNull(onTrain.progressAnchorCrs)
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
            progressNow = LocalDateTime.of(2026, 8, 16, 10, 0),
        )
        assertEquals(CommuteSession.Idle, result.session)
        assertTrue(result.effects.contains(CommuteEffect.WriteJourneyLog))
        assertTrue(result.effects.contains(CommuteEffect.ClearPin))
    }

    @Test
    fun endTripWritesLogAndClearsPin() {
        val result = reduce(onTrain(), CommuteEvent.EndTrip)
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
    fun restorePinEntersOnTrainWithoutStartTrackingEffect() {
        val pin = onTrain().pin
        val result = reduce(CommuteSession.Idle, CommuteEvent.RestorePin(pin))
        assertTrue(result.session is CommuteSession.OnTrain)
        assertTrue((result.session as CommuteSession.OnTrain).ridePhase is OnTrainRidePhase.WaitingAtOrigin)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun boardedMovesToAwaitingDeparture() {
        val waiting = onTrain(ridePhase = initialWaitingAtOrigin("London Paddington"))
        val result = reduce(waiting, CommuteEvent.Boarded)
        assertEquals(OnTrainRidePhase.AwaitingDeparture, (result.session as CommuteSession.OnTrain).ridePhase)
    }

    @Test
    fun movementAfterBoardedPromotesToEnRoute() {
        val awaiting = onTrain(ridePhase = OnTrainRidePhase.AwaitingDeparture)
        val moved = reduceOnTrain(awaiting, lat = 51.5, lng = -0.9, speed = 15.0, t = t0, route = emptyList())
        assertEquals(OnTrainRidePhase.EnRoute, (moved.session as CommuteSession.OnTrain).ridePhase)
    }

    @Test
    fun preDepartureLocationDoesNotEnterAtCallingPoint() {
        val waiting = onTrain(ridePhase = initialWaitingAtOrigin("London Paddington"))
        val route = listOf(RouteStation("PAD", "Paddington", 51.515, -0.176))
        val dwell = reduceOnTrain(waiting, lat = 51.515, lng = -0.176, speed = 0.4, t = t0, route = route)
        val onTrain = dwell.session as CommuteSession.OnTrain
        assertTrue(onTrain.ridePhase is OnTrainRidePhase.WaitingAtOrigin)
        assertNull(onTrain.progressAnchorCrs)
    }

    @Test
    fun watchPlatformNestsUnderAtStation() {
        val result = reduce(atStation(), watchEvent())
        val at = result.session as CommuteSession.AtStation
        val watch = at.platformWatch as PlatformWatchPhase.Watching
        assertEquals("svc-1", watch.serviceId)
        assertEquals("RDG", watch.destinationCrs)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun watchPlatformIgnoredWhenIdle() {
        val result = reduce(CommuteSession.Idle, watchEvent())
        assertEquals(CommuteSession.Idle, result.session)
    }

    @Test
    fun clearPlatformWatchReturnsToBrowsing() {
        val watching = reduce(atStation(), watchEvent()).session
        val cleared = reduce(watching, CommuteEvent.ClearPlatformWatch)
        val at = cleared.session as CommuteSession.AtStation
        assertEquals(PlatformWatchPhase.Idle, at.platformWatch)
        assertEquals("PAD", at.origin.crs)
    }

    @Test
    fun boardingSustainPreservesPlatformWatch() {
        val watching = reduce(atStation(), watchEvent()).session as CommuteSession.AtStation
        val sustaining = reduceFar(watching, speed = 12.0, t = t0)
        val at = sustaining.session as CommuteSession.AtStation
        assertTrue(at.boarding is BoardingPhase.Sustaining)
        assertEquals("svc-1", (at.platformWatch as PlatformWatchPhase.Watching).serviceId)
    }

    @Test
    fun serviceDetailsAutoBoardsAtTMinusTwoBeforeDeparture() {
        val waiting = onTrain(
            dest = "EUS",
            ridePhase = OnTrainRidePhase.WaitingAtOrigin(
                originStationName = "Apsley",
                minutesUntilTrainArrives = 5,
                approachingFromStopName = null,
                originIsFirstOnRoute = true,
                boarded = false,
            ),
        )
        val option = JourneyOption(
            serviceId = "svc-1",
            originCrs = "APS",
            destinationCrs = "EUS",
            destinationName = "London Euston",
            scheduledDepartureLabel = "08:02",
            expectedDepartureLabel = "On time",
            scheduledArrivalLabel = "08:45",
            expectedArrivalLabel = "On time",
            platform = "1",
            operatorName = "Avanti",
            status = TrainStatus.OnTime,
            delayMinutes = 0,
            callingPoints = listOf(
                CallingPoint("Apsley", "APS", "08:02", "On time"),
                CallingPoint("London Euston", "EUS", "08:45", "On time"),
            ),
            isCancelled = false,
        )
        val result = reduce(
            waiting.copy(pin = waiting.pin.copy(originCrs = "APS", originName = "Apsley")),
            CommuteEvent.ServiceDetails(option),
            progressNow = LocalDateTime.of(2026, 8, 16, 8, 0),
        )
        assertEquals(
            OnTrainRidePhase.AwaitingDeparture,
            (result.session as CommuteSession.OnTrain).ridePhase,
        )
    }

    @Test
    fun serviceDetailsAutoBoardsIntermediateOriginAtTMinusTwo() {
        val waiting = onTrain(
            dest = "EUS",
            ridePhase = OnTrainRidePhase.WaitingAtOrigin(
                originStationName = "Apsley",
                minutesUntilTrainArrives = 1,
                approachingFromStopName = "Milton Keynes Central",
                originIsFirstOnRoute = false,
                boarded = false,
            ),
        )
        val option = JourneyOption(
            serviceId = "svc-1",
            originCrs = "APS",
            destinationCrs = "EUS",
            destinationName = "London Euston",
            scheduledDepartureLabel = "17:28",
            expectedDepartureLabel = "On time",
            scheduledArrivalLabel = "18:10",
            expectedArrivalLabel = "On time",
            platform = "3",
            operatorName = "Avanti",
            status = TrainStatus.OnTime,
            delayMinutes = 0,
            callingPoints = listOf(
                CallingPoint("Milton Keynes Central", "MKC", "17:05", "On time"),
                CallingPoint("Apsley", "APS", "17:28", "On time"),
                CallingPoint("London Euston", "EUS", "18:10", "On time"),
            ),
            isCancelled = false,
        )
        val result = reduce(
            waiting.copy(pin = waiting.pin.copy(originCrs = "APS", originName = "Apsley")),
            CommuteEvent.ServiceDetails(option),
            progressNow = LocalDateTime.of(2026, 8, 16, 17, 26),
        )
        assertEquals(
            OnTrainRidePhase.AwaitingDeparture,
            (result.session as CommuteSession.OnTrain).ridePhase,
        )
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

    private fun onTrain(
        dest: String = "RDG",
        ridePhase: OnTrainRidePhase = OnTrainRidePhase.EnRoute,
    ): CommuteSession.OnTrain =
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
            ridePhase = ridePhase,
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
            ),
        )
    }

    private fun nearEvent(distance: Double = 280.0, epochMs: Long = 0L) = CommuteEvent.NearStation(
        crs = "PAD",
        name = "London Paddington",
        latitude = pad.latitude,
        longitude = pad.longitude,
        distanceMetres = distance,
        epochMs = epochMs,
    )

    private fun atEvent(distance: Double = 40.0, epochMs: Long = 0L) = CommuteEvent.AtStation(
        crs = "PAD",
        name = "London Paddington",
        latitude = pad.latitude,
        longitude = pad.longitude,
        distanceMetres = distance,
        epochMs = epochMs,
    )

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
