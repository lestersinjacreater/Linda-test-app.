package com.linda.app

import android.app.Application
import com.linda.app.core.data.LindaDatabase
import com.linda.app.features.detection.LindaDetector
import com.linda.app.features.detection.ModelScorer
import com.linda.app.features.detection.ScamDetector
import com.linda.app.features.learning.LearningStore
import com.linda.app.features.sync.BlocklistSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class. It owns the one Room database and the one detector so every screen, and the
 * SMS receiver, share the same instances.
 */
class LindaApp : Application() {
    val database: LindaDatabase by lazy { LindaDatabase.create(this) }

    /** Loaded once from assets/model.json (about 87 KB). Everything that scores a message goes through this. */
    val detector: ScamDetector by lazy {
        val json = assets.open("model.json").bufferedReader().use { it.readText() }
        LindaDetector(ModelScorer(json))
    }

    override fun onCreate() {
        super.onCreate()
        com.linda.app.features.demo.DemoOverlay.init(this)
        BlocklistSyncWorker.schedule(this) // keeps the offline list of confirmed scam numbers fresh
        // M-Pesa memory: once, read the last 30 days of M-Pesa messages (only if the person allowed inbox reading).
        appScope.launch { LearningStore.backfillOnce(this@LindaApp) }
    }

    /** For short one-off jobs that should not hold up app start. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
