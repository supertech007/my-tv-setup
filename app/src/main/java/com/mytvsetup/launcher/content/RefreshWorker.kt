package com.mytvsetup.launcher.content

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val stamps = applicationContext.getSharedPreferences("content_refresh", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        // Music is small and cheap: refresh it on every run, even when the film cache is still fresh.
        ContentRuntime.refreshMusic(applicationContext)
        // The launcher is restarted often on a 2 GB stick; a startup run skips a fresh cache.
        if (inputData.getBoolean(STARTUP, false) && now - stamps.getLong(LAST_OK, 0) < FRESH_MS) return Result.success()
        if (!ContentRuntime.repository(applicationContext).refresh().isSuccess) return Result.retry()
        stamps.edit().putLong(LAST_OK, now).apply()
        return Result.success()
    }

    companion object {
        private const val STARTUP = "startup"
        private const val LAST_OK = "last_success_at"
        private const val FRESH_MS = 6 * 60 * 60 * 1000L

        fun schedule(context: Context) {
            val manager = WorkManager.getInstance(context)
            val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            manager.enqueueUniquePeriodicWork(
                "content-refresh-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RefreshWorker>(12, TimeUnit.HOURS)
                    .setInitialDelay(12, TimeUnit.HOURS)
                    .setConstraints(network).build()
            )
            manager.enqueueUniqueWork(
                "content-refresh-startup", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(network)
                    .setInputData(workDataOf(STARTUP to true)).build()
            )
        }
    }
}
