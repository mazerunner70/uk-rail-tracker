package com.ukrailtracker.app.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.data.local.datastore.FavouriteJourneysStore
import com.ukrailtracker.app.data.local.datastore.RecentJourneysStore
import com.ukrailtracker.app.domain.model.DisruptionSeverity
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.DeriveBoardDisruption
import com.ukrailtracker.app.ui.station.StatusKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class JourneyOptionRowUi(
    val serviceId: String,
    val departureLabel: String,
    val arrivalLabel: String,
    val platformLabel: String,
    val operatorLabel: String,
    val statusLabel: String,
    val statusKind: StatusKind,
    val flagged: Boolean,
)

sealed interface JourneyOptionsUiState {
    data object Loading : JourneyOptionsUiState
    data class Content(
        val originName: String,
        val destinationName: String,
        val originCrs: String,
        val destinationCrs: String,
        val options: List<JourneyOptionRowUi>,
        val routeHeadline: String,
        val routeSeverity: DisruptionSeverity,
        val isFavourite: Boolean,
        val refreshing: Boolean = false,
        val error: String? = null,
    ) : JourneyOptionsUiState
    data class Error(val message: String) : JourneyOptionsUiState
}

class JourneyOptionsViewModel(
    private val originCrs: String,
    private val destinationCrs: String,
    private val stationRepository: StationRepository,
    private val journeyRepository: JourneyRepository,
    private val recentJourneysStore: RecentJourneysStore,
    private val favouriteJourneysStore: FavouriteJourneysStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<JourneyOptionsUiState>(JourneyOptionsUiState.Loading)
    val uiState: StateFlow<JourneyOptionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            recentJourneysStore.record(originCrs, destinationCrs)
            load(forceRefresh = false)
            favouriteJourneysStore.journeysFlow.collect { favourites ->
                val current = _uiState.value
                if (current is JourneyOptionsUiState.Content) {
                    val isFav = favourites.any {
                        it.originCrs == originCrs.uppercase() &&
                            it.destinationCrs == destinationCrs.uppercase()
                    }
                    _uiState.value = current.copy(isFavourite = isFav)
                }
            }
        }
    }

    fun refresh() {
        val current = _uiState.value
        if (current is JourneyOptionsUiState.Content) {
            _uiState.value = current.copy(refreshing = true, error = null)
        } else {
            _uiState.value = JourneyOptionsUiState.Loading
        }
        viewModelScope.launch { load(forceRefresh = true) }
    }

    fun toggleFavourite() {
        viewModelScope.launch {
            favouriteJourneysStore.toggle(originCrs, destinationCrs)
        }
    }

    private suspend fun load(forceRefresh: Boolean) {
        try {
            stationRepository.ensureImported()
            val origin = stationRepository.getByCrs(originCrs)
            val dest = stationRepository.getByCrs(destinationCrs)
            val options = journeyRepository.getOptions(originCrs, destinationCrs, forceRefresh)
            val disruption = DeriveBoardDisruption.fromDepartures(
                options.map {
                    com.ukrailtracker.app.domain.model.Departure(
                        destination = it.destinationName,
                        destinationCrs = it.destinationCrs,
                        scheduledTimeLabel = it.scheduledDepartureLabel,
                        expectedLabel = it.expectedDepartureLabel,
                        platform = it.platform,
                        operatorName = it.operatorName,
                        status = it.status,
                        delayMinutes = it.delayMinutes,
                        serviceId = it.serviceId,
                        isArrival = false,
                        callingPoints = it.callingPoints,
                    )
                },
            )
            val favourites = favouriteJourneysStore.getJourneys()
            val isFav = favourites.any {
                it.originCrs == originCrs.uppercase() &&
                    it.destinationCrs == destinationCrs.uppercase()
            }
            _uiState.value = JourneyOptionsUiState.Content(
                originName = origin?.name ?: originCrs.uppercase(),
                destinationName = dest?.name ?: destinationCrs.uppercase(),
                originCrs = originCrs.uppercase(),
                destinationCrs = destinationCrs.uppercase(),
                options = options.map { opt ->
                    val kind = statusKind(opt.status)
                    val flagged = opt.isCancelled ||
                        opt.status == TrainStatus.Cancelled ||
                        (opt.delayMinutes ?: 0) >= 15
                    JourneyOptionRowUi(
                        serviceId = opt.serviceId,
                        departureLabel = opt.scheduledDepartureLabel,
                        arrivalLabel = opt.expectedArrivalLabel
                            ?: opt.scheduledArrivalLabel
                            ?: "—",
                        platformLabel = opt.platform?.let { "Plat $it" } ?: "Plat —",
                        operatorLabel = opt.operatorName,
                        statusLabel = statusLabel(opt.status, opt.expectedDepartureLabel, opt.delayMinutes),
                        statusKind = kind,
                        flagged = flagged,
                    )
                },
                routeHeadline = disruption.headline,
                routeSeverity = disruption.severity,
                isFavourite = isFav,
                refreshing = false,
            )
        } catch (t: Throwable) {
            val current = _uiState.value
            if (current is JourneyOptionsUiState.Content) {
                _uiState.value = current.copy(
                    refreshing = false,
                    error = t.message ?: "Failed to load services",
                )
            } else {
                _uiState.value = JourneyOptionsUiState.Error(t.message ?: "Failed to load services")
            }
        }
    }

    companion object {
        fun factory(
            originCrs: String,
            destinationCrs: String,
            stationRepository: StationRepository,
            journeyRepository: JourneyRepository,
            recentJourneysStore: RecentJourneysStore,
            favouriteJourneysStore: FavouriteJourneysStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                JourneyOptionsViewModel(
                    originCrs = originCrs,
                    destinationCrs = destinationCrs,
                    stationRepository = stationRepository,
                    journeyRepository = journeyRepository,
                    recentJourneysStore = recentJourneysStore,
                    favouriteJourneysStore = favouriteJourneysStore,
                ) as T
        }
    }
}
