package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.StationWithDistance

fun reduce(
    session: CommuteSession,
    event: CommuteEvent,
    config: WalkUpConfig = WalkUpConfig(),
): CommuteTransition {
    return when (event) {
        is CommuteEvent.Location -> reduceLocation(session, event, config)
        is CommuteEvent.SetOriginOverride -> {
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
        is CommuteEvent.OpenedForFrequentChecks -> reduceOpenedForFrequentChecks(session, event.origin)
        CommuteEvent.ClearOverride,
        CommuteEvent.PermissionLost -> CommuteTransition(CommuteSession.Idle)
    }
}

private fun reduceOpenedForFrequentChecks(
    session: CommuteSession,
    origin: LockedOrigin,
): CommuteTransition {
    val load = listOf(
        CommuteEffect.LoadFavouriteBoundDepartures(
            originCrs = origin.crs,
            forceRefresh = true,
        ),
    )
    val at = session as? CommuteSession.AtStation
    if (at != null && at.origin.crs.equals(origin.crs, ignoreCase = true)) {
        return CommuteTransition(session = session, effects = load)
    }
    return CommuteTransition(
        session = CommuteSession.AtStation(
            origin = origin.copy(manualOverride = false),
            boarding = BoardingPhase.Idle,
        ),
        effects = load,
    )
}

private fun reduceLocation(
    session: CommuteSession,
    event: CommuteEvent.Location,
    config: WalkUpConfig,
): CommuteTransition {
    if (session.isManualOriginOverride()) {
        return CommuteTransition(session)
    }
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
    CommuteSession.Idle -> WalkUpPhase.Away
    is CommuteSession.Near -> dwelling
    is CommuteSession.AtStation -> WalkUpPhase.Arrived(
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
            boarding = BoardingPhase.Idle,
        )
    }
}

private fun loadEffects(previous: CommuteSession, next: CommuteSession): List<CommuteEffect> {
    val nextAt = next as? CommuteSession.AtStation ?: return emptyList()
    val previousCrs = (previous as? CommuteSession.AtStation)?.origin?.crs
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
