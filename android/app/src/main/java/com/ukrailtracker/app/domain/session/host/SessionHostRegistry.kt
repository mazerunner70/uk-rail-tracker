package com.ukrailtracker.app.domain.session.host

import com.ukrailtracker.app.domain.session.SessionReduceContext
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition

/**
 * Routes reduce to the matching host. Used by [com.ukrailtracker.app.domain.session.SessionReducer]
 * for pure reduce tests, and by [com.ukrailtracker.app.domain.commute.CommuteRuntime] with
 * lifecycle-wired host instances.
 */
class SessionHostRegistry(
    val idle: IdleHost = IdleHost(),
    val near: NearHost = NearHost(),
    val atStation: AtStationHost = AtStationHost(),
    val confirming: ConfirmingHost = ConfirmingHost(),
    val onTrain: OnTrainHost = OnTrainHost(),
) {
    fun reduce(
        session: CommuteSession,
        event: CommuteEvent,
        context: SessionReduceContext,
    ): CommuteTransition = when (session) {
        is CommuteSession.Idle -> idle.handle(session, event, context)
        is CommuteSession.Near -> near.handle(session, event, context)
        is CommuteSession.AtStation -> atStation.handle(session, event, context)
        is CommuteSession.Confirming -> confirming.handle(session, event, context)
        is CommuteSession.OnTrain -> onTrain.handle(session, event, context)
    }

    fun hostFor(typeId: SessionTypeId): SessionHost<*> = when (typeId) {
        SessionTypeId.Idle -> idle
        SessionTypeId.Near -> near
        SessionTypeId.AtStation -> atStation
        SessionTypeId.Confirming -> confirming
        SessionTypeId.OnTrain -> onTrain
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : CommuteSession> typedHost(session: S): SessionHost<S> =
        hostFor(session.typeId()) as SessionHost<S>
}
