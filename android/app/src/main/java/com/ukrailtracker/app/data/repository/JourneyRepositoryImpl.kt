package com.ukrailtracker.app.data.repository

import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.repository.DepartureRepository
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.usecase.MapBoardToJourneyOptions

class JourneyRepositoryImpl(
    private val departureRepository: DepartureRepository,
) : JourneyRepository {

    override suspend fun getOptions(
        originCrs: String,
        destinationCrs: String,
        forceRefresh: Boolean,
    ): List<JourneyOption> {
        val origin = originCrs.trim().uppercase()
        val dest = destinationCrs.trim().uppercase()
        require(origin.isNotBlank() && dest.isNotBlank()) { "Origin and destination required" }
        require(origin != dest) { "Origin and destination must differ" }

        val board = departureRepository.getBoard(
            crs = origin,
            forceRefresh = forceRefresh,
            filterCrs = dest,
            filterType = "to",
            numRows = 10,
        )
        return MapBoardToJourneyOptions.fromBoard(board, origin, dest)
    }

    override suspend fun getOption(
        originCrs: String,
        destinationCrs: String,
        serviceId: String,
        forceRefresh: Boolean,
    ): JourneyOption? =
        getOptions(originCrs, destinationCrs, forceRefresh)
            .firstOrNull { it.serviceId == serviceId }
}
