package com.linda.app.features.reporting

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.linda.app.LindaApp
import com.linda.app.core.data.ReportQueueEntity
import com.linda.app.core.net.Http
import com.linda.app.core.util.Prefs
import java.util.concurrent.TimeUnit

/**
 * Sends queued reports to the radar. Works offline: reports wait in the database and the system
 * retries with growing delays until the radar can be reached. If the radar is down or no server
 * is set, nothing is lost and nothing else in Linda is affected.
 */
class ReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val server = Prefs.serverUrl(applicationContext)
        if (server.isBlank()) return Result.success() // nowhere to send yet; reports stay queued
        if (!Prefs.reportingConsent(applicationContext)) return Result.success() // user turned reporting off
        val queue = (applicationContext as LindaApp).database.reportQueueDao()

        for (item in queue.pending(50)) {
            val response = Http.postJson("$server/v1/reports", item.toPayload(Prefs.deviceId(applicationContext)).toJson())
            when {
                response == null || response.code == 429 || response.code >= 500 -> {
                    queue.countAttempt(item.id)
                    return Result.retry() // radar unreachable or busy: try again later, keep the rest queued
                }
                else -> queue.delete(item.id) // sent (2xx), or rejected for good (4xx): do not loop on it
            }
        }
        return Result.success()
    }

    private fun ReportQueueEntity.toPayload(deviceId: String) =
        ReportPayload(sender, category, confidence, fingerprint, modelVersion, deviceId, sentAt)

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReportWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("send_reports", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
