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
import com.ukrailtracker.app.domain.usecase.ArrivalBuzzTracker
import com.ukrailtracker.app.domain.usecase.BoardingConfig
import com.ukrailtracker.app.domain.usecase.BoardingPhase
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteEffect
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.isManualOriginOverride
import com.ukrailtracker.app.domain.usecase.lockedOriginOrNull
import com.ukrailtracker.app.domain.usecase.reduce
import com.ukrailtracker.app.domain.usecase.ComputeJourneyProgress
import com.ukrailtracker.app.domain.usecase.DeriveBoardDisruption
import com.ukrailtracker.app.domain.usecase.DetectBoarding
import com.ukrailtracker.app.domain.usecase.DetectStationStop
import com.ukrailtracker.app.domain.usecase.DetectWalkUpArrival
import com.ukrailtracker.app.domain.usecase.FavouriteDestinationServices
import com.ukrailtracker.app.domain.usecase.FavouriteServiceUiModel
import com.ukrailtracker.app.domain.usecase.GetContextualStationsUseCase
import com.ukrailtracker.app.domain.usecase.JourneyTimeUtils
import com.ukrailtracker.app.domain.usecase.LoadFavouriteBoundDepartures
import com.ukrailtracker.app.domain.usecase.MatchBoardingCandidates
import com.ukrailtracker.app.domain.usecase.ProgressPointUiModel
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

data class HomeDepartureChipUi(
    val timeLabel: String,
    val destinationLabel: String,
    val statusLabel: String,
    val statusKind: StatusKind,
)

data class HomeStationCardUi(
    val crsCode: String,
    val stationName: String,
    val reasonLabel: String,
    val disruption: DisruptionSummary,
    val nextTrains: List<HomeDepartureChipUi>,
    val boardError: String? = null,
    val fetchedLabel: String? = null,
    val stale: Boolean = false,
)

data class WalkUpDestinationUi(
    val destinationCrs: String,
    val destinationName: String,
    val services: List<FavouriteServiceUiModel>,
    val errorMessage: String? = null,
)

/** Selected favourite-bound service being polled until a departure platform appears. */
data class PlatformWatchUi(
    val serviceId: String,
    val originCrs: String,
    val destinationCrs: String,
    val destinationName: String,
    val scheduledDepartureLabel: String,
    val expectedDepartureLabel: String = "",
    val platform: String?,
    /** Clock time (HH:mm) of the last successful board refresh. */
    val checkedAtLabel: String? = null,
    val polling: Boolean = false,
    val errorMessage: String? = null,
)

data class BoardingCandidateUi(
    val serviceId: String,
    val originCrs: String,
    val destinationCrs: String,
    val destinationName: String,
    val originName: String,
    val operatorName: String,
    val scheduledDepartureLabel: String,
    val expectedDepartureLabel: String,
    val platform: String?,
)

/**
 * Presence used by the Home status bar.
 * Default / sensing / permission issues keep the usual home body.
 * [AtStation] / [BoardingPrompt] replace the home body with trip-focused content.
 */
sealed interface WalkUpHomeUi {
    data object NeedsPermission : WalkUpHomeUi
    data object LocationUnavailable : WalkUpHomeUi
    /** Not inside a station geofence — status bar shows "Not at a station". */
    data object NotAtStation : WalkUpHomeUi
    data class NearStation(
        val crsCode: String,
        val stationName: String,
        val distanceLabel: String,
    ) : WalkUpHomeUi

    data class AtStation(
        val crsCode: String,
        val stationName: String,
        val distanceLabel: String?,
        val manualOverride: Boolean,
        val destinations: List<WalkUpDestinationUi>,
        val destinationsLoading: Boolean,
        val noFavouritesConfigured: Boolean,
        val platformWatch: PlatformWatchUi? = null,
    ) : WalkUpHomeUi

    data class BoardingPrompt(
        val originCrs: String,
        val originName: String,
        val assumed: BoardingCandidateUi,
        val alternatives: List<BoardingCandidateUi>,
        val showPicker: Boolean,
    ) : WalkUpHomeUi

    data class OnJourney(
        val serviceId: String,
        val originCrs: String,
        val destinationCrs: String,
        val destinationName: String,
        val scheduledDepartureLabel: String,
        val minutesRemaining: Long?,
        val nextStopName: String?,
        val currentStopName: String?,
        val delayMinutes: Int?,
        val statusKind: StatusKind,
        val routeMismatch: Boolean,
        val mismatchStationName: String?,
        val expectedNextStopName: String?,
        val points: List<ProgressPointUiModel>,
        val fractionComplete: Float,
        val stoppedAtStation: Boolean = false,
        val refreshing: Boolean = false,
        val errorMessage: String? = null,
        val serviceMissing: Boolean = false,
    ) : WalkUpHomeUi
}

val WalkUpHomeUi?.focusesAtStation: Boolean
    get() = this is WalkUpHomeUi.AtStation || this is WalkUpHomeUi.BoardingPrompt

val WalkUpHomeUi?.focusesTripUi: Boolean
    get() = focusesAtStation || this is WalkUpHomeUi.OnJourney

val WalkUpHomeUi?.focusesOnTrain: Boolean
    get() = this is WalkUpHomeUi.OnJourney

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Empty(val messageKey: EmptyReason) : HomeUiState
    data class Content(
        val walkUp: WalkUpHomeUi? = null,
        val stations: List<HomeStationCardUi>,
        val refreshing: Boolean = false,
        val selectedDisruption: DisruptionSummary? = null,
        val selectedStationName: String? = null,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

enum class EmptyReason {
    NoStationsConfigured,
}

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
    private var boardingPhase: BoardingPhase = BoardingPhase.Idle
    private var previousSample: WalkUpSample? = null
    private var locationWatchJob: Job? = null
    private var destinationLoadJob: Job? = null
    private var boardingAutoAcceptJob: Job? = null
    private var platformWatchJob: Job? = null
    private var onTrainPollJob: Job? = null
    private var permissionGranted: Boolean = false
    private var lastLoadedOriginCrs: String? = null
    /** First GPS fix after start may lock "already at station" without a walk-up sequence. */
    private var alreadyPresentOk: Boolean = true
    private var cachedBoardingServices: List<BoardingServiceSnapshot> = emptyList()
    private var lastAtStationUi: WalkUpHomeUi.AtStation? = null
    private var platformNotified: Boolean = false

    private var activePinned: PinnedJourney? = null
    private var latestOnTrainOption: JourneyOption? = null
    private var routeStations: List<RouteStation> = emptyList()
    private var stationStopPhase: StationStopPhase = StationStopPhase.Moving
    private var confirmedStopCrs: String? = null
    /** CRS of the last stop that triggered a GetServiceDetails confirm refresh. */
    private var lastServiceDetailsStopCrs: String? = null
    private var buzzedThresholds: Set<Int> = emptySet()
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
                if (pinned == null) {
                    if (activePinned != null) {
                        tearDownOnTrainSession(clearPin = false, logJourney = false)
                    }
                } else if (pinned.serviceId != activePinned?.serviceId) {
                    beginOnTrainSession(pinned)
                }
            }
        }
    }

    fun onNeedsPermission() {
        permissionGranted = false
        stopLocationWatch()
        applyTransition(reduce(commuteSession, CommuteEvent.PermissionLost))
        patchWalkUp(WalkUpHomeUi.NeedsPermission)
    }

    fun onPermissionGranted() {
        permissionGranted = true
        startLocationWatch()
    }

    /**
     * Notification tap / deep wake — focus the ENTER station and load boards immediately,
     * then elevate GPS poll so walk-up can confirm.
     */
    fun onOpenedForFrequentChecks(hintCrs: String? = null) {
        frequentChecksUntilEpochMs = System.currentTimeMillis() + FREQUENT_CHECKS_TTL_MS
        viewModelScope.launch {
            if (activePinned == null) {
                val crs = hintCrs?.trim()?.uppercase().orEmpty()
                if (crs.isNotEmpty()) {
                    val station = stationRepository.getByCrs(crs)
                    applyTransition(
                        reduce(
                            commuteSession,
                            CommuteEvent.OpenedForFrequentChecks(
                                LockedOrigin(
                                    crs = crs,
                                    name = station?.name ?: crs,
                                    latitude = station?.latitude ?: 0.0,
                                    longitude = station?.longitude ?: 0.0,
                                    lockedAtEpochMs = System.currentTimeMillis(),
                                    distanceMetres = 0.0,
                                    manualOverride = false,
                                ),
                            ),
                        ),
                    )
                }
            }
            if (permissionGranted) {
                startLocationWatch(forceRefresh = true)
            }
        }
    }

    /**
     * Resets walk-up sensing so an external debug location override can drive Away → Near → At.
     * Used by [com.ukrailtracker.app.debug.DebugWalkUp] (debug builds only).
     */
    fun prepareForDebugWalkUp(elevatePollForMs: Long) {
        viewModelScope.launch {
            if (activePinned != null) {
                tearDownOnTrainSession(clearPin = true, logJourney = false)
            }
            clearBoardingSession(keepUi = false)
            cancelBoardingAutoAccept()
            stopPlatformWatch()
            lastLoadedOriginCrs = null
            commuteSession = CommuteSession.Idle
            previousSample = null
            alreadyPresentOk = false
            boardingPhase = BoardingPhase.Idle
            frequentChecksUntilEpochMs = System.currentTimeMillis() + elevatePollForMs.coerceAtLeast(0L)
            permissionGranted = true
            patchWalkUp(WalkUpHomeUi.NotAtStation)
            startLocationWatch(forceRefresh = true)
        }
    }

    fun onPermissionDenied() {
        permissionGranted = false
        stopLocationWatch()
        applyTransition(reduce(commuteSession, CommuteEvent.PermissionLost))
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
                reduce(
                    commuteSession,
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
        applyTransition(reduce(commuteSession, CommuteEvent.ClearOverride))
        if (permissionGranted) {
            startLocationWatch(forceRefresh = true)
        } else {
            patchWalkUp(WalkUpHomeUi.NeedsPermission)
        }
    }

    fun confirmBoarding() {
        val prompt = currentBoardingPrompt() ?: return
        pinAndOpen(prompt.assumed)
    }

    fun rejectAssumedBoarding() {
        val prompt = currentBoardingPrompt() ?: return
        patchWalkUp(prompt.copy(showPicker = true))
        // Keep auto-accept on assumed until user picks another or dismisses.
    }

    fun selectBoardingCandidate(serviceId: String) {
        val prompt = currentBoardingPrompt() ?: return
        val chosen = sequenceOf(prompt.assumed)
            .plus(prompt.alternatives)
            .firstOrNull { it.serviceId == serviceId }
            ?: return
        pinAndOpen(chosen)
    }

    fun dismissBoarding() {
        cancelBoardingAutoAccept()
        clearBoardingSession(keepUi = false)
        commuteSession = CommuteSession.Idle
        previousSample = null
        alreadyPresentOk = true
        patchWalkUp(WalkUpHomeUi.NotAtStation)
    }

    fun endOnTrainTrip() {
        viewModelScope.launch {
            tearDownOnTrainSession(clearPin = true, logJourney = true)
        }
    }

    fun openOnTrainServiceDetail() {
        val pinned = activePinned ?: return
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
            if (activePinned != null) {
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

    private fun currentBoardingPrompt(): WalkUpHomeUi.BoardingPrompt? =
        (_uiState.value as? HomeUiState.Content)?.walkUp as? WalkUpHomeUi.BoardingPrompt

    private fun currentAtStation(): WalkUpHomeUi.AtStation? =
        (_uiState.value as? HomeUiState.Content)?.walkUp as? WalkUpHomeUi.AtStation

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

    private fun pinAndOpen(candidate: BoardingCandidateUi) {
        cancelBoardingAutoAccept()
        viewModelScope.launch {
            clearBoardingSession(keepUi = false)
            commuteSession = CommuteSession.Idle
            previousSample = null
            alreadyPresentOk = false
            pinnedJourneyStore.pin(
                PinnedJourney(
                    serviceId = candidate.serviceId,
                    originCrs = candidate.originCrs,
                    destinationCrs = candidate.destinationCrs,
                    originName = candidate.originName,
                    destinationName = candidate.destinationName,
                    operatorName = candidate.operatorName,
                    scheduledDepartureLabel = candidate.scheduledDepartureLabel,
                    pinnedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            // pinnedFlow collector starts the on-train session on Home.
        }
    }

    private fun startLocationWatch(forceRefresh: Boolean = false) {
        if (locationWatchJob?.isActive == true && !forceRefresh) return
        locationWatchJob?.cancel()
        alreadyPresentOk = true
        locationWatchJob = viewModelScope.launch {
            if (!commuteSession.isManualOriginOverride() &&
                currentBoardingPrompt() == null &&
                activePinned == null &&
                commuteSession is CommuteSession.Idle
            ) {
                patchWalkUp(WalkUpHomeUi.NotAtStation)
            }
            sampleLocation(forceRefresh = true)
            while (isActive) {
                delay(currentPollIntervalMs())
                if (!commuteSession.isManualOriginOverride()) {
                    val watching = activePinned != null ||
                        commuteSession is CommuteSession.AtStation ||
                        currentBoardingPrompt() != null ||
                        System.currentTimeMillis() < frequentChecksUntilEpochMs
                    sampleLocation(forceRefresh = watching)
                }
            }
        }
    }

    private fun currentPollIntervalMs(): Long {
        if (activePinned != null) return LOCATION_POLL_ON_TRAIN_MS
        if (System.currentTimeMillis() < frequentChecksUntilEpochMs) {
            return LOCATION_POLL_WATCHING_MS
        }
        val watching = commuteSession is CommuteSession.AtStation ||
            currentBoardingPrompt() != null
        return if (watching) LOCATION_POLL_WATCHING_MS else LOCATION_POLL_MS
    }

    private fun stopLocationWatch() {
        locationWatchJob?.cancel()
        locationWatchJob = null
    }

    private suspend fun sampleLocation(forceRefresh: Boolean) {
        if (commuteSession.isManualOriginOverride()) return
        // Freeze walk-up overwrites while the user is confirming boarding.
        if (currentBoardingPrompt() != null) return

        val location = locationProvider.currentLocation(forceRefresh = forceRefresh)
        if (location == null) {
            if (activePinned == null && commuteSession !is CommuteSession.AtStation) {
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
        previousSample = sample

        // M6 on-train session owns GPS — do not let walk-up steal focus.
        if (activePinned != null) {
            sampleOnTrainLocation(sample, speed)
            return
        }

        val nearby = stationRepository.getNearby(
            lat = location.latitude,
            lng = location.longitude,
            limit = 5,
        )

        val locked = commuteSession.lockedOriginOrNull()
        if (locked != null) {
            boardingPhase = DetectBoarding.next(
                phase = boardingPhase,
                sample = sample,
                originLatitude = locked.latitude,
                originLongitude = locked.longitude,
                speedMps = speed,
                config = boardingConfig,
            )
            if (boardingPhase is BoardingPhase.Triggered) {
                val triggered = boardingPhase as BoardingPhase.Triggered
                enterBoardingPrompt(triggered.triggeredAtEpochMs)
                return
            }
        }

        val previous = commuteSession
        val transition = reduce(
            previous,
            CommuteEvent.Location(
                sample = sample,
                nearest = nearby.firstOrNull(),
                speedMps = speed,
                alreadyPresentOk = alreadyPresentOk && previous is CommuteSession.Idle,
            ),
        )
        alreadyPresentOk = false

        val next = transition.session
        val leftAt = previous is CommuteSession.AtStation && next !is CommuteSession.AtStation

        when {
            next is CommuteSession.AtStation -> {
                if (previous !is CommuteSession.AtStation ||
                    !previous.origin.crs.equals(next.origin.crs, ignoreCase = true)
                ) {
                    boardingPhase = BoardingPhase.Idle
                }
                applyTransition(transition)
            }

            leftAt || locked != null -> {
                handleLeftOrWatching(
                    previousOrigin = locked ?: previous.lockedOriginOrNull(),
                    next = next,
                    sample = sample,
                    speedMps = speed,
                )
            }

            else -> {
                clearBoardingSession(keepUi = false)
                applyTransition(transition)
            }
        }
    }

    private suspend fun handleLeftOrWatching(
        previousOrigin: LockedOrigin?,
        next: CommuteSession,
        sample: WalkUpSample,
        speedMps: Double?,
    ) {
        val locked = previousOrigin ?: run {
            clearBoardingSession(keepUi = false)
            commuteSession = next
            applyPresenceUi(next)
            return
        }
        val distance = GeoUtils.haversineMetres(
            locked.latitude,
            locked.longitude,
            sample.latitude,
            sample.longitude,
        )

        when (boardingPhase) {
            is BoardingPhase.Sustaining -> {
                // Keep last at-station UI while motion is still qualifying.
                lastAtStationUi?.let { patchWalkUp(it) }
            }

            BoardingPhase.Idle -> {
                val walkedAway =
                    distance >= boardingConfig.leaveMetres &&
                        (speedMps == null || speedMps < boardingConfig.boardMinMps)
                val stillNear = distance < boardingConfig.leaveMetres
                when {
                    walkedAway -> {
                        clearBoardingSession(keepUi = false)
                        commuteSession = CommuteSession.Idle
                        applyPresenceUi(CommuteSession.Idle)
                    }
                    stillNear -> {
                        // Vehicle leave / platform motion before leave threshold — keep watching.
                        lastAtStationUi?.let { patchWalkUp(it) }
                            ?: applyPresenceUi(next)
                    }
                    else -> {
                        clearBoardingSession(keepUi = false)
                        commuteSession = next
                        applyPresenceUi(next)
                    }
                }
            }

            is BoardingPhase.Triggered -> {
                enterBoardingPrompt((boardingPhase as BoardingPhase.Triggered).triggeredAtEpochMs)
            }
        }
    }

    private fun enterBoardingPrompt(triggeredAtEpochMs: Long) {
        val locked = commuteSession.lockedOriginOrNull() ?: return
        val match = MatchBoardingCandidates.match(
            cached = cachedBoardingServices,
            boardedAtEpochMs = triggeredAtEpochMs,
            pastMinutes = boardingConfig.matchPastMinutes,
            futureMinutes = boardingConfig.matchFutureMinutes,
        )
        if (match == null) {
            clearBoardingSession(keepUi = false)
            commuteSession = CommuteSession.Idle
            patchWalkUp(WalkUpHomeUi.NotAtStation)
            return
        }
        val prompt = WalkUpHomeUi.BoardingPrompt(
            originCrs = locked.crs,
            originName = locked.name,
            assumed = match.assumed.toUi(),
            alternatives = match.alternatives.map { it.toUi() },
            showPicker = false,
        )
        patchWalkUp(prompt)
        startBoardingAutoAccept()
    }

    private fun startBoardingAutoAccept() {
        cancelBoardingAutoAccept()
        boardingAutoAcceptJob = viewModelScope.launch {
            delay(BOARDING_AUTO_ACCEPT_MS)
            if (currentBoardingPrompt() != null) {
                confirmBoarding()
            }
        }
    }

    private fun cancelBoardingAutoAccept() {
        boardingAutoAcceptJob?.cancel()
        boardingAutoAcceptJob = null
    }

    private fun clearBoardingSession(keepUi: Boolean) {
        cancelBoardingAutoAccept()
        stopPlatformWatch()
        platformNotified = false
        boardingPhase = BoardingPhase.Idle
        cachedBoardingServices = emptyList()
        if (!keepUi) {
            lastAtStationUi = null
        }
    }

    private fun applyTransition(transition: CommuteTransition) {
        commuteSession = transition.session
        applyPresenceUi(transition.session)
        for (effect in transition.effects) {
            when (effect) {
                is CommuteEffect.LoadFavouriteBoundDepartures ->
                    loadDestinations(originCrs = effect.originCrs, forceRefresh = effect.forceRefresh)
            }
        }
    }

    private fun applyPresenceUi(session: CommuteSession) {
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
        }
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

    private suspend fun beginOnTrainSession(pinned: PinnedJourney) {
        clearBoardingSession(keepUi = false)
        commuteSession = CommuteSession.Idle
        activePinned = pinned
        stationStopPhase = StationStopPhase.Moving
        confirmedStopCrs = null
        lastServiceDetailsStopCrs = null
        buzzedThresholds = emptySet()
        latestOnTrainOption = null
        routeStations = emptyList()

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
                refreshing = true,
            ),
        )
        startOnTrainService(pinned.destinationName, null)
        if (permissionGranted) {
            startLocationWatch(forceRefresh = true)
        }
        refreshOnTrainOption()
        startOnTrainPolling()
    }

    private suspend fun tearDownOnTrainSession(clearPin: Boolean, logJourney: Boolean) {
        onTrainPollJob?.cancel()
        onTrainPollJob = null
        val option = latestOnTrainOption
        val wasActive = activePinned != null
        // Clear session flag first so pinnedFlow(null) does not re-enter teardown.
        activePinned = null
        latestOnTrainOption = null
        routeStations = emptyList()
        stationStopPhase = StationStopPhase.Moving
        confirmedStopCrs = null
        lastServiceDetailsStopCrs = null
        buzzedThresholds = emptySet()
        alreadyPresentOk = true
        if (logJourney && option != null) {
            runCatching { journeyLogRepository.logFromOption(option) }
        }
        stopOnTrainService()
        if (clearPin) {
            pinnedJourneyStore.clear()
        }
        if (wasActive &&
            (_uiState.value as? HomeUiState.Content)?.walkUp is WalkUpHomeUi.OnJourney
        ) {
            patchWalkUp(WalkUpHomeUi.NotAtStation)
        }
    }

    private fun startOnTrainPolling() {
        onTrainPollJob?.cancel()
        onTrainPollJob = viewModelScope.launch {
            while (isActive && activePinned != null) {
                val stopped = stationStopPhase is StationStopPhase.Confirmed ||
                    stationStopPhase is StationStopPhase.Dwelling
                // Between stops we keep a light heartbeat; at a stop we refresh more often
                // so GetServiceDetails can confirm timings while dwelling.
                delay(if (stopped) ON_TRAIN_POLL_STOPPED_MS else ON_TRAIN_POLL_MS)
                if (activePinned == null) break
                refreshOnTrainOption()
            }
        }
    }

    private suspend fun refreshOnTrainOption() {
        val pinned = activePinned ?: return
        updateOnJourney { it.copy(refreshing = true, errorMessage = null) }
        try {
            val option = journeyRepository.getServiceDetails(
                serviceId = pinned.serviceId,
                originCrs = pinned.originCrs,
                destinationCrs = pinned.destinationCrs,
                destinationNameFallback = pinned.destinationName,
            )
            latestOnTrainOption = option
            routeStations = buildRouteStations(option)
            applyOnTrainProgress(option)
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

    private suspend fun sampleOnTrainLocation(sample: WalkUpSample, speedMps: Double?) {
        val pinned = activePinned ?: return
        stationStopPhase = DetectStationStop.next(
            phase = stationStopPhase,
            sample = sample,
            speedMps = speedMps,
            routeStations = routeStations,
            config = stationStopConfig,
        )
        when (val phase = stationStopPhase) {
            is StationStopPhase.Confirmed -> {
                confirmedStopCrs = phase.crs
                val isNewStop = !phase.crs.equals(lastServiceDetailsStopCrs, ignoreCase = true)
                if (isNewStop) {
                    lastServiceDetailsStopCrs = phase.crs
                    // Confirm timing + destination against Darwin for this stop.
                    refreshOnTrainOption()
                    if (phase.crs.equals(pinned.destinationCrs, ignoreCase = true)) {
                        tearDownOnTrainSession(clearPin = true, logJourney = true)
                    }
                    return
                }
                if (phase.crs.equals(pinned.destinationCrs, ignoreCase = true)) {
                    tearDownOnTrainSession(clearPin = true, logJourney = true)
                    return
                }
            }
            is StationStopPhase.Dwelling -> {
                // Keep last confirmed until a new confirmation replaces it.
            }
            StationStopPhase.Moving -> {
                // Retain confirmedStopCrs for progress until the next confirm.
            }
        }
        latestOnTrainOption?.let { applyOnTrainProgress(it) }
            ?: refreshOnTrainOption()
    }

    private suspend fun applyOnTrainProgress(option: JourneyOption) {
        val pinned = activePinned ?: return
        val progress = ComputeJourneyProgress.compute(
            callingPoints = option.callingPoints,
            originCrs = pinned.originCrs,
            destinationCrs = pinned.destinationCrs,
            destinationNameFallback = pinned.destinationName,
            confirmedStopCrs = confirmedStopCrs,
            now = LocalDateTime.now(),
        )
        // Darwin AT at the destination means the train has arrived — end the live trip.
        if (progress.destinationArrived) {
            tearDownOnTrainSession(clearPin = true, logJourney = true)
            return
        }
        val statusKind = when (option.status) {
            TrainStatus.OnTime -> StatusKind.OnTime
            TrainStatus.Delayed -> StatusKind.Delayed
            TrainStatus.Cancelled -> StatusKind.Cancelled
            else -> StatusKind.Other
        }
        val stopped = stationStopPhase is StationStopPhase.Confirmed ||
            stationStopPhase is StationStopPhase.Dwelling
        val mismatchName = if (progress.routeMismatch) {
            (stationStopPhase as? StationStopPhase.Confirmed)?.name
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
                stoppedAtStation = stopped,
                refreshing = false,
                errorMessage = null,
                serviceMissing = false,
            ),
        )
        updateOnTrainService(progress.destinationName, progress.minutesToDestination)
        fireArrivalBuzzIfNeeded(progress.minutesToDestination, progress.approachingDestination)
    }

    private fun fireArrivalBuzzIfNeeded(minutesRemaining: Long?, approaching: Boolean) {
        val due = ArrivalBuzzTracker.thresholdsToFire(
            minutesRemaining = minutesRemaining,
            alreadyFired = buzzedThresholds,
            approachingDestination = approaching,
        )
        if (due.isEmpty()) return
        buzzedThresholds = ArrivalBuzzTracker.withFired(buzzedThresholds, due)
        due.forEach { threshold -> _arrivalBuzz.tryEmit(threshold) }
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
        private const val BOARDING_AUTO_ACCEPT_MS = 60_000L
        private const val ON_TRAIN_POLL_MS = 45_000L
        /** Per-minute ETA refresh while dwelling / stopped at a station. */
        private const val ON_TRAIN_POLL_STOPPED_MS = 60_000L

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
                ) as T
            }
        }
    }
}
