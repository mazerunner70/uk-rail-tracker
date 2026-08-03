package com.ukrailtracker.app.ui.station

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.repository.DepartureRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DepartureRowUi(
    val timeLabel: String,
    val destinationLabel: String,
    val platformLabel: String,
    val statusLabel: String,
    val statusKind: StatusKind,
    val operatorLabel: String,
)

enum class StatusKind {
    OnTime,
    Delayed,
    Cancelled,
    Other,
}

data class StationDetailUi(
    val station: Station,
    val boardRows: List<DepartureRowUi> = emptyList(),
    val boardLoading: Boolean = false,
    val boardRefreshing: Boolean = false,
    val boardError: String? = null,
    val boardFetchedLabel: String? = null,
    val boardStale: Boolean = false,
)

sealed interface StationDetailUiState {
    data object Loading : StationDetailUiState
    data object NotFound : StationDetailUiState
    data class Content(val detail: StationDetailUi) : StationDetailUiState
}

class StationDetailViewModel(
    private val crsCode: String,
    private val stationRepository: StationRepository,
    private val departureRepository: DepartureRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StationDetailUiState>(StationDetailUiState.Loading)
    val uiState: StateFlow<StationDetailUiState> = _uiState.asStateFlow()

    init {
        load(forceBoardRefresh = false)
    }

    fun refreshBoard() {
        val current = (_uiState.value as? StationDetailUiState.Content)?.detail ?: return
        _uiState.value = StationDetailUiState.Content(
            current.copy(boardRefreshing = true, boardError = null),
        )
        viewModelScope.launch {
            loadBoard(current.station, forceRefresh = true)
        }
    }

    private fun load(forceBoardRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.value = StationDetailUiState.Loading
            stationRepository.ensureImported()
            val station = stationRepository.getByCrs(crsCode)
            if (station == null) {
                _uiState.value = StationDetailUiState.NotFound
                return@launch
            }
            _uiState.value = StationDetailUiState.Content(
                StationDetailUi(station = station, boardLoading = true),
            )
            loadBoard(station, forceRefresh = forceBoardRefresh)
        }
    }

    private suspend fun loadBoard(station: Station, forceRefresh: Boolean) {
        val base = (_uiState.value as? StationDetailUiState.Content)?.detail
            ?: StationDetailUi(station = station, boardLoading = true)
        try {
            val board = departureRepository.getBoard(station.crsCode, forceRefresh = forceRefresh)
            val rows = board.departures.map { dep ->
                val kind = when (dep.status) {
                    TrainStatus.OnTime -> StatusKind.OnTime
                    TrainStatus.Delayed -> StatusKind.Delayed
                    TrainStatus.Cancelled -> StatusKind.Cancelled
                    else -> StatusKind.Other
                }
                val statusLabel = when {
                    dep.status == TrainStatus.Cancelled -> "Cancelled"
                    dep.status == TrainStatus.OnTime -> "On time"
                    dep.delayMinutes != null && dep.delayMinutes > 0 -> "+${dep.delayMinutes} min"
                    else -> dep.expectedLabel
                }
                DepartureRowUi(
                    timeLabel = dep.scheduledTimeLabel.ifBlank { "—" },
                    destinationLabel = dep.destination,
                    platformLabel = dep.platform?.let { "Plat $it" } ?: "Plat —",
                    statusLabel = statusLabel,
                    statusKind = kind,
                    operatorLabel = dep.operatorName,
                )
            }
            val ageMs = System.currentTimeMillis() - board.fetchedAtEpochMs
            _uiState.value = StationDetailUiState.Content(
                base.copy(
                    boardRows = rows,
                    boardLoading = false,
                    boardRefreshing = false,
                    boardError = null,
                    boardFetchedLabel = formatFetched(board.fetchedAtEpochMs),
                    boardStale = board.fromCache || ageMs > 90_000L,
                ),
            )
        } catch (t: Throwable) {
            _uiState.value = StationDetailUiState.Content(
                base.copy(
                    boardLoading = false,
                    boardRefreshing = false,
                    boardError = t.message ?: "Could not load departures",
                ),
            )
        }
    }

    companion object {
        private val FETCHED_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

        private fun formatFetched(epochMs: Long): String =
            "Updated ${FETCHED_FMT.format(Instant.ofEpochMilli(epochMs))}"

        fun factory(
            crsCode: String,
            stationRepository: StationRepository,
            departureRepository: DepartureRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StationDetailViewModel(
                    crsCode = crsCode,
                    stationRepository = stationRepository,
                    departureRepository = departureRepository,
                ) as T
            }
        }
    }
}
