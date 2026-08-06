package com.ukrailtracker.app.worker

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ukrailtracker.app.appContainer
import com.ukrailtracker.app.domain.usecase.GetContextualStationsUseCase

/**
 * Refreshes departure boards for contextual (commute + favourite) stations.
 * Disruption severity is derived from those boards on next Home open.
 */
class HomeRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer()
        if (!container.appPreferencesStore.isBackgroundRefreshEnabled()) {
            return Result.success()
        }

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isPowerSaveMode) {
            Log.i(TAG, "Skipping refresh — battery saver on")
            return Result.success()
        }

        return try {
            val windows = container.commuteWindowStore.getWindows()
            val favourites = container.favouritesStore.getFavourites()
            val crsList = GetContextualStationsUseCase().select(
                windows = windows,
                favouriteCrs = favourites,
                limit = 5,
            )
            if (crsList.isEmpty()) return Result.success()

            container.stationRepository.ensureImported()
            crsList.forEach { crs ->
                runCatching {
                    container.departureRepository.getBoard(crs, forceRefresh = true)
                }.onFailure {
                    Log.w(TAG, "Background board refresh failed for $crs: ${it.message}")
                }
            }
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Background refresh failed: ${t.message}")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "home_board_refresh"
        private const val TAG = "UkRailTracker"
    }
}
