package com.ukrailtracker.app.domain.session

import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections

/**
 * Reactive state engine that owns the commute session lifecycle.
 *
 * Implements:
 * 1. Event buffer with priority arbitration: user commands and critical system signals
 *    preempt ambient sensor/timer updates that occur in the same evaluation slice.
 * 2. Immediate zero-latency dispatch for user commands.
 * 3. Coalescing of rapid ambient sensor updates (e.g. GPS fixes).
 *
 * Flow: [dispatch] enqueues events → [processPendingEvents] picks a winner via
 * [EventArbitrator] → [SessionReducer] produces a [CommuteTransition] → session
 * StateFlow updates and [onTransitionApplied] runs side effects.
 */
class SessionEngine(
    private val scope: CoroutineScope,
    initialSession: CommuteSession = CommuteSession.Idle,
    /** Supplies fresh reduce inputs (clock, location helpers, etc.) at reduce time. */
    private val contextProvider: () -> SessionReduceContext,
    /** Invoked after each successful reduce so callers can react (logging, UI, alarms). */
    private val onTransitionApplied: (previous: CommuteSession, transition: CommuteTransition) -> Unit,
    /**
     * How often the background loop re-checks the pending queue when no high-priority
     * flush wakes it. Caps how long coalesced ambient events can sit before reduce.
     */
    private val ambientMicroTickMs: Long = 250L,
    private val reduce: (
        CommuteSession,
        CommuteEvent,
        SessionReduceContext,
    ) -> CommuteTransition = { session, event, context ->
        SessionReducer.reduce(session, event, context)
    },
) {
    private val _sessionFlow = MutableStateFlow(initialSession)
    /** Observable current [CommuteSession]; updated only via reduce or [setSessionDirect]. */
    val sessionFlow: StateFlow<CommuteSession> = _sessionFlow.asStateFlow()

    /** Thread-safe buffer of events waiting for the next arbitration/reduce slice. */
    private val pendingEvents = Collections.synchronizedList(mutableListOf<CommuteEvent>())

    /**
     * Conflated wake-up channel for the flush loop. [Channel.CONFLATED] means multiple
     * wake signals collapse to one — useful if callers start signalling future flushes.
     */
    private val flushSignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * Background flush loop: waits up to [ambientMicroTickMs] for a [flushSignal], then
     * always calls [processPendingEvents]. In practice this acts as a periodic tick that
     * drains ambient events coalesced while higher-rate GPS (etc.) was still arriving.
     */
    init {
        scope.launch {
            while (isActive) {
                withTimeoutOrNull(ambientMicroTickMs) {
                    flushSignal.receive()
                }
                processPendingEvents()
            }
        }
    }

    /**
     * Enqueues a [CommuteEvent] and decides whether to reduce immediately.
     *
     * - [EventPriority.USER_INTENT] / [EventPriority.SYSTEM_CRITICAL]: process now
     *   (zero-latency path for taps, cancels, and critical system signals).
     * - Ambient / other: process immediately only when this event is alone in the queue
     *   (nothing to coalesce with). If other events are already pending, leave them
     *   buffered so the next tick or high-priority dispatch can arbitrate a batch.
     */
    @Synchronized
    fun dispatch(event: CommuteEvent) {
        pendingEvents.add(event)
        if (event.priority == EventPriority.USER_INTENT ||
            event.priority == EventPriority.SYSTEM_CRITICAL
        ) {
            processPendingEvents()
        } else {
            // Sole ambient event → reduce now; otherwise wait to coalesce with the batch.
            if (pendingEvents.size == 1) {
                processPendingEvents()
            }
        }
    }

    /**
     * Reduces [events] one at a time so ambient coalescing cannot drop a presence
     * edge that belongs to the same GPS fix as a [CommuteEvent.Location].
     */
    @Synchronized
    fun dispatchSequential(events: List<CommuteEvent>) {
        for (event in events) {
            pendingEvents.add(event)
            processPendingEvents()
        }
    }

    /**
     * Forces the session to [session] without going through the reducer.
     * Clears any buffered events so a stale pending event cannot overwrite the reset.
     * Intended for debug teardown or explicit hard resets, not normal transitions.
     */
    fun setSessionDirect(session: CommuteSession) {
        synchronized(pendingEvents) {
            pendingEvents.clear()
        }
        _sessionFlow.value = session
    }

    /**
     * Drains the pending queue, arbitrates to a single winning event, reduces the
     * current session against it, then publishes the new session and notifies the caller.
     *
     * Synchronized so concurrent [dispatch] / tick / high-priority flush cannot run two
     * reduces overlapping. Empty queue is a no-op; arbitrator may also drop a batch.
     */
    @Synchronized
    fun processPendingEvents() {
        val batch: List<CommuteEvent>
        synchronized(pendingEvents) {
            if (pendingEvents.isEmpty()) return
            // Snapshot + clear under the list lock so new dispatches start a fresh batch.
            batch = ArrayList(pendingEvents)
            pendingEvents.clear()
        }

        // Prefer user/critical over timeouts/ambient; coalesce peers within the same tier.
        val winningEvent = EventArbitrator.arbitrate(batch) ?: return
        val previous = _sessionFlow.value
        val context = contextProvider()
        val transition = reduce(previous, winningEvent, context)

        _sessionFlow.value = transition.session
        onTransitionApplied(previous, transition)
    }
}
