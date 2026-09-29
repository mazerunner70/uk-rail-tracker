package com.ukrailtracker.app.ui.home

import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.ComputeJourneyProgress
import com.ukrailtracker.app.domain.usecase.PlatformWatchPhase
import com.ukrailtracker.app.domain.usecase.RouteStation
import com.ukrailtracker.app.domain.usecase.ComputeTrainApproach
import com.ukrailtracker.app.domain.usecase.OnTrainRidePhase
import com.ukrailtracker.app.domain.usecase.atCallingPointOrNull
import com.ukrailtracker.app.domain.usecase.isUnexpected
import com.ukrailtracker.app.domain.usecase.tracksJourneyProgress
import com.ukrailtracker.app.domain.usecase.waitingAtOriginOrNull
import com.ukrailtracker.app.ui.station.StatusKind
import java.time.LocalDateTime

/**
 * Maps commute session + auxiliary data to status-bar chrome and an optional trip body.
 * When [MappedCommutePresence.tripBody] is null, Home shows [HomeBodyUi.StationList].
 */
fun mapCommutePresence(
    session: CommuteSession,
    aux: CommuteAuxState,
    lastLoadedOriginCrs: String? = null,
): MappedCommutePresence {
    when (aux.overlay) {
        PresenceOverlay.NeedsPermission -> {
            return MappedCommutePresence(
                chrome = PresenceChromeUi.NeedsPermission,
                tripBody = null,
            )
        }
        PresenceOverlay.LocationUnavailable -> {
            if (session !is CommuteSession.OnTrain && session !is CommuteSession.AtStation) {
                return MappedCommutePresence(
                    chrome = PresenceChromeUi.LocationUnavailable,
                    tripBody = null,
                )
            }
        }
        PresenceOverlay.None -> Unit
    }
    return when (session) {
        CommuteSession.Idle -> MappedCommutePresence(
            chrome = PresenceChromeUi.NotAtStation,
            tripBody = null,
        )
        is CommuteSession.Near -> MappedCommutePresence(
            chrome = PresenceChromeUi.NearStation(
                crsCode = session.crs,
                stationName = session.name,
                distanceLabel = GeoUtils.formatDistance(session.distanceMetres),
            ),
            tripBody = null,
        )
        is CommuteSession.AtStation -> mapAtStationPresence(session, aux.atStation, lastLoadedOriginCrs)
        is CommuteSession.Confirming -> {
            val assumed = session.assumed.toBoardingCandidateUi()
            MappedCommutePresence(
                chrome = PresenceChromeUi.Boarding(
                    originName = session.origin.name,
                    assumedDepartureLabel = assumed.scheduledDepartureLabel,
                    assumedDestinationName = assumed.destinationName,
                ),
                tripBody = HomeBodyUi.BoardingPrompt(
                    originCrs = session.origin.crs,
                    originName = session.origin.name,
                    assumed = assumed,
                    alternatives = session.alternatives.map { it.toBoardingCandidateUi() },
                    showPicker = session.showPicker,
                ),
            )
        }
        is CommuteSession.OnTrain -> mapOnTrainPresence(session, aux.onTrain)
    }
}

fun mergeAtStationAuxForArrival(
    session: CommuteSession.AtStation,
    current: AtStationAux,
    lastLoadedOriginCrs: String?,
): AtStationAux {
    val phase = session.origin
    val sameStation = current.originCrs.equals(phase.crs, ignoreCase = true) && !phase.manualOverride
    val loading = !phase.crs.equals(lastLoadedOriginCrs, ignoreCase = true)
    return if (sameStation) {
        current.copy(
            originCrs = phase.crs,
            destinationsLoading = loading || current.destinationsLoading,
        )
    } else {
        AtStationAux(
            originCrs = phase.crs,
            destinationsLoading = loading,
        )
    }
}

fun buildOnTrainUiAux(
    option: JourneyOption,
    onTrain: CommuteSession.OnTrain,
    routeStations: List<RouteStation> = emptyList(),
    now: LocalDateTime = LocalDateTime.now(),
): OnTrainUiAux {
    val pinned = onTrain.pin
    val preDeparture = mapPreDepartureUi(onTrain.ridePhase, option, pinned.originCrs, pinned.destinationCrs, now)
    if (!onTrain.ridePhase.tracksJourneyProgress()) {
        val statusKind = when (option.status) {
            TrainStatus.OnTime -> StatusKind.OnTime
            TrainStatus.Delayed -> StatusKind.Delayed
            TrainStatus.Cancelled -> StatusKind.Cancelled
            else -> StatusKind.Other
        }
        return OnTrainUiAux(
            destinationName = pinned.destinationName,
            delayMinutes = option.delayMinutes,
            statusKind = statusKind,
            refreshing = false,
            preDeparture = preDeparture,
        )
    }
    val atCalling = onTrain.ridePhase.atCallingPointOrNull()
    val routeMismatch = atCalling?.expectation?.isUnexpected() == true
    val progress = ComputeJourneyProgress.compute(
        callingPoints = option.callingPoints,
        originCrs = pinned.originCrs,
        destinationCrs = pinned.destinationCrs,
        destinationNameFallback = pinned.destinationName,
        progressAnchorCrs = onTrain.progressAnchorCrs,
        atCallingPoint = atCalling != null,
        journeyFraction = onTrain.journeyFraction,
        routeMismatch = routeMismatch,
        routeStations = routeStations,
        now = now,
    )
    val statusKind = when (option.status) {
        TrainStatus.OnTime -> StatusKind.OnTime
        TrainStatus.Delayed -> StatusKind.Delayed
        TrainStatus.Cancelled -> StatusKind.Cancelled
        else -> StatusKind.Other
    }
    val mismatchName = if (routeMismatch) {
        atCalling?.name ?: progress.currentStopName
    } else {
        null
    }
    return OnTrainUiAux(
        destinationName = progress.destinationName,
        minutesRemaining = progress.minutesToDestination,
        nextStopName = progress.nextStopName,
        currentStopName = atCalling?.name ?: progress.currentStopName,
        delayMinutes = option.delayMinutes,
        statusKind = statusKind,
        routeMismatch = routeMismatch,
        mismatchStationName = mismatchName,
        expectedNextStopName = progress.timetableNextStopName,
        points = progress.points,
        fractionComplete = progress.journeyFraction,
        predictedDurationMinutes = progress.predictedDurationMinutes,
        stoppedAtStation = atCalling != null,
        refreshing = false,
        errorMessage = null,
        serviceMissing = false,
        preDeparture = null,
    )
}

private fun mapPreDepartureUi(
    ridePhase: OnTrainRidePhase,
    option: JourneyOption,
    originCrs: String,
    destinationCrs: String,
    now: LocalDateTime,
): PreDepartureUi? {
    val waiting = ridePhase.waitingAtOriginOrNull()
    val awaitingDeparture = ridePhase is OnTrainRidePhase.AwaitingDeparture
    if (waiting == null && !awaitingDeparture) return null

    val approach = ComputeTrainApproach.compute(
        callingPoints = option.callingPoints,
        originCrs = originCrs,
        destinationCrs = destinationCrs,
        now = now,
    )
    val minutesUntilTrainArrives = waiting?.minutesUntilTrainArrives
        ?: approach.minutesUntilOriginArrival
    val showBoardedButton = waiting != null
    return PreDepartureUi(
        originStationName = waiting?.originStationName ?: approach.originStationName,
        minutesUntilTrainArrives = minutesUntilTrainArrives,
        approachingFromStopName = waiting?.approachingFromStopName ?: approach.approachingFromStopName,
        originIsFirstOnRoute = waiting?.originIsFirstOnRoute ?: approach.originIsFirstOnRoute,
        awaitingDeparture = awaitingDeparture,
        showBoardedButton = showBoardedButton,
    )
}

fun onTrainSkeletonAux(): OnTrainUiAux = OnTrainUiAux(refreshing = true)

private fun mapAtStationPresence(
    session: CommuteSession.AtStation,
    atStation: AtStationAux,
    lastLoadedOriginCrs: String?,
): MappedCommutePresence {
    val phase = session.origin
    val merged = mergeAtStationAuxForArrival(session, atStation, lastLoadedOriginCrs)
    val distanceLabel = if (phase.manualOverride) {
        null
    } else {
        GeoUtils.formatDistance(phase.distanceMetres)
    }
    val body = HomeBodyUi.AtStation(
        crsCode = phase.crs,
        stationName = phase.name,
        distanceLabel = distanceLabel,
        manualOverride = phase.manualOverride,
        destinations = merged.destinations,
        destinationsLoading = merged.destinationsLoading,
        noFavouritesConfigured = merged.noFavouritesConfigured,
        platformWatch = mapPlatformWatchUi(session.platformWatch, merged.platformWatch),
    )
    return MappedCommutePresence(
        chrome = PresenceChromeUi.AtStation(
            crsCode = phase.crs,
            stationName = phase.name,
            distanceLabel = distanceLabel,
            manualOverride = phase.manualOverride,
        ),
        tripBody = body,
    )
}

fun mapPlatformWatchUi(
    phase: PlatformWatchPhase,
    auxWatch: PlatformWatchUi?,
): PlatformWatchUi? = when (phase) {
    PlatformWatchPhase.Idle -> null
    is PlatformWatchPhase.Watching -> {
        val live = auxWatch?.takeIf { it.serviceId == phase.serviceId }
        PlatformWatchUi(
            serviceId = phase.serviceId,
            originCrs = phase.originCrs,
            destinationCrs = phase.destinationCrs,
            destinationName = phase.destinationName,
            scheduledDepartureLabel = live?.scheduledDepartureLabel
                ?.takeIf { it.isNotBlank() }
                ?: phase.scheduledDepartureLabel,
            expectedDepartureLabel = live?.expectedDepartureLabel
                ?: phase.expectedDepartureLabel,
            platform = live?.platform ?: phase.platform,
            checkedAtLabel = live?.checkedAtLabel,
            polling = live?.polling ?: true,
            errorMessage = live?.errorMessage,
        )
    }
}

private fun mapOnTrainPresence(
    session: CommuteSession.OnTrain,
    onTrain: OnTrainUiAux,
): MappedCommutePresence {
    val pinned = session.pin
    val body = HomeBodyUi.OnJourney(
        serviceId = pinned.serviceId,
        originCrs = pinned.originCrs,
        destinationCrs = pinned.destinationCrs,
        destinationName = onTrain.destinationName ?: pinned.destinationName,
        scheduledDepartureLabel = pinned.scheduledDepartureLabel,
        minutesRemaining = onTrain.minutesRemaining,
        nextStopName = onTrain.nextStopName,
        currentStopName = onTrain.currentStopName,
        delayMinutes = onTrain.delayMinutes,
        statusKind = onTrain.statusKind,
        routeMismatch = onTrain.routeMismatch,
        mismatchStationName = onTrain.mismatchStationName,
        expectedNextStopName = onTrain.expectedNextStopName,
        points = onTrain.points,
        fractionComplete = onTrain.fractionComplete,
        predictedDurationMinutes = onTrain.predictedDurationMinutes,
        stoppedAtStation = onTrain.stoppedAtStation,
        refreshing = onTrain.refreshing,
        errorMessage = onTrain.errorMessage,
        serviceMissing = onTrain.serviceMissing,
        preDeparture = onTrain.preDeparture,
        showJourneyProgress = session.ridePhase.tracksJourneyProgress(),
    )
    return MappedCommutePresence(
        chrome = PresenceChromeUi.OnJourney(
            destinationName = body.destinationName,
            originCrs = body.originCrs,
            destinationCrs = body.destinationCrs,
            preDeparture = body.preDeparture,
            showJourneyProgress = body.showJourneyProgress,
            stoppedAtStation = body.stoppedAtStation,
            currentStopName = body.currentStopName,
            nextStopName = body.nextStopName,
        ),
        tripBody = body,
    )
}

fun BoardingServiceSnapshot.toBoardingCandidateUi(): BoardingCandidateUi =
    BoardingCandidateUi(
        serviceId = serviceId,
        originCrs = originCrs,
        destinationCrs = destinationCrs,
        destinationName = destinationName,
        originName = originName,
        operatorName = operatorName,
        scheduledDepartureLabel = scheduledDepartureLabel,
        expectedDepartureLabel = expectedDepartureLabel,
        platform = platform,
    )
