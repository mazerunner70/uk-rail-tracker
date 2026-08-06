package com.ukrailtracker.app.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.domain.repository.StationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlannerStationUi(
    val crsCode: String,
    val name: String,
)

data class JourneyPlannerUiState(
    val origin: PlannerStationUi? = null,
    val destination: PlannerStationUi? = null,
    val error: String? = null,
) {
    val canSearch: Boolean
        get() = origin != null &&
            destination != null &&
            origin.crsCode != destination.crsCode
}

class JourneyPlannerViewModel(
    private val stationRepository: StationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JourneyPlannerUiState())
    val uiState: StateFlow<JourneyPlannerUiState> = _uiState.asStateFlow()

    fun setOriginCrs(crs: String?) {
        if (crs.isNullOrBlank()) return
        viewModelScope.launch {
            val station = stationRepository.getByCrs(crs) ?: return@launch
            _uiState.value = _uiState.value.copy(
                origin = PlannerStationUi(station.crsCode, station.name),
                error = null,
            )
        }
    }

    fun setDestinationCrs(crs: String?) {
        if (crs.isNullOrBlank()) return
        viewModelScope.launch {
            val station = stationRepository.getByCrs(crs) ?: return@launch
            _uiState.value = _uiState.value.copy(
                destination = PlannerStationUi(station.crsCode, station.name),
                error = null,
            )
        }
    }

    fun swap() {
        val current = _uiState.value
        _uiState.value = current.copy(
            origin = current.destination,
            destination = current.origin,
            error = null,
        )
    }

    fun validateOrError(): Boolean {
        val current = _uiState.value
        return when {
            current.origin == null || current.destination == null -> {
                _uiState.value = current.copy(error = "Pick origin and destination")
                false
            }
            current.origin.crsCode == current.destination.crsCode -> {
                _uiState.value = current.copy(error = "Stations must be different")
                false
            }
            else -> true
        }
    }

    companion object {
        fun factory(stationRepository: StationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    JourneyPlannerViewModel(stationRepository) as T
            }
    }
}
