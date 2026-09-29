package com.ukrailtracker.app.domain.session

/**
 * Priority tiers for arbitrating events that arrive during the same evaluation slice.
 *
 * Lower [ordinal] values represent higher priority.
 */
enum class EventPriority {
    /** Explicit user commands (e.g. Confirm, Dismiss, EndTrip). Always preempt ambient events. */
    USER_INTENT,

    /** Hard system boundaries (e.g. PermissionLost, PinCleared). */
    SYSTEM_CRITICAL,

    /** Time-based deadline expirations (e.g. Tick / auto-accept timeouts). */
    DEADLINE_TIMEOUT,

    /** Ambient hardware or network updates (e.g. GPS fixes, GeofenceEnter, ServiceDetails). */
    AMBIENT_SENSOR,
}
