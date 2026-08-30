package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.model.StationWithDistance
import java.time.LocalDateTime
import java.time.ZoneId

fun reduce(
    session: CommuteSession,
    event: CommuteEvent,
    config: WalkUpConfig = WalkUpConfig(),
    boardingConfig: BoardingConfig = BoardingConfig(),
    stationStopConfig: StationStopConfig = StationStopConfig(),
    cachedBoardingServices: List<BoardingServiceSnapshot> = emptyList(),
    routeStations: List<RouteStation> = emptyList(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    progressNow: LocalDateTime = LocalDateTime.now(),
): CommuteTransition {
    return when (event) {
        is CommuteEvent.Location -> reduceLocation(
            session = session,
            event = event,
            config = config,
            boardingConfig = boardingConfig,
            stationStopConfig = stationStopConfig,
            cachedBoardingServices = cachedBoardingServices,
            routeStations = routeStations,
            zoneId = zoneId,
        )
        is CommuteEvent.SetOriginOverride -> {
            if (session is CommuteSession.OnTrain) return CommuteTransition(session)
            val next = CommuteSession.AtStation(origin = event.origin, boarding = BoardingPhase.Idle)
            CommuteTransition(
                session = next,
                effects = listOf(
                    CommuteEffect.LoadFavouriteBoundDepartures(
                        originCrs = event.origin.crs,
                        forceRefresh = true,
                    ),
                ),
            )
        }
        is CommuteEvent.OsGeofenceEnter -> reduceOsGeofenceEnter(session, event)
        is CommuteEvent.OpenedForFrequentChecks -> reduceOpenedForFrequentChecks(session, event)
        CommuteEvent.ClearOverride,
        CommuteEvent.PermissionLost,
        CommuteEvent.Dismiss -> {
            if (session is CommuteSession.OnTrain) CommuteTransition(session)
            else CommuteTransition(CommuteSession.Idle)
        }
        is CommuteEvent.Confirm -> pinAssumed(session, event.nowEpochMs)
        is CommuteEvent.SelectService -> pinSelected(session, event.serviceId, event.nowEpochMs)
        CommuteEvent.ShowPicker -> {
            val confirming = session as? CommuteSession.Confirming
                ?: return CommuteTransition(session)
            CommuteTransition(confirming.copy(showPicker = true))
        }
        is CommuteEvent.Tick -> reduceTick(session, event.nowEpochMs, boardingConfig)
        is CommuteEvent.RestorePin -> restorePin(session, event.journey)
        CommuteEvent.PinCleared -> finishIfOnTrain(session, log = false, clearPin = false)
        CommuteEvent.EndTrip -> finishIfOnTrain(session, log = true, clearPin = true)
        CommuteEvent.DiscardTrip -> finishIfOnTrain(session, log = false, clearPin = true)
        is CommuteEvent.ServiceDetails -> reduceServiceDetails(session, event.option, progressNow)
    }
}

private fun pinAssumed(session: CommuteSession, nowEpochMs: Long): CommuteTransition {
    val confirming = session as? CommuteSession.Confirming ?: return CommuteTransition(session)
    return pinSnapshot(confirming.assumed, nowEpochMs)
}

private fun pinSelected(
    session: CommuteSession,
    serviceId: String,
    nowEpochMs: Long,
): CommuteTransition {
    val confirming = session as? CommuteSession.Confirming ?: return CommuteTransition(session)
    val chosen = sequenceOf(confirming.assumed)
        .plus(confirming.alternatives)
        .firstOrNull { it.serviceId == serviceId }
        ?: return CommuteTransition(session)
    return pinSnapshot(chosen, nowEpochMs)
}

private fun pinSnapshot(snapshot: BoardingServiceSnapshot, nowEpochMs: Long): CommuteTransition {
    val pin = snapshot.toPinned(nowEpochMs)
    return CommuteTransition(
        session = CommuteSession.OnTrain(pin = pin),
        effects = listOf(
            CommuteEffect.Pin(pin),
            CommuteEffect.StartTracking(destinationName = pin.destinationName),
        ),
    )
}

private fun restorePin(session: CommuteSession, journey: PinnedJourney): CommuteTransition {
    val current = session as? CommuteSession.OnTrain
    if (current != null && current.pin.serviceId == journey.serviceId) {
        return CommuteTransition(session)
    }
    return CommuteTransition(
        session = CommuteSession.OnTrain(pin = journey),
        effects = listOf(CommuteEffect.StartTracking(destinationName = journey.destinationName)),
    )
}

private fun abandonTrip(log: Boolean, clearPin: Boolean): CommuteTransition {
    val effects = buildList {
        if (log) add(CommuteEffect.WriteJourneyLog)
        add(CommuteEffect.StopTracking)
        if (clearPin) add(CommuteEffect.ClearPin)
    }
    return CommuteTransition(CommuteSession.Idle, effects)
}

private fun finishIfOnTrain(
    session: CommuteSession,
    log: Boolean,
    clearPin: Boolean,
): CommuteTransition {
    if (session !is CommuteSession.OnTrain) return CommuteTransition(session)
    return abandonTrip(log = log, clearPin = clearPin)
}

private fun reduceServiceDetails(
    session: CommuteSession,
    option: JourneyOption,
    progressNow: LocalDateTime,
): CommuteTransition {
    val onTrain = session as? CommuteSession.OnTrain ?: return CommuteTransition(session)
    val progress = ComputeJourneyProgress.compute(
        callingPoints = option.callingPoints,
        originCrs = onTrain.pin.originCrs,
        destinationCrs = onTrain.pin.destinationCrs,
        destinationNameFallback = onTrain.pin.destinationName,
        confirmedStopCrs = onTrain.confirmedStopCrs,
        now = progressNow,
    )
    if (progress.destinationArrived) {
        return abandonTrip(log = true, clearPin = true)
    }
    val due = ArrivalBuzzTracker.thresholdsToFire(
        minutesRemaining = progress.minutesToDestination,
        alreadyFired = onTrain.buzzed,
        approachingDestination = progress.approachingDestination,
    )
    val next = onTrain.copy(buzzed = ArrivalBuzzTracker.withFired(onTrain.buzzed, due))
    val effects = buildList {
        add(
            CommuteEffect.UpdateTracking(
                destinationName = progress.destinationName,
                minutesRemaining = progress.minutesToDestination,
            ),
        )
        if (due.isNotEmpty()) add(CommuteEffect.Vibrate(due))
    }
    return CommuteTransition(next, effects)
}

private fun reduceTick(
    session: CommuteSession,
    nowEpochMs: Long,
    boardingConfig: BoardingConfig,
): CommuteTransition {
    val confirming = session as? CommuteSession.Confirming ?: return CommuteTransition(session)
    if (nowEpochMs < confirming.triggeredAtEpochMs + boardingConfig.autoAcceptMillis) {
        return CommuteTransition(session)
    }
    return pinAssumed(session, nowEpochMs)
}

private fun reduceOsGeofenceEnter(
    session: CommuteSession,
    event: CommuteEvent.OsGeofenceEnter,
): CommuteTransition {
    if (session is CommuteSession.OnTrain) return CommuteTransition(session)
    return CommuteTransition(
        session = session,
        effects = listOf(
            CommuteEffect.NotifyProximity(
                crs = event.crs,
                stationName = event.stationName,
            ),
        ),
    )
}

private fun reduceOpenedForFrequentChecks(
    session: CommuteSession,
    event: CommuteEvent.OpenedForFrequentChecks,
): CommuteTransition {
    if (session is CommuteSession.OnTrain) return CommuteTransition(session)
    return CommuteTransition(
        session = session,
        effects = listOf(CommuteEffect.ElevateFrequentChecks(event.untilEpochMs)),
    )
}

private fun reduceLocation(
    session: CommuteSession,
    event: CommuteEvent.Location,
    config: WalkUpConfig,
    boardingConfig: BoardingConfig,
    stationStopConfig: StationStopConfig,
    cachedBoardingServices: List<BoardingServiceSnapshot>,
    routeStations: List<RouteStation>,
    zoneId: ZoneId,
): CommuteTransition {
    if (session is CommuteSession.OnTrain) {
        return reduceOnTrainLocation(session, event, stationStopConfig, routeStations)
    }
    if (session is CommuteSession.Confirming) {
        return CommuteTransition(session)
    }
    if (session.isManualOriginOverride()) {
        return CommuteTransition(session)
    }
    val at = session as? CommuteSession.AtStation
    if (at != null) {
        val boardingNext = DetectBoarding.next(
            phase = at.boarding,
            sample = event.sample,
            originLatitude = at.origin.latitude,
            originLongitude = at.origin.longitude,
            speedMps = event.speedMps,
            config = boardingConfig,
        )
        if (boardingNext is BoardingPhase.Triggered && at.boarding !is BoardingPhase.Triggered) {
            return matchBoarding(at.origin, boardingNext, cachedBoardingServices, boardingConfig, zoneId)
        }
        if (boardingNext is BoardingPhase.Sustaining) {
            return CommuteTransition(at.copy(boarding = boardingNext))
        }
        return composeWalkUpAfterIdleBoarding(at, event, config, boardingConfig)
    }
    return walkUpTransition(session, event, config)
}

private fun matchBoarding(
    origin: LockedOrigin,
    triggered: BoardingPhase.Triggered,
    cached: List<BoardingServiceSnapshot>,
    boardingConfig: BoardingConfig,
    zoneId: ZoneId,
): CommuteTransition {
    val match = MatchBoardingCandidates.match(
        cached = cached,
        boardedAtEpochMs = triggered.triggeredAtEpochMs,
        pastMinutes = boardingConfig.matchPastMinutes,
        futureMinutes = boardingConfig.matchFutureMinutes,
        zoneId = zoneId,
    ) ?: return CommuteTransition(CommuteSession.Idle)
    return CommuteTransition(
        session = CommuteSession.Confirming(
            origin = origin,
            assumed = match.assumed,
            alternatives = match.alternatives,
            triggeredAtEpochMs = triggered.triggeredAtEpochMs,
        ),
    )
}

private fun composeWalkUpAfterIdleBoarding(
    at: CommuteSession.AtStation,
    event: CommuteEvent.Location,
    config: WalkUpConfig,
    boardingConfig: BoardingConfig,
): CommuteTransition {
    val walk = walkUpTransition(at, event, config)
    val walkNext = walk.session
    if (walkNext is CommuteSession.AtStation) {
        return CommuteTransition(
            session = walkNext.copy(boarding = BoardingPhase.Idle),
            effects = walk.effects,
        )
    }
    val distance = GeoUtils.haversineMetres(
        at.origin.latitude,
        at.origin.longitude,
        event.sample.latitude,
        event.sample.longitude,
    )
    val speedMps = event.speedMps
    val walkedAway =
        distance >= boardingConfig.leaveMetres &&
            (speedMps == null || speedMps < boardingConfig.boardMinMps)
    val stillNear = distance < boardingConfig.leaveMetres
    return when {
        walkedAway -> CommuteTransition(CommuteSession.Idle)
        stillNear -> CommuteTransition(at.copy(boarding = BoardingPhase.Idle))
        else -> walk
    }
}

private fun reduceOnTrainLocation(
    session: CommuteSession.OnTrain,
    event: CommuteEvent.Location,
    stationStopConfig: StationStopConfig,
    routeStations: List<RouteStation>,
): CommuteTransition {
    val nextStop = DetectStationStop.next(
        phase = session.stop,
        sample = event.sample,
        speedMps = event.speedMps,
        routeStations = routeStations,
        config = stationStopConfig,
    )
    val confirmedCrs = when (nextStop) {
        is StationStopPhase.Confirmed -> nextStop.crs
        else -> session.confirmedStopCrs
    }
    val nextSession = session.copy(stop = nextStop, confirmedStopCrs = confirmedCrs)
    val newConfirmed = nextStop as? StationStopPhase.Confirmed
    if (newConfirmed != null &&
        newConfirmed.crs.equals(session.pin.destinationCrs, ignoreCase = true)
    ) {
        return abandonTrip(log = true, clearPin = true)
    }
    val isNewStop = newConfirmed != null &&
        !newConfirmed.crs.equals(session.confirmedStopCrs, ignoreCase = true)
    val effects = if (isNewStop) {
        listOf(CommuteEffect.RefreshServiceDetails)
    } else {
        emptyList()
    }
    return CommuteTransition(nextSession, effects)
}

private fun walkUpTransition(
    session: CommuteSession,
    event: CommuteEvent.Location,
    config: WalkUpConfig,
): CommuteTransition {
    val phase = session.toWalkUpPhase()
    val nextPhase = DetectWalkUpArrival.next(
        phase = phase,
        sample = event.sample,
        nearest = event.nearest,
        speedMps = event.speedMps,
        config = config,
        alreadyPresentOk = event.alreadyPresentOk && session is CommuteSession.Idle,
    )
    val nextSession = nextPhase.toSession(event.nearest, session)
    return CommuteTransition(
        session = nextSession,
        effects = loadEffects(session, nextSession),
    )
}

internal fun CommuteSession.toWalkUpPhase(): WalkUpPhase = when (this) {
    CommuteSession.Idle,
    is CommuteSession.OnTrain -> WalkUpPhase.Away
    is CommuteSession.Near -> dwelling
    is CommuteSession.AtStation -> WalkUpPhase.Arrived(
        crs = origin.crs,
        stationName = origin.name,
        distanceMetres = origin.distanceMetres,
        lockedAtEpochMs = origin.lockedAtEpochMs,
    )
    is CommuteSession.Confirming -> WalkUpPhase.Arrived(
        crs = origin.crs,
        stationName = origin.name,
        distanceMetres = origin.distanceMetres,
        lockedAtEpochMs = origin.lockedAtEpochMs,
    )
}

private fun WalkUpPhase.toSession(
    nearest: StationWithDistance?,
    previous: CommuteSession,
): CommuteSession = when (this) {
    WalkUpPhase.Away -> CommuteSession.Idle
    is WalkUpPhase.Dwelling -> CommuteSession.Near(this)
    is WalkUpPhase.Arrived -> {
        val previousOrigin = previous.lockedOriginOrNull()
        val station = nearest?.station
        val latitude = when {
            previousOrigin != null && previousOrigin.crs.equals(crs, ignoreCase = true) ->
                previousOrigin.latitude
            station != null -> station.latitude
            previousOrigin != null -> previousOrigin.latitude
            else -> 0.0
        }
        val longitude = when {
            previousOrigin != null && previousOrigin.crs.equals(crs, ignoreCase = true) ->
                previousOrigin.longitude
            station != null -> station.longitude
            previousOrigin != null -> previousOrigin.longitude
            else -> 0.0
        }
        val boarding = (previous as? CommuteSession.AtStation)
            ?.takeIf { it.origin.crs.equals(crs, ignoreCase = true) }
            ?.boarding
            ?: BoardingPhase.Idle
        CommuteSession.AtStation(
            origin = LockedOrigin(
                crs = crs,
                name = stationName,
                latitude = latitude,
                longitude = longitude,
                lockedAtEpochMs = lockedAtEpochMs,
                distanceMetres = distanceMetres,
                manualOverride = false,
            ),
            boarding = boarding,
        )
    }
}

private fun loadEffects(previous: CommuteSession, next: CommuteSession): List<CommuteEffect> {
    val nextAt = next as? CommuteSession.AtStation ?: return emptyList()
    val previousCrs = previous.lockedOriginOrNull()?.crs
    if (previousCrs != null && previousCrs.equals(nextAt.origin.crs, ignoreCase = true)) {
        return emptyList()
    }
    return listOf(
        CommuteEffect.LoadFavouriteBoundDepartures(
            originCrs = nextAt.origin.crs,
            forceRefresh = false,
        ),
    )
}

private fun BoardingServiceSnapshot.toPinned(nowEpochMs: Long): PinnedJourney =
    PinnedJourney(
        serviceId = serviceId,
        originCrs = originCrs,
        destinationCrs = destinationCrs,
        originName = originName,
        destinationName = destinationName,
        operatorName = operatorName,
        scheduledDepartureLabel = scheduledDepartureLabel,
        pinnedAtEpochMs = nowEpochMs,
    )
