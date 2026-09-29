package com.ukrailtracker.app.domain.session.host

import com.ukrailtracker.app.domain.session.SessionReduceContext
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition

/**
 * Owns reduce + enter/exit lifecycle for one [SessionTypeId].
 *
 * - [onEntry] starts long-running work and builds live context from the previous type's handoff.
 * - [onExit] tears that work down and returns a final handoff for the next type.
 * - [onActiveChange] handles same-type key changes (CRS, pin serviceId, platform watch)
 *   without exiting the type.
 */
interface SessionHost<S : CommuteSession> {
    val typeId: SessionTypeId

    fun handle(
        session: S,
        event: CommuteEvent,
        context: SessionReduceContext,
    ): CommuteTransition

    fun onEntry(
        previousId: SessionTypeId,
        handoff: SessionHandoff,
        session: S,
    ): SessionHandoff

    fun onActiveChange(
        previous: S,
        next: S,
        handoff: SessionHandoff,
    ): SessionHandoff

    fun onExit(nextId: SessionTypeId): SessionHandoff
}
