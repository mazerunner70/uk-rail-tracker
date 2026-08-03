package com.ukrailtracker.app.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.usecase.GetNearbyStationsUseCase
import com.ukrailtracker.app.domain.usecase.NearbyResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NearbyStationRowUi(
    val crsCode: String,
    val name: String,
    val operator: String,
    val distanceLabel: String,
)

sealed interface NearbyUiState {
    data object Importing : NearbyUiState
    data object Loading : NearbyUiState
    data object NeedsPermission : NearbyUiState
    data object PermissionDenied : NearbyUiState
    data object LocationUnavailable : NearbyUiState
    data class Error(val message: String? = null) : NearbyUiState
    data class Content(
        val closest: NearbyStationRowUi,
        val others: List<NearbyStationRowUi>,
        val isRefreshing: Boolean = false,
    ) : NearbyUiState
}

class NearbyViewModel(
    private val getNearbyStations: GetNearbyStationsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NearbyUiState>(NearbyUiState.NeedsPermission)
    val uiState: StateFlow<NearbyUiState> = _uiState.asStateFlow()

    fun onPermissionGranted() {
        refresh(showImporting = true)
    }

    fun onPermissionDenied() {
        _uiState.value = NearbyUiState.PermissionDenied
    }

    fun onNeedsPermission() {
        _uiState.value = NearbyUiState.NeedsPermission
    }

    fun refresh(showImporting: Boolean = false) {
        viewModelScope.launch {
            val previous = _uiState.value
            _uiState.value = when {
                showImporting -> NearbyUiState.Importing
                previous is NearbyUiState.Content -> previous.copy(isRefreshing = true)
                else -> NearbyUiState.Loading
            }
            try {
                // Always force a fresh GPS read on refresh so emulator geo fix / movement is picked up.
                when (val result = getNearbyStations(forceLocationRefresh = true)) {
                    is NearbyResult.LocationUnavailable -> {
                        _uiState.value = NearbyUiState.LocationUnavailable
                    }
                    is NearbyResult.Success -> {
                        val rows = result.stations.map { item ->
                            NearbyStationRowUi(
                                crsCode = item.station.crsCode,
                                name = item.station.name,
                                operator = item.station.operatorName,
                                distanceLabel = GeoUtils.formatDistance(item.distanceMetres),
                            )
                        }
                        if (rows.isEmpty()) {
                            _uiState.value = NearbyUiState.Error("No stations found")
                        } else {
                            _uiState.value = NearbyUiState.Content(
                                closest = rows.first(),
                                others = rows.drop(1),
                                isRefreshing = false,
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                _uiState.value = NearbyUiState.Error(t.message)
            }
        }
    }

    companion object {
        fun factory(getNearbyStations: GetNearbyStationsUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NearbyViewModel(getNearbyStations) as T
                }
            }
    }
}
