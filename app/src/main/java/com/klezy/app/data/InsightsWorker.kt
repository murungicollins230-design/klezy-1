package com.klezy.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class InsightsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val apiKey = ApiKeyStore.getGroqKey(applicationContext) ?: return Result.success()
        val uid = AuthManager.ensureSignedIn()
        InsightsEngine(apiKey).refresh(uid)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "klezy_daily_insights"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<InsightsWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
