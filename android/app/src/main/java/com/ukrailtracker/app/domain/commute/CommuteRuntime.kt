package com.ukrailtracker.app.domain.commute

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ukrailtracker.app.debug.DebugWalkUp
import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.BoardingConfig
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteEffect
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition
import com.ukrailtracker.app.domain.usecase.DetectWalkUpArrival
import com.ukrailtracker.app.domain.usecase.FavouriteDestinationServices
import com.ukrailtracker.app.domain.usecase.JourneyTimeUtils
import com.ukrailtracker.app.domain.usecase.LoadFavouriteBoundDepartures
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.MatchBoardingCandidates
import com.ukrailtracker.app.domain.usecase.RouteStation
import com.ukrailtracker.app.domain.usecase.StationStopConfig
import com.ukrailtracker.app.domain.usecase.StationStopPhase
import com.ukrailtracker.app.domain.usecase.WalkUpSample
import com.ukrailtracker.app.domain.usecase.isManualOriginOverride
import com.ukrailtracker.app.domain.usecase.lockedOriginOrNull
import com.ukrailtracker.app.domain.usecase.onTrainOrNull
import com.ukrailtracker.app.domain.usecase.pinnedJourneyOrNull
import com.ukrailtracker.app.domain.usecase.platformWatchOrNull
import com.ukrailtracker.app.domain.usecase.reduce
import com.ukrailtracker.app.service.OnTrainTrackingService
import com.ukrailtracker.app.ui.home.AtStationAux
import com.ukrailtracker.app.ui.home.CommuteAuxState
import com.ukrailtracker.app.ui.home.OnTrainUiAux
import com.ukrailtracker.app.ui.home.PlatformWatchUi
import com.ukrailtracker.app.ui.home.PresenceOverlay
import com.ukrailtracker.app.ui.home.WalkUpDestinationUi
import com.ukrailtracker.app.ui.home.WalkUpHomeUi
import com.ukrailtracker.app.ui.home.buildOnTrainUiAux
import com.ukrailtracker.app.ui.home.mapCommuteWalkUpUi
import com.ukrailtracker.app.ui.home.mapPlatformWatchUi
import com.ukrailtracker.app.ui.home.mergeAtStationAuxForArrival
import com.ukrailtracker.app.ui.home.onTrainSkeletonAux
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Owns commute session dispatch, transition side effects, and phase-scoped jobs.
 *
 * Enter/exit hooks (session type transitions):
 * | Transition | Action |
 * |------------|--------|
 * | → Confirming | Start boarding auto-accept tick |
 * | Confirming → * | Cancel boarding tick |
 * | → OnTrain (new pin) | Clear boarding aux, start on-train poll + progress tick, refresh service |
 * | OnTrain → * | Cancel on-train jobs, reset on-train aux |
 * | Idle ← AtStation/Confirming | Stop platform watch, clear at-station aux + boarding cache |
 * | → AtStation (new/changed) | Merge at-station aux; stop platform watch if origin changed |
 * | AtStation → Watching (new service) | Start platform poll job; seed aux snapshot |
 * | Watching → Idle / leave AtStation | Cancel platform poll; clear aux watch |
 *
 * GPS / poll cadence: see [CommuteSessionPolicy] — do not add ad-hoc `is CommuteSession.X` there.
 */
class CommuteRuntime(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val stationRepository: StationRepository,
    private val journeyRepository: JourneyRepository,
    private val locationProvider: LocationProvider,
    private val favouritesStore: CommuteFavouritesAccess,
    private val pinnedJourneyStore: CommutePinStore,
    private val journeyLogRepository: CommuteJourneyLogger,
    private val loadFavouriteBoundDepartures: LoadFavouriteBoundDepartures =
        LoadFavouriteBoundDepartures(journeyRepository, stationRepository),
    private val boardingConfig: BoardingConfig = BoardingConfig(),
    private val stationStopConfig: StationStopConfig = StationStopConfig(),
    private val onLatestSpeedMps: ((Double?) -> Unit)? = null,
) {
    private val _sessionFlow = MutableStateFlow<CommuteSession>(CommuteSession.Idle)
    val sessionFlow: StateFlow<CommuteSession> = _sessionFlow.asStateFlow()

    private val _auxFlow = MutableStateFlow(CommuteAuxState())
    val auxFlow: StateFlow<CommuteAuxState> = _auxFlow.asStateFlow()

    private val _lastLoadedOriginCrs = MutableStateFlow<String?>(null)
    val lastLoadedOriginCrs: StateFlow<String?> = _lastLoadedOriginCrs.asStateFlow()

    private val _arrivalBuzz = MutableSharedFlow<Int>(extraBufferCapacity = 3)
    val arrivalBuzz: SharedFlow<Int> = _arrivalBuzz.asSharedFlow()

    private val _platformAppeared = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val platformAppeared: SharedFlow<String> = _platformAppeared.asSharedFlow()

    private var previousSample: WalkUpSample? = null
    private var locationWatchJob: Job? = null
    private var destinationLoadJob: Job? = null
    private var boardingTickJob: Job? = null
    private var platformWatchJob: Job? = null
    private var onTrainPollJob: Job? = null
    private var onTrainProgressTickJob: Job? = null
    private var pinRestoreJob: Job? = null
    private var permissionGranted: Boolean = false
    private var alreadyPresentOk: Boolean = true
    private var cachedBoardingServices: List<BoardingServiceSnapshot> = emptyList()
    private var platformNotified: Boolean = false
    private var latestOnTrainOption: JourneyOption? = null
    private var routeStations: List<RouteStation> = emptyList()
    private var onTrainServiceStarted: Boolean = false
    private var frequentChecksUntilEpochMs: Long = 0L

    init {
        pinRestoreJob = scope.launch {
            pinnedJourneyStore.pinnedFlow.collect { pinned ->
                val current = _sessionFlow.value.onTrainOrNull()
                if (pinned == null) {
                    if (current != null) {
                        dispatch(CommuteEvent.PinCleared)
                    }
                } else if (current?.pin?.serviceId != pinned.serviceId) {
                    dispatch(CommuteEvent.RestorePin(pinned))
                }
            }
        }
    }

    fun dispatch(event: CommuteEvent) {
        applyTransition(reduceEvent(event))
    }

    fun onPermissionGranted() {
        permissionGranted = true
        _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.None)
        startLocationWatch()
    }

    fun onPermissionDenied() {
        permissionGranted = false
        stopLocationWatch()
        _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.NeedsPermission)
        dispatch(CommuteEvent.PermissionLost)
    }

    fun onNeedsPermission() {
        permissionGranted = false
        stopLocationWatch()
        _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.NeedsPermission)
        dispatch(CommuteEvent.PermissionLost)
    }

    fun onOpenedForFrequentChecks(hintCrs: String? = null) {
        dispatch(
            CommuteEvent.OpenedForFrequentChecks(
                untilEpochMs = System.currentTimeMillis() + FREQUENT_CHECKS_TTL_MS,
                hintCrs = hintCrs?.trim()?.uppercase()?.ifEmpty { null },
            ),
        )
        if (permissionGranted) {
            startLocationWatch(forceRefresh = true)
        }
    }

    fun prepareForDebugWalkUp(elevatePollForMs: Long) {
        scope.launch {
            if (_sessionFlow.value.isOnTrain()) {
                dispatch(CommuteEvent.DiscardTrip)
            }
            clearBoardingSession(keepUi = false)
            cancelBoardingTick()
            stopPlatformWatch()
            _lastLoadedOriginCrs.value = null
            _sessionFlow.value = CommuteSession.Idle
            previousSample = null
            alreadyPresentOk = false
            frequentChecksUntilEpochMs = System.currentTimeMillis() + elevatePollForMs.coerceAtLeast(0L)
            permissionGranted = true
            _auxFlow.value = CommuteAuxState()
            startLocationWatch(forceRefresh = true)
        }
    }

    fun setOriginOverride(crs: String) {
        scope.launch {
            val normalised = crs.trim().uppercase()
            if (normalised.isBlank()) return@launch
            clearBoardingSession(keepUi = false)
            val station = stationRepository.getByCrs(normalised)
            val name = station?.name ?: normalised
            _lastLoadedOriginCrs.value = null
            stopPlatformWatch()
            platformNotified = false
            dispatch(
                CommuteEvent.SetOriginOverride(
                    LockedOrigin(
                        crs = normalised,
                        name = name,
                        latitude = station?.latitude ?: 0.0,
                        longitude = station?.longitude ?: 0.0,
                        lockedAtEpochMs = System.currentTimeMillis(),
                        distanceMetres = 0.0,
                        manualOverride = true,
                    ),
                ),
            )
        }
    }

    fun clearOriginOverride() {
        _lastLoadedOriginCrs.value = null
        previousSample = null
        alreadyPresentOk = false
        clearBoardingSession(keepUi = false)
        dispatch(CommuteEvent.ClearOverride)
        if (permissionGranted) {
            _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.None)
            startLocationWatch(forceRefresh = true)
        } else {
            _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.NeedsPermission)
        }
    }

    fun dismissBoarding() {
        previousSample = null
        alreadyPresentOk = true
        dispatch(CommuteEvent.Dismiss)
        clearBoardingSession(keepUi = false)
    }

    fun selectServiceForPlatformWatch(
        originCrs: String,
        destinationCrs: String,
        serviceId: String,
    ) {
        val at = currentAtStation() ?: return
        val dest = at.destinations.find { it.destinationCrs.equals(destinationCrs, ignoreCase = true) }
        val service = dest?.services?.find { it.serviceId == serviceId } ?: return
        dispatch(
            CommuteEvent.WatchPlatform(
                serviceId = serviceId,
                originCrs = originCrs,
                destinationCrs = destinationCrs,
                destinationName = dest.destinationName,
                scheduledDepartureLabel = service.scheduledDepartureLabel,
                expectedDepartureLabel = service.expectedDepartureLabel,
                platform = service.platform?.takeIf { it.isNotBlank() },
            ),
        )
    }

    fun clearPlatformWatch() {
        dispatch(CommuteEvent.ClearPlatformWatch)
    }

    fun refreshPlatformWatch() {
        val watch = currentAtStation()?.platformWatch ?: return
        scope.launch { pingPlatform(watch) }
    }

    fun reloadDestinationsIfAtStation(forceRefresh: Boolean) {
        val originCrs = _sessionFlow.value.destinationLoadOriginCrs() ?: return
        loadDestinations(originCrs = originCrs, forceRefresh = forceRefresh)
    }

    fun refreshOnTrainOption() {
        scope.launch { refreshOnTrainOptionInternal() }
    }

    fun sampleLocationIfPermitted(forceRefresh: Boolean) {
        if (!permissionGranted || !_sessionFlow.value.acceptsWalkUpLocationEvents()) return
        scope.launch { sampleLocation(forceRefresh) }
    }

    fun startLocationWatchIfPermitted(forceRefresh: Boolean = false) {
        if (permissionGranted) {
            startLocationWatch(forceRefresh = forceRefresh)
        }
    }

    fun onCleared() {
        pinRestoreJob?.cancel()
        locationWatchJob?.cancel()
        destinationLoadJob?.cancel()
        boardingTickJob?.cancel()
        platformWatchJob?.cancel()
        onTrainPollJob?.cancel()
        onTrainProgressTickJob?.cancel()
    }

    private fun currentAtStation(): WalkUpHomeUi.AtStation? =
        mapCommuteWalkUpUi(
            _sessionFlow.value,
            _auxFlow.value,
            _lastLoadedOriginCrs.value,
        ) as? WalkUpHomeUi.AtStation

    private fun startPlatformWatch(initial: PlatformWatchUi) {
        stopPlatformWatch()
        platformWatchJob = scope.launch {
            pingPlatform(initial)
            while (isActive) {
                val watch = currentAtStation()?.platformWatch ?: break
                if (watch.serviceId != initial.serviceId) break
                val departureLabel = JourneyTimeUtils.effectiveDepartureLabel(
                    scheduledDepartureLabel = watch.scheduledDepartureLabel,
                    expectedDepartureLabel = watch.expectedDepartureLabel,
                )
                val minutesLeft = JourneyTimeUtils.minutesUntilDeparture(departureLabel)
                delay(platformPollIntervalMs(minutesLeft))
                val still = currentAtStation()?.platformWatch ?: break
                if (still.serviceId != initial.serviceId) break
                pingPlatform(still)
            }
        }
    }

    private suspend fun pingPlatform(watch: PlatformWatchUi) {
        updateAtStationAux { at ->
            val current = at.platformWatch?.takeIf { it.serviceId == watch.serviceId } ?: return@updateAtStationAux at
            at.copy(platformWatch = current.copy(polling = true, errorMessage = null))
        }
        try {
            val option = journeyRepository.getOption(
                originCrs = watch.originCrs,
                destinationCrs = watch.destinationCrs,
                serviceId = watch.serviceId,
                forceRefresh = true,
            )
            val platform = option?.platform?.trim()?.takeIf { it.isNotEmpty() }
            val checked = formatCheckedNow()
            updateAtStationAux { at ->
                val current = at.platformWatch?.takeIf { it.serviceId == watch.serviceId }
                    ?: return@updateAtStationAux at
                val destinations = at.destinations.map { dest ->
                    if (!dest.destinationCrs.equals(watch.destinationCrs, ignoreCase = true)) {
                        dest
                    } else {
                        dest.copy(
                            services = dest.services.map { svc ->
                                if (svc.serviceId == watch.serviceId && platform != null) {
                                    svc.copy(platform = platform)
                                } else {
                                    svc
                                }
                            },
                        )
                    }
                }
                at.copy(
                    destinations = destinations,
                    platformWatch = current.copy(
                        platform = platform ?: current.platform,
                        scheduledDepartureLabel = option?.scheduledDepartureLabel
                            ?.takeIf { it.isNotBlank() }
                            ?: current.scheduledDepartureLabel,
                        expectedDepartureLabel = option?.expectedDepartureLabel
                            ?: current.expectedDepartureLabel,
                        checkedAtLabel = checked,
                        polling = false,
                        errorMessage = if (option == null) "Service not found on board" else null,
                    ),
                )
            }
            if (platform != null && !platformNotified) {
                platformNotified = true
                _platformAppeared.emit(platform)
            }
        } catch (t: Throwable) {
            updateAtStationAux { at ->
                val current = at.platformWatch?.takeIf { it.serviceId == watch.serviceId }
                    ?: return@updateAtStationAux at
                at.copy(
                    platformWatch = current.copy(
                        polling = false,
                        errorMessage = t.message ?: "Could not refresh service",
                    ),
                )
            }
        }
    }

    private fun stopPlatformWatch() {
        platformWatchJob?.cancel()
        platformWatchJob = null
    }

    private fun startLocationWatch(forceRefresh: Boolean = false) {
        if (locationWatchJob?.isActive == true && !forceRefresh) return
        locationWatchJob?.cancel()
        alreadyPresentOk = true
        locationWatchJob = scope.launch {
            if (_sessionFlow.value.clearsPresenceOverlayOnWatchStart()) {
                _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.None)
            }
            sampleLocation(forceRefresh = true)
            while (isActive) {
                val now = System.currentTimeMillis()
                delay(
                    _sessionFlow.value.locationPollIntervalMs(
                        nowEpochMs = now,
                        elevatedUntilEpochMs = frequentChecksUntilEpochMs,
                    ),
                )
                val session = _sessionFlow.value
                if (!session.isManualOriginOverride()) {
                    val watching = session.forceRefreshLocationSample(
                        nowEpochMs = System.currentTimeMillis(),
                        elevatedUntilEpochMs = frequentChecksUntilEpochMs,
                    )
                    sampleLocation(forceRefresh = watching)
                }
            }
        }
    }

    private fun stopLocationWatch() {
        locationWatchJob?.cancel()
        locationWatchJob = null
    }

    private suspend fun sampleLocation(forceRefresh: Boolean) {
        val session = _sessionFlow.value
        if (!session.acceptsWalkUpLocationEvents()) return

        val location = locationProvider.currentLocation(forceRefresh = forceRefresh)
        if (location == null) {
            if (session.showsLocationUnavailableOverlay()) {
                _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.LocationUnavailable)
            }
            return
        }

        _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.None)

        val sample = WalkUpSample(
            latitude = location.latitude,
            longitude = location.longitude,
            speedMps = location.speedMetresPerSecond?.toDouble(),
            epochMs = location.epochMs,
        )
        val speed = DetectWalkUpArrival.resolveSpeed(sample, previousSample)
        onLatestSpeedMps?.invoke(speed)
        previousSample = sample

        val nearby = if (session.shouldResolveNearbyStations()) {
            stationRepository.getNearby(
                lat = location.latitude,
                lng = location.longitude,
                limit = 5,
            )
        } else {
            emptyList()
        }
        val previous = _sessionFlow.value
        val transition = reduceEvent(
            CommuteEvent.Location(
                sample = sample,
                nearest = nearby.firstOrNull(),
                speedMps = speed,
                alreadyPresentOk = alreadyPresentOk && previous.allowsColdStartAlreadyPresentLock(),
            ),
        )
        alreadyPresentOk = false
        applyTransition(transition)
        val stillOnTrain = _sessionFlow.value.onTrainOrNull()
        val option = latestOnTrainOption
        if (stillOnTrain != null && option != null) {
            applyTransition(reduceEvent(CommuteEvent.ServiceDetails(option)))
        }
    }

    private fun reduceEvent(event: CommuteEvent): CommuteTransition =
        reduce(
            session = _sessionFlow.value,
            event = event,
            boardingConfig = boardingConfig,
            stationStopConfig = stationStopConfig,
            cachedBoardingServices = cachedBoardingServices,
            routeStations = routeStations,
        )

    private fun startBoardingTick(confirming: CommuteSession.Confirming) {
        cancelBoardingTick()
        val deadline = confirming.triggeredAtEpochMs + boardingConfig.autoAcceptMillis
        boardingTickJob = scope.launch {
            delay((deadline - System.currentTimeMillis()).coerceAtLeast(0L))
            if (_sessionFlow.value.isConfirming()) {
                dispatch(CommuteEvent.Tick(System.currentTimeMillis()))
            }
        }
    }

    private fun cancelBoardingTick() {
        boardingTickJob?.cancel()
        boardingTickJob = null
    }

    private fun clearBoardingSession(keepUi: Boolean) {
        cancelBoardingTick()
        stopPlatformWatch()
        platformNotified = false
        cachedBoardingServices = emptyList()
        if (!keepUi) {
            _auxFlow.value = _auxFlow.value.copy(atStation = AtStationAux())
        }
    }

    private fun applyTransition(transition: CommuteTransition) {
        val previous = _sessionFlow.value
        _sessionFlow.value = transition.session
        var aux = _auxFlow.value
        if (previous is CommuteSession.Confirming &&
            transition.session !is CommuteSession.Confirming
        ) {
            cancelBoardingTick()
        }
        if (transition.session is CommuteSession.Idle &&
            (previous is CommuteSession.AtStation || previous is CommuteSession.Confirming)
        ) {
            stopPlatformWatch()
            platformNotified = false
            cachedBoardingServices = emptyList()
            aux = aux.copy(atStation = AtStationAux())
        }
        val nextOnTrain = transition.session as? CommuteSession.OnTrain
        val previousOnTrain = previous as? CommuteSession.OnTrain
        val enteredOnTrain = nextOnTrain != null &&
            previousOnTrain?.pin?.serviceId != nextOnTrain.pin.serviceId
        val leftOnTrain = previousOnTrain != null && nextOnTrain == null
        if (enteredOnTrain) {
            clearBoardingSession(keepUi = false)
            latestOnTrainOption = null
            routeStations = emptyList()
            aux = aux.copy(onTrain = onTrainSkeletonAux())
        }
        val atStation = transition.session as? CommuteSession.AtStation
        if (atStation != null && previous != transition.session) {
            val sameStation = aux.atStation.originCrs.equals(atStation.origin.crs, ignoreCase = true) &&
                !atStation.origin.manualOverride
            if (!sameStation) {
                stopPlatformWatch()
                platformNotified = false
            }
            aux = aux.copy(
                atStation = mergeAtStationAuxForArrival(
                    atStation,
                    aux.atStation,
                    _lastLoadedOriginCrs.value,
                ),
            )
        }
        val prevWatch = previous.platformWatchOrNull()
        val nextWatch = transition.session.platformWatchOrNull()
        val leftWatch = prevWatch != null && prevWatch.serviceId != nextWatch?.serviceId
        if (leftWatch) {
            stopPlatformWatch()
            platformNotified = false
            aux = aux.copy(atStation = aux.atStation.copy(platformWatch = null))
        }
        if (nextWatch != null && prevWatch?.serviceId != nextWatch.serviceId) {
            platformNotified = false
            val ui = mapPlatformWatchUi(nextWatch, auxWatch = null)
            if (ui != null) {
                aux = aux.copy(atStation = aux.atStation.copy(platformWatch = ui))
                startPlatformWatch(ui)
                val already = ui.platform
                if (already != null) {
                    platformNotified = true
                    _platformAppeared.tryEmit(already)
                }
            }
        }
        for (effect in transition.effects) {
            interpretEffect(effect)
        }
        if (enteredOnTrain) {
            if (permissionGranted) {
                startLocationWatch(forceRefresh = true)
            }
            startOnTrainPolling()
            startOnTrainProgressTick()
            scope.launch { refreshOnTrainOptionInternal() }
        }
        if (leftOnTrain) {
            onTrainPollJob?.cancel()
            onTrainPollJob = null
            onTrainProgressTickJob?.cancel()
            onTrainProgressTickJob = null
            latestOnTrainOption = null
            routeStations = emptyList()
            alreadyPresentOk = true
            aux = aux.copy(onTrain = OnTrainUiAux())
        }
        val nextConfirming = transition.session as? CommuteSession.Confirming
        if (nextConfirming != null && previous !is CommuteSession.Confirming) {
            startBoardingTick(nextConfirming)
        }
        val onTrain = _sessionFlow.value.onTrainOrNull()
        val option = latestOnTrainOption
        if (onTrain != null && option != null) {
            aux = aux.copy(onTrain = buildOnTrainUiAux(option, onTrain))
        }
        _auxFlow.value = aux
    }

    private fun interpretEffect(effect: CommuteEffect) {
        when (effect) {
            is CommuteEffect.LoadFavouriteBoundDepartures ->
                loadDestinations(originCrs = effect.originCrs, forceRefresh = effect.forceRefresh)
            is CommuteEffect.Pin -> pinFromEffect(effect.journey)
            is CommuteEffect.StartTracking ->
                startOnTrainService(effect.destinationName, effect.minutesRemaining)
            is CommuteEffect.UpdateTracking ->
                updateOnTrainService(effect.destinationName, effect.minutesRemaining)
            CommuteEffect.StopTracking -> stopOnTrainService()
            CommuteEffect.WriteJourneyLog -> {
                val option = latestOnTrainOption
                if (option != null) {
                    scope.launch {
                        runCatching { journeyLogRepository.logFromOption(option) }
                    }
                }
            }
            CommuteEffect.ClearPin -> {
                scope.launch { pinnedJourneyStore.clear() }
            }
            is CommuteEffect.Vibrate ->
                effect.thresholds.forEach { threshold -> _arrivalBuzz.tryEmit(threshold) }
            CommuteEffect.RefreshServiceDetails ->
                scope.launch { refreshOnTrainOptionInternal() }
            is CommuteEffect.ElevateFrequentChecks -> {
                frequentChecksUntilEpochMs = effect.untilEpochMs
                alreadyPresentOk = true
            }
            is CommuteEffect.NotifyProximity -> Unit
        }
    }

    private fun pinFromEffect(journey: PinnedJourney) {
        previousSample = null
        alreadyPresentOk = false
        scope.launch {
            pinnedJourneyStore.pin(journey)
        }
    }

    private fun loadDestinations(originCrs: String, forceRefresh: Boolean) {
        destinationLoadJob?.cancel()
        destinationLoadJob = scope.launch {
            val favourites = favouritesStore.getFavourites()
            val noFavourites = favourites.none { it.uppercase() != originCrs.uppercase() }
            updateAtStationAux { current ->
                current.copy(
                    originCrs = originCrs.uppercase(),
                    destinationsLoading = true,
                    noFavouritesConfigured = noFavourites,
                )
            }
            if (noFavourites) {
                cachedBoardingServices = emptyList()
                stopPlatformWatch()
                platformNotified = false
                updateAtStationAux { current ->
                    current.copy(
                        destinations = emptyList(),
                        destinationsLoading = false,
                        noFavouritesConfigured = true,
                        platformWatch = null,
                    )
                }
                _lastLoadedOriginCrs.value = originCrs.uppercase()
                return@launch
            }
            val rows = loadFavouriteBoundDepartures.load(
                originCrs = originCrs,
                favouriteDestinationCrs = favourites,
                forceRefresh = forceRefresh,
            )
            _lastLoadedOriginCrs.value = originCrs.uppercase()
            val originName = _sessionFlow.value.lockedOriginOrNull()?.name ?: originCrs
            cachedBoardingServices = MatchBoardingCandidates.snapshotsFromDestinations(
                originCrs = originCrs,
                originName = originName,
                destinations = rows,
                favouriteDestinationOrder = favourites,
            )
            updateAtStationAux { current ->
                val mapped = rows.map { it.toUi() }
                val watch = current.platformWatch?.let { existingWatch ->
                    val refreshedPlatform = mapped
                        .flatMap { it.services }
                        .find { it.serviceId == existingWatch.serviceId }
                        ?.platform
                        ?.takeIf { it.isNotBlank() }
                    existingWatch.copy(platform = refreshedPlatform ?: existingWatch.platform)
                }
                val destinations = if (watch?.platform != null) {
                    mapped.map { dest ->
                        dest.copy(
                            services = dest.services.map { svc ->
                                if (svc.serviceId == watch.serviceId) {
                                    svc.copy(platform = watch.platform)
                                } else {
                                    svc
                                }
                            },
                        )
                    }
                } else {
                    mapped
                }
                current.copy(
                    destinations = destinations,
                    destinationsLoading = false,
                    noFavouritesConfigured = false,
                    platformWatch = watch,
                )
            }
        }
    }

    private fun FavouriteDestinationServices.toUi(): WalkUpDestinationUi =
        WalkUpDestinationUi(
            destinationCrs = destinationCrs,
            destinationName = destinationName,
            services = services,
            errorMessage = errorMessage,
        )

    private fun updateAtStationAux(transform: (AtStationAux) -> AtStationAux) {
        _auxFlow.value = _auxFlow.value.copy(atStation = transform(_auxFlow.value.atStation))
    }

    private fun updateOnTrainAux(transform: (OnTrainUiAux) -> OnTrainUiAux) {
        _auxFlow.value = _auxFlow.value.copy(onTrain = transform(_auxFlow.value.onTrain))
    }

    private fun startOnTrainPolling() {
        onTrainPollJob?.cancel()
        onTrainPollJob = scope.launch {
            while (isActive && _sessionFlow.value.isOnTrain()) {
                val stop = _sessionFlow.value.onTrainOrNull()?.stop
                val stopped = stop is StationStopPhase.Confirmed ||
                    stop is StationStopPhase.Dwelling
                delay(if (stopped) ON_TRAIN_POLL_STOPPED_MS else ON_TRAIN_POLL_MS)
                if (!_sessionFlow.value.isOnTrain()) break
                refreshOnTrainOptionInternal()
            }
        }
    }

    private fun startOnTrainProgressTick() {
        onTrainProgressTickJob?.cancel()
        onTrainProgressTickJob = scope.launch {
            while (isActive && _sessionFlow.value.isOnTrain()) {
                delay(ON_TRAIN_PROGRESS_TICK_MS)
                val onTrain = _sessionFlow.value.onTrainOrNull() ?: break
                val option = latestOnTrainOption ?: continue
                _auxFlow.value = _auxFlow.value.copy(onTrain = buildOnTrainUiAux(option, onTrain))
            }
        }
    }

    private suspend fun refreshOnTrainOptionInternal() {
        val pinned = _sessionFlow.value.pinnedJourneyOrNull() ?: return
        updateOnTrainAux { it.copy(refreshing = true, errorMessage = null) }
        try {
            val option = journeyRepository.getServiceDetails(
                serviceId = pinned.serviceId,
                originCrs = pinned.originCrs,
                destinationCrs = pinned.destinationCrs,
                destinationNameFallback = pinned.destinationName,
            )
            if (!_sessionFlow.value.isOnTrain()) return
            latestOnTrainOption = option
            routeStations = buildRouteStations(option)
            applyTransition(reduceEvent(CommuteEvent.ServiceDetails(option)))
        } catch (t: Throwable) {
            val missing = t.message?.contains("not found", ignoreCase = true) == true ||
                t.message?.contains("HTTP 404", ignoreCase = true) == true
            updateOnTrainAux {
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
        }
    }

    private suspend fun buildRouteStations(option: JourneyOption): List<RouteStation> {
        val result = ArrayList<RouteStation>()
        val seen = HashSet<String>()
        for (cp in option.callingPoints) {
            val crs = cp.crs?.trim()?.uppercase().orEmpty()
            if (crs.isEmpty() || !seen.add(crs)) continue
            val station = stationRepository.getByCrs(crs) ?: continue
            result += RouteStation(
                crs = crs,
                name = station.name.ifBlank { cp.locationName },
                latitude = station.latitude,
                longitude = station.longitude,
            )
        }
        return result
    }

    private fun startOnTrainService(destinationName: String, minutes: Long?) {
        val simulating = DebugWalkUp.isSimulatingLocation()
        if (!hasOsLocationPermission() && !simulating) {
            Log.w(TAG, "Skip on-train FGS — no location permission and not simulating")
            return
        }
        runCatching {
            OnTrainTrackingService.start(
                context = appContext,
                destinationName = destinationName,
                minutesRemaining = minutes?.toInt(),
                allowWithoutLocation = simulating,
            )
            onTrainServiceStarted = true
        }
    }

    private fun updateOnTrainService(destinationName: String, minutes: Long?) {
        if (!onTrainServiceStarted) {
            startOnTrainService(destinationName, minutes)
            return
        }
        val simulating = DebugWalkUp.isSimulatingLocation()
        if (!hasOsLocationPermission() && !simulating) {
            stopOnTrainService()
            return
        }
        runCatching {
            OnTrainTrackingService.update(
                context = appContext,
                destinationName = destinationName,
                minutesRemaining = minutes?.toInt(),
                allowWithoutLocation = simulating,
            )
        }
    }

    private fun hasOsLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun stopOnTrainService() {
        if (!onTrainServiceStarted) return
        runCatching { OnTrainTrackingService.stop(appContext) }
        onTrainServiceStarted = false
    }

    companion object {
        private const val TAG = "CommuteRuntime"
        private const val FREQUENT_CHECKS_TTL_MS = 5 * 60_000L
        private const val ON_TRAIN_POLL_MS = 45_000L
        private const val ON_TRAIN_POLL_STOPPED_MS = 60_000L
        private const val ON_TRAIN_PROGRESS_TICK_MS = 10_000L

        private val CHECKED_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

        private fun formatCheckedNow(): String =
            CHECKED_FMT.format(Instant.now())

        internal fun platformPollIntervalMs(minutesUntilDeparture: Long?): Long = when {
            minutesUntilDeparture == null -> 20_000L
            minutesUntilDeparture > 20L -> 60_000L
            minutesUntilDeparture > 10L -> 30_000L
            minutesUntilDeparture > 5L -> 15_000L
            minutesUntilDeparture >= 0L -> 10_000L
            else -> 30_000L
        }
    }
}
