package com.ukrailtracker.app.ui.home

import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.ComputeJourneyProgress
import com.ukrailtracker.app.domain.usecase.PlatformWatchPhase
import com.ukrailtracker.app.domain.usecase.StationStopPhase
import com.ukrailtracker.app.ui.station.StatusKind
import java.time.LocalDateTime

/**
 * Single mapping from commute session + auxiliary data to the Home presence UI contract.
 */
fun mapCommuteWalkUpUi(
    session: CommuteSession,
    aux: CommuteAuxState,
    lastLoadedOriginCrs: String? = null,
): WalkUpHomeUi? {
    when (aux.overlay) {
        PresenceOverlay.NeedsPermission -> return WalkUpHomeUi.NeedsPermission
        PresenceOverlay.LocationUnavailable -> {
            if (session !is CommuteSession.OnTrain && session !is CommuteSession.AtStation) {
                return WalkUpHomeUi.LocationUnavailable
            }
        }
        PresenceOverlay.None -> Unit
    }
    return when (session) {
        CommuteSession.Idle -> WalkUpHomeUi.NotAtStation
        is CommuteSession.Near -> WalkUpHomeUi.NearStation(
            crsCode = session.dwelling.crs,
            stationName = session.dwelling.stationName,
            distanceLabel = GeoUtils.formatDistance(session.dwelling.distanceMetres),
        )
        is CommuteSession.AtStation -> mapAtStationUi(session, aux.atStation, lastLoadedOriginCrs)
        is CommuteSession.Confirming -> WalkUpHomeUi.BoardingPrompt(
            originCrs = session.origin.crs,
            originName = session.origin.name,
            assumed = session.assumed.toBoardingCandidateUi(),
            alternatives = session.alternatives.map { it.toBoardingCandidateUi() },
            showPicker = session.showPicker,
        )
        is CommuteSession.OnTrain -> mapOnTrainUi(session, aux.onTrain)
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
    now: LocalDateTime = LocalDateTime.now(),
): OnTrainUiAux {
    val pinned = onTrain.pin
    val progress = ComputeJourneyProgress.compute(
        callingPoints = option.callingPoints,
        originCrs = pinned.originCrs,
        destinationCrs = pinned.destinationCrs,
        destinationNameFallback = pinned.destinationName,
        confirmedStopCrs = onTrain.confirmedStopCrs,
        now = now,
    )
    val statusKind = when (option.status) {
        TrainStatus.OnTime -> StatusKind.OnTime
        TrainStatus.Delayed -> StatusKind.Delayed
        TrainStatus.Cancelled -> StatusKind.Cancelled
        else -> StatusKind.Other
    }
    val stopped = onTrain.stop is StationStopPhase.Confirmed ||
        onTrain.stop is StationStopPhase.Dwelling
    val mismatchName = if (progress.routeMismatch) {
        (onTrain.stop as? StationStopPhase.Confirmed)?.name
            ?: progress.currentStopName
    } else {
        null
    }
    return OnTrainUiAux(
        destinationName = progress.destinationName,
        minutesRemaining = progress.minutesToDestination,
        nextStopName = progress.nextStopName,
        currentStopName = progress.currentStopName,
        delayMinutes = option.delayMinutes,
        statusKind = statusKind,
        routeMismatch = progress.routeMismatch,
        mismatchStationName = mismatchName,
        expectedNextStopName = progress.timetableNextStopName,
        points = progress.points,
        fractionComplete = progress.fractionComplete,
        predictedDurationMinutes = progress.predictedDurationMinutes,
        stoppedAtStation = stopped,
        refreshing = false,
        errorMessage = null,
        serviceMissing = false,
    )
}

fun onTrainSkeletonAux(): OnTrainUiAux = OnTrainUiAux(refreshing = true)

private fun mapAtStationUi(
    session: CommuteSession.AtStation,
    atStation: AtStationAux,
    lastLoadedOriginCrs: String?,
): WalkUpHomeUi.AtStation {
    val phase = session.origin
    val merged = mergeAtStationAuxForArrival(session, atStation, lastLoadedOriginCrs)
    return WalkUpHomeUi.AtStation(
        crsCode = phase.crs,
        stationName = phase.name,
        distanceLabel = if (phase.manualOverride) {
            null
        } else {
            GeoUtils.formatDistance(phase.distanceMetres)
        },
        manualOverride = phase.manualOverride,
        destinations = merged.destinations,
        destinationsLoading = merged.destinationsLoading,
        noFavouritesConfigured = merged.noFavouritesConfigured,
        platformWatch = mapPlatformWatchUi(session.platformWatch, merged.platformWatch),
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

private fun mapOnTrainUi(
    session: CommuteSession.OnTrain,
    onTrain: OnTrainUiAux,
): WalkUpHomeUi.OnJourney {
    val pinned = session.pin
    return WalkUpHomeUi.OnJourney(
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
