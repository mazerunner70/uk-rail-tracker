package com.ukrailtracker.app.debug

import androidx.compose.runtime.Composable
import com.ukrailtracker.app.data.local.datastore.CommuteWindowStore
import com.ukrailtracker.app.data.local.datastore.FavouritesStore
import com.ukrailtracker.app.data.local.datastore.PinnedJourneyStore
import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.ui.home.PlatformWatchUi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** No-op when the simulator Gradle flag is off. */
object DebugWalkUp {
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    fun wrapLocation(real: LocationProvider): LocationProvider = real

    fun isSimulatingLocation(): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun HomeAppBarAction(
        stationRepository: StationRepository,
        favouritesStore: FavouritesStore,
        commuteWindowStore: CommuteWindowStore,
        journeyRepository: JourneyRepository,
        pinnedJourneyStore: PinnedJourneyStore,
        selectedWatch: PlatformWatchUi?,
        onPreparePresence: (elevatePollForMs: Long) -> Unit,
    ) {
        // Intentionally empty.
    }
}
