package com.klezy.app.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * ScheduledTriggerWorker
 *
 * WorkManager's minimum periodic interval is 15 minutes — that's an Android
 * platform limit, not something this code can shrink. So TimeOfDay triggers
 * are accurate to within ~15 minutes, not to the exact minute.
 *
 * If you need exact-minute firing (e.g. "text mom at 9:00 sharp"), the
 * upgrade path is AlarmManager.setExactAndAllowWhileIdle() scheduled
 * per-rule instead of this poller — more precise, more battery-sensitive,
 * worth adding only if the 15-minute window actually becomes a problem.
 */
class ScheduledTriggerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AutomationEngine.init(applicationContext)
        AutomationEngine.checkTimeTriggers()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "klezy_scheduled_trigger_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduledTriggerWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
