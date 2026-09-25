package com.example.relevantreasearchupdates.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object WorkScheduler {

    /**
     * Called every app startup. UPDATE (unlike REPLACE) keeps the existing schedule's timing, so
     * opening the app never pushes back the next check — but it does apply any changes to the
     * request itself (constraints, backoff) to a job that was scheduled by an older app version.
     */
    fun ensurePeriodicScheduled(context: Context, intervalHours: Int) {
        enqueuePeriodic(context, intervalHours, ExistingPeriodicWorkPolicy.UPDATE)
    }

    /** Called when the user explicitly changes the poll interval in Settings. */
    fun reschedulePeriodic(context: Context, intervalHours: Int) {
        enqueuePeriodic(context, intervalHours, ExistingPeriodicWorkPolicy.UPDATE)
    }

    private fun enqueuePeriodic(context: Context, intervalHours: Int, policy: ExistingPeriodicWorkPolicy) {
        // Background scans defer to system battery/storage pressure; a manual "check now" (below)
        // does not, since the user explicitly asked for it right now.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<LiteratureCheckWorker>(
            intervalHours.toLong().coerceAtLeast(1), TimeUnit.HOURS
        ).setConstraints(constraints)
            // A failed run (e.g. no connection) retries after a few minutes, not a full interval.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LiteratureCheckWorker.UNIQUE_PERIODIC_NAME,
            policy,
            request
        )
    }

    fun runOnce(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Expedited so a manual check starts immediately and keeps going if you leave the app,
        // instead of sitting in the queue until the system gets around to it. Falls back to
        // normal priority if the app's expedited-job quota is used up.
        val request = OneTimeWorkRequestBuilder<LiteratureCheckWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(LiteratureCheckWorker.KEY_MANUAL to true))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            LiteratureCheckWorker.UNIQUE_ONE_TIME_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
