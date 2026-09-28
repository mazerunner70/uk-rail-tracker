package com.ukrailtracker.app.location.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.ukrailtracker.app.appContainer
import com.ukrailtracker.app.domain.session.SessionReducer
import com.ukrailtracker.app.domain.usecase.CommuteEffect
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives OS geofence ENTER, reduces [CommuteEvent.OsGeofenceEnter], and runs
 * [CommuteEffect.NotifyProximity]. Does not set walk-up presence.
 */
class GeofenceTransitionsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != GeofenceIntents.ACTION_GEOFENCE_TRANSITION) return
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Log.w(TAG, "GeofencingEvent missing")
            return
        }
        if (event.hasError()) {
            Log.w(TAG, "GeofencingEvent error=${event.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                val container = appContext.appContainer()
                val pin = container.pinnedJourneyStore.getPinned()
                val session = if (pin != null) {
                    CommuteSession.OnTrain(pin = pin)
                } else {
                    CommuteSession.Idle
                }
                val notifier = GeofenceProximityNotifier(appContext)
                for (geofence in event.triggeringGeofences.orEmpty()) {
                    val crs = StationGeofenceIds.crsFromRequestId(geofence.requestId) ?: continue
                    val station = container.stationRepository.getByCrs(crs)
                    val name = station?.name?.ifBlank { crs } ?: crs
                    val transition = SessionReducer.reduce(
                        session,
                        CommuteEvent.OsGeofenceEnter(crs = crs, stationName = name),
                    )
                    for (effect in transition.effects) {
                        when (effect) {
                            is CommuteEffect.NotifyProximity ->
                                notifier.notifyEnter(effect.crs, effect.stationName)
                            else -> Unit
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Geofence ENTER handling failed: ${t.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UkRailTracker"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
