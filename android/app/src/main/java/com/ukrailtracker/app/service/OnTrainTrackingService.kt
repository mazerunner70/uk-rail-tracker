package com.ukrailtracker.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ukrailtracker.app.MainActivity
import com.ukrailtracker.app.R

/**
 * Foreground service while an M6 on-train session is active.
 * Keeps a persistent notification with destination + countdown so location / haptics
 * remain reliable when the app is backgrounded.
 *
 * Uses [ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION] when runtime location is granted.
 * When the debug GPS simulator is driving positions (no real GPS), starts as
 * [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] so Android does not require
 * location permission. Otherwise refuses to promote to foreground instead of crashing.
 */
class OnTrainTrackingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val destination = intent?.getStringExtra(EXTRA_DESTINATION)
                    ?: getString(R.string.on_train_notification_destination_fallback)
                val minutes = intent?.getIntExtra(EXTRA_MINUTES, -1) ?: -1
                val allowWithoutLocation =
                    intent?.getBooleanExtra(EXTRA_ALLOW_WITHOUT_LOCATION, false) == true
                ensureChannel()
                val notification = buildNotification(destination, minutes)
                if (!enterForeground(notification, allowWithoutLocation)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    /**
     * @return false if we cannot keep a real on-train FGS (caller should [stopSelf]).
     * Always calls [startForeground] on Q+ when invoked after [ContextCompat.startForegroundService],
     * otherwise the system kills the app with a RemoteServiceException.
     */
    private fun enterForeground(notification: Notification, allowWithoutLocation: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
            return true
        }
        val preferred = when {
            hasLocationPermission() -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            allowWithoutLocation -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else -> null
        }
        if (preferred != null) {
            return try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, preferred)
                true
            } catch (e: RuntimeException) {
                Log.w(TAG, "startForeground($preferred) failed — trying specialUse teardown", e)
                promoteThenStop(notification)
                false
            }
        }
        Log.w(TAG, "No location permission and not allowed without it — promote then stop")
        promoteThenStop(notification)
        return false
    }

    /** Satisfy the startForegroundService contract, then drop the notification. */
    private fun promoteThenStop(notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to satisfy startForeground contract", e)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.on_train_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.on_train_notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(destination: String, minutes: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (minutes >= 0) {
            getString(R.string.on_train_notification_text, destination, minutes)
        } else {
            getString(R.string.on_train_notification_text_unknown, destination)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.on_train_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "UkRailTracker"
        private const val CHANNEL_ID = "on_train_tracking"
        private const val NOTIFICATION_ID = 6106
        private const val EXTRA_DESTINATION = "destination"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_ALLOW_WITHOUT_LOCATION = "allow_without_location"
        const val ACTION_STOP = "com.ukrailtracker.app.action.STOP_ON_TRAIN"

        /**
         * @param allowWithoutLocation when true (debug GPS simulator), start as specialUse FGS
         * without runtime location. Ignored when location is already granted.
         */
        fun start(
            context: Context,
            destinationName: String,
            minutesRemaining: Int?,
            allowWithoutLocation: Boolean = false,
        ) {
            val intent = Intent(context, OnTrainTrackingService::class.java).apply {
                putExtra(EXTRA_DESTINATION, destinationName)
                putExtra(EXTRA_MINUTES, minutesRemaining?.toInt() ?: -1)
                putExtra(EXTRA_ALLOW_WITHOUT_LOCATION, allowWithoutLocation)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun update(
            context: Context,
            destinationName: String,
            minutesRemaining: Int?,
            allowWithoutLocation: Boolean = false,
        ) {
            start(context, destinationName, minutesRemaining, allowWithoutLocation)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OnTrainTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            // startService (not foreground) is enough to deliver ACTION_STOP.
            context.startService(intent)
        }
    }
}
