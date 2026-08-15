package com.ukrailtracker.app.location.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ukrailtracker.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-schedules geofence re-arm work and re-registers fences after device boot.
 */
class GeofenceBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                val container = appContext.appContainer()
                container.geofenceRearmScheduler.scheduleIfLocationGranted()
                container.stationGeofenceController.armFromCurrentLocation(forceLocationRefresh = true)
            } catch (t: Throwable) {
                Log.w(TAG, "Boot geofence re-arm failed: ${t.message}")
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
