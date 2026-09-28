package com.ukrailtracker.app.domain.session.host

import com.ukrailtracker.app.domain.session.SessionReduceContext
import com.ukrailtracker.app.domain.session.SessionWalkUpSupport
import com.ukrailtracker.app.domain.commute.isConfirming
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Confirming (boarding prompt) host.
 *
 * onEntry starts the auto-accept tick; onExit cancels it.
 * Same-type updates (picker flag) need no job changes.
 */
class ConfirmingHost(
    private val services: SessionHostServices? = null,
) : SessionHost<CommuteSession.Confirming> {
    override val typeId: SessionTypeId = SessionTypeId.Confirming

    private var liveHandoff: SessionHandoff = SessionHandoff()
    private var boardingTickJob: Job? = null

    override fun handle(
        session: CommuteSession.Confirming,
        event: CommuteEvent,
        context: SessionReduceContext,
    ): CommuteTransition = when (event) {
        is CommuteEvent.Confirm -> pinAssumed(session, event.nowEpochMs)
        is CommuteEvent.SelectService -> pinSelected(session, event.serviceId, event.nowEpochMs)
        CommuteEvent.ShowPicker -> CommuteTransition(session.copy(showPicker = true))
        CommuteEvent.Dismiss,
        CommuteEvent.ClearOverride,
        CommuteEvent.PermissionLost -> CommuteTransition(CommuteSession.Idle)
        is CommuteEvent.Tick -> {
            if (event.nowEpochMs < session.triggeredAtEpochMs + context.boardingConfig.autoAcceptMillis) {
                CommuteTransition(session)
            } else {
                pinAssumed(session, event.nowEpochMs)
            }
        }
        is CommuteEvent.RestorePin -> SessionWalkUpSupport.restorePin(session, event.journey)
        else -> CommuteTransition(session)
    }

    override fun onEntry(
        previousId: SessionTypeId,
        handoff: SessionHandoff,
        session: CommuteSession.Confirming,
    ): SessionHandoff {
        liveHandoff = handoff
        startBoardingTick(session)
        return liveHandoff
    }

    override fun onActiveChange(
        previous: CommuteSession.Confirming,
        next: CommuteSession.Confirming,
        handoff: SessionHandoff,
    ): SessionHandoff {
        liveHandoff = handoff
        return liveHandoff
    }

    override fun onExit(nextId: SessionTypeId): SessionHandoff {
        cancelBoardingTick()
        return liveHandoff
    }

    private fun pinAssumed(session: CommuteSession.Confirming, nowEpochMs: Long): CommuteTransition =
        SessionWalkUpSupport.pinSnapshot(session.assumed, nowEpochMs)

    private fun pinSelected(
        session: CommuteSession.Confirming,
        serviceId: String,
        nowEpochMs: Long,
    ): CommuteTransition {
        val chosen = sequenceOf(session.assumed)
            .plus(session.alternatives)
            .firstOrNull { it.serviceId == serviceId }
            ?: return CommuteTransition(session)
        return SessionWalkUpSupport.pinSnapshot(chosen, nowEpochMs)
    }

    private fun startBoardingTick(confirming: CommuteSession.Confirming) {
        val svc = services ?: return
        cancelBoardingTick()
        val deadline = confirming.triggeredAtEpochMs + svc.boardingConfig.autoAcceptMillis
        boardingTickJob = svc.scope.launch {
            delay((deadline - System.currentTimeMillis()).coerceAtLeast(0L))
            if (svc.sessionFlow.value.isConfirming()) {
                svc.dispatch(CommuteEvent.Tick(System.currentTimeMillis()))
            }
        }
    }

    private fun cancelBoardingTick() {
        boardingTickJob?.cancel()
        boardingTickJob = null
    }
}
