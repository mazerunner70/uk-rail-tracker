# Session State Engine Architecture: Reactive Statechart with Priority Event Reduction

## 1. Decision & Overview

This document defines the target architecture for the commute session state engine in UK Rail Tracker. 

By combining **state-scoped reactive triggers** with **priority-based event reduction**, this architecture delivers:
1. **Zero-Latency User Responsiveness:** User commands (e.g. Dismiss, Confirm) execute immediately without waiting for a polling interval.
2. **Deterministic Race Resolution:** User commands preempt ambient background events (e.g., GPS noise or timer expirations occurring in the same micro-window).
3. **Structured Concurrency:** Background tasks (GPS polling, platform watchers, countdown timers) are automatically launched upon state entry and cancelled upon state exit.
4. **Extensibility & Readability:** Each state explicitly declares its valid onward transitions and active triggers in an isolated handler.

---

## 2. Core Architecture Components

```
+---------------------------------------------------------------------------------------+
|                                    SessionEngine                                      |
|                                                                                       |
|   Current State: S (e.g. Confirming)                                                  |
|   State Lifetime: StateCoroutineScope (cancelled on exit)                             |
|                                                                                       |
|   Active Triggers (running inside State Lifetime):                                    |
|     - Ambient: GPS Flow / Simulator (Low Priority)       ---\                         |
|     - Ambient: Countdown Timer (Medium Priority)         ----+-> [ Event Buffer ]     |
|     - User Tap: Dismiss / Confirm (High Priority)        ---/         |              |
|                                                                       v               |
|                                                              +------------------+     |
|   Immediate flush on High Priority                           | Event Arbitrator |     |
|   OR Micro-tick on Ambient (~250-500ms)                      | (Priority Reducer|     |
|                                                              +------------------+     |
|                                                                       |               |
|                                                             Winning Event (E)         |
|                                                                       v               |
|                                                              +------------------+     |
|                                                              |  State Handler   |     |
|                                                              | S.transition(E)  |     |
|                                                              +------------------+     |
|                                                                       |               |
|                                                           Next State (S_next)         |
|                                                                       v               |
|                                                           1. Cancel State Scope       |
|                                                           2. S = S_next               |
|                                                           3. Launch New State Scope   |
+---------------------------------------------------------------------------------------+
```

---

## 3. Priority Tiers & Event Reduction

All events carry an explicit `EventPriority`. When multiple events accumulate in the buffer during an evaluation slice, the **Event Arbitrator** reduces the batch down to the single authoritative event.

### 3.1. Priority Hierarchy

| Tier | Priority Enum | Event Examples | Invariant / Behavior |
| :--- | :--- | :--- | :--- |
| **1** | `USER_INTENT` | `Dismiss`, `Confirm`, `SelectService`, `EndTrip` | **Always wins.** Flushes buffer immediately ($0\text{ms}$ delay). Invalidates competing ambient/sensor events. |
| **2** | `SYSTEM_CRITICAL` | `PermissionLost`, `PinCleared` | Hard system boundary changes that force reset. |
| **3** | `DEADLINE_TIMEOUT` | `AutoAcceptExpired`, `DwellTimeout` | Time-based thresholds reached. |
| **4** | `AMBIENT_SENSOR` | `GpsFix`, `GeofenceEnter`, `DarwinPollResult` | Coalesced/dropped if a higher-priority event is present. |

### 3.2. Arbitration Rules

1. **Higher tier preempts lower tier:** If a batch contains `[GpsFix(Departed), Dismiss]`, `Dismiss` wins and `GpsFix` is dropped.
2. **Same-tier resolution:**
   - For `USER_INTENT`: The latest user tap in the batch wins.
   - For `AMBIENT_SENSOR`: Multiple GPS fixes are coalesced down to the newest fix; earlier fixes in the same slice are discarded.

---

## 4. Kotlin Architecture & Reference Implementation

### 4.1. Priority & Event Definitions

```kotlin
package com.ukrailtracker.app.domain.session

enum class EventPriority {
    USER_INTENT,       // Rank 0: Highest
    SYSTEM_CRITICAL,   // Rank 1
    DEADLINE_TIMEOUT,  // Rank 2
    AMBIENT_SENSOR,    // Rank 3: Lowest
}

sealed interface SessionEvent {
    val priority: EventPriority

    // User Intent (Rank 0)
    data object Confirm : SessionEvent { override val priority = EventPriority.USER_INTENT }
    data object Dismiss : SessionEvent { override val priority = EventPriority.USER_INTENT }
    data class SelectService(val serviceId: String) : SessionEvent { 
        override val priority = EventPriority.USER_INTENT 
    }
    data object EndTrip : SessionEvent { override val priority = EventPriority.USER_INTENT }

    // System Critical (Rank 1)
    data object PermissionLost : SessionEvent { override val priority = EventPriority.SYSTEM_CRITICAL }
    data object PinCleared : SessionEvent { override val priority = EventPriority.SYSTEM_CRITICAL }

    // Timeout (Rank 2)
    data class AutoAcceptExpired(val nowEpochMs: Long) : SessionEvent { 
        override val priority = EventPriority.DEADLINE_TIMEOUT 
    }

    // Ambient Sensor (Rank 3)
    data class GpsFix(
        val lat: Double, 
        val lon: Double, 
        val speedMps: Double?, 
        val timestampEpochMs: Long
    ) : SessionEvent {
        override val priority = EventPriority.AMBIENT_SENSOR
    }
    data class GeofenceEnter(val crs: String) : SessionEvent {
        override val priority = EventPriority.AMBIENT_SENSOR
    }
}
```

### 4.2. States & Transition Result

```kotlin
sealed interface SessionState {
    data object ColdStart : SessionState
    data object Idle : SessionState
    data class NearStation(val crs: String, val enteredAtEpochMs: Long) : SessionState
    data class AtStation(val originCrs: String) : SessionState
    data class Confirming(
        val originCrs: String,
        val assumedServiceId: String,
        val autoAcceptDeadlineEpochMs: Long
    ) : SessionState
    data class OnTrain(
        val serviceId: String,
        val destinationCrs: String
    ) : SessionState
}

data class StateTransitionResult(
    val nextState: SessionState,
    val sideEffects: List<SessionSideEffect> = emptyList(),
)

sealed interface SessionSideEffect {
    data class Vibrate(val pattern: LongArray) : SessionSideEffect
    data class ShowNotification(val title: String, val body: String) : SessionSideEffect
    data class StartForegroundTracking(val serviceId: String) : SessionSideEffect
    data object StopForegroundTracking : SessionSideEffect
}
```

### 4.3. Pure Event Arbitrator (The Multi-Event Reducer)

```kotlin
object EventArbitrator {
    /**
     * Reduces a batch of candidate events down to the single event that should execute.
     */
    fun arbitrate(events: List<SessionEvent>): SessionEvent? {
        if (events.isEmpty()) return null
        if (events.size == 1) return events.first()

        // 1. Group by priority tier
        val topPriority = events.minByOrNull { it.priority.ordinal }!!.priority
        val topTierEvents = events.filter { it.priority == topPriority }

        // 2. Intra-tier arbitration
        return when (topPriority) {
            EventPriority.USER_INTENT -> topTierEvents.last() // Latest user tap wins
            EventPriority.SYSTEM_CRITICAL -> topTierEvents.first()
            EventPriority.DEADLINE_TIMEOUT -> topTierEvents.first()
            EventPriority.AMBIENT_SENSOR -> {
                // Coalesce: if multiple GPS fixes arrived, take the newest
                topTierEvents.filterIsInstance<SessionEvent.GpsFix>().lastOrNull()
                    ?: topTierEvents.last()
            }
        }
    }
}
```

### 4.4. State Handler Contract

Each state defines its valid onward transitions and registers its active triggers:

```kotlin
interface StateHandler<S : SessionState> {
    /** Evaluates an event against the current state and returns next transition, or null if ignored. */
    fun transition(current: S, event: SessionEvent): StateTransitionResult?

    /** Activates state-scoped triggers. Automatically cancelled when exiting this state. */
    fun activateTriggers(
        current: S,
        scope: CoroutineScope,
        emit: (SessionEvent) -> Unit,
    )
}
```

### 4.5. Concrete Example: `ConfirmingHandler`

```kotlin
class ConfirmingHandler(
    private val autoAcceptDurationMs: Long = 30_000L,
) : StateHandler<SessionState.Confirming> {

    override fun transition(
        current: SessionState.Confirming,
        event: SessionEvent,
    ): StateTransitionResult? = when (event) {
        // User explicitly confirms -> transition to OnTrain
        SessionEvent.Confirm -> StateTransitionResult(
            nextState = SessionState.OnTrain(current.assumedServiceId, destinationCrs = "KGX"),
            sideEffects = listOf(
                SessionSideEffect.StartForegroundTracking(current.assumedServiceId)
            ),
        )
        // User explicitly dismisses -> return to Idle
        SessionEvent.Dismiss -> StateTransitionResult(
            nextState = SessionState.Idle,
        )
        // Auto-accept timeout expired -> auto-transition to OnTrain
        is SessionEvent.AutoAcceptExpired -> {
            if (event.nowEpochMs >= current.autoAcceptDeadlineEpochMs) {
                StateTransitionResult(
                    nextState = SessionState.OnTrain(current.assumedServiceId, destinationCrs = "KGX"),
                    sideEffects = listOf(
                        SessionSideEffect.StartForegroundTracking(current.assumedServiceId)
                    ),
                )
            } else null
        }
        // System boundaries
        SessionEvent.PermissionLost -> StateTransitionResult(nextState = SessionState.Idle)
        // All other events (e.g. ambient GPS fixes) are discarded in Confirming
        else -> null
    }

    override fun activateTriggers(
        current: SessionState.Confirming,
        scope: CoroutineScope,
        emit: (SessionEvent) -> Unit,
    ) {
        // State-scoped timeout trigger
        scope.launch {
            val remainingMs = current.autoAcceptDeadlineEpochMs - System.currentTimeMillis()
            if (remainingMs > 0) {
                delay(remainingMs)
            }
            emit(SessionEvent.AutoAcceptExpired(System.currentTimeMillis()))
        }
    }
}
```

### 4.6. The Session Engine Execution Loop

The engine manages the state, buffers events, flushes high-priority events with 0ms delay, and manages coroutine lifecycles:

```kotlin
class SessionEngine(
    private val externalScope: CoroutineScope,
    private val handlers: Map<Class<out SessionState>, StateHandler<out SessionState>>,
    private val effectDispatcher: (SessionSideEffect) -> Unit,
    private val ambientMicroTickMs: Long = 300L,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.ColdStart)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val pendingEvents = Collections.synchronizedList(mutableListOf<SessionEvent>())
    private val flushSignal = Channel<Unit>(Channel.CONFLATED)
    private var stateScope: CoroutineScope? = null

    init {
        // 1. Processing Loop
        externalScope.launch {
            while (isActive) {
                // Wait for either a high-priority flush or the ambient micro-tick interval
                withTimeoutOrNull(ambientMicroTickMs) {
                    flushSignal.receive()
                }
                processPendingEvents()
            }
        }

        // 2. Initialise ColdStart
        startStateTriggers(_state.value)
    }

    fun dispatch(event: SessionEvent) {
        pendingEvents.add(event)
        if (event.priority == EventPriority.USER_INTENT || 
            event.priority == EventPriority.SYSTEM_CRITICAL) {
            // Immediate flush: user commands do not wait for the micro-tick
            flushSignal.trySend(Unit)
        }
    }

    private fun processPendingEvents() {
        val batch: List<SessionEvent>
        synchronized(pendingEvents) {
            if (pendingEvents.isEmpty()) return
            batch = ArrayList(pendingEvents)
            pendingEvents.clear()
        }

        val winningEvent = EventArbitrator.arbitrate(batch) ?: return
        val currentState = _state.value

        @Suppress("UNCHECKED_CAST")
        val handler = handlers[currentState::class.java] as? StateHandler<SessionState> ?: return
        val result = handler.transition(currentState, winningEvent)

        if (result != null && result.nextState != currentState) {
            // 1. Cancel active triggers for outgoing state
            stateScope?.cancel()

            // 2. Update state
            _state.value = result.nextState

            // 3. Dispatch side-effects
            result.sideEffects.forEach(effectDispatcher)

            // 4. Start triggers for incoming state
            startStateTriggers(result.nextState)
        }
    }

    private fun startStateTriggers(state: SessionState) {
        @Suppress("UNCHECKED_CAST")
        val handler = handlers[state::class.java] as? StateHandler<SessionState> ?: return
        val newScope = CoroutineScope(externalScope.coroutineContext + Job())
        stateScope = newScope
        handler.activateTriggers(state, newScope) { event ->
            dispatch(event)
        }
    }
}
```

---

## 5. Walkthrough of Critical Edge Cases

### Case 1: Ambient GPS Leap vs. User Taps "Dismiss"
1. **$t = 0\text{ms}$:** GPS sensor delivers an erroneous fix outside the station (`GpsFix`, priority `AMBIENT_SENSOR`). It enters `pendingEvents`.
2. **$t = 12\text{ms}$:** User taps "Dismiss" (`Dismiss`, priority `USER_INTENT`).
3. **Dispatch:** Because `Dismiss` has priority `USER_INTENT`, it immediately triggers `flushSignal.trySend(Unit)`.
4. **Arbitration:** `EventArbitrator.arbitrate([GpsFix, Dismiss])` runs:
   - `Dismiss` (Tier 1) trumps `GpsFix` (Tier 4).
   - `GpsFix` is dropped.
5. **Outcome:** State transitions cleanly from `Confirming` $\to$ `Idle`. Zero UI stutter; false GPS leap is eliminated.

### Case 2: Auto-Accept Expiration vs. User Taps "Dismiss"
1. **$t = 0\text{ms}$:** Auto-accept timer fires `AutoAcceptExpired` (priority `DEADLINE_TIMEOUT`).
2. **$t = 10\text{ms}$:** User taps "Dismiss" (priority `USER_INTENT`) having just seen the prompt.
3. **Arbitration:** Batch contains `[AutoAcceptExpired, Dismiss]`.
   - `USER_INTENT` trumps `DEADLINE_TIMEOUT`.
   - `Dismiss` wins.
4. **Outcome:** User intent prevails. The trip is dismissed rather than erroneously pinned.

---

## 6. Benefits for Readability, Maintenance & Extensibility

1. **Readability:**
   - Any developer can open `ConfirmingHandler.kt` and immediately see all valid onward states without wading through unrelated GPS math or platform watch logic.
2. **Maintenance:**
   - All race condition resolution is localized to `EventArbitrator`. There are no scattered `if (recentlyTapped)` flags or timing hacks.
3. **Extensibility:**
   - Adding a new state (e.g., `DisruptionReroute`) only requires creating `DisruptionRerouteHandler` and registering it in the engine. Existing handlers remain untouched.
   - Adding a new sensor (e.g. Activity Recognition API or Bluetooth Beacon) only requires defining an event with `AMBIENT_SENSOR` priority.
4. **Testability:**
   - `EventArbitratorTest`: Pure unit tests verifying priority matrices without mocking coroutines or Android frameworks.
   - `StateHandlerTest`: Pure unit tests verifying state transitions for each handler in isolation.
