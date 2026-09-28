package com.ukrailtracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.data.local.datastore.CommuteWindowStore
import com.ukrailtracker.app.data.local.datastore.FavouritesStore
import com.ukrailtracker.app.domain.commute.CommuteSessionController
import com.ukrailtracker.app.domain.commute.isOnTrain
import com.ukrailtracker.app.domain.model.Departure
import com.ukrailtracker.app.domain.model.DisruptionSeverity
import com.ukrailtracker.app.domain.model.DisruptionSummary
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.repository.DepartureRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.DeriveBoardDisruption
import com.ukrailtracker.app.domain.usecase.GetContextualStationsUseCase
import com.ukrailtracker.app.domain.usecase.pinnedJourneyOrNull
import com.ukrailtracker.app.ui.station.StatusKind
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HomeViewModel(
    private val stationRepository: StationRepository,
    private val departureRepository: DepartureRepository,
    private val commuteWindowStore: CommuteWindowStore,
    private val favouritesStore: FavouritesStore,
    private val commuteSession: CommuteSessionController,
    private val getContextualStations: GetContextualStationsUseCase = GetContextualStationsUseCase(),
) : ViewModel() {

    private val commuteRuntime = commuteSession.runtime

    private val stationBoards = MutableStateFlow(StationBoardsState())
    private val disruptionModal = MutableStateFlow<DisruptionModalState?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        commuteRuntime.sessionFlow,
        commuteRuntime.auxFlow,
        commuteRuntime.lastLoadedOriginCrs,
        stationBoards,
        disruptionModal,
    ) { session, aux, loadedOriginCrs, boards, modal ->
        deriveHomeUiState(session, aux, loadedOriginCrs, boards, modal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    private val _openJourney = MutableSharedFlow<OpenJourneyEvent>(extraBufferCapacity = 1)
    val openJourney: SharedFlow<OpenJourneyEvent> = _openJourney.asSharedFlow()

    val platformAppeared: SharedFlow<String> = commuteRuntime.platformAppeared
    val arrivalBuzz: SharedFlow<Int> = commuteRuntime.arrivalBuzz

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
                    commuteRuntime.reloadDestinationsIfAtStation(forceRefresh = false)
                }
        }
    }

    fun onNeedsPermission() = commuteSession.onNeedsPermission()

    fun onPermissionGranted() = commuteSession.onPermissionGranted()

    fun onBackgroundLocationGranted() = commuteSession.onBackgroundLocationGranted()

    fun onHomeStarted() = commuteSession.onHomeStarted()

    fun onHomeStopped() = commuteSession.onHomeStopped()

    fun onOpenedForFrequentChecks() = commuteSession.requestFreshFix()

    fun prepareForDebugWalkUp(elevatePollForMs: Long) =
        commuteSession.prepareForDebugWalkUp(elevatePollForMs)

    fun onPermissionDenied() = commuteSession.onPermissionDenied()

    fun setOriginOverride(crs: String) = commuteRuntime.setOriginOverride(crs)

    fun clearOriginOverride() = commuteSession.clearOriginOverride()

    fun confirmBoarding() =
        commuteRuntime.dispatch(CommuteEvent.Confirm(System.currentTimeMillis()))

    fun rejectAssumedBoarding() = commuteRuntime.dispatch(CommuteEvent.ShowPicker)

    fun selectBoardingCandidate(serviceId: String) =
        commuteRuntime.dispatch(CommuteEvent.SelectService(serviceId, System.currentTimeMillis()))

    fun dismissBoarding() = commuteRuntime.dismissBoarding()

    fun endOnTrainTrip() = commuteRuntime.dispatch(CommuteEvent.EndTrip)

    fun confirmBoarded() = commuteRuntime.dispatch(CommuteEvent.Boarded)

    fun openOnTrainServiceDetail() {
        val pinned = commuteRuntime.sessionFlow.value.pinnedJourneyOrNull() ?: return
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

    fun selectServiceForPlatformWatch(
        originCrs: String,
        destinationCrs: String,
        serviceId: String,
    ) = commuteRuntime.selectServiceForPlatformWatch(originCrs, destinationCrs, serviceId)

    fun clearPlatformWatch() = commuteRuntime.clearPlatformWatch()

    fun refreshPlatformWatch() = commuteRuntime.refreshPlatformWatch()

    fun openWatchedService() {
        val watch = (mapCommutePresence(
            commuteRuntime.sessionFlow.value,
            commuteRuntime.auxFlow.value,
            commuteRuntime.lastLoadedOriginCrs.value,
        ).tripBody as? HomeBodyUi.AtStation)?.platformWatch ?: return
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
        val current = uiState.value
        val crsList = when (current) {
            is HomeUiState.Ready -> current.stationList.map { it.crsCode }
            else -> emptyList()
        }
        viewModelScope.launch {
            val boards = stationBoards.value
            stationBoards.value = if (current is HomeUiState.Ready) {
                boards.copy(refreshing = true)
            } else {
                boards.copy(awaitingFirstLoad = true, refreshing = false)
            }
            val windows = commuteWindowStore.getWindows()
            val favourites = favouritesStore.getFavourites()
            val selected = getContextualStations.select(windows, favourites)
                .ifEmpty { crsList }
            loadBoards(selected, forceRefresh = true)
            commuteRuntime.reloadDestinationsIfAtStation(forceRefresh = true)
            if (commuteRuntime.sessionFlow.value.isOnTrain()) {
                commuteRuntime.refreshOnTrainOption()
            }
            commuteSession.requestFreshFix()
        }
    }

    fun openDisruption(crs: String) {
        val content = uiState.value as? HomeUiState.Ready ?: return
        val card = content.stationList.find { it.crsCode == crs } ?: return
        disruptionModal.value = DisruptionModalState(
            disruption = card.disruption,
            stationName = card.stationName,
        )
    }

    fun dismissDisruption() {
        disruptionModal.value = null
    }

    private suspend fun loadBoards(windowsCrs: List<String>, forceRefresh: Boolean) {
        if (windowsCrs.isEmpty()) {
            stationBoards.value = stationBoards.value.copy(
                stations = emptyList(),
                refreshing = false,
                awaitingFirstLoad = false,
                noStationsConfigured = true,
            )
            return
        }

        val reasonByCrs = reasonLabels(windowsCrs)
        val cards = coroutineScope {
            windowsCrs.map { crs ->
                async { loadCard(crs, reasonByCrs[crs].orEmpty(), forceRefresh) }
            }.awaitAll()
        }
        stationBoards.value = stationBoards.value.copy(
            stations = cards,
            refreshing = false,
            awaitingFirstLoad = false,
            noStationsConfigured = false,
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

    override fun onCleared() {
        commuteSession.onHomeStopped()
        super.onCleared()
    }

    data class OpenJourneyEvent(
        val originCrs: String,
        val destinationCrs: String,
        val serviceId: String,
    )

    companion object {
        private val FETCHED_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

        private fun formatFetched(epochMs: Long): String =
            "Updated ${FETCHED_FMT.format(Instant.ofEpochMilli(epochMs))}"

        fun factory(
            stationRepository: StationRepository,
            departureRepository: DepartureRepository,
            commuteWindowStore: CommuteWindowStore,
            favouritesStore: FavouritesStore,
            commuteSession: CommuteSessionController,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    stationRepository = stationRepository,
                    departureRepository = departureRepository,
                    commuteWindowStore = commuteWindowStore,
                    favouritesStore = favouritesStore,
                    commuteSession = commuteSession,
                ) as T
            }
        }
    }
}
