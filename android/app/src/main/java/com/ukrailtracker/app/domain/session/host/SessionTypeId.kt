package com.ukrailtracker.app.domain.session.host

import com.ukrailtracker.app.domain.usecase.CommuteSession

/** Type-level session identity for enter/exit host swaps. */
enum class SessionTypeId {
    Idle,
    Near,
    AtStation,
    Confirming,
    OnTrain,
}

fun CommuteSession.typeId(): SessionTypeId = when (this) {
    CommuteSession.Idle -> SessionTypeId.Idle
    is CommuteSession.Near -> SessionTypeId.Near
    is CommuteSession.AtStation -> SessionTypeId.AtStation
    is CommuteSession.Confirming -> SessionTypeId.Confirming
    is CommuteSession.OnTrain -> SessionTypeId.OnTrain
}
