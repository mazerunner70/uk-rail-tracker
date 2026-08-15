package com.ukrailtracker.app.location.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ukrailtracker.app.R

/**
 * Posts a one-shot "near station" notification for OS geofence ENTER.
 * Tap opens Home with elevated location polling.
 */
class GeofenceProximityNotifier(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun notifyEnter(crs: String, stationName: String) {
        val normalised = crs.trim().uppercase()
        if (normalised.isEmpty()) return
        if (wasRecentlyNotified(normalised)) return

        ensureChannel()
        val title = appContext.getString(R.string.geofence_notification_title)
        val text = appContext.getString(
            R.string.geofence_notification_text,
            stationName.ifBlank { normalised },
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(
                GeofenceIntents.openHomeFrequentChecksPendingIntent(
                    appContext,
                    normalised,
                    stationName,
                ),
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(
                notificationId(normalised),
                notification,
            )
            markNotified(normalised)
            vibrateEnter()
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied on API 33+.
        }
    }

    private fun wasRecentlyNotified(crs: String): Boolean {
        val last = prefs.getLong(keyLast(crs), 0L)
        return System.currentTimeMillis() - last < COOLDOWN_MS
    }

    private fun markNotified(crs: String) {
        prefs.edit().putLong(keyLast(crs), System.currentTimeMillis()).apply()
    }

    private fun keyLast(crs: String) = "last_$crs"

    private fun notificationId(crs: String): Int = 7200 + (crs.hashCode() and 0x0FFF)

    private fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.geofence_notification_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appContext.getString(R.string.geofence_notification_channel_desc)
            enableVibration(true)
            vibrationPattern = VIBRATE_PATTERN
        }
        manager.createNotificationChannel(channel)
    }

    private fun vibrateEnter() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATE_PATTERN, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATE_PATTERN, -1)
        }
    }

    companion object {
        private const val CHANNEL_ID = "station_geofence"
        private const val PREFS = "station_geofence_notify"
        private const val COOLDOWN_MS = 30 * 60_000L
        private val VIBRATE_PATTERN = longArrayOf(0, 160, 90, 220)
    }
}
