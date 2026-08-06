package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.JourneyOption
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Parses Darwin HH:mm (and H:mm) labels and derives journey duration / next-hour filters.
 */
object JourneyTimeUtils {

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

    fun parseTimeLabel(label: String?): LocalTime? {
        val trimmed = label?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val candidate = trimmed.take(5)
        return runCatching { LocalTime.parse(candidate, TIME) }.getOrNull()
    }

    /**
     * Minutes from departure label to arrival label. Handles midnight wrap.
     * Prefers expected times when present and parseable.
     */
    fun durationMinutes(
        departureLabel: String?,
        arrivalLabel: String?,
    ): Int? {
        val dep = parseTimeLabel(departureLabel) ?: return null
        val arr = parseTimeLabel(arrivalLabel) ?: return null
        var minutes = ChronoUnit.MINUTES.between(dep, arr)
        if (minutes < 0) minutes += 24 * 60
        return minutes.toInt().takeIf { it in 0..24 * 60 }
    }

    fun durationMinutes(option: JourneyOption): Int? {
        val dep = option.expectedDepartureLabel
            .takeUnless { it.equals("On time", ignoreCase = true) || it.equals("Delayed", ignoreCase = true) }
            ?: option.scheduledDepartureLabel
        val arr = option.expectedArrivalLabel
            ?.takeUnless { it.equals("On time", ignoreCase = true) || it.equals("Delayed", ignoreCase = true) }
            ?: option.scheduledArrivalLabel
        return durationMinutes(dep, arr)
            ?: durationMinutes(option.scheduledDepartureLabel, option.scheduledArrivalLabel)
    }

    /**
     * True when the service's departure is within [windowMinutes] from [now]
     * (already-departed services in the last [gracePastMinutes] are excluded).
     */
    fun isDepartureWithinWindow(
        departureLabel: String,
        now: LocalDateTime = LocalDateTime.now(),
        windowMinutes: Long = 60,
        gracePastMinutes: Long = 2,
    ): Boolean {
        val time = parseTimeLabel(departureLabel) ?: return true
        val today = LocalDateTime.of(now.toLocalDate(), time)
        val candidates = listOf(today.minusDays(1), today, today.plusDays(1))
        val nearest = candidates.minBy { kotlin.math.abs(ChronoUnit.MINUTES.between(now, it)) }
        val delta = ChronoUnit.MINUTES.between(now, nearest)
        return delta in -gracePastMinutes..windowMinutes
    }

    fun filterNextHour(
        options: List<JourneyOption>,
        now: LocalDateTime = LocalDateTime.now(),
        windowMinutes: Long = 60,
    ): List<JourneyOption> =
        options.filter { option ->
            !option.isCancelled &&
                isDepartureWithinWindow(
                    departureLabel = option.scheduledDepartureLabel,
                    now = now,
                    windowMinutes = windowMinutes,
                )
        }
}
