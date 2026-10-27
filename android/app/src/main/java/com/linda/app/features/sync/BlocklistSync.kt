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
import com.linda.app.features.calls.VerifiedCallers
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object BlocklistSync {
    /** Downloads the hashed changes (contract 5.11) and stores them. Returns false when the gateway cannot be reached (offline is normal). */
    suspend fun run(context: Context): Boolean {
        val server = Prefs.serverUrl(context)
        if (server.isBlank()) return false
        val since = Prefs.lastSyncAsOf(context)?.let { "?since=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        val response = Http.get("$server/v1/sync$since", headers = PrivateLookup.headers(context)) ?: return false
        if (response.code != 200) return false
        val delta = try { BlocklistParser.parse(response.body) } catch (e: Exception) { return false }
        val dao = (context.applicationContext as LindaApp).database.blockedNumberDao()
        dao.insertAll(delta.added.map { BlockedNumberEntity(it.hash, it.category, it.label, it.labelSw) })
        if (delta.removed.isNotEmpty()) dao.deleteAll(delta.removed)
        Prefs.setLastSync(context, delta.asOf, System.currentTimeMillis())
        syncVerifiedCallers(context, server)
        return true
    }

    /** Refreshes the verified-callers list (contract 5.13). Failure is fine: the bundled copy still works. */
    private fun syncVerifiedCallers(context: Context, server: String) {
        val response = Http.get("$server/v1/verified-callers") ?: return
        if (response.code == 200) VerifiedCallers.store(context, response.body)
    }
}

/** Runs the sync in the background. Works from the last sync when the phone is offline. */
class BlocklistSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when {
        // No server address in this build: nothing to do, and nothing to retry (retrying would loop all day).
        Prefs.serverUrl(applicationContext).isBlank() -> Result.success()
        BlocklistSync.run(applicationContext) -> Result.success()
        else -> Result.retry()
    }

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

        /** Opening the app syncs again only when the last good sync is older than this (saves data and battery). */
        const val OPEN_SYNC_GAP_MS = 5 * 60 * 1000L

        fun dueOnOpen(lastSyncAt: Long, now: Long): Boolean = now - lastSyncAt >= OPEN_SYNC_GAP_MS

        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "blocklist_sync_now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BlocklistSyncWorker>()
                    .setConstraints(online).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
            )
        }
    }
}
