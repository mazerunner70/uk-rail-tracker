package com.ukrailtracker.app.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.data.local.datastore.FavouriteJourneysStore
import com.ukrailtracker.app.data.local.datastore.PinnedJourneyStore
import com.ukrailtracker.app.data.local.datastore.RecentJourneysStore
import com.ukrailtracker.app.data.repository.JourneyLogRepository
import com.ukrailtracker.app.domain.model.JourneyLogEntry
import com.ukrailtracker.app.domain.model.JourneyPair
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.JourneyHistoryFilter
import com.ukrailtracker.app.domain.usecase.JourneyHistoryMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class JourneyPairUi(
    val originCrs: String,
    val destinationCrs: String,
    val originName: String,
    val destinationName: String,
    val isFavourite: Boolean,
)

data class PinnedJourneyUi(
    val serviceId: String,
    val originCrs: String,
    val destinationCrs: String,
    val title: String,
    val subtitle: String,
)

data class HistoryTripUi(
    val id: Long,
    val dateLabel: String,
    val originCrs: String,
    val destinationCrs: String,
    val originName: String,
    val destinationName: String,
    val departureLabel: String,
    val operatorName: String,
    val statusLabel: String,
    val statusKind: HistoryStatusKind,
    val claimable: Boolean,
)

enum class HistoryStatusKind {
    OnTime,
    Delayed,
    Cancelled,
}

sealed interface JourneysUiState {
    data object Loading : JourneysUiState
    data class Content(
        val pinned: PinnedJourneyUi?,
        val favourites: List<JourneyPairUi>,
        val recent: List<JourneyPairUi>,
        val historyMode: JourneyHistoryMode,
        val history: List<HistoryTripUi>,
        val historyTotalInWindow: Int,
    ) : JourneysUiState
}

class JourneysViewModel(
    private val stationRepository: StationRepository,
    private val favouriteJourneysStore: FavouriteJourneysStore,
    private val recentJourneysStore: RecentJourneysStore,
    private val pinnedJourneyStore: PinnedJourneyStore,
    private val journeyLogRepository: JourneyLogRepository,
) : ViewModel() {

    private val historyMode = MutableStateFlow(JourneyHistoryMode.Claimable)

    private val _uiState = MutableStateFlow<JourneysUiState>(JourneysUiState.Loading)
    val uiState: StateFlow<JourneysUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            stationRepository.ensureImported()
            combine(
                favouriteJourneysStore.journeysFlow,
                recentJourneysStore.recentFlow,
                pinnedJourneyStore.pinnedFlow,
                journeyLogRepository.observeRecent(),
                historyMode,
            ) { favourites, recent, pinned, logEntries, mode ->
                HistoryBundle(favourites, recent, pinned, logEntries, mode)
            }.collect { bundle ->
                val favouriteUi = bundle.favourites.map { resolvePair(it, isFavourite = true) }
                val recentUi = bundle.recent.map { pair ->
                    resolvePair(
                        pair,
                        isFavourite = bundle.favourites.any {
                            it.originCrs == pair.originCrs &&
                                it.destinationCrs == pair.destinationCrs
                        },
                    )
                }
                val today = LocalDate.now()
                val inWindow = JourneyHistoryFilter.visible(
                    entries = bundle.logEntries,
                    mode = JourneyHistoryMode.All,
                    today = today,
                )
                val visible = JourneyHistoryFilter.visible(
                    entries = bundle.logEntries,
                    mode = bundle.mode,
                    today = today,
                )
                _uiState.value = JourneysUiState.Content(
                    pinned = bundle.pinned?.toUi(),
                    favourites = favouriteUi,
                    recent = recentUi,
                    historyMode = bundle.mode,
                    history = visible.map { it.toHistoryUi() },
                    historyTotalInWindow = inWindow.size,
                )
            }
        }
    }

    fun setHistoryMode(mode: JourneyHistoryMode) {
        historyMode.update { mode }
    }

    fun toggleFavourite(originCrs: String, destinationCrs: String) {
        viewModelScope.launch {
            favouriteJourneysStore.toggle(originCrs, destinationCrs)
        }
    }

    private suspend fun resolvePair(pair: JourneyPair, isFavourite: Boolean): JourneyPairUi {
        val origin = stationRepository.getByCrs(pair.originCrs)
        val dest = stationRepository.getByCrs(pair.destinationCrs)
        return JourneyPairUi(
            originCrs = pair.originCrs,
            destinationCrs = pair.destinationCrs,
            originName = origin?.name ?: pair.originCrs,
            destinationName = dest?.name ?: pair.destinationCrs,
            isFavourite = isFavourite,
        )
    }

    private suspend fun JourneyLogEntry.toHistoryUi(): HistoryTripUi {
        val origin = stationRepository.getByCrs(originCrs)
        val dest = stationRepository.getByCrs(destinationCrs)
        val claimable = JourneyHistoryFilter.isClaimable(this)
        val (statusLabel, statusKind) = when {
            wasCancelled -> "Cancelled" to HistoryStatusKind.Cancelled
            (delayMinutes ?: 0) > 0 -> "+$delayMinutes min" to HistoryStatusKind.Delayed
            else -> "On time" to HistoryStatusKind.OnTime
        }
        return HistoryTripUi(
            id = id,
            dateLabel = formatHistoryDate(dateIso),
            originCrs = originCrs,
            destinationCrs = destinationCrs,
            originName = origin?.name ?: originCrs,
            destinationName = dest?.name ?: destinationCrs,
            departureLabel = scheduledDepartureLabel,
            operatorName = operatorName,
            statusLabel = statusLabel,
            statusKind = statusKind,
            claimable = claimable,
        )
    }

    private fun PinnedJourney.toUi(): PinnedJourneyUi =
        PinnedJourneyUi(
            serviceId = serviceId,
            originCrs = originCrs,
            destinationCrs = destinationCrs,
            title = "$originName → $destinationName",
            subtitle = "$scheduledDepartureLabel · $operatorName",
        )

    private data class HistoryBundle(
        val favourites: List<JourneyPair>,
        val recent: List<JourneyPair>,
        val pinned: PinnedJourney?,
        val logEntries: List<JourneyLogEntry>,
        val mode: JourneyHistoryMode,
    )

    companion object {
        private val HISTORY_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM")

        fun formatHistoryDate(dateIso: String): String =
            runCatching { LocalDate.parse(dateIso).format(HISTORY_DATE) }
                .getOrDefault(dateIso)

        fun factory(
            stationRepository: StationRepository,
            favouriteJourneysStore: FavouriteJourneysStore,
            recentJourneysStore: RecentJourneysStore,
            pinnedJourneyStore: PinnedJourneyStore,
            journeyLogRepository: JourneyLogRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                JourneysViewModel(
                    stationRepository = stationRepository,
                    favouriteJourneysStore = favouriteJourneysStore,
                    recentJourneysStore = recentJourneysStore,
                    pinnedJourneyStore = pinnedJourneyStore,
                    journeyLogRepository = journeyLogRepository,
                ) as T
        }
    }
}
