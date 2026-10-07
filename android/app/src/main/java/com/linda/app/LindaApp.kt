package com.linda.app

import android.app.Application
import com.linda.app.core.data.LindaDatabase
import com.linda.app.features.detection.LindaDetector
import com.linda.app.features.detection.ModelScorer
import com.linda.app.features.detection.ScamDetector
import com.linda.app.features.sync.BlocklistSyncWorker

/**
 * Application class. It owns the one Room database and the one detector so every screen, and the
 * SMS receiver, share the same instances.
 */
class LindaApp : Application() {
    val database: LindaDatabase by lazy { LindaDatabase.create(this) }

    /** Loaded once from assets/model.json (about 360 KB). Everything that scores a message goes through this. */
    val detector: ScamDetector by lazy {
        val json = assets.open("model.json").bufferedReader().use { it.readText() }
        LindaDetector(ModelScorer(json))
    }

    override fun onCreate() {
        super.onCreate()
        BlocklistSyncWorker.schedule(this) // keeps the offline list of confirmed scam numbers fresh
    }
}
