package com.ukrailtracker.app.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.data.local.datastore.PinnedJourneyStore
import com.ukrailtracker.app.data.repository.JourneyLogRepository
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.ui.station.StatusKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CallingPointRowUi(
    val name: String,
    val crs: String?,
    val scheduledLabel: String,
    val expectedLabel: String,
    val isDestination: Boolean,
    val isCancelled: Boolean,
)

data class JourneyDetailUi(
    val originName: String,
    val destinationName: String,
    val originCrs: String,
    val destinationCrs: String,
    val serviceId: String,
    val departureScheduled: String,
    val departureExpected: String,
    val arrivalScheduled: String,
    val arrivalExpected: String,
    val platformLabel: String,
    val operatorLabel: String,
    val statusLabel: String,
    val statusKind: StatusKind,
    val flagged: Boolean,
    val callingPoints: List<CallingPointRowUi>,
    val isPinned: Boolean,
    val refreshing: Boolean = false,
    val lastUpdatedLabel: String? = null,
    val error: String? = null,
)

sealed interface JourneyDetailUiState {
    data object Loading : JourneyDetailUiState
    data class Content(val detail: JourneyDetailUi) : JourneyDetailUiState
    data class Error(val message: String) : JourneyDetailUiState
}

class JourneyDetailViewModel(
    private val originCrs: String,
    private val destinationCrs: String,
    private val serviceId: String,
    private val stationRepository: StationRepository,
    private val journeyRepository: JourneyRepository,
    private val pinnedJourneyStore: PinnedJourneyStore,
    private val journeyLogRepository: JourneyLogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<JourneyDetailUiState>(JourneyDetailUiState.Loading)
    val uiState: StateFlow<JourneyDetailUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private var latestOption: JourneyOption? = null

    init {
        viewModelScope.launch {
            stationRepository.ensureImported()
            load(forceRefresh = true)
            startPolling()
            pinnedJourneyStore.pinnedFlow.collect { pinned ->
                val current = _uiState.value
                if (current is JourneyDetailUiState.Content) {
                    val isPinned = pinned?.serviceId == serviceId
                    _uiState.value = JourneyDetailUiState.Content(
                        current.detail.copy(isPinned = isPinned),
                    )
                }
            }
        }
    }

    fun refresh() {
        val current = (_uiState.value as? JourneyDetailUiState.Content)?.detail
        if (current != null) {
            _uiState.value = JourneyDetailUiState.Content(current.copy(refreshing = true, error = null))
        }
        viewModelScope.launch { load(forceRefresh = true) }
    }

    fun togglePin() {
        viewModelScope.launch {
            val option = latestOption ?: return@launch
            val pinned = pinnedJourneyStore.getPinned()
            if (pinned?.serviceId == serviceId) {
                journeyLogRepository.logFromOption(option)
                pinnedJourneyStore.clear()
            } else {
                val origin = stationRepository.getByCrs(originCrs)
                val dest = stationRepository.getByCrs(destinationCrs)
                pinnedJourneyStore.pin(
                    PinnedJourney(
                        serviceId = option.serviceId,
                        originCrs = option.originCrs,
                        destinationCrs = option.destinationCrs,
                        originName = origin?.name ?: option.originCrs,
                        destinationName = dest?.name ?: option.destinationCrs,
                        operatorName = option.operatorName,
                        scheduledDepartureLabel = option.scheduledDepartureLabel,
                        pinnedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_MS)
                load(forceRefresh = true)
            }
        }
    }

    private suspend fun load(forceRefresh: Boolean) {
        try {
            val option = journeyRepository.getOption(
                originCrs = originCrs,
                destinationCrs = destinationCrs,
                serviceId = serviceId,
                forceRefresh = forceRefresh,
            ) ?: throw IllegalStateException("Service not found on this route")
            latestOption = option
            val origin = stationRepository.getByCrs(originCrs)
            val dest = stationRepository.getByCrs(destinationCrs)
            val pinned = pinnedJourneyStore.getPinned()
            val flagged = option.isCancelled ||
                option.status == TrainStatus.Cancelled ||
                (option.delayMinutes ?: 0) >= 15
            val points = option.callingPoints.map { cp ->
                CallingPointRowUi(
                    name = cp.locationName,
                    crs = cp.crs,
                    scheduledLabel = cp.scheduledTimeLabel ?: "—",
                    expectedLabel = cp.expectedLabel ?: "—",
                    isDestination = cp.crs?.equals(destinationCrs, ignoreCase = true) == true,
                    isCancelled = cp.isCancelled,
                )
            }
            _uiState.value = JourneyDetailUiState.Content(
                JourneyDetailUi(
                    originName = origin?.name ?: originCrs.uppercase(),
                    destinationName = dest?.name ?: destinationCrs.uppercase(),
                    originCrs = originCrs.uppercase(),
                    destinationCrs = destinationCrs.uppercase(),
                    serviceId = option.serviceId,
                    departureScheduled = option.scheduledDepartureLabel,
                    departureExpected = option.expectedDepartureLabel,
                    arrivalScheduled = option.scheduledArrivalLabel ?: "—",
                    arrivalExpected = option.expectedArrivalLabel ?: "—",
                    platformLabel = option.platform?.let { "Platform $it" } ?: "Platform —",
                    operatorLabel = option.operatorName,
                    statusLabel = statusLabel(option.status, option.expectedDepartureLabel, option.delayMinutes),
                    statusKind = statusKind(option.status),
                    flagged = flagged,
                    callingPoints = points,
                    isPinned = pinned?.serviceId == serviceId,
                    refreshing = false,
                    lastUpdatedLabel = "Updated just now",
                    error = null,
                ),
            )
        } catch (t: Throwable) {
            val current = _uiState.value
            if (current is JourneyDetailUiState.Content) {
                _uiState.value = JourneyDetailUiState.Content(
                    current.detail.copy(
                        refreshing = false,
                        error = t.message ?: "Refresh failed",
                    ),
                )
            } else {
                _uiState.value = JourneyDetailUiState.Error(t.message ?: "Failed to load service")
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val POLL_MS = 45_000L

        fun factory(
            originCrs: String,
            destinationCrs: String,
            serviceId: String,
            stationRepository: StationRepository,
            journeyRepository: JourneyRepository,
            pinnedJourneyStore: PinnedJourneyStore,
            journeyLogRepository: JourneyLogRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                JourneyDetailViewModel(
                    originCrs = originCrs,
                    destinationCrs = destinationCrs,
                    serviceId = serviceId,
                    stationRepository = stationRepository,
                    journeyRepository = journeyRepository,
                    pinnedJourneyStore = pinnedJourneyStore,
                    journeyLogRepository = journeyLogRepository,
                ) as T
        }
    }
}
