package com.ukrailtracker.app.domain.session.host

import com.ukrailtracker.app.domain.session.SessionReduceContext
import com.ukrailtracker.app.domain.session.SessionWalkUpSupport
import com.ukrailtracker.app.domain.session.toAtSession
import com.ukrailtracker.app.domain.session.toNearSession
import com.ukrailtracker.app.domain.usecase.CommuteEffect
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition

/**
 * Near-station host. Steps up to At or back to Idle from distance events.
 */
class NearHost : SessionHost<CommuteSession.Near> {
    override val typeId: SessionTypeId = SessionTypeId.Near

    private var liveHandoff: SessionHandoff = SessionHandoff()

    override fun handle(
        session: CommuteSession.Near,
        event: CommuteEvent,
        context: SessionReduceContext,
    ): CommuteTransition = when (event) {
        is CommuteEvent.NearStation -> CommuteTransition(event.toNearSession())
        is CommuteEvent.AtStation -> CommuteTransition(event.toAtSession())
        CommuteEvent.LeftStation -> CommuteTransition(CommuteSession.Idle)
        is CommuteEvent.SetOriginOverride -> CommuteTransition(
            session = CommuteSession.AtStation(origin = event.origin),
        )
        is CommuteEvent.OsGeofenceEnter -> CommuteTransition(
            session = session,
            effects = listOf(
                CommuteEffect.NotifyProximity(crs = event.crs, stationName = event.stationName),
            ),
        )
        is CommuteEvent.RestorePin -> SessionWalkUpSupport.restorePin(session, event.journey)
        CommuteEvent.ClearOverride,
        CommuteEvent.PermissionLost,
        CommuteEvent.Dismiss -> CommuteTransition(CommuteSession.Idle)
        CommuteEvent.LeftStation,
        is CommuteEvent.Location -> CommuteTransition(session)
        else -> CommuteTransition(session)
    }

    override fun onEntry(
        previousId: SessionTypeId,
        handoff: SessionHandoff,
        session: CommuteSession.Near,
    ): SessionHandoff {
        liveHandoff = handoff
        return liveHandoff
    }

    override fun onActiveChange(
        previous: CommuteSession.Near,
        next: CommuteSession.Near,
        handoff: SessionHandoff,
    ): SessionHandoff {
        liveHandoff = handoff
        return liveHandoff
    }

    override fun onExit(nextId: SessionTypeId): SessionHandoff = liveHandoff
}
