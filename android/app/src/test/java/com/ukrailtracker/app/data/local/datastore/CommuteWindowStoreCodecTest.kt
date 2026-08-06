package com.ukrailtracker.app.data.local.datastore

import com.ukrailtracker.app.domain.model.CommuteWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class CommuteWindowStoreCodecTest {

    @Test
    fun roundTripsWindows() {
        val original = listOf(
            CommuteWindow(
                id = "w1",
                stationCrs = "pad",
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                startMinutes = 450,
                endMinutes = 540,
                label = "Morning",
            ),
        )
        val decoded = CommuteWindowStore.decode(CommuteWindowStore.encode(original))
        assertEquals(1, decoded.size)
        assertEquals("PAD", decoded[0].stationCrs)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), decoded[0].days)
        assertEquals(450, decoded[0].startMinutes)
        assertEquals("Morning", decoded[0].label)
    }
}
