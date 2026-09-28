package com.ukrailtracker.app.domain.session

import com.ukrailtracker.app.domain.session.host.SessionHostRegistry
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition

/**
 * Top-level pure session reducer. Routes the incoming [event] to the matching
 * [com.ukrailtracker.app.domain.session.host.SessionHost].
 */
object SessionReducer {
    private val defaultHosts = SessionHostRegistry()

    fun reduce(
        session: CommuteSession,
        event: CommuteEvent,
        context: SessionReduceContext = SessionReduceContext(),
        hosts: SessionHostRegistry = defaultHosts,
    ): CommuteTransition = hosts.reduce(session, event, context)
}
