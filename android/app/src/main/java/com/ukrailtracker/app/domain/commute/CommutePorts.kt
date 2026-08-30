package com.ukrailtracker.app.domain.commute

import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import kotlinx.coroutines.flow.Flow

interface CommuteFavouritesAccess {
    suspend fun getFavourites(): List<String>
}

interface CommutePinStore {
    val pinnedFlow: Flow<PinnedJourney?>
    suspend fun pin(journey: PinnedJourney)
    suspend fun clear()
}

interface CommuteJourneyLogger {
    suspend fun logFromOption(option: JourneyOption)
}
