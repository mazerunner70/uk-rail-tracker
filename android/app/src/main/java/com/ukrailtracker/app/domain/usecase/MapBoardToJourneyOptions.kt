package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.CallingPoint
import com.ukrailtracker.app.domain.model.Departure
import com.ukrailtracker.app.domain.model.DepartureBoard
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.TrainStatus

/**
 * Maps a destination-filtered ArrDep board into journey options for origin → destination.
 */
object MapBoardToJourneyOptions {

    fun fromBoard(
        board: DepartureBoard,
        originCrs: String,
        destinationCrs: String,
    ): List<JourneyOption> {
        val origin = originCrs.trim().uppercase()
        val dest = destinationCrs.trim().uppercase()
        return board.departures
            .filter { !it.isArrival }
            .mapNotNull { dep -> toOption(dep, origin, dest) }
    }

    fun toOption(
        dep: Departure,
        originCrs: String,
        destinationCrs: String,
    ): JourneyOption? {
        val serviceId = dep.serviceId?.takeIf { it.isNotBlank() } ?: return null
        val destPoint = findDestinationPoint(dep.callingPoints, destinationCrs)
        val cancelled = dep.status == TrainStatus.Cancelled
        return JourneyOption(
            serviceId = serviceId,
            originCrs = originCrs.uppercase(),
            destinationCrs = destinationCrs.uppercase(),
            destinationName = dep.destination,
            scheduledDepartureLabel = dep.scheduledTimeLabel,
            expectedDepartureLabel = dep.expectedLabel,
            scheduledArrivalLabel = destPoint?.scheduledTimeLabel,
            expectedArrivalLabel = destPoint?.expectedLabel,
            platform = dep.platform,
            operatorName = dep.operatorName,
            status = dep.status,
            delayMinutes = dep.delayMinutes,
            callingPoints = dep.callingPoints,
            isCancelled = cancelled || (destPoint?.isCancelled == true),
        )
    }

    private fun findDestinationPoint(
        points: List<CallingPoint>,
        destinationCrs: String,
    ): CallingPoint? {
        val dest = destinationCrs.uppercase()
        return points.firstOrNull { it.crs?.uppercase() == dest }
            ?: points.lastOrNull()
    }
}
