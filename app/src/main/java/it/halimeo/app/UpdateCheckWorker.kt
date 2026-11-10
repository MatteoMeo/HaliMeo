package it.halimeo.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Checks public GitHub releases independently of the signed-in state and updater UI.
 * WorkManager persists the schedule across app restarts and device reboots.
 */
internal class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("halimeo_preferences", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_check_updates", true)) return Result.success()
        val channel = prefs.getString("update_channel", "alpha") ?: "alpha"
        return try {
            val release = fetchLatestRelease(channel)
            notifyUpdateAvailable(applicationContext, release)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

internal object UpdateCheckScheduler {
    private const val UNIQUE_WORK_NAME = "halimeo_periodic_update_check"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
