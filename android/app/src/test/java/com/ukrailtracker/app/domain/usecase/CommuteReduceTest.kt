package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommuteReduceTest {

    private val config = WalkUpConfig(
        geofenceMetres = 200.0,
        dwellMillis = 10_000L,
        lockHoldMetres = 350.0,
    )

    private val pad = station("PAD", "London Paddington", 51.515, -0.175)

    @Test
    fun awayNearAtWalkAndDwell() {
        var session: CommuteSession = CommuteSession.Idle

        val away = reduceLocation(session, speed = 1.4, distance = 500.0, t = 0)
        assertEquals(CommuteSession.Idle, away.session)
        assertTrue(away.effects.isEmpty())
        session = away.session

        val near = reduceLocation(session, speed = 1.4, distance = 120.0, t = 1_000)
        assertTrue(near.session is CommuteSession.Near)
        assertEquals("PAD", (near.session as CommuteSession.Near).dwelling.crs)
        assertTrue(near.effects.isEmpty())
        session = near.session

        val at = reduceLocation(session, speed = 0.1, distance = 80.0, t = 12_000)
        val atStation = at.session as CommuteSession.AtStation
        assertEquals("PAD", atStation.origin.crs)
        assertEquals(BoardingPhase.Idle, atStation.boarding)
        assertEquals(
            listOf(
                CommuteEffect.LoadFavouriteBoundDepartures(originCrs = "PAD", forceRefresh = false),
            ),
            at.effects,
        )
    }

    @Test
    fun coldStartAlreadyAtStationLocksImmediately() {
        val result = reduceLocation(
            session = CommuteSession.Idle,
            speed = 0.0,
            distance = 40.0,
            t = 0,
            alreadyPresentOk = true,
        )
        val at = result.session as CommuteSession.AtStation
        assertEquals("PAD", at.origin.crs)
        assertEquals(
            listOf(CommuteEffect.LoadFavouriteBoundDepartures("PAD", forceRefresh = false)),
            result.effects,
        )
    }

    @Test
    fun vehiclePassStaysIdle() {
        val result = reduceLocation(
            session = CommuteSession.Idle,
            speed = 15.0,
            distance = 40.0,
            t = 0,
            alreadyPresentOk = true,
        )
        assertEquals(CommuteSession.Idle, result.session)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun setOriginOverrideLocksAtStation() {
        val origin = LockedOrigin(
            crs = "PAD",
            name = "London Paddington",
            latitude = pad.latitude,
            longitude = pad.longitude,
            lockedAtEpochMs = 0L,
            distanceMetres = 0.0,
            manualOverride = true,
        )
        val result = reduce(CommuteSession.Idle, CommuteEvent.SetOriginOverride(origin), config)
        val at = result.session as CommuteSession.AtStation
        assertTrue(at.origin.manualOverride)
        assertEquals(
            listOf(CommuteEffect.LoadFavouriteBoundDepartures("PAD", forceRefresh = true)),
            result.effects,
        )

        val ignored = reduceLocation(at, speed = 1.4, distance = 500.0, t = 5_000)
        assertEquals(at, ignored.session)
        assertTrue(ignored.effects.isEmpty())

        val cleared = reduce(at, CommuteEvent.ClearOverride, config)
        assertEquals(CommuteSession.Idle, cleared.session)
    }

    @Test
    fun openedForFrequentChecksLocksAtStationAndLoads() {
        val origin = LockedOrigin(
            crs = "PAD",
            name = "London Paddington",
            latitude = pad.latitude,
            longitude = pad.longitude,
            lockedAtEpochMs = 0L,
            distanceMetres = 0.0,
        )
        val result = reduce(
            CommuteSession.Idle,
            CommuteEvent.OpenedForFrequentChecks(origin),
            config,
        )
        val at = result.session as CommuteSession.AtStation
        assertEquals("PAD", at.origin.crs)
        assertEquals(false, at.origin.manualOverride)
        assertEquals(
            listOf(CommuteEffect.LoadFavouriteBoundDepartures("PAD", forceRefresh = true)),
            result.effects,
        )
    }

    @Test
    fun permissionLostReturnsIdle() {
        val near = reduceLocation(CommuteSession.Idle, speed = 1.4, distance = 120.0, t = 0)
        val lost = reduce(near.session, CommuteEvent.PermissionLost, config)
        assertEquals(CommuteSession.Idle, lost.session)
    }

    private fun reduceLocation(
        session: CommuteSession,
        speed: Double?,
        distance: Double,
        t: Long,
        alreadyPresentOk: Boolean = false,
    ): CommuteTransition {
        val nearest = StationWithDistance(pad, distance)
        val sample = WalkUpSample(
            latitude = pad.latitude,
            longitude = pad.longitude,
            speedMps = speed,
            epochMs = t,
        )
        return reduce(
            session = session,
            event = CommuteEvent.Location(
                sample = sample,
                nearest = nearest,
                speedMps = speed,
                alreadyPresentOk = alreadyPresentOk,
            ),
            config = config,
        )
    }

    private fun station(crs: String, name: String, lat: Double, lng: Double): Station =
        Station(
            crsCode = crs,
            name = name,
            latitude = lat,
            longitude = lng,
            operatorName = "GWR",
            operatorCode = "GW",
        )
}
