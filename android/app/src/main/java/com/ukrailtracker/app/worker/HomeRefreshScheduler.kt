package com.ukrailtracker.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ukrailtracker.app.data.local.datastore.AppPreferencesStore
import java.util.concurrent.TimeUnit

class HomeRefreshScheduler(
    private val context: Context,
) {
    fun scheduleIfEnabled() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HomeRefreshWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            buildRequest(),
        )
    }

    fun applyPreference(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            wm.enqueueUniquePeriodicWork(
                HomeRefreshWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                buildRequest(),
            )
        } else {
            wm.cancelUniqueWork(HomeRefreshWorker.UNIQUE_NAME)
        }
    }

    private fun buildRequest() =
        PeriodicWorkRequestBuilder<HomeRefreshWorker>(
            AppPreferencesStore.DEFAULT_INTERVAL_MINUTES.toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
}
