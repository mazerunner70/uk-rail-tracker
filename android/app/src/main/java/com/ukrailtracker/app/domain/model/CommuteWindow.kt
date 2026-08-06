package com.ukrailtracker.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.UUID

/**
 * User-defined time window that maps to a preferred station (M2 routine inference v1).
 *
 * [startMinutes] / [endMinutes] are minutes from midnight (0–1439).
 * Overnight windows are supported when start > end (e.g. 22:00–01:00).
 */
data class CommuteWindow(
    val id: String = UUID.randomUUID().toString(),
    val stationCrs: String,
    val days: Set<DayOfWeek>,
    val startMinutes: Int,
    val endMinutes: Int,
    val label: String = "",
) {
    fun isActiveAt(dateTime: LocalDateTime): Boolean {
        if (days.isEmpty() || dateTime.dayOfWeek !in days) return false
        val mins = dateTime.hour * 60 + dateTime.minute
        return if (startMinutes <= endMinutes) {
            mins in startMinutes..endMinutes
        } else {
            mins >= startMinutes || mins <= endMinutes
        }
    }

    companion object {
        fun parseHhMm(value: String): Int? {
            val parts = value.trim().split(':')
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }

        fun formatHhMm(minutes: Int): String {
            val clamped = minutes.coerceIn(0, 23 * 60 + 59)
            return "%02d:%02d".format(clamped / 60, clamped % 60)
        }
    }
}
