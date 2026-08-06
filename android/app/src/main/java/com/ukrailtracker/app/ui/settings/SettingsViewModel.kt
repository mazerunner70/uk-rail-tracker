package com.ukrailtracker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ukrailtracker.app.data.local.datastore.AppPreferencesStore
import com.ukrailtracker.app.data.local.datastore.CommuteWindowStore
import com.ukrailtracker.app.data.local.datastore.FavouritesStore
import com.ukrailtracker.app.domain.model.CommuteWindow
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.worker.HomeRefreshScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek

data class FavouriteRowUi(
    val crsCode: String,
    val name: String,
)

data class CommuteWindowRowUi(
    val id: String,
    val stationCrs: String,
    val stationName: String,
    val timeRangeLabel: String,
    val daysLabel: String,
    val label: String,
)

data class CommuteEditorUi(
    val id: String? = null,
    val stationCrs: String = "",
    val stationName: String = "",
    val startTime: String = "07:30",
    val endTime: String = "09:00",
    val selectedDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    ),
    val label: String = "",
    val error: String? = null,
)

data class SettingsUiState(
    val favourites: List<FavouriteRowUi> = emptyList(),
    val windows: List<CommuteWindowRowUi> = emptyList(),
    val backgroundRefreshEnabled: Boolean = true,
    val editor: CommuteEditorUi? = null,
    val loading: Boolean = true,
)

class SettingsViewModel(
    private val stationRepository: StationRepository,
    private val favouritesStore: FavouritesStore,
    private val commuteWindowStore: CommuteWindowStore,
    private val appPreferencesStore: AppPreferencesStore,
    private val homeRefreshScheduler: HomeRefreshScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            stationRepository.ensureImported()
            combine(
                favouritesStore.favouritesFlow,
                commuteWindowStore.windowsFlow,
                appPreferencesStore.backgroundRefreshEnabledFlow,
            ) { favourites, windows, refreshEnabled ->
                Triple(favourites, windows, refreshEnabled)
            }.collect { (favourites, windows, refreshEnabled) ->
                _uiState.value = _uiState.value.copy(
                    favourites = favourites.mapNotNull { crs ->
                        stationRepository.getByCrs(crs)?.let {
                            FavouriteRowUi(crsCode = it.crsCode, name = it.name)
                        } ?: FavouriteRowUi(crsCode = crs, name = crs)
                    },
                    windows = windows.map { w ->
                        val station = stationRepository.getByCrs(w.stationCrs)
                        CommuteWindowRowUi(
                            id = w.id,
                            stationCrs = w.stationCrs,
                            stationName = station?.name ?: w.stationCrs,
                            timeRangeLabel = "${CommuteWindow.formatHhMm(w.startMinutes)}–${CommuteWindow.formatHhMm(w.endMinutes)}",
                            daysLabel = formatDays(w.days),
                            label = w.label,
                        )
                    },
                    backgroundRefreshEnabled = refreshEnabled,
                    loading = false,
                )
            }
        }
    }

    fun removeFavourite(crs: String) {
        viewModelScope.launch { favouritesStore.remove(crs) }
    }

    fun addFavourite(crs: String) {
        viewModelScope.launch { favouritesStore.add(crs) }
    }

    fun setBackgroundRefresh(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesStore.setBackgroundRefreshEnabled(enabled)
            homeRefreshScheduler.applyPreference(enabled)
        }
    }

    fun startAddWindow() {
        _uiState.value = _uiState.value.copy(editor = CommuteEditorUi())
    }

    fun startEditWindow(id: String) {
        viewModelScope.launch {
            val window = commuteWindowStore.getWindows().find { it.id == id } ?: return@launch
            val station = stationRepository.getByCrs(window.stationCrs)
            _uiState.value = _uiState.value.copy(
                editor = CommuteEditorUi(
                    id = window.id,
                    stationCrs = window.stationCrs,
                    stationName = station?.name.orEmpty(),
                    startTime = CommuteWindow.formatHhMm(window.startMinutes),
                    endTime = CommuteWindow.formatHhMm(window.endMinutes),
                    selectedDays = window.days,
                    label = window.label,
                ),
            )
        }
    }

    fun dismissEditor() {
        _uiState.value = _uiState.value.copy(editor = null)
    }

    fun onEditorStationPicked(crs: String) {
        viewModelScope.launch {
            val editor = _uiState.value.editor ?: return@launch
            val station = stationRepository.getByCrs(crs)
            _uiState.value = _uiState.value.copy(
                editor = editor.copy(
                    stationCrs = crs.uppercase(),
                    stationName = station?.name.orEmpty(),
                    error = null,
                ),
            )
        }
    }

    fun onEditorStartChange(value: String) {
        updateEditor { it.copy(startTime = value, error = null) }
    }

    fun onEditorEndChange(value: String) {
        updateEditor { it.copy(endTime = value, error = null) }
    }

    fun onEditorLabelChange(value: String) {
        updateEditor { it.copy(label = value) }
    }

    fun toggleEditorDay(day: DayOfWeek) {
        updateEditor { editor ->
            val next = editor.selectedDays.toMutableSet()
            if (!next.add(day)) next.remove(day)
            editor.copy(selectedDays = next, error = null)
        }
    }

    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        val start = CommuteWindow.parseHhMm(editor.startTime)
        val end = CommuteWindow.parseHhMm(editor.endTime)
        when {
            editor.stationCrs.isBlank() -> {
                updateEditor { it.copy(error = "Pick a station") }
            }
            editor.selectedDays.isEmpty() -> {
                updateEditor { it.copy(error = "Select at least one day") }
            }
            start == null || end == null -> {
                updateEditor { it.copy(error = "Use HH:mm times, e.g. 07:30") }
            }
            else -> {
                viewModelScope.launch {
                    commuteWindowStore.upsert(
                        CommuteWindow(
                            id = editor.id ?: java.util.UUID.randomUUID().toString(),
                            stationCrs = editor.stationCrs,
                            days = editor.selectedDays,
                            startMinutes = start,
                            endMinutes = end,
                            label = editor.label.trim(),
                        ),
                    )
                    _uiState.value = _uiState.value.copy(editor = null)
                }
            }
        }
    }

    fun deleteWindow(id: String) {
        viewModelScope.launch { commuteWindowStore.delete(id) }
    }

    private fun updateEditor(transform: (CommuteEditorUi) -> CommuteEditorUi) {
        val editor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(editor = transform(editor))
    }

    companion object {
        private val DAY_ORDER = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
        )

        fun formatDays(days: Set<DayOfWeek>): String {
            if (days.containsAll(DAY_ORDER)) return "Every day"
            if (days == setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                )
            ) {
                return "Mon–Fri"
            }
            return DAY_ORDER.filter { it in days }
                .joinToString(" ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
        }

        fun factory(
            stationRepository: StationRepository,
            favouritesStore: FavouritesStore,
            commuteWindowStore: CommuteWindowStore,
            appPreferencesStore: AppPreferencesStore,
            homeRefreshScheduler: HomeRefreshScheduler,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    stationRepository = stationRepository,
                    favouritesStore = favouritesStore,
                    commuteWindowStore = commuteWindowStore,
                    appPreferencesStore = appPreferencesStore,
                    homeRefreshScheduler = homeRefreshScheduler,
                ) as T
            }
        }
    }
}
