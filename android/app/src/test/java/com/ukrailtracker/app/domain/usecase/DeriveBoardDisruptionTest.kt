package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.Departure
import com.ukrailtracker.app.domain.model.DisruptionSeverity
import com.ukrailtracker.app.domain.model.TrainStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeriveBoardDisruptionTest {

    @Test
    fun greenWhenAllOnTime() {
        val summary = DeriveBoardDisruption.fromDepartures(
            listOf(dep("GWR", TrainStatus.OnTime), dep("GWR", TrainStatus.OnTime)),
        )
        assertEquals(DisruptionSeverity.Green, summary.severity)
        assertEquals("Good service", summary.headline)
    }

    @Test
    fun amberWhenSmallDelay() {
        val summary = DeriveBoardDisruption.fromDepartures(
            listOf(
                dep("GWR", TrainStatus.OnTime),
                dep("GWR", TrainStatus.Delayed, delay = 4),
                dep("GWR", TrainStatus.OnTime),
            ),
        )
        assertEquals(DisruptionSeverity.Amber, summary.severity)
        assertTrue(summary.headline.contains("4"))
    }

    @Test
    fun redWhenCancelled() {
        val summary = DeriveBoardDisruption.fromDepartures(
            listOf(
                dep("GWR", TrainStatus.Cancelled),
                dep("GWR", TrainStatus.OnTime),
            ),
        )
        assertEquals(DisruptionSeverity.Red, summary.severity)
        assertEquals(1, summary.cancelledCount)
        assertTrue(summary.affectedOperators.contains("GWR"))
    }

    @Test
    fun redWhenSignificantDelay() {
        val summary = DeriveBoardDisruption.fromDepartures(
            listOf(dep("XC", TrainStatus.Delayed, delay = 18)),
        )
        assertEquals(DisruptionSeverity.Red, summary.severity)
        assertEquals(18, summary.maxDelayMinutes)
    }

    private fun dep(
        operator: String,
        status: TrainStatus,
        delay: Int? = null,
    ) = Departure(
        destination = "Somewhere",
        destinationCrs = "XXX",
        scheduledTimeLabel = "08:00",
        expectedLabel = if (status == TrainStatus.OnTime) "On time" else "08:10",
        platform = "1",
        operatorName = operator,
        status = status,
        delayMinutes = delay,
        serviceId = null,
    )
}
