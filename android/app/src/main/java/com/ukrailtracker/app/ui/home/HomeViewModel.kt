package com.ukrailtracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.data.local.datastore.CommuteWindowStore
import com.ukrailtracker.app.data.local.datastore.FavouritesStore
import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.model.Departure
import com.ukrailtracker.app.domain.model.DisruptionSeverity
import com.ukrailtracker.app.domain.model.DisruptionSummary
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.repository.DepartureRepository
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.DeriveBoardDisruption
import com.ukrailtracker.app.domain.usecase.DetectWalkUpArrival
import com.ukrailtracker.app.domain.usecase.FavouriteDestinationServices
import com.ukrailtracker.app.domain.usecase.FavouriteServiceUiModel
import com.ukrailtracker.app.domain.usecase.GetContextualStationsUseCase
import com.ukrailtracker.app.domain.usecase.LoadFavouriteBoundDepartures
import com.ukrailtracker.app.domain.usecase.WalkUpPhase
import com.ukrailtracker.app.domain.usecase.WalkUpSample
import com.ukrailtracker.app.ui.station.StatusKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
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

/**
 * Presence used by the Home status bar.
 * Default / sensing / permission issues keep the usual home body.
 * [AtStation] (and later on-journey) replaces the home body with trip-focused content.
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
    ) : WalkUpHomeUi

    // M6: OnJourney(currentStop, nextStop, destination) for way-station status-bar updates
}

val WalkUpHomeUi?.focusesAtStation: Boolean
    get() = this is WalkUpHomeUi.AtStation

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
    private val stationRepository: StationRepository,
    private val departureRepository: DepartureRepository,
    private val journeyRepository: JourneyRepository,
    private val locationProvider: LocationProvider,
    private val commuteWindowStore: CommuteWindowStore,
    private val favouritesStore: FavouritesStore,
    private val getContextualStations: GetContextualStationsUseCase = GetContextualStationsUseCase(),
    private val loadFavouriteBoundDepartures: LoadFavouriteBoundDepartures =
        LoadFavouriteBoundDepartures(journeyRepository, stationRepository),
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var walkUpPhase: WalkUpPhase = WalkUpPhase.Away
    private var previousSample: WalkUpSample? = null
    private var locationWatchJob: Job? = null
    private var destinationLoadJob: Job? = null
    private var permissionGranted: Boolean = false
    private var originOverrideCrs: String? = null
    private var lastLoadedOriginCrs: String? = null
    /** First GPS fix after start may lock "already at station" without a walk-up sequence. */
    private var alreadyPresentOk: Boolean = true

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
    }

    fun onNeedsPermission() {
        permissionGranted = false
        stopLocationWatch()
        patchWalkUp(WalkUpHomeUi.NeedsPermission)
    }

    fun onPermissionGranted() {
        permissionGranted = true
        startLocationWatch()
    }

    fun onPermissionDenied() {
        permissionGranted = false
        stopLocationWatch()
        patchWalkUp(WalkUpHomeUi.NeedsPermission)
    }

    fun setOriginOverride(crs: String) {
        viewModelScope.launch {
            val normalised = crs.trim().uppercase()
            if (normalised.isBlank()) return@launch
            originOverrideCrs = normalised
            val station = stationRepository.getByCrs(normalised)
            val name = station?.name ?: normalised
            lastLoadedOriginCrs = null
            patchWalkUp(
                WalkUpHomeUi.AtStation(
                    crsCode = normalised,
                    stationName = name,
                    distanceLabel = null,
                    manualOverride = true,
                    destinations = emptyList(),
                    destinationsLoading = true,
                    noFavouritesConfigured = false,
                ),
            )
            loadDestinations(originCrs = normalised, forceRefresh = true)
        }
    }

    fun clearOriginOverride() {
        originOverrideCrs = null
        lastLoadedOriginCrs = null
        walkUpPhase = WalkUpPhase.Away
        previousSample = null
        alreadyPresentOk = true
        if (permissionGranted) {
            patchWalkUp(WalkUpHomeUi.NotAtStation)
            startLocationWatch(forceRefresh = true)
        } else {
            patchWalkUp(WalkUpHomeUi.NeedsPermission)
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
            if (permissionGranted && originOverrideCrs == null) {
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

    private fun startLocationWatch(forceRefresh: Boolean = false) {
        if (locationWatchJob?.isActive == true && !forceRefresh) return
        locationWatchJob?.cancel()
        alreadyPresentOk = true
        locationWatchJob = viewModelScope.launch {
            if (originOverrideCrs == null) {
                patchWalkUp(WalkUpHomeUi.NotAtStation)
            }
            sampleLocation(forceRefresh = true)
            while (isActive) {
                delay(LOCATION_POLL_MS)
                if (originOverrideCrs == null) {
                    sampleLocation(forceRefresh = false)
                }
            }
        }
    }

    private fun stopLocationWatch() {
        locationWatchJob?.cancel()
        locationWatchJob = null
    }

    private suspend fun sampleLocation(forceRefresh: Boolean) {
        if (originOverrideCrs != null) return
        val location = locationProvider.currentLocation(forceRefresh = forceRefresh)
        if (location == null) {
            if (walkUpPhase !is WalkUpPhase.Arrived) {
                patchWalkUp(WalkUpHomeUi.LocationUnavailable)
            }
            return
        }

        val nearby = stationRepository.getNearby(
            lat = location.latitude,
            lng = location.longitude,
            limit = 5,
        )
        val sample = WalkUpSample(
            latitude = location.latitude,
            longitude = location.longitude,
            speedMps = location.speedMetresPerSecond?.toDouble(),
            epochMs = location.epochMs,
        )
        val speed = DetectWalkUpArrival.resolveSpeed(sample, previousSample)
        val next = DetectWalkUpArrival.next(
            phase = walkUpPhase,
            sample = sample,
            nearest = nearby.firstOrNull(),
            speedMps = speed,
            alreadyPresentOk = alreadyPresentOk && walkUpPhase == WalkUpPhase.Away,
        )
        previousSample = sample
        walkUpPhase = next
        alreadyPresentOk = false
        applyWalkUpPhase(next)
    }

    private suspend fun applyWalkUpPhase(phase: WalkUpPhase) {
        when (phase) {
            WalkUpPhase.Away -> patchWalkUp(WalkUpHomeUi.NotAtStation)
            is WalkUpPhase.Dwelling -> patchWalkUp(
                WalkUpHomeUi.NearStation(
                    crsCode = phase.crs,
                    stationName = phase.stationName,
                    distanceLabel = GeoUtils.formatDistance(phase.distanceMetres),
                ),
            )
            is WalkUpPhase.Arrived -> {
                val existing = (_uiState.value as? HomeUiState.Content)?.walkUp as? WalkUpHomeUi.AtStation
                val keepDestinations = existing
                    ?.takeIf { it.crsCode == phase.crs && !it.manualOverride }
                    ?.destinations
                    .orEmpty()
                val loading = phase.crs != lastLoadedOriginCrs
                patchWalkUp(
                    WalkUpHomeUi.AtStation(
                        crsCode = phase.crs,
                        stationName = phase.stationName,
                        distanceLabel = GeoUtils.formatDistance(phase.distanceMetres),
                        manualOverride = false,
                        destinations = keepDestinations,
                        destinationsLoading = loading || (existing?.destinationsLoading == true),
                        noFavouritesConfigured = existing?.noFavouritesConfigured == true,
                    ),
                )
                if (loading) {
                    loadDestinations(originCrs = phase.crs, forceRefresh = false)
                }
            }
        }
    }

    private fun reloadDestinationsIfAtStation(forceRefresh: Boolean) {
        val at = when (val walkUp = (_uiState.value as? HomeUiState.Content)?.walkUp) {
            is WalkUpHomeUi.AtStation -> walkUp.crsCode
            else -> originOverrideCrs
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
                updateAtStation { current ->
                    current.copy(
                        destinations = emptyList(),
                        destinationsLoading = false,
                        noFavouritesConfigured = true,
                    )
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
            updateAtStation { current ->
                current.copy(
                    destinations = rows.map { it.toUi() },
                    destinationsLoading = false,
                    noFavouritesConfigured = false,
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

    private fun updateAtStation(transform: (WalkUpHomeUi.AtStation) -> WalkUpHomeUi.AtStation) {
        val content = _uiState.value as? HomeUiState.Content ?: return
        val at = content.walkUp as? WalkUpHomeUi.AtStation ?: return
        _uiState.value = content.copy(walkUp = transform(at))
    }

    private fun patchWalkUp(walkUp: WalkUpHomeUi?) {
        when (val current = _uiState.value) {
            is HomeUiState.Content -> _uiState.value = current.copy(walkUp = walkUp)
            is HomeUiState.Empty -> {
                // Still surface walk-up even when commute/favourites boards are empty.
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

    companion object {
        private const val LOCATION_POLL_MS = 10_000L

        private val FETCHED_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

        private fun formatFetched(epochMs: Long): String =
            "Updated ${FETCHED_FMT.format(Instant.ofEpochMilli(epochMs))}"

        fun factory(
            stationRepository: StationRepository,
            departureRepository: DepartureRepository,
            journeyRepository: JourneyRepository,
            locationProvider: LocationProvider,
            commuteWindowStore: CommuteWindowStore,
            favouritesStore: FavouritesStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    stationRepository = stationRepository,
                    departureRepository = departureRepository,
                    journeyRepository = journeyRepository,
                    locationProvider = locationProvider,
                    commuteWindowStore = commuteWindowStore,
                    favouritesStore = favouritesStore,
                ) as T
            }
        }
    }
}
