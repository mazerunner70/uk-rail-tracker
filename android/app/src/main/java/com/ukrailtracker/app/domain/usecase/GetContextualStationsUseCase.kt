package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.CommuteWindow
import java.time.Clock
import java.time.LocalDateTime

/**
 * Picks 1–3 stations for the Home screen from active commute windows,
 * then favourites as fill-ins.
 */
class GetContextualStationsUseCase(
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun select(
        windows: List<CommuteWindow>,
        favouriteCrs: List<String>,
        limit: Int = 3,
    ): List<String> {
        val now = LocalDateTime.now(clock)
        val active = windows
            .filter { it.isActiveAt(now) }
            .map { it.stationCrs.trim().uppercase() }
            .filter { it.isNotBlank() }

        val favourites = favouriteCrs
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }

        val ordered = LinkedHashSet<String>()
        active.forEach { ordered.add(it) }
        favourites.forEach { ordered.add(it) }
        return ordered.take(limit)
    }
}
