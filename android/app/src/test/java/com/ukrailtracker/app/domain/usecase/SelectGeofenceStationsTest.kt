package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectGeofenceStationsTest {

    @Test
    fun `takes nearest then fills with always-on without duplicates`() {
        val nearby = listOf(
            row("A", 10.0),
            row("B", 20.0),
            row("C", 30.0),
            row("D", 40.0),
            row("E", 50.0),
            row("F", 60.0),
        )
        val always = listOf(station("B"), station("Z"))

        val selected = SelectGeofenceStations.select(
            nearby = nearby,
            alwaysOn = always,
            nearbyLimit = 5,
            maxTotal = 20,
        )

        assertEquals(listOf("A", "B", "C", "D", "E", "Z"), selected.map { it.crs })
    }

    @Test
    fun `respects max total`() {
        val nearby = (1..10).map { row("N$it", it.toDouble()) }
        val always = (1..10).map { station("A$it") }

        val selected = SelectGeofenceStations.select(
            nearby = nearby,
            alwaysOn = always,
            nearbyLimit = 5,
            maxTotal = 7,
        )

        assertEquals(7, selected.size)
        assertTrue(selected.take(5).all { it.crs.startsWith("N") })
    }

    private fun row(crs: String, metres: Double) =
        StationWithDistance(station = station(crs), distanceMetres = metres)

    private fun station(crs: String) = Station(
        crsCode = crs,
        name = crs,
        latitude = 51.0,
        longitude = -0.1,
        operatorName = "",
        operatorCode = "",
    )
}
