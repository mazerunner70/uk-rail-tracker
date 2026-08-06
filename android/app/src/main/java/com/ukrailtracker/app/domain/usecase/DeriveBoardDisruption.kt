package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.Departure
import com.ukrailtracker.app.domain.model.DepartureBoard
import com.ukrailtracker.app.domain.model.DisruptionSeverity
import com.ukrailtracker.app.domain.model.DisruptionSummary
import com.ukrailtracker.app.domain.model.TrainStatus

/**
 * Derives a green / amber / red disruption summary from an ArrDep board.
 *
 * Thresholds (v1, board-only):
 * - Red: any cancellation, or max delay ≥ 15 min, or ≥ half of services delayed
 * - Amber: any delay
 * - Green: otherwise
 */
object DeriveBoardDisruption {

    fun from(board: DepartureBoard): DisruptionSummary =
        fromDepartures(board.departures)

    fun fromDepartures(departures: List<Departure>): DisruptionSummary {
        if (departures.isEmpty()) {
            return DisruptionSummary(
                severity = DisruptionSeverity.Green,
                headline = "No services in the current window",
                detail = "Nothing scheduled on this board right now.",
                affectedOperators = emptyList(),
                cancelledCount = 0,
                delayedCount = 0,
                maxDelayMinutes = 0,
            )
        }

        val cancelled = departures.filter { it.status == TrainStatus.Cancelled }
        val delayed = departures.filter {
            it.status == TrainStatus.Delayed && (it.delayMinutes ?: 0) > 0
        }
        val maxDelay = delayed.maxOfOrNull { it.delayMinutes ?: 0 } ?: 0
        val affected = (cancelled + delayed)
            .map { it.operatorName.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val severity = when {
            cancelled.isNotEmpty() -> DisruptionSeverity.Red
            maxDelay >= 15 -> DisruptionSeverity.Red
            delayed.size * 2 >= departures.size && delayed.isNotEmpty() -> DisruptionSeverity.Red
            delayed.isNotEmpty() -> DisruptionSeverity.Amber
            else -> DisruptionSeverity.Green
        }

        val headline = when (severity) {
            DisruptionSeverity.Green -> "Good service"
            DisruptionSeverity.Amber -> {
                if (maxDelay > 0) "Delays of up to $maxDelay min"
                else "Minor delays"
            }
            DisruptionSeverity.Red -> when {
                cancelled.isNotEmpty() && delayed.isNotEmpty() ->
                    "${cancelled.size} cancelled · delays up to $maxDelay min"
                cancelled.isNotEmpty() ->
                    "${cancelled.size} cancelled"
                else ->
                    "Significant delays (up to $maxDelay min)"
            }
        }

        val detail = buildString {
            append("${departures.size} services on the live board")
            if (cancelled.isNotEmpty()) {
                append(". ${cancelled.size} cancelled")
            }
            if (delayed.isNotEmpty()) {
                append(". ${delayed.size} delayed")
                if (maxDelay > 0) append(" (max +$maxDelay min)")
            }
            if (affected.isNotEmpty()) {
                append(". Operators: ${affected.joinToString(", ")}")
            }
            append('.')
        }

        return DisruptionSummary(
            severity = severity,
            headline = headline,
            detail = detail,
            affectedOperators = affected,
            cancelledCount = cancelled.size,
            delayedCount = delayed.size,
            maxDelayMinutes = maxDelay,
        )
    }
}
