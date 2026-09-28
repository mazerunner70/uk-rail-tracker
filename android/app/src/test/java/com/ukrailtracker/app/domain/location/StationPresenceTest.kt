package com.ukrailtracker.app.domain.location

import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import com.ukrailtracker.app.domain.model.UserLocation
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationPresenceTest {

    private val config = StationPresenceConfig(
        nearMetres = 350.0,
        atMetres = 200.0,
        exitMarginMetres = 40.0,
    )
    private val pad = station("PAD", "London Paddington", 51.515, -0.175)
    private val rdg = station("RDG", "Reading", 51.458, -0.972)

    @Test
    fun awayEntersNearThenAt() {
        val near = StationPresenceClassifier.next(PresenceBand.Away, atDistance(280.0), config)
        assertTrue(near is PresenceBand.Near)
        val at = StationPresenceClassifier.next(near, atDistance(80.0), config)
        assertTrue(at is PresenceBand.At)
        val edge = StationPresenceClassifier.edgeEvent(near, at, epochMs = 12_000)
        val entered = edge as CommuteEvent.AtStation
        assertEquals("PAD", entered.crs)
        assertEquals(12_000L, entered.epochMs)
    }

    @Test
    fun awayInsideAtRadiusLocksWithoutDwelling() {
        val at = StationPresenceClassifier.next(PresenceBand.Away, atDistance(40.0), config)
        assertTrue(at is PresenceBand.At)
    }

    @Test
    fun hysteresisKeepsBandUntilMarginIsCrossed() {
        val at = PresenceBand.At(atDistance(40.0))
        val stillAt = StationPresenceClassifier.next(at, atDistance(230.0), config)
        assertTrue(stillAt is PresenceBand.At)
        val stepped = StationPresenceClassifier.next(at, atDistance(250.0), config)
        assertTrue(stepped is PresenceBand.Near)

        val near = PresenceBand.Near(atDistance(340.0))
        val stillNear = StationPresenceClassifier.next(near, atDistance(380.0), config)
        assertTrue(stillNear is PresenceBand.Near)
        val left = StationPresenceClassifier.next(near, atDistance(400.0), config)
        assertEquals(PresenceBand.Away, left)
        assertEquals(
            CommuteEvent.LeftStation,
            StationPresenceClassifier.edgeEvent(near, left, epochMs = 1L),
        )
    }

    @Test
    fun crsChangeEmitsTheNewStation() {
        val near = PresenceBand.Near(atDistance(300.0))
        val next = StationPresenceClassifier.next(near, readingAt(100.0), config)
        assertTrue(next is PresenceBand.At)
        val edge = StationPresenceClassifier.edgeEvent(near, next, epochMs = 5L) as CommuteEvent.AtStation
        assertEquals("RDG", edge.crs)
    }

    @Test
    fun sameBandDoesNotRepeat() {
        val near = PresenceBand.Near(atDistance(300.0))
        val next = StationPresenceClassifier.next(near, atDistance(290.0), config)
        assertNull(StationPresenceClassifier.edgeEvent(near, next, epochMs = 2L))
    }

    @Test
    fun serviceEmitsLocationAndDoesNotRepeatWhileInside() = kotlinx.coroutines.test.runTest {
        val service = StationPresenceService(
            stationRepository = FakeStations(listOf(pad, rdg)),
            config = config,
        )
        val first = service.onLocation(UserLocation(pad.latitude, pad.longitude, epochMs = 1L))
        assertTrue(first.events.any { it is CommuteEvent.Location })
        assertTrue(first.events.any { it is CommuteEvent.AtStation })

        val second = service.onLocation(
            UserLocation(pad.latitude + 0.0001, pad.longitude, epochMs = 2L),
        )
        assertEquals(1, second.events.size)
        assertTrue(second.events.single() is CommuteEvent.Location)
    }

    private fun atDistance(metres: Double) = StationWithDistance(pad, metres)

    private fun readingAt(metres: Double) = StationWithDistance(rdg, metres)

    private fun station(crs: String, name: String, lat: Double, lng: Double) = Station(
        crsCode = crs,
        name = name,
        latitude = lat,
        longitude = lng,
        operatorName = "GWR",
        operatorCode = "GW",
    )

    private class FakeStations(private val stations: List<Station>) : StationRepository {
        override suspend fun ensureImported() = Unit
        override suspend fun count(): Int = stations.size
        override suspend fun getByCrs(crs: String): Station? =
            stations.find { it.crsCode.equals(crs, ignoreCase = true) }
        override suspend fun search(query: String): List<Station> = emptyList()
        override suspend fun getAll(): List<Station> = stations
        override suspend fun getNearby(lat: Double, lng: Double, limit: Int): List<StationWithDistance> =
            emptyList()
    }
}
