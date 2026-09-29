package io.github.f_e_n_y_x.nebula.nowplaying

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.f_e_n_y_x.nebula.container
import java.util.concurrent.TimeUnit

/**
 * While the user is away from Nebula: asks the paired PCs what they run and keeps the "game
 * running" notification's elapsed time current, every 15 minutes (WorkManager's minimum) on any
 * network. The first run starts as soon as it's scheduled. Stops itself once nothing runs.
 */
class RunningGameWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val center = applicationContext.container.nowPlaying
        if (!center.enabled.value || center.appVisible()) {
            cancel(applicationContext)
            return Result.success()
        }
        val keepWatching = runCatching { center.refreshNotifications() }.getOrDefault(true)
        if (!keepWatching) cancel(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "nebula-running-games"

        /** Starts now (replacing a pending run) and repeats every 15 minutes. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RunningGameWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
            }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
        }
    }
}
