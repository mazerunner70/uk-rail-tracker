package com.ukrailtracker.app.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class GeofenceRearmScheduler(
    private val context: Context,
) {
    fun scheduleIfLocationGranted() {
        if (!hasForegroundLocation()) {
            cancel()
            return
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            GeofenceRearmWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            buildRequest(),
        )
    }

    fun scheduleNow() {
        if (!hasForegroundLocation()) {
            cancel()
            return
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            GeofenceRearmWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildRequest(),
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(GeofenceRearmWorker.UNIQUE_NAME)
    }

    private fun buildRequest() =
        PeriodicWorkRequestBuilder<GeofenceRearmWorker>(
            INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

    private fun hasForegroundLocation(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    companion object {
        /** WorkManager periodic minimum is 15 minutes. */
        const val INTERVAL_MINUTES = 15L
    }
}
