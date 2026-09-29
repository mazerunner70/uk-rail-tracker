package com.ukrailtracker.app.domain.session

import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.usecase.BoardingPhase
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteEffect
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.initialWaitingAtOrigin

internal object SessionWalkUpSupport {

    fun pinSnapshot(snapshot: BoardingServiceSnapshot, nowEpochMs: Long): CommuteTransition {
        val pin = snapshot.toPinned(nowEpochMs)
        return CommuteTransition(
            session = CommuteSession.OnTrain(
                pin = pin,
                ridePhase = initialWaitingAtOrigin(originName = pin.originName),
            ),
            effects = listOf(CommuteEffect.Pin(pin)),
        )
    }

    fun BoardingServiceSnapshot.toPinned(nowEpochMs: Long): PinnedJourney =
        PinnedJourney(
            serviceId = serviceId,
            originCrs = originCrs,
            destinationCrs = destinationCrs,
            originName = originName,
            destinationName = destinationName,
            operatorName = operatorName,
            scheduledDepartureLabel = scheduledDepartureLabel,
            pinnedAtEpochMs = nowEpochMs,
        )

    fun restorePin(session: CommuteSession, journey: PinnedJourney): CommuteTransition {
        val current = session as? CommuteSession.OnTrain
        if (current != null && current.pin.serviceId == journey.serviceId) {
            return CommuteTransition(session)
        }
        return CommuteTransition(
            session = CommuteSession.OnTrain(
                pin = journey,
                ridePhase = initialWaitingAtOrigin(originName = journey.originName),
            ),
        )
    }
}

internal fun CommuteEvent.NearStation.toNearSession(): CommuteSession.Near =
    CommuteSession.Near(
        crs = crs,
        name = name,
        latitude = latitude,
        longitude = longitude,
        distanceMetres = distanceMetres,
    )

internal fun CommuteEvent.AtStation.toAtSession(): CommuteSession.AtStation =
    CommuteSession.AtStation(
        origin = LockedOrigin(
            crs = crs,
            name = name,
            latitude = latitude,
            longitude = longitude,
            lockedAtEpochMs = epochMs,
            distanceMetres = distanceMetres,
        ),
        boarding = BoardingPhase.Idle,
    )
