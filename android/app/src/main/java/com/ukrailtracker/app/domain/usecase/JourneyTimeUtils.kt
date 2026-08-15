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
        val trimmed = label?.trim()?.removeSuffix("*")?.trim().orEmpty()
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

    /**
     * Prefer a parseable expected HH:mm over scheduled when the expected label is not
     * a status phrase like "On time" / "Delayed".
     */
    fun effectiveDepartureLabel(
        scheduledDepartureLabel: String,
        expectedDepartureLabel: String?,
    ): String {
        val expected = expectedDepartureLabel?.trim().orEmpty()
        if (expected.isNotEmpty() &&
            !expected.equals("On time", ignoreCase = true) &&
            !expected.equals("Delayed", ignoreCase = true) &&
            parseTimeLabel(expected) != null
        ) {
            return expected
        }
        return scheduledDepartureLabel
    }

    /**
     * Minutes from [now] to the nearest calendar occurrence of [departureLabel].
     * Negative when the departure is in the past. Null if the label is not parseable.
     */
    fun minutesUntilDeparture(
        departureLabel: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): Long? {
        val time = parseTimeLabel(departureLabel) ?: return null
        val today = LocalDateTime.of(now.toLocalDate(), time)
        val candidates = listOf(today.minusDays(1), today, today.plusDays(1))
        val nearest = candidates.minBy { kotlin.math.abs(ChronoUnit.MINUTES.between(now, it)) }
        return ChronoUnit.MINUTES.between(now, nearest)
    }

    /**
     * True when departure falls in [[-pastMinutes], [futureMinutes]] relative to [now].
     */
    fun isDepartureAroundNow(
        departureLabel: String,
        now: LocalDateTime = LocalDateTime.now(),
        pastMinutes: Long = 8,
        futureMinutes: Long = 3,
    ): Boolean {
        val delta = minutesUntilDeparture(departureLabel, now) ?: return false
        return delta in -pastMinutes..futureMinutes
    }

    /**
     * Minutes from [now] to the nearest calendar occurrence of an HH:mm [label].
     * Negative when that time is in the past. Null if not parseable.
     */
    fun minutesUntilArrivalLabel(
        label: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ): Long? {
        val time = parseTimeLabel(label) ?: return null
        val today = LocalDateTime.of(now.toLocalDate(), time)
        val candidates = listOf(today.minusDays(1), today, today.plusDays(1))
        val nearest = candidates.minBy { kotlin.math.abs(ChronoUnit.MINUTES.between(now, it)) }
        return ChronoUnit.MINUTES.between(now, nearest)
    }

    /**
     * Prefers a parseable expected arrival label over scheduled for countdown / ETA.
     */
    fun minutesUntilArrival(
        scheduledArrivalLabel: String?,
        expectedArrivalLabel: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ): Long? {
        val expected = expectedArrivalLabel?.trim().orEmpty()
        if (expected.isNotEmpty() &&
            !expected.equals("On time", ignoreCase = true) &&
            !expected.equals("Delayed", ignoreCase = true) &&
            parseTimeLabel(expected) != null
        ) {
            return minutesUntilArrivalLabel(expected, now)
        }
        return minutesUntilArrivalLabel(scheduledArrivalLabel, now)
            ?: minutesUntilArrivalLabel(expectedArrivalLabel, now)
    }

    /**
     * Minutes until destination ET. "On time" / "Delayed" / blank ET use scheduled (ST).
     * Never treats an actual (AT) clock as the countdown target.
     */
    fun minutesUntilExpectedArrival(
        scheduledArrivalLabel: String?,
        expectedArrivalLabel: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ): Long? = minutesUntilArrival(
        scheduledArrivalLabel = scheduledArrivalLabel,
        expectedArrivalLabel = expectedArrivalLabel,
        now = now,
    )

    /**
     * Signed minutes of [observedLabel] vs [scheduledLabel] (positive = late).
     * Handles midnight wrap for small overnight deltas.
     */
    fun scheduleVarianceMinutes(
        scheduledLabel: String?,
        observedLabel: String?,
    ): Int? {
        val scheduled = parseTimeLabel(scheduledLabel) ?: return null
        val observed = parseTimeLabel(parseableClockLabel(observedLabel)) ?: return null
        var diff = ChronoUnit.MINUTES.between(scheduled, observed).toInt()
        if (diff < -12 * 60) diff += 24 * 60
        if (diff > 12 * 60) diff -= 24 * 60
        return diff
    }

    /** HH:mm clock labels only — rejects status phrases like "On time". */
    fun parseableClockLabel(label: String?): String? {
        val trimmed = label?.trim()?.removeSuffix("*")?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.equals("On time", ignoreCase = true) ||
            trimmed.equals("Delayed", ignoreCase = true) ||
            trimmed.equals("Cancelled", ignoreCase = true) ||
            trimmed.equals("Departed", ignoreCase = true) ||
            trimmed.equals("No report", ignoreCase = true)
        ) {
            return null
        }
        return trimmed.takeIf { parseTimeLabel(it) != null }
    }

    /**
     * True when Darwin has published an actual (`at` / `ata` / `atd`) for a stop.
     * Includes HH:mm and "On time" (arrived to timetable); excludes "No report" / blank.
     */
    fun hasActualArrival(label: String?): Boolean {
        val trimmed = label?.trim()?.removeSuffix("*")?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        if (trimmed.equals("No report", ignoreCase = true)) return false
        if (trimmed.equals("Delayed", ignoreCase = true)) return false
        if (trimmed.equals("Cancelled", ignoreCase = true)) return false
        if (trimmed.equals("On time", ignoreCase = true)) return true
        return parseTimeLabel(trimmed) != null
    }

    /** Display form of an actual time label, or null when [hasActualArrival] is false. */
    fun displayActualLabel(label: String?): String? {
        if (!hasActualArrival(label)) return null
        return label?.trim()?.removeSuffix("*")?.trim()?.takeIf { it.isNotEmpty() }
    }
}
