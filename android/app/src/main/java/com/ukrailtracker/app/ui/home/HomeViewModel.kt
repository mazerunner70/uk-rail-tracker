package com.ukrailtracker.app.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.data.local.datastore.CommuteWindowStore
import com.ukrailtracker.app.data.local.datastore.FavouritesStore
import com.ukrailtracker.app.data.local.datastore.PinnedJourneyStore
import com.ukrailtracker.app.data.repository.JourneyLogRepository
import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.model.Departure
import com.ukrailtracker.app.domain.model.DisruptionSeverity
import com.ukrailtracker.app.domain.model.DisruptionSummary
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.repository.DepartureRepository
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.BoardingConfig
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteEffect
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.isManualOriginOverride
import com.ukrailtracker.app.domain.usecase.lockedOriginOrNull
import com.ukrailtracker.app.domain.usecase.onTrainOrNull
import com.ukrailtracker.app.domain.usecase.pinnedJourneyOrNull
import com.ukrailtracker.app.domain.usecase.reduce
import com.ukrailtracker.app.domain.usecase.ComputeJourneyProgress
import com.ukrailtracker.app.domain.usecase.DeriveBoardDisruption
import com.ukrailtracker.app.domain.usecase.DetectWalkUpArrival
import com.ukrailtracker.app.domain.usecase.FavouriteDestinationServices
import com.ukrailtracker.app.domain.usecase.GetContextualStationsUseCase
import com.ukrailtracker.app.domain.usecase.JourneyTimeUtils
import com.ukrailtracker.app.domain.usecase.LoadFavouriteBoundDepartures
import com.ukrailtracker.app.domain.usecase.MatchBoardingCandidates
import com.ukrailtracker.app.domain.usecase.RouteStation
import com.ukrailtracker.app.domain.usecase.StationStopConfig
import com.ukrailtracker.app.domain.usecase.StationStopPhase
import com.ukrailtracker.app.domain.usecase.WalkUpSample
import com.ukrailtracker.app.debug.DebugWalkUp
import com.ukrailtracker.app.service.OnTrainTrackingService
import com.ukrailtracker.app.ui.station.StatusKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HomeViewModel(
    private val appContext: Context,
    private val stationRepository: StationRepository,
    private val departureRepository: DepartureRepository,
    private val journeyRepository: JourneyRepository,
    private val journeyLogRepository: JourneyLogRepository,
    private val locationProvider: LocationProvider,
    private val commuteWindowStore: CommuteWindowStore,
    private val favouritesStore: FavouritesStore,
    private val pinnedJourneyStore: PinnedJourneyStore,
    private val getContextualStations: GetContextualStationsUseCase = GetContextualStationsUseCase(),
    private val loadFavouriteBoundDepartures: LoadFavouriteBoundDepartures =
        LoadFavouriteBoundDepartures(journeyRepository, stationRepository),
    private val boardingConfig: BoardingConfig = BoardingConfig(),
    private val stationStopConfig: StationStopConfig = StationStopConfig(),
    private val latestSpeedMps: MutableStateFlow<Double?> = MutableStateFlow(null),
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _openJourney = MutableSharedFlow<OpenJourneyEvent>(extraBufferCapacity = 1)
    val openJourney: SharedFlow<OpenJourneyEvent> = _openJourney.asSharedFlow()

    private val _platformAppeared = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Emits the platform label when it first appears for the watched service. */
    val platformAppeared: SharedFlow<String> = _platformAppeared.asSharedFlow()

    private val _arrivalBuzz = MutableSharedFlow<Int>(extraBufferCapacity = 3)
    /** Emits a minute threshold (3, 2, or 1) when the final-approach buzz should fire. */
    val arrivalBuzz: SharedFlow<Int> = _arrivalBuzz.asSharedFlow()

    private var commuteSession: CommuteSession = CommuteSession.Idle
    private var previousSample: WalkUpSample? = null
    private var locationWatchJob: Job? = null
    private var destinationLoadJob: Job? = null
    private var boardingTickJob: Job? = null
    private var platformWatchJob: Job? = null
    private var onTrainPollJob: Job? = null
    private var onTrainProgressTickJob: Job? = null
    private var permissionGranted: Boolean = false
    private var lastLoadedOriginCrs: String? = null
    /** First GPS fix after start may lock "already at station" without a walk-up sequence. */
    private var alreadyPresentOk: Boolean = true
    private var cachedBoardingServices: List<BoardingServiceSnapshot> = emptyList()
    private var lastAtStationUi: WalkUpHomeUi.AtStation? = null
    private var platformNotified: Boolean = false

    private var latestOnTrainOption: JourneyOption? = null
    private var routeStations: List<RouteStation> = emptyList()
    private var onTrainServiceStarted: Boolean = false
    private var frequentChecksUntilEpochMs: Long = 0L

    init {
        viewModelScope.launch {
            stationRepository.ensureImported()
            combine(
                commuteWindowStore.windowsFlow,
                favouritesStore.favouritesFlow,
            ) { windows, favourites -> windows to favourites }
                .collect { (windows, favourites) ->
                    loadBoards(
                        windowsCrs = getContextualStations.select(windows, favourites),
                        forceRefresh = false,
                    )
                    reloadDestinationsIfAtStation(forceRefresh = false)
                }
        }
        viewModelScope.launch {
            pinnedJourneyStore.pinnedFlow.collect { pinned ->
                val current = commuteSession.onTrainOrNull()
                if (pinned == null) {
                    if (current != null) {
                        applyTransition(reduceEvent(CommuteEvent.PinCleared))
                    }
                } else if (current?.pin?.serviceId != pinned.serviceId) {
                    applyTransition(reduceEvent(CommuteEvent.RestorePin(pinned)))
                }
            }
        }
    }

    fun onNeedsPermission() {
        permissionGranted = false
        stopLocationWatch()
        applyTransition(reduceEvent(CommuteEvent.PermissionLost))
        patchWalkUp(WalkUpHomeUi.NeedsPermission)
    }

    fun onPermissionGranted() {
        permissionGranted = true
        startLocationWatch()
    }

    /**
     * Notification tap / deep wake — enqueue [CommuteEvent.OpenedForFrequentChecks]
     * so reduce can elevate GPS poll; walk-up still confirms presence.
     */
    fun onOpenedForFrequentChecks(hintCrs: String? = null) {
        applyTransition(
            reduceEvent(
                CommuteEvent.OpenedForFrequentChecks(
                    untilEpochMs = System.currentTimeMillis() + FREQUENT_CHECKS_TTL_MS,
                    hintCrs = hintCrs?.trim()?.uppercase()?.ifEmpty { null },
                ),
            ),
        )
        if (permissionGranted) {
            startLocationWatch(forceRefresh = true)
        }
    }

    /**
     * Resets walk-up sensing so an external debug location override can drive Away → Near → At.
     * Used by [com.ukrailtracker.app.debug.DebugWalkUp] (debug builds only).
     */
    fun prepareForDebugWalkUp(elevatePollForMs: Long) {
        viewModelScope.launch {
            if (commuteSession is CommuteSession.OnTrain) {
                applyTransition(reduceEvent(CommuteEvent.DiscardTrip))
            }
            clearBoardingSession(keepUi = false)
            cancelBoardingTick()
            stopPlatformWatch()
            lastLoadedOriginCrs = null
            commuteSession = CommuteSession.Idle
            previousSample = null
            alreadyPresentOk = false
            frequentChecksUntilEpochMs = System.currentTimeMillis() + elevatePollForMs.coerceAtLeast(0L)
            permissionGranted = true
            patchWalkUp(WalkUpHomeUi.NotAtStation)
            startLocationWatch(forceRefresh = true)
        }
    }

    fun onPermissionDenied() {
        permissionGranted = false
        stopLocationWatch()
        applyTransition(reduceEvent(CommuteEvent.PermissionLost))
        patchWalkUp(WalkUpHomeUi.NeedsPermission)
    }

    fun setOriginOverride(crs: String) {
        viewModelScope.launch {
            val normalised = crs.trim().uppercase()
            if (normalised.isBlank()) return@launch
            clearBoardingSession(keepUi = false)
            val station = stationRepository.getByCrs(normalised)
            val name = station?.name ?: normalised
            lastLoadedOriginCrs = null
            stopPlatformWatch()
            platformNotified = false
            applyTransition(
                reduceEvent(
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
                ),
            )
        }
    }

    fun clearOriginOverride() {
        lastLoadedOriginCrs = null
        previousSample = null
        alreadyPresentOk = false
        clearBoardingSession(keepUi = false)
        applyTransition(reduceEvent(CommuteEvent.ClearOverride))
        if (permissionGranted) {
            startLocationWatch(forceRefresh = true)
        } else {
            patchWalkUp(WalkUpHomeUi.NeedsPermission)
        }
    }

    fun confirmBoarding() {
        applyTransition(reduceEvent(CommuteEvent.Confirm(System.currentTimeMillis())))
    }

    fun rejectAssumedBoarding() {
        applyTransition(reduceEvent(CommuteEvent.ShowPicker))
    }

    fun selectBoardingCandidate(serviceId: String) {
        applyTransition(
            reduceEvent(CommuteEvent.SelectService(serviceId, System.currentTimeMillis())),
        )
    }

    fun dismissBoarding() {
        previousSample = null
        alreadyPresentOk = true
        applyTransition(reduceEvent(CommuteEvent.Dismiss))
        clearBoardingSession(keepUi = false)
    }

    fun endOnTrainTrip() {
        applyTransition(reduceEvent(CommuteEvent.EndTrip))
    }

    fun openOnTrainServiceDetail() {
        val pinned = commuteSession.pinnedJourneyOrNull() ?: return
        viewModelScope.launch {
            _openJourney.emit(
                OpenJourneyEvent(
                    originCrs = pinned.originCrs,
                    destinationCrs = pinned.destinationCrs,
                    serviceId = pinned.serviceId,
                ),
            )
        }
    }

    /**
     * Select a favourite-bound service at the station and poll until a departure platform appears.
     * Vibrates (via [platformAppeared]) when the platform first shows up.
     */
    fun selectServiceForPlatformWatch(
        originCrs: String,
        destinationCrs: String,
        serviceId: String,
    ) {
        val at = currentAtStation() ?: return
        val dest = at.destinations.find { it.destinationCrs.equals(destinationCrs, ignoreCase = true) }
        val service = dest?.services?.find { it.serviceId == serviceId } ?: return
        val watch = PlatformWatchUi(
            serviceId = serviceId,
            originCrs = originCrs.uppercase(),
            destinationCrs = destinationCrs.uppercase(),
            destinationName = dest.destinationName,
            scheduledDepartureLabel = service.scheduledDepartureLabel,
            expectedDepartureLabel = service.expectedDepartureLabel,
            platform = service.platform?.takeIf { it.isNotBlank() },
            checkedAtLabel = formatCheckedNow(),
            polling = true,
        )
        platformNotified = false
        updateAtStation { it.copy(platformWatch = watch) }
        startPlatformWatch(watch)
        val already = watch.platform
        if (already != null) {
            platformNotified = true
            _platformAppeared.tryEmit(already)
        }
    }

    fun clearPlatformWatch() {
        stopPlatformWatch()
        platformNotified = false
        updateAtStation { it.copy(platformWatch = null) }
    }

    /** Manual refresh of the selected service (platform / departure countdown). */
    fun refreshPlatformWatch() {
        val watch = currentAtStation()?.platformWatch ?: return
        viewModelScope.launch {
            pingPlatform(watch)
        }
    }

    fun openWatchedService() {
        val watch = currentAtStation()?.platformWatch ?: return
        viewModelScope.launch {
            _openJourney.emit(
                OpenJourneyEvent(
                    originCrs = watch.originCrs,
                    destinationCrs = watch.destinationCrs,
                    serviceId = watch.serviceId,
                ),
            )
        }
    }

    fun refresh() {
        val current = _uiState.value
        val crsList = when (current) {
            is HomeUiState.Content -> current.stations.map { it.crsCode }
            else -> emptyList()
        }
        viewModelScope.launch {
            if (current is HomeUiState.Content) {
                _uiState.value = current.copy(refreshing = true)
            } else {
                _uiState.value = HomeUiState.Loading
            }
            val windows = commuteWindowStore.getWindows()
            val favourites = favouritesStore.getFavourites()
            val selected = getContextualStations.select(windows, favourites)
                .ifEmpty { crsList }
            loadBoards(selected, forceRefresh = true)
            reloadDestinationsIfAtStation(forceRefresh = true)
            if (commuteSession is CommuteSession.OnTrain) {
                refreshOnTrainOption()
            }
            if (permissionGranted && !commuteSession.isManualOriginOverride()) {
                sampleLocation(forceRefresh = true)
            }
        }
    }

    fun openDisruption(crs: String) {
        val content = _uiState.value as? HomeUiState.Content ?: return
        val card = content.stations.find { it.crsCode == crs } ?: return
        _uiState.value = content.copy(
            selectedDisruption = card.disruption,
            selectedStationName = card.stationName,
        )
    }

    fun dismissDisruption() {
        val content = _uiState.value as? HomeUiState.Content ?: return
        _uiState.value = content.copy(selectedDisruption = null, selectedStationName = null)
    }

    private fun currentAtStation(): WalkUpHomeUi.AtStation? =
        (_uiState.value as? HomeUiState.Content)?.walkUp as? WalkUpHomeUi.AtStation

    private fun isConfirmingBoarding(): Boolean = commuteSession is CommuteSession.Confirming

    private fun startPlatformWatch(initial: PlatformWatchUi) {
        stopPlatformWatch()
        platformWatchJob = viewModelScope.launch {
            // Immediate force refresh, then keep pinging (faster near departure).
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
        updateAtStation { at ->
            val current = at.platformWatch?.takeIf { it.serviceId == watch.serviceId } ?: return@updateAtStation at
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
            updateAtStation { at ->
                val current = at.platformWatch?.takeIf { it.serviceId == watch.serviceId }
                    ?: return@updateAtStation at
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
            updateAtStation { at ->
                val current = at.platformWatch?.takeIf { it.serviceId == watch.serviceId }
                    ?: return@updateAtStation at
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
        locationWatchJob = viewModelScope.launch {
            if (!commuteSession.isManualOriginOverride() &&
                !isConfirmingBoarding() &&
                commuteSession !is CommuteSession.OnTrain &&
                commuteSession is CommuteSession.Idle
            ) {
                patchWalkUp(WalkUpHomeUi.NotAtStation)
            }
            sampleLocation(forceRefresh = true)
            while (isActive) {
                delay(currentPollIntervalMs())
                if (!commuteSession.isManualOriginOverride()) {
                    val watching = commuteSession is CommuteSession.OnTrain ||
                        commuteSession is CommuteSession.AtStation ||
                        isConfirmingBoarding() ||
                        System.currentTimeMillis() < frequentChecksUntilEpochMs
                    sampleLocation(forceRefresh = watching)
                }
            }
        }
    }

    private fun currentPollIntervalMs(): Long {
        if (commuteSession is CommuteSession.OnTrain) return LOCATION_POLL_ON_TRAIN_MS
        if (System.currentTimeMillis() < frequentChecksUntilEpochMs) {
            return LOCATION_POLL_WATCHING_MS
        }
        val watching = commuteSession is CommuteSession.AtStation || isConfirmingBoarding()
        return if (watching) LOCATION_POLL_WATCHING_MS else LOCATION_POLL_MS
    }

    private fun stopLocationWatch() {
        locationWatchJob?.cancel()
        locationWatchJob = null
    }

    private suspend fun sampleLocation(forceRefresh: Boolean) {
        if (commuteSession.isManualOriginOverride()) return
        if (isConfirmingBoarding()) return

        val location = locationProvider.currentLocation(forceRefresh = forceRefresh)
        if (location == null) {
            if (commuteSession !is CommuteSession.OnTrain &&
                commuteSession !is CommuteSession.AtStation
            ) {
                patchWalkUp(WalkUpHomeUi.LocationUnavailable)
            }
            return
        }

        val sample = WalkUpSample(
            latitude = location.latitude,
            longitude = location.longitude,
            speedMps = location.speedMetresPerSecond?.toDouble(),
            epochMs = location.epochMs,
        )
        val speed = DetectWalkUpArrival.resolveSpeed(sample, previousSample)
        latestSpeedMps.value = speed
        previousSample = sample

        val nearby = if (commuteSession is CommuteSession.OnTrain) {
            emptyList()
        } else {
            stationRepository.getNearby(
                lat = location.latitude,
                lng = location.longitude,
                limit = 5,
            )
        }
        val previous = commuteSession
        val transition = reduceEvent(
            CommuteEvent.Location(
                sample = sample,
                nearest = nearby.firstOrNull(),
                speedMps = speed,
                alreadyPresentOk = alreadyPresentOk && previous is CommuteSession.Idle,
            ),
        )
        alreadyPresentOk = false
        applyTransition(transition)
        val stillOnTrain = commuteSession as? CommuteSession.OnTrain
        val option = latestOnTrainOption
        if (stillOnTrain != null && option != null) {
            applyTransition(reduceEvent(CommuteEvent.ServiceDetails(option)))
        }
    }

    private fun reduceEvent(event: CommuteEvent): CommuteTransition =
        reduce(
            session = commuteSession,
            event = event,
            boardingConfig = boardingConfig,
            stationStopConfig = stationStopConfig,
            cachedBoardingServices = cachedBoardingServices,
            routeStations = routeStations,
        )

    private fun startBoardingTick(confirming: CommuteSession.Confirming) {
        cancelBoardingTick()
        val deadline = confirming.triggeredAtEpochMs + boardingConfig.autoAcceptMillis
        boardingTickJob = viewModelScope.launch {
            delay((deadline - System.currentTimeMillis()).coerceAtLeast(0L))
            if (commuteSession is CommuteSession.Confirming) {
                applyTransition(reduceEvent(CommuteEvent.Tick(System.currentTimeMillis())))
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
            lastAtStationUi = null
        }
    }

    private fun applyTransition(transition: CommuteTransition) {
        val previous = commuteSession
        commuteSession = transition.session
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
            lastAtStationUi = null
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
        }
        if (previous != transition.session) {
            applyPresenceUi(transition.session, enteredOnTrain)
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
            viewModelScope.launch { refreshOnTrainOption() }
        }
        if (leftOnTrain) {
            onTrainPollJob?.cancel()
            onTrainPollJob = null
            onTrainProgressTickJob?.cancel()
            onTrainProgressTickJob = null
            latestOnTrainOption = null
            routeStations = emptyList()
            alreadyPresentOk = true
        }
        val nextConfirming = transition.session as? CommuteSession.Confirming
        if (nextConfirming != null && previous !is CommuteSession.Confirming) {
            startBoardingTick(nextConfirming)
        }
        val onTrain = commuteSession.onTrainOrNull()
        val option = latestOnTrainOption
        if (onTrain != null && option != null) {
            applyOnTrainProgress(option, onTrain)
        }
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
                    viewModelScope.launch {
                        runCatching { journeyLogRepository.logFromOption(option) }
                    }
                }
            }
            CommuteEffect.ClearPin -> {
                viewModelScope.launch { pinnedJourneyStore.clear() }
            }
            is CommuteEffect.Vibrate ->
                effect.thresholds.forEach { threshold -> _arrivalBuzz.tryEmit(threshold) }
            CommuteEffect.RefreshServiceDetails ->
                viewModelScope.launch { refreshOnTrainOption() }
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
        viewModelScope.launch {
            pinnedJourneyStore.pin(journey)
        }
    }

    private fun applyPresenceUi(session: CommuteSession, enteredOnTrain: Boolean) {
        when (session) {
            CommuteSession.Idle -> patchWalkUp(WalkUpHomeUi.NotAtStation)
            is CommuteSession.Near -> patchWalkUp(
                WalkUpHomeUi.NearStation(
                    crsCode = session.dwelling.crs,
                    stationName = session.dwelling.stationName,
                    distanceLabel = GeoUtils.formatDistance(session.dwelling.distanceMetres),
                ),
            )
            is CommuteSession.AtStation -> applyArrived(session)
            is CommuteSession.Confirming -> patchWalkUp(
                WalkUpHomeUi.BoardingPrompt(
                    originCrs = session.origin.crs,
                    originName = session.origin.name,
                    assumed = session.assumed.toUi(),
                    alternatives = session.alternatives.map { it.toUi() },
                    showPicker = session.showPicker,
                ),
            )
            is CommuteSession.OnTrain -> if (enteredOnTrain) {
                patchOnTrainSkeleton(session.pin)
            }
        }
    }

    private fun patchOnTrainSkeleton(pinned: PinnedJourney) {
        patchWalkUp(
            WalkUpHomeUi.OnJourney(
                serviceId = pinned.serviceId,
                originCrs = pinned.originCrs,
                destinationCrs = pinned.destinationCrs,
                destinationName = pinned.destinationName,
                scheduledDepartureLabel = pinned.scheduledDepartureLabel,
                minutesRemaining = null,
                nextStopName = null,
                currentStopName = null,
                delayMinutes = null,
                statusKind = StatusKind.Other,
                routeMismatch = false,
                mismatchStationName = null,
                expectedNextStopName = null,
                points = emptyList(),
                fractionComplete = 0f,
                predictedDurationMinutes = null,
                refreshing = true,
            ),
        )
    }

    private fun applyArrived(session: CommuteSession.AtStation) {
        val phase = session.origin
        val existing = (_uiState.value as? HomeUiState.Content)?.walkUp as? WalkUpHomeUi.AtStation
        val sameStation = existing?.crsCode == phase.crs && existing?.manualOverride != true
        val keepDestinations = existing?.takeIf { sameStation }?.destinations.orEmpty()
        val keepWatch = existing?.takeIf { sameStation }?.platformWatch
        if (!sameStation) {
            stopPlatformWatch()
            platformNotified = false
        }
        val loading = phase.crs != lastLoadedOriginCrs
        val at = WalkUpHomeUi.AtStation(
            crsCode = phase.crs,
            stationName = phase.name,
            distanceLabel = if (phase.manualOverride) {
                null
            } else {
                GeoUtils.formatDistance(phase.distanceMetres)
            },
            manualOverride = phase.manualOverride,
            destinations = keepDestinations,
            destinationsLoading = loading || (existing?.destinationsLoading == true),
            noFavouritesConfigured = existing?.noFavouritesConfigured == true,
            platformWatch = keepWatch,
        )
        lastAtStationUi = at
        patchWalkUp(at)
    }

    private fun reloadDestinationsIfAtStation(forceRefresh: Boolean) {
        val at = when (val walkUp = (_uiState.value as? HomeUiState.Content)?.walkUp) {
            is WalkUpHomeUi.AtStation -> walkUp.crsCode
            is WalkUpHomeUi.BoardingPrompt -> null
            else -> commuteSession.lockedOriginOrNull()?.crs
        } ?: return
        loadDestinations(originCrs = at, forceRefresh = forceRefresh)
    }

    private fun loadDestinations(originCrs: String, forceRefresh: Boolean) {
        destinationLoadJob?.cancel()
        destinationLoadJob = viewModelScope.launch {
            val favourites = favouritesStore.getFavourites()
            val noFavourites = favourites.none { it.uppercase() != originCrs.uppercase() }
            updateAtStation { current ->
                current.copy(
                    destinationsLoading = true,
                    noFavouritesConfigured = noFavourites,
                )
            }
            if (noFavourites) {
                cachedBoardingServices = emptyList()
                stopPlatformWatch()
                platformNotified = false
                updateAtStation { current ->
                    val updated = current.copy(
                        destinations = emptyList(),
                        destinationsLoading = false,
                        noFavouritesConfigured = true,
                        platformWatch = null,
                    )
                    lastAtStationUi = updated
                    updated
                }
                lastLoadedOriginCrs = originCrs.uppercase()
                return@launch
            }
            val rows = loadFavouriteBoundDepartures.load(
                originCrs = originCrs,
                favouriteDestinationCrs = favourites,
                forceRefresh = forceRefresh,
            )
            lastLoadedOriginCrs = originCrs.uppercase()
            val originName = commuteSession.lockedOriginOrNull()?.name
                ?: lastAtStationUi?.stationName
                ?: originCrs
            cachedBoardingServices = MatchBoardingCandidates.snapshotsFromDestinations(
                originCrs = originCrs,
                originName = originName,
                destinations = rows,
                favouriteDestinationOrder = favourites,
            )
            updateAtStation { current ->
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
                val updated = current.copy(
                    destinations = destinations,
                    destinationsLoading = false,
                    noFavouritesConfigured = false,
                    platformWatch = watch,
                )
                lastAtStationUi = updated
                updated
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

    private fun BoardingServiceSnapshot.toUi(): BoardingCandidateUi =
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

    private fun updateAtStation(transform: (WalkUpHomeUi.AtStation) -> WalkUpHomeUi.AtStation) {
        val content = _uiState.value as? HomeUiState.Content ?: return
        val at = content.walkUp as? WalkUpHomeUi.AtStation ?: return
        val updated = transform(at)
        lastAtStationUi = updated
        _uiState.value = content.copy(walkUp = updated)
    }

    private fun patchWalkUp(walkUp: WalkUpHomeUi?) {
        when (val current = _uiState.value) {
            is HomeUiState.Content -> _uiState.value = current.copy(walkUp = walkUp)
            is HomeUiState.Empty -> {
                _uiState.value = HomeUiState.Content(
                    walkUp = walkUp,
                    stations = emptyList(),
                )
            }
            HomeUiState.Loading -> {
                _uiState.value = HomeUiState.Content(
                    walkUp = walkUp,
                    stations = emptyList(),
                )
            }
            is HomeUiState.Error -> {
                _uiState.value = HomeUiState.Content(
                    walkUp = walkUp,
                    stations = emptyList(),
                )
            }
        }
    }

    private suspend fun loadBoards(windowsCrs: List<String>, forceRefresh: Boolean) {
        val walkUp = (_uiState.value as? HomeUiState.Content)?.walkUp
        if (windowsCrs.isEmpty()) {
            if (walkUp != null) {
                _uiState.value = HomeUiState.Content(
                    walkUp = walkUp,
                    stations = emptyList(),
                    refreshing = false,
                )
            } else {
                _uiState.value = HomeUiState.Empty(EmptyReason.NoStationsConfigured)
            }
            return
        }

        val reasonByCrs = reasonLabels(windowsCrs)
        val cards = coroutineScope {
            windowsCrs.map { crs ->
                async { loadCard(crs, reasonByCrs[crs].orEmpty(), forceRefresh) }
            }.awaitAll()
        }
        val previousWalkUp = (_uiState.value as? HomeUiState.Content)?.walkUp
        _uiState.value = HomeUiState.Content(
            walkUp = previousWalkUp ?: walkUp,
            stations = cards,
            refreshing = false,
        )
    }

    private suspend fun reasonLabels(crsList: List<String>): Map<String, String> {
        val windows = commuteWindowStore.getWindows()
        val favourites = favouritesStore.getFavourites().toSet()
        val nowActive = windows.filter {
            it.isActiveAt(java.time.LocalDateTime.now())
        }.map { it.stationCrs.uppercase() }.toSet()

        return crsList.associateWith { crs ->
            when {
                crs in nowActive -> "Commute window"
                crs in favourites -> "Favourite"
                else -> "Suggested"
            }
        }
    }

    private suspend fun loadCard(
        crs: String,
        reasonLabel: String,
        forceRefresh: Boolean,
    ): HomeStationCardUi {
        val station = stationRepository.getByCrs(crs)
        val name = station?.name ?: crs
        return try {
            val board = departureRepository.getBoard(crs, forceRefresh = forceRefresh)
            val disruption = DeriveBoardDisruption.from(board)
            val trains = board.departures
                .filterNot { it.isArrival }
                .ifEmpty { board.departures }
                .take(5)
                .map { it.toChip() }
            val ageMs = System.currentTimeMillis() - board.fetchedAtEpochMs
            HomeStationCardUi(
                crsCode = crs,
                stationName = board.stationName.ifBlank { name },
                reasonLabel = reasonLabel,
                disruption = disruption,
                nextTrains = trains,
                fetchedLabel = formatFetched(board.fetchedAtEpochMs),
                stale = board.fromCache || ageMs > 90_000L,
            )
        } catch (t: Throwable) {
            HomeStationCardUi(
                crsCode = crs,
                stationName = name,
                reasonLabel = reasonLabel,
                disruption = DisruptionSummary(
                    severity = DisruptionSeverity.Amber,
                    headline = "Board unavailable",
                    detail = t.message ?: "Could not load departures",
                    affectedOperators = emptyList(),
                    cancelledCount = 0,
                    delayedCount = 0,
                    maxDelayMinutes = 0,
                ),
                nextTrains = emptyList(),
                boardError = t.message ?: "Could not load departures",
            )
        }
    }

    private fun Departure.toChip(): HomeDepartureChipUi {
        val kind = when (status) {
            TrainStatus.OnTime -> StatusKind.OnTime
            TrainStatus.Delayed -> StatusKind.Delayed
            TrainStatus.Cancelled -> StatusKind.Cancelled
            else -> StatusKind.Other
        }
        val statusLabel = when {
            status == TrainStatus.Cancelled -> "Cancelled"
            status == TrainStatus.OnTime -> "On time"
            delayMinutes != null && delayMinutes > 0 -> "+$delayMinutes"
            else -> expectedLabel
        }
        return HomeDepartureChipUi(
            timeLabel = scheduledTimeLabel.ifBlank { "—" },
            destinationLabel = destination,
            statusLabel = statusLabel,
            statusKind = kind,
        )
    }

    private fun startOnTrainPolling() {
        onTrainPollJob?.cancel()
        onTrainPollJob = viewModelScope.launch {
            while (isActive && commuteSession is CommuteSession.OnTrain) {
                val stop = commuteSession.onTrainOrNull()?.stop
                val stopped = stop is StationStopPhase.Confirmed ||
                    stop is StationStopPhase.Dwelling
                delay(if (stopped) ON_TRAIN_POLL_STOPPED_MS else ON_TRAIN_POLL_MS)
                if (commuteSession !is CommuteSession.OnTrain) break
                refreshOnTrainOption()
            }
        }
    }

    private fun startOnTrainProgressTick() {
        onTrainProgressTickJob?.cancel()
        onTrainProgressTickJob = viewModelScope.launch {
            while (isActive && commuteSession is CommuteSession.OnTrain) {
                delay(ON_TRAIN_PROGRESS_TICK_MS)
                val onTrain = commuteSession.onTrainOrNull() ?: break
                val option = latestOnTrainOption ?: continue
                applyOnTrainProgress(option, onTrain)
            }
        }
    }

    private suspend fun refreshOnTrainOption() {
        val pinned = commuteSession.pinnedJourneyOrNull() ?: return
        updateOnJourney { it.copy(refreshing = true, errorMessage = null) }
        try {
            val option = journeyRepository.getServiceDetails(
                serviceId = pinned.serviceId,
                originCrs = pinned.originCrs,
                destinationCrs = pinned.destinationCrs,
                destinationNameFallback = pinned.destinationName,
            )
            if (commuteSession !is CommuteSession.OnTrain) return
            latestOnTrainOption = option
            routeStations = buildRouteStations(option)
            applyTransition(reduceEvent(CommuteEvent.ServiceDetails(option)))
        } catch (t: Throwable) {
            val missing = t.message?.contains("not found", ignoreCase = true) == true ||
                t.message?.contains("HTTP 404", ignoreCase = true) == true
            updateOnJourney {
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

    private fun applyOnTrainProgress(option: JourneyOption, onTrain: CommuteSession.OnTrain) {
        val pinned = onTrain.pin
        val progress = ComputeJourneyProgress.compute(
            callingPoints = option.callingPoints,
            originCrs = pinned.originCrs,
            destinationCrs = pinned.destinationCrs,
            destinationNameFallback = pinned.destinationName,
            confirmedStopCrs = onTrain.confirmedStopCrs,
            now = LocalDateTime.now(),
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

        patchWalkUp(
            WalkUpHomeUi.OnJourney(
                serviceId = pinned.serviceId,
                originCrs = pinned.originCrs,
                destinationCrs = pinned.destinationCrs,
                destinationName = progress.destinationName,
                scheduledDepartureLabel = pinned.scheduledDepartureLabel,
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
            ),
        )
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

    private fun updateOnJourney(transform: (WalkUpHomeUi.OnJourney) -> WalkUpHomeUi.OnJourney) {
        val content = _uiState.value as? HomeUiState.Content ?: return
        val onJourney = content.walkUp as? WalkUpHomeUi.OnJourney ?: return
        _uiState.value = content.copy(walkUp = transform(onJourney))
    }

    override fun onCleared() {
        onTrainPollJob?.cancel()
        // Leave the foreground service running if a pin is still active — Home will
        // re-attach on the next open. Stop it only when the session itself ends.
        super.onCleared()
    }

    data class OpenJourneyEvent(
        val originCrs: String,
        val destinationCrs: String,
        val serviceId: String,
    )

    companion object {
        private const val TAG = "UkRailTracker"
        private const val LOCATION_POLL_MS = 10_000L
        private const val LOCATION_POLL_WATCHING_MS = 4_000L
        private const val LOCATION_POLL_ON_TRAIN_MS = 5_000L
        private const val FREQUENT_CHECKS_TTL_MS = 5 * 60_000L
        private const val ON_TRAIN_POLL_MS = 45_000L
        /** Per-minute ETA refresh while dwelling / stopped at a station. */
        private const val ON_TRAIN_POLL_STOPPED_MS = 60_000L
        /** Recompute hop-interpolated progress without a Darwin round-trip. */
        private const val ON_TRAIN_PROGRESS_TICK_MS = 10_000L
        private val FETCHED_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

        private val CHECKED_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

        private fun formatFetched(epochMs: Long): String =
            "Updated ${FETCHED_FMT.format(Instant.ofEpochMilli(epochMs))}"

        private fun formatCheckedNow(): String =
            CHECKED_FMT.format(Instant.now())

        /**
         * Poll cadence for the selected departure — denser as the train approaches.
         * Unknown ETA uses a moderate default.
         */
        internal fun platformPollIntervalMs(minutesUntilDeparture: Long?): Long = when {
            minutesUntilDeparture == null -> 20_000L
            minutesUntilDeparture > 20L -> 60_000L
            minutesUntilDeparture > 10L -> 30_000L
            minutesUntilDeparture > 5L -> 15_000L
            minutesUntilDeparture >= 0L -> 10_000L
            else -> 30_000L // already departed — keep a light watch
        }

        fun factory(
            appContext: Context,
            stationRepository: StationRepository,
            departureRepository: DepartureRepository,
            journeyRepository: JourneyRepository,
            journeyLogRepository: JourneyLogRepository,
            locationProvider: LocationProvider,
            commuteWindowStore: CommuteWindowStore,
            favouritesStore: FavouritesStore,
            pinnedJourneyStore: PinnedJourneyStore,
            latestSpeedMps: MutableStateFlow<Double?>,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    appContext = appContext.applicationContext,
                    stationRepository = stationRepository,
                    departureRepository = departureRepository,
                    journeyRepository = journeyRepository,
                    journeyLogRepository = journeyLogRepository,
                    locationProvider = locationProvider,
                    commuteWindowStore = commuteWindowStore,
                    favouritesStore = favouritesStore,
                    pinnedJourneyStore = pinnedJourneyStore,
                    latestSpeedMps = latestSpeedMps,
                ) as T
            }
        }
    }
}
