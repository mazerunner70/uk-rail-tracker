package com.ukrailtracker.app.domain.session

import com.ukrailtracker.app.domain.usecase.CommuteEvent

/**
 * Pure multi-event reducer that resolves competing [CommuteEvent]s accumulated during
 * an evaluation slice into a single winning event to process against the session state.
 */
object EventArbitrator {

    /**
     * Arbitrates a batch of events by priority tier:
     * 1. [EventPriority.USER_INTENT] always wins over lower priorities.
     * 2. [EventPriority.SYSTEM_CRITICAL] wins over timeouts and ambient updates.
     * 3. [EventPriority.DEADLINE_TIMEOUT] wins over ambient sensor updates.
     * 4. Intra-tier conflicts:
     *    - [EventPriority.USER_INTENT]: Latest user command wins.
     *    - [EventPriority.AMBIENT_SENSOR]: A presence edge (Near / At / Left) wins over a
     *      GPS [CommuteEvent.Location] in the same slice; otherwise the newest location wins.
     */
    fun arbitrate(events: List<CommuteEvent>): CommuteEvent? {
        if (events.isEmpty()) return null
        if (events.size == 1) return events.first()

        val topPriority = events.minBy { it.priority.ordinal }.priority
        val topTierEvents = events.filter { it.priority == topPriority }

        return when (topPriority) {
            EventPriority.USER_INTENT -> topTierEvents.last()
            EventPriority.SYSTEM_CRITICAL -> topTierEvents.first()
            EventPriority.DEADLINE_TIMEOUT -> topTierEvents.last()
            EventPriority.AMBIENT_SENSOR -> {
                topTierEvents.lastOrNull { it.isPresenceEdge() }
                    ?: topTierEvents.filterIsInstance<CommuteEvent.Location>().lastOrNull()
                    ?: topTierEvents.last()
            }
        }
    }
}

private fun CommuteEvent.isPresenceEdge(): Boolean =
    this is CommuteEvent.NearStation ||
        this is CommuteEvent.AtStation ||
        this is CommuteEvent.LeftStation
