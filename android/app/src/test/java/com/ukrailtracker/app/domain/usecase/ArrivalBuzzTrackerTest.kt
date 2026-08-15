package com.ukrailtracker.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalBuzzTrackerTest {

    @Test
    fun noBuzzWhenNotApproachingDestination() {
        val fired = ArrivalBuzzTracker.thresholdsToFire(
            minutesRemaining = 2,
            alreadyFired = emptySet(),
            approachingDestination = false,
        )
        assertTrue(fired.isEmpty())
    }

    @Test
    fun firesThreeTwoOneAsCountdownCrosses() {
        var already = emptySet<Int>()

        val at4 = ArrivalBuzzTracker.thresholdsToFire(4, already, true)
        assertTrue(at4.isEmpty())

        val at3 = ArrivalBuzzTracker.thresholdsToFire(3, already, true)
        assertEquals(listOf(3), at3)
        already = ArrivalBuzzTracker.withFired(already, at3)

        val at2 = ArrivalBuzzTracker.thresholdsToFire(2, already, true)
        assertEquals(listOf(2), at2)
        already = ArrivalBuzzTracker.withFired(already, at2)

        val at1 = ArrivalBuzzTracker.thresholdsToFire(1, already, true)
        assertEquals(listOf(1), at1)
        already = ArrivalBuzzTracker.withFired(already, at1)

        val again = ArrivalBuzzTracker.thresholdsToFire(1, already, true)
        assertTrue(again.isEmpty())
    }

    @Test
    fun lateJoinFiresAllDueThresholdsOnce() {
        val fired = ArrivalBuzzTracker.thresholdsToFire(
            minutesRemaining = 1,
            alreadyFired = emptySet(),
            approachingDestination = true,
        )
        assertEquals(listOf(3, 2, 1), fired)
    }

    @Test
    fun etaSlipDoesNotRefire() {
        val already = setOf(3)
        // ETA slipped back above 3 — still don't re-fire 3
        val at4 = ArrivalBuzzTracker.thresholdsToFire(4, already, true)
        assertTrue(at4.isEmpty())
        val at3Again = ArrivalBuzzTracker.thresholdsToFire(3, already, true)
        assertTrue(at3Again.isEmpty())
    }

    @Test
    fun negativeEtaDoesNotBuzz() {
        val fired = ArrivalBuzzTracker.thresholdsToFire(
            minutesRemaining = -1,
            alreadyFired = emptySet(),
            approachingDestination = true,
        )
        assertTrue(fired.isEmpty())
    }
}
