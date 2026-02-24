package ru.allisighs.funpaytools

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import java.util.concurrent.TimeUnit

class WatchdogWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = FunPayRepository(context)
        if (repository.hasAuth() && repository.getSetting("auto_start_on_boot")) {
            val serviceIntent = Intent(context, FunPayService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
            }
        }
        return Result.success()
    }

    companion object {
        fun start(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "FunPayWatchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
        fun startOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<WatchdogWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "UrgentWatchdog",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}