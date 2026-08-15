package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.JourneyLogEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class JourneyHistoryMode {
    Claimable,
    All,
}

/**
 * Filters completed [journey_log] rows for the Journeys History section (M7 v1).
 */
object JourneyHistoryFilter {

    /** Typical Delay Repay claim window. */
    const val CLAIM_WINDOW_DAYS = 56

    fun isClaimable(entry: JourneyLogEntry): Boolean {
        if (entry.wasCancelled) return true
        if ((entry.delayMinutes ?: 0) >= 15) return true
        return entry.claimStatus.equals("eligible", ignoreCase = true)
    }

    fun inClaimWindow(dateIso: String, today: LocalDate = LocalDate.now()): Boolean {
        val date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return false
        val ageDays = ChronoUnit.DAYS.between(date, today)
        return ageDays in 0..CLAIM_WINDOW_DAYS
    }

    fun visible(
        entries: List<JourneyLogEntry>,
        mode: JourneyHistoryMode,
        today: LocalDate = LocalDate.now(),
    ): List<JourneyLogEntry> {
        val inWindow = entries.filter { inClaimWindow(it.dateIso, today) }
        return when (mode) {
            JourneyHistoryMode.Claimable -> inWindow.filter(::isClaimable)
            JourneyHistoryMode.All -> inWindow
        }
    }
}
