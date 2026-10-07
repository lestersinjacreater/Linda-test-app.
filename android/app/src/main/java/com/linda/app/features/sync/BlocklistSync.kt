package com.linda.app.features.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.linda.app.LindaApp
import com.linda.app.core.data.BlockedNumberEntity
import com.linda.app.core.net.Http
import com.linda.app.core.util.Prefs
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object BlocklistSync {
    /** Downloads the changes and stores them. Returns false when the radar cannot be reached (offline is normal). */
    suspend fun run(context: Context): Boolean {
        val server = Prefs.serverUrl(context)
        if (server.isBlank()) return false
        val since = Prefs.lastSyncAsOf(context)?.let { "?since=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        val response = Http.get("$server/v1/blocklist$since") ?: return false
        if (response.code != 200) return false
        val delta = try { BlocklistParser.parse(response.body) } catch (e: Exception) { return false }
        val dao = (context.applicationContext as LindaApp).database.blockedNumberDao()
        dao.insertAll(delta.added.map { BlockedNumberEntity(it.msisdn, it.category) })
        if (delta.removed.isNotEmpty()) dao.deleteAll(delta.removed)
        Prefs.setLastSync(context, delta.asOf, System.currentTimeMillis())
        return true
    }
}

/** Runs the sync in the background. Works from the last sync when the phone is offline. */
class BlocklistSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (BlocklistSync.run(applicationContext)) Result.success() else Result.retry()

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 15 minutes (Android's minimum), plus once right now. Called when the app starts. */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                "blocklist_sync_periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BlocklistSyncWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
            )
            syncNow(context)
        }

        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "blocklist_sync_now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BlocklistSyncWorker>()
                    .setConstraints(online).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
            )
        }
    }
}
