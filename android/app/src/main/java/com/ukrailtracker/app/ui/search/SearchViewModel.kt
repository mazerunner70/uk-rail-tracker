package com.ukrailtracker.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.data.local.datastore.RecentSearchStore
import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.repository.StationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchStationRowUi(
    val crsCode: String,
    val name: String,
    val operator: String,
)

data class SearchUiState(
    val query: String = "",
    val results: List<SearchStationRowUi> = emptyList(),
    val recent: List<SearchStationRowUi> = emptyList(),
    val loading: Boolean = false,
    val importing: Boolean = false,
)

class SearchViewModel(
    private val stationRepository: StationRepository,
    private val recentSearchStore: RecentSearchStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState(importing = true))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            stationRepository.ensureImported()
            refreshRecent()
            _uiState.value = _uiState.value.copy(importing = false)
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), loading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(200)
            _uiState.value = _uiState.value.copy(loading = true)
            val hits = stationRepository.search(query).map { it.toRow() }
            _uiState.value = _uiState.value.copy(results = hits, loading = false)
        }
    }

    fun onStationOpened(crs: String) {
        viewModelScope.launch {
            recentSearchStore.record(crs)
            refreshRecent()
        }
    }

    private suspend fun refreshRecent() {
        val crsList = recentSearchStore.getRecent()
        val rows = crsList.mapNotNull { crs ->
            stationRepository.getByCrs(crs)?.toRow()
        }
        _uiState.value = _uiState.value.copy(recent = rows)
    }

    private fun Station.toRow() = SearchStationRowUi(
        crsCode = crsCode,
        name = name,
        operator = operatorName,
    )

    companion object {
        fun factory(
            stationRepository: StationRepository,
            recentSearchStore: RecentSearchStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(stationRepository, recentSearchStore) as T
            }
        }
    }
}
