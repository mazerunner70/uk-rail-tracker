package com.ukrailtracker.app.domain.session.host

import com.ukrailtracker.app.domain.commute.isOnTrain
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.session.SessionReduceContext
import com.ukrailtracker.app.domain.session.SessionWalkUpSupport
import com.ukrailtracker.app.domain.usecase.ArrivalBuzzTracker
import com.ukrailtracker.app.domain.usecase.CommuteEffect
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition
import com.ukrailtracker.app.domain.usecase.ComputeJourneyProgress
import com.ukrailtracker.app.domain.usecase.ComputeTrainApproach
import com.ukrailtracker.app.domain.usecase.DetectStationStop
import com.ukrailtracker.app.domain.usecase.GpsMotion
import com.ukrailtracker.app.domain.usecase.OnTrainRidePhase
import com.ukrailtracker.app.domain.usecase.RouteStation
import com.ukrailtracker.app.domain.usecase.StopExpectation
import com.ukrailtracker.app.domain.usecase.TrainApproachState
import com.ukrailtracker.app.domain.usecase.atCallingPointOrNull
import com.ukrailtracker.app.domain.usecase.isTrainMovingSpeed
import com.ukrailtracker.app.domain.usecase.isUnexpected
import com.ukrailtracker.app.domain.usecase.onTrainOrNull
import com.ukrailtracker.app.domain.usecase.pinnedJourneyOrNull
import com.ukrailtracker.app.domain.usecase.tracksJourneyProgress
import com.ukrailtracker.app.ui.home.AtStationAux
import com.ukrailtracker.app.ui.home.OnTrainUiAux
import com.ukrailtracker.app.ui.home.buildOnTrainUiAux
import com.ukrailtracker.app.ui.home.onTrainSkeletonAux
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * On-train host.
 *
 * - [onEntry] clears boarding state, starts Darwin poll + progress tick + initial refresh,
 *   and starts foreground tracking.
 * - [onExit] cancels those jobs and resets on-train aux.
 * - [onActiveChange] restarts jobs when pin serviceId changes; ridePhase changes only
 *   affect poll cadence via the running loops reading [SessionHostServices.sessionFlow].
 */
class OnTrainHost(
    private val services: SessionHostServices? = null,
) : SessionHost<CommuteSession.OnTrain> {
    override val typeId: SessionTypeId = SessionTypeId.OnTrain

    private var liveHandoff: SessionHandoff = SessionHandoff()
    private var onTrainPollJob: Job? = null
    private var onTrainProgressTickJob: Job? = null

    override fun handle(
        session: CommuteSession.OnTrain,
        event: CommuteEvent,
        context: SessionReduceContext,
    ): CommuteTransition = when (event) {
        is CommuteEvent.Location -> reduceLocation(session, event, context)
        is CommuteEvent.ServiceDetails -> reduceServiceDetails(
            session = session,
            option = event.option,
            progressNow = context.progressNow,
            routeStations = context.routeStations,
        )
        CommuteEvent.Boarded -> reduceBoarded(session)
        CommuteEvent.EndTrip -> abandonTrip(log = true, clearPin = true)
        CommuteEvent.DiscardTrip -> abandonTrip(log = false, clearPin = true)
        CommuteEvent.PinCleared -> abandonTrip(log = false, clearPin = false)
        is CommuteEvent.RestorePin -> SessionWalkUpSupport.restorePin(session, event.journey)
        is CommuteEvent.SetOriginOverride,
        CommuteEvent.ClearOverride,
        CommuteEvent.Dismiss,
        CommuteEvent.PermissionLost,
        is CommuteEvent.OsGeofenceEnter,
        is CommuteEvent.NearStation,
        is CommuteEvent.AtStation,
        CommuteEvent.LeftStation -> CommuteTransition(session)
        else -> CommuteTransition(session)
    }

    override fun onEntry(
        previousId: SessionTypeId,
        handoff: SessionHandoff,
        session: CommuteSession.OnTrain,
    ): SessionHandoff {
        liveHandoff = handoff.copy(
            cachedBoardingServices = emptyList(),
            latestOnTrainOption = null,
            routeStations = emptyList(),
            aux = handoff.aux.copy(
                atStation = AtStationAux(),
                onTrain = onTrainSkeletonAux(),
            ),
            platformNotified = false,
        )
        publish()
        val svc = services
        if (svc != null) {
            if (svc.handoff.permissionGranted) {
                // Location watch restart is coordinator-owned; entry only starts on-train jobs.
            }
            startOnTrainPolling()
            startOnTrainProgressTick()
            svc.scope.launch { refreshOnTrainOptionInternal() }
            svc.startOnTrainForeground(
                destinationName = session.pin.destinationName,
                minutesRemaining = null,
            )
            liveHandoff = liveHandoff.copy(onTrainServiceStarted = true)
            publish()
        }
        return liveHandoff
    }

    override fun onActiveChange(
        previous: CommuteSession.OnTrain,
        next: CommuteSession.OnTrain,
        handoff: SessionHandoff,
    ): SessionHandoff {
        liveHandoff = handoff
        if (previous.pin.serviceId != next.pin.serviceId) {
            cancelOnTrainJobs()
            liveHandoff = liveHandoff.copy(
                latestOnTrainOption = null,
                routeStations = emptyList(),
                aux = liveHandoff.aux.copy(onTrain = onTrainSkeletonAux()),
            )
            publish()
            val svc = services
            if (svc != null) {
                startOnTrainPolling()
                startOnTrainProgressTick()
                svc.scope.launch { refreshOnTrainOptionInternal() }
                svc.startOnTrainForeground(
                    destinationName = next.pin.destinationName,
                    minutesRemaining = null,
                )
                liveHandoff = liveHandoff.copy(onTrainServiceStarted = true)
                publish()
            }
        } else {
            val option = liveHandoff.latestOnTrainOption
            if (option != null) {
                liveHandoff = liveHandoff.copy(
                    aux = liveHandoff.aux.copy(
                        onTrain = buildOnTrainUiAux(option, next, liveHandoff.routeStations),
                    ),
                )
                publish()
            }
        }
        return liveHandoff
    }

    override fun onExit(nextId: SessionTypeId): SessionHandoff {
        cancelOnTrainJobs()
        services?.stopOnTrainForeground()
        liveHandoff = liveHandoff.copy(
            latestOnTrainOption = null,
            routeStations = emptyList(),
            onTrainServiceStarted = false,
            aux = liveHandoff.aux.copy(onTrain = OnTrainUiAux()),
        )
        publish()
        return liveHandoff
    }

    fun refreshOnTrainOption() {
        val svc = services ?: return
        svc.scope.launch { refreshOnTrainOptionInternal() }
    }

    fun latestOption(): JourneyOption? = liveHandoff.latestOnTrainOption

    fun routeStations(): List<RouteStation> = liveHandoff.routeStations

    private fun reduceBoarded(session: CommuteSession.OnTrain): CommuteTransition {
        if (session.ridePhase !is OnTrainRidePhase.WaitingAtOrigin) {
            return CommuteTransition(session)
        }
        return CommuteTransition(session.copy(ridePhase = OnTrainRidePhase.AwaitingDeparture))
    }

    private fun abandonTrip(log: Boolean, clearPin: Boolean): CommuteTransition {
        val effects = buildList {
            if (log) add(CommuteEffect.WriteJourneyLog)
            add(CommuteEffect.StopTracking)
            if (clearPin) add(CommuteEffect.ClearPin)
        }
        return CommuteTransition(CommuteSession.Idle, effects)
    }

    private fun reduceServiceDetails(
        session: CommuteSession.OnTrain,
        option: JourneyOption,
        progressNow: LocalDateTime,
        routeStations: List<RouteStation>,
    ): CommuteTransition {
        val approach = ComputeTrainApproach.compute(
            callingPoints = option.callingPoints,
            originCrs = session.pin.originCrs,
            destinationCrs = session.pin.destinationCrs,
            now = progressNow,
        )
        var ridePhase = applyApproachToRidePhase(session.ridePhase, approach)
        val atCalling = ridePhase.atCallingPointOrNull()
        ridePhase = if (atCalling != null && atCalling.expectation is StopExpectation.Unknown) {
            atCalling.copy(
                expectation = ComputeJourneyProgress.judgeStopExpectation(
                    confirmedCrs = atCalling.crs,
                    callingPoints = option.callingPoints,
                    originCrs = session.pin.originCrs,
                    destinationCrs = session.pin.destinationCrs,
                    now = progressNow,
                ),
            )
        } else {
            ridePhase
        }

        if (!ridePhase.tracksJourneyProgress()) {
            val next = session.copy(
                ridePhase = ridePhase,
                journeyFraction = 0f,
            )
            return CommuteTransition(
                next,
                listOf(
                    CommuteEffect.UpdateTracking(
                        destinationName = session.pin.destinationName,
                        minutesRemaining = trackingMinutesForPhase(
                            ridePhase = ridePhase,
                            approach = approach,
                            destinationMinutes = null,
                        ),
                    ),
                ),
            )
        }

        val atCallingForProgress = ridePhase.atCallingPointOrNull()
        val routeMismatch =
            (ridePhase as? OnTrainRidePhase.AtCallingPoint)?.expectation?.isUnexpected() == true
        val progress = ComputeJourneyProgress.compute(
            callingPoints = option.callingPoints,
            originCrs = session.pin.originCrs,
            destinationCrs = session.pin.destinationCrs,
            destinationNameFallback = session.pin.destinationName,
            progressAnchorCrs = session.progressAnchorCrs,
            atCallingPoint = atCallingForProgress != null,
            journeyFraction = session.journeyFraction,
            routeMismatch = routeMismatch,
            routeStations = routeStations,
            now = progressNow,
        )
        if (progress.destinationArrived) {
            return abandonTrip(log = true, clearPin = true)
        }
        val due = ArrivalBuzzTracker.thresholdsToFire(
            minutesRemaining = progress.minutesToDestination,
            alreadyFired = session.buzzed,
            approachingDestination = progress.approachingDestination,
        )
        val next = session.copy(
            ridePhase = ridePhase,
            buzzed = ArrivalBuzzTracker.withFired(session.buzzed, due),
        )
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

    private fun reduceLocation(
        session: CommuteSession.OnTrain,
        event: CommuteEvent.Location,
        context: SessionReduceContext,
    ): CommuteTransition {
        val nextMotion = DetectStationStop.next(
            phase = session.gpsMotion,
            sample = event.sample,
            speedMps = event.speedMps,
            routeStations = context.routeStations,
            config = context.stationStopConfig,
        )

        var working = session
        if (!session.ridePhase.tracksJourneyProgress()) {
            val approach = context.onTrainCallingPoints.takeIf { it.isNotEmpty() }?.let {
                ComputeTrainApproach.compute(
                    callingPoints = it,
                    originCrs = session.pin.originCrs,
                    destinationCrs = session.pin.destinationCrs,
                    now = context.progressNow,
                )
            }
            var ridePhase = approach?.let { applyApproachToRidePhase(session.ridePhase, it) }
                ?: session.ridePhase
            ridePhase = maybePromoteToEnRoute(ridePhase, event.speedMps)
            if (!ridePhase.tracksJourneyProgress()) {
                return preDepartureLocationResult(session, nextMotion, ridePhase)
            }
            working = session.copy(
                gpsMotion = nextMotion,
                ridePhase = ridePhase,
            )
        }

        val confirmed = nextMotion as? GpsMotion.Confirmed
        if (confirmed != null &&
            confirmed.crs.equals(working.pin.destinationCrs, ignoreCase = true)
        ) {
            return abandonTrip(log = true, clearPin = true)
        }

        val previousAtCalling = working.ridePhase.atCallingPointOrNull()
        val ridePhase: OnTrainRidePhase
        val progressAnchorCrs: String?
        val effects: List<CommuteEffect>
        val atCallingCrs: String?

        if (confirmed != null) {
            val isNewCrs = !confirmed.crs.equals(working.progressAnchorCrs, ignoreCase = true)
            val enteredOrChanged = previousAtCalling == null ||
                !previousAtCalling.crs.equals(confirmed.crs, ignoreCase = true)
            val expectation = if (
                enteredOrChanged ||
                previousAtCalling?.expectation is StopExpectation.Unknown
            ) {
                ComputeJourneyProgress.judgeStopExpectation(
                    confirmedCrs = confirmed.crs,
                    callingPoints = context.onTrainCallingPoints,
                    originCrs = working.pin.originCrs,
                    destinationCrs = working.pin.destinationCrs,
                    now = context.progressNow,
                )
            } else {
                previousAtCalling!!.expectation
            }
            ridePhase = OnTrainRidePhase.AtCallingPoint(
                crs = confirmed.crs,
                name = confirmed.name,
                confirmedAtEpochMs = confirmed.confirmedAtEpochMs,
                expectation = expectation,
            )
            progressAnchorCrs = confirmed.crs
            atCallingCrs = confirmed.crs
            effects = if (isNewCrs || enteredOrChanged) {
                listOf(CommuteEffect.RefreshServiceDetails)
            } else {
                emptyList()
            }
        } else {
            ridePhase = if (working.ridePhase is OnTrainRidePhase.AtCallingPoint) {
                OnTrainRidePhase.EnRoute
            } else {
                working.ridePhase
            }
            progressAnchorCrs = working.progressAnchorCrs
            atCallingCrs = null
            effects = emptyList()
        }

        val journeyFraction = ComputeJourneyProgress.spatialJourneyFraction(
            latitude = event.sample.latitude,
            longitude = event.sample.longitude,
            progressAnchorCrs = progressAnchorCrs,
            atCallingPointCrs = atCallingCrs,
            originCrs = working.pin.originCrs,
            destinationCrs = working.pin.destinationCrs,
            callingPoints = context.onTrainCallingPoints,
            routeStations = context.routeStations,
            previousFraction = working.journeyFraction,
        )

        return CommuteTransition(
            session = working.copy(
                gpsMotion = nextMotion,
                ridePhase = ridePhase,
                progressAnchorCrs = progressAnchorCrs,
                journeyFraction = journeyFraction,
            ),
            effects = effects,
        )
    }

    private fun preDepartureLocationResult(
        session: CommuteSession.OnTrain,
        nextMotion: GpsMotion,
        ridePhase: OnTrainRidePhase,
    ): CommuteTransition = CommuteTransition(
        session = session.copy(
            gpsMotion = nextMotion,
            ridePhase = ridePhase,
            journeyFraction = 0f,
        ),
    )

    private fun applyApproachToRidePhase(
        phase: OnTrainRidePhase,
        approach: TrainApproachState,
    ): OnTrainRidePhase = when (phase) {
        is OnTrainRidePhase.WaitingAtOrigin -> {
            if (phase.boarded || approach.shouldAutoBoard) {
                OnTrainRidePhase.AwaitingDeparture
            } else {
                phase.copy(
                    originStationName = approach.originStationName,
                    minutesUntilTrainArrives = approach.minutesUntilOriginArrival,
                    approachingFromStopName = approach.approachingFromStopName,
                    originIsFirstOnRoute = approach.originIsFirstOnRoute,
                )
            }
        }
        else -> phase
    }

    private fun maybePromoteToEnRoute(
        phase: OnTrainRidePhase,
        speedMps: Double?,
    ): OnTrainRidePhase = when (phase) {
        OnTrainRidePhase.AwaitingDeparture ->
            if (isTrainMovingSpeed(speedMps)) OnTrainRidePhase.EnRoute else phase
        is OnTrainRidePhase.WaitingAtOrigin ->
            if (phase.boarded && isTrainMovingSpeed(speedMps)) OnTrainRidePhase.EnRoute else phase
        else -> phase
    }

    private fun trackingMinutesForPhase(
        ridePhase: OnTrainRidePhase,
        approach: TrainApproachState?,
        destinationMinutes: Long?,
    ): Long? = when (ridePhase) {
        is OnTrainRidePhase.WaitingAtOrigin -> ridePhase.minutesUntilTrainArrives
            ?: approach?.minutesUntilOriginArrival
        OnTrainRidePhase.AwaitingDeparture -> approach?.minutesUntilDeparture
        else -> destinationMinutes
    }

    private fun startOnTrainPolling() {
        val svc = services ?: return
        onTrainPollJob?.cancel()
        onTrainPollJob = svc.scope.launch {
            while (isActive && svc.sessionFlow.value.isOnTrain()) {
                val atCalling =
                    svc.sessionFlow.value.onTrainOrNull()?.ridePhase is OnTrainRidePhase.AtCallingPoint
                delay(if (atCalling) ON_TRAIN_POLL_STOPPED_MS else ON_TRAIN_POLL_MS)
                if (!svc.sessionFlow.value.isOnTrain()) break
                refreshOnTrainOptionInternal()
            }
        }
    }

    private fun startOnTrainProgressTick() {
        val svc = services ?: return
        onTrainProgressTickJob?.cancel()
        onTrainProgressTickJob = svc.scope.launch {
            while (isActive && svc.sessionFlow.value.isOnTrain()) {
                delay(ON_TRAIN_PROGRESS_TICK_MS)
                val onTrain = svc.sessionFlow.value.onTrainOrNull() ?: break
                val option = liveHandoff.latestOnTrainOption ?: continue
                if (!onTrain.ridePhase.tracksJourneyProgress()) {
                    svc.dispatch(CommuteEvent.ServiceDetails(option))
                } else {
                    liveHandoff = liveHandoff.copy(
                        aux = liveHandoff.aux.copy(
                            onTrain = buildOnTrainUiAux(option, onTrain, liveHandoff.routeStations),
                        ),
                    )
                    publish()
                }
            }
        }
    }

    private fun cancelOnTrainJobs() {
        onTrainPollJob?.cancel()
        onTrainPollJob = null
        onTrainProgressTickJob?.cancel()
        onTrainProgressTickJob = null
    }

    private suspend fun refreshOnTrainOptionInternal() {
        val svc = services ?: return
        val pinned = svc.sessionFlow.value.pinnedJourneyOrNull() ?: return
        svc.updateOnTrainAux { it.copy(refreshing = true, errorMessage = null) }
        syncFromServices()
        try {
            val option = svc.journeyRepository.getServiceDetails(
                serviceId = pinned.serviceId,
                originCrs = pinned.originCrs,
                destinationCrs = pinned.destinationCrs,
                destinationNameFallback = pinned.destinationName,
            )
            if (!svc.sessionFlow.value.isOnTrain()) return
            val route = buildRouteStations(option)
            liveHandoff = liveHandoff.copy(
                latestOnTrainOption = option,
                routeStations = route,
            )
            publish()
            svc.dispatch(CommuteEvent.ServiceDetails(option))
        } catch (t: Throwable) {
            val missing = t.message?.contains("not found", ignoreCase = true) == true ||
                t.message?.contains("HTTP 404", ignoreCase = true) == true
            svc.updateOnTrainAux {
                it.copy(
                    refreshing = false,
                    serviceMissing = missing || it.serviceMissing,
                    errorMessage = t.message ?: "Could not refresh service",
                    minutesRemaining = null,
                    points = emptyList(),
                    fractionComplete = 0f,
                    predictedDurationMinutes = null,
                )
            }
            syncFromServices()
        }
    }

    private suspend fun buildRouteStations(option: JourneyOption): List<RouteStation> {
        val svc = services ?: return emptyList()
        val result = ArrayList<RouteStation>()
        val seen = HashSet<String>()
        for (cp in option.callingPoints) {
            val crs = cp.crs?.trim()?.uppercase().orEmpty()
            if (crs.isEmpty() || !seen.add(crs)) continue
            val station = svc.stationRepository.getByCrs(crs) ?: continue
            result += RouteStation(
                crs = crs,
                name = station.name.ifBlank { cp.locationName },
                latitude = station.latitude,
                longitude = station.longitude,
            )
        }
        return result
    }

    private fun publish() {
        services?.publishHandoff(liveHandoff)
    }

    private fun syncFromServices() {
        val svc = services ?: return
        liveHandoff = svc.handoff.copy(
            latestOnTrainOption = liveHandoff.latestOnTrainOption,
            routeStations = liveHandoff.routeStations,
            onTrainServiceStarted = liveHandoff.onTrainServiceStarted,
        )
    }

    companion object {
        private const val ON_TRAIN_POLL_MS = 45_000L
        private const val ON_TRAIN_POLL_STOPPED_MS = 60_000L
        private const val ON_TRAIN_PROGRESS_TICK_MS = 10_000L
    }
}
