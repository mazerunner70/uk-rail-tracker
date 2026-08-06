package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.CallingPoint
import com.ukrailtracker.app.domain.model.Departure
import com.ukrailtracker.app.domain.model.DepartureBoard
import com.ukrailtracker.app.domain.model.TrainStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapBoardToJourneyOptionsTest {

    @Test
    fun mapsDeparturesWithDestinationCallingPoint() {
        val board = DepartureBoard(
            crsCode = "PAD",
            stationName = "London Paddington",
            generatedAtEpochMs = 1L,
            fetchedAtEpochMs = 1L,
            departures = listOf(
                Departure(
                    destination = "Bristol Temple Meads",
                    destinationCrs = "BRI",
                    scheduledTimeLabel = "08:00",
                    expectedLabel = "08:12",
                    platform = "5",
                    operatorName = "GWR",
                    status = TrainStatus.Delayed,
                    delayMinutes = 12,
                    serviceId = "svc1",
                    callingPoints = listOf(
                        CallingPoint("Reading", "RDG", "08:30", "08:40"),
                        CallingPoint("Bristol Temple Meads", "BRI", "09:30", "09:45"),
                    ),
                ),
            ),
        )

        val options = MapBoardToJourneyOptions.fromBoard(board, "PAD", "BRI")
        assertEquals(1, options.size)
        assertEquals("09:30", options[0].scheduledArrivalLabel)
        assertEquals("09:45", options[0].expectedArrivalLabel)
        assertEquals(12, options[0].delayMinutes)
        assertEquals(2, options[0].callingPoints.size)
    }

    @Test
    fun skipsArrivalsAndMissingServiceId() {
        val board = DepartureBoard(
            crsCode = "PAD",
            stationName = "London Paddington",
            generatedAtEpochMs = 1L,
            fetchedAtEpochMs = 1L,
            departures = listOf(
                Departure(
                    destination = "from Reading",
                    destinationCrs = null,
                    scheduledTimeLabel = "07:50",
                    expectedLabel = "On time",
                    platform = "1",
                    operatorName = "GWR",
                    status = TrainStatus.OnTime,
                    delayMinutes = null,
                    serviceId = "arr1",
                    isArrival = true,
                ),
                Departure(
                    destination = "Bristol Temple Meads",
                    destinationCrs = "BRI",
                    scheduledTimeLabel = "08:00",
                    expectedLabel = "On time",
                    platform = "5",
                    operatorName = "GWR",
                    status = TrainStatus.OnTime,
                    delayMinutes = null,
                    serviceId = null,
                ),
            ),
        )
        assertTrue(MapBoardToJourneyOptions.fromBoard(board, "PAD", "BRI").isEmpty())
    }
}
