package com.ukrailtracker.app.domain.session.host

import android.content.Context
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.BoardingConfig
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.LoadFavouriteBoundDepartures
import com.ukrailtracker.app.domain.commute.CommuteFavouritesAccess
import com.ukrailtracker.app.domain.commute.CommuteJourneyLogger
import com.ukrailtracker.app.domain.commute.CommutePinStore
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.ui.home.AtStationAux
import com.ukrailtracker.app.ui.home.CommuteAuxState
import com.ukrailtracker.app.ui.home.OnTrainUiAux
import com.ukrailtracker.app.ui.home.PlatformWatchUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared I/O and mutable store for [SessionHost] lifecycle work.
 * Owned by [com.ukrailtracker.app.domain.commute.CommuteRuntime].
 */
interface SessionHostServices {
    val scope: CoroutineScope
    val appContext: Context
    val sessionFlow: StateFlow<CommuteSession>
    val handoff: SessionHandoff
    val boardingConfig: BoardingConfig
    val stationRepository: StationRepository
    val journeyRepository: JourneyRepository
    val favouritesStore: CommuteFavouritesAccess
    val pinnedJourneyStore: CommutePinStore
    val journeyLogRepository: CommuteJourneyLogger
    val loadFavouriteBoundDepartures: LoadFavouriteBoundDepartures
    val platformAppeared: SharedFlow<String>
    val arrivalBuzz: SharedFlow<Int>

    fun publishHandoff(handoff: SessionHandoff)
    fun dispatch(event: CommuteEvent)
    fun emitPlatformAppeared(platform: String)
    fun emitArrivalBuzz(threshold: Int)

    fun updateAtStationAux(transform: (AtStationAux) -> AtStationAux)
    fun updateOnTrainAux(transform: (OnTrainUiAux) -> OnTrainUiAux)
    fun updateAux(transform: (CommuteAuxState) -> CommuteAuxState)

    fun currentAtStationBody(): com.ukrailtracker.app.ui.home.HomeBodyUi.AtStation?
    fun mapPlatformWatch(watch: com.ukrailtracker.app.domain.usecase.PlatformWatchPhase.Watching): PlatformWatchUi?
    fun startOnTrainForeground(destinationName: String, minutesRemaining: Long?)
    fun updateOnTrainForeground(destinationName: String, minutesRemaining: Long?)
    fun stopOnTrainForeground()
    fun pinJourney(journey: PinnedJourney)
    fun clearPin()
    fun logJourney(option: JourneyOption)
}
