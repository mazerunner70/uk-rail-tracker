package com.ukrailtracker.app.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.data.local.datastore.FavouriteJourneysStore
import com.ukrailtracker.app.data.local.datastore.PinnedJourneyStore
import com.ukrailtracker.app.data.local.datastore.RecentJourneysStore
import com.ukrailtracker.app.domain.model.JourneyPair
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.repository.StationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

sealed interface JourneysUiState {
    data object Loading : JourneysUiState
    data class Content(
        val pinned: PinnedJourneyUi?,
        val favourites: List<JourneyPairUi>,
        val recent: List<JourneyPairUi>,
    ) : JourneysUiState
}

class JourneysViewModel(
    private val stationRepository: StationRepository,
    private val favouriteJourneysStore: FavouriteJourneysStore,
    private val recentJourneysStore: RecentJourneysStore,
    private val pinnedJourneyStore: PinnedJourneyStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<JourneysUiState>(JourneysUiState.Loading)
    val uiState: StateFlow<JourneysUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            stationRepository.ensureImported()
            combine(
                favouriteJourneysStore.journeysFlow,
                recentJourneysStore.recentFlow,
                pinnedJourneyStore.pinnedFlow,
            ) { favourites, recent, pinned ->
                Triple(favourites, recent, pinned)
            }.collect { (favourites, recent, pinned) ->
                val favouriteUi = favourites.map { resolvePair(it, isFavourite = true) }
                val recentUi = recent.map { pair ->
                    resolvePair(
                        pair,
                        isFavourite = favourites.any {
                            it.originCrs == pair.originCrs &&
                                it.destinationCrs == pair.destinationCrs
                        },
                    )
                }
                _uiState.value = JourneysUiState.Content(
                    pinned = pinned?.toUi(),
                    favourites = favouriteUi,
                    recent = recentUi,
                )
            }
        }
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

    private fun PinnedJourney.toUi(): PinnedJourneyUi =
        PinnedJourneyUi(
            serviceId = serviceId,
            originCrs = originCrs,
            destinationCrs = destinationCrs,
            title = "$originName → $destinationName",
            subtitle = "$scheduledDepartureLabel · $operatorName",
        )

    companion object {
        fun factory(
            stationRepository: StationRepository,
            favouriteJourneysStore: FavouriteJourneysStore,
            recentJourneysStore: RecentJourneysStore,
            pinnedJourneyStore: PinnedJourneyStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                JourneysViewModel(
                    stationRepository = stationRepository,
                    favouriteJourneysStore = favouriteJourneysStore,
                    recentJourneysStore = recentJourneysStore,
                    pinnedJourneyStore = pinnedJourneyStore,
                ) as T
        }
    }
}
