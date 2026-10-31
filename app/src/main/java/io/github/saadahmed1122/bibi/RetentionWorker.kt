package io.github.saadahmed1122.bibi

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.saadahmed1122.bibi.domain.usecase.ApplyRetention
import java.util.concurrent.TimeUnit

/**
 * Daily memory retention (SPEC-memory.md, PRD §14.6): deletes unpinned memories older than the chosen
 * period. Runs only while charging and idle, so it never competes with the user.
 */
class RetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as BibiApplication).container
        ApplyRetention(container.memory)(container.retention.current.value)
        return Result.success()
    }

    companion object {
        private const val NAME = "memory-retention"

        /** Main process, at app start; keeps an existing schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresDeviceIdle(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
