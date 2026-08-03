package com.ukrailtracker.app.domain.geo

import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun paddingtonCoords_rankPadFirst() {
        // Approximate Paddington station coordinates from plan
        val userLat = 51.517
        val userLng = -0.177

        val stations = listOf(
            station("KGX", "London Kings Cross", 51.5308, -0.1238),
            station("PAD", "London Paddington", 51.5158, -0.1760),
            station("EUS", "London Euston", 51.5282, -0.1337),
            station("BRI", "Bristol Temple Meads", 51.4491, -2.5803),
            station("MYB", "London Marylebone", 51.5225, -0.1631),
        )

        val ranked = stations
            .map {
                StationWithDistance(
                    station = it,
                    distanceMetres = GeoUtils.haversineMetres(
                        userLat, userLng, it.latitude, it.longitude,
                    ),
                )
            }
            .sortedBy { it.distanceMetres }

        assertEquals("PAD", ranked.first().station.crsCode)
        assertTrue(ranked.first().distanceMetres < 500)
        assertTrue(
            ranked.first().distanceMetres < ranked.first { it.station.crsCode == "KGX" }.distanceMetres,
        )
    }

    @Test
    fun formatDistance_usesMetresAndKm() {
        assertEquals("250 m", GeoUtils.formatDistance(250.0))
        assertEquals("1.2 km", GeoUtils.formatDistance(1200.0))
        assertEquals("15 km", GeoUtils.formatDistance(15_400.0))
    }

    private fun station(
        crs: String,
        name: String,
        lat: Double,
        lng: Double,
    ) = Station(
        crsCode = crs,
        name = name,
        latitude = lat,
        longitude = lng,
        operatorName = "Test",
        operatorCode = "XX",
    )
}
