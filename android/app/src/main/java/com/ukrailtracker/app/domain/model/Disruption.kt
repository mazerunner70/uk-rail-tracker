package com.ukrailtracker.app.domain.model

enum class DisruptionSeverity {
    /** Services look normal. */
    Green,

    /** Delays present; still mostly running. */
    Amber,

    /** Cancellations or significant delay. */
    Red,
}

/**
 * Station-level disruption summary derived from a live [DepartureBoard]
 * (GetArrDepBoardWithDetails) until a dedicated incidents feed is wired.
 */
data class DisruptionSummary(
    val severity: DisruptionSeverity,
    val headline: String,
    val detail: String,
    val affectedOperators: List<String>,
    val cancelledCount: Int,
    val delayedCount: Int,
    val maxDelayMinutes: Int,
)
