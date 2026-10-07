package com.linda.app.features.reporting

import android.content.Context
import com.linda.app.LindaApp
import com.linda.app.core.data.ReportQueueEntity
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.core.util.Prefs
import com.linda.app.features.detection.Verdict

/** Decides whether a verdict is reported, puts it in the offline queue, and wakes the sender. */
object ReportingService {
    suspend fun maybeEnqueue(context: Context, verdict: Verdict, sender: String?, receivedAt: Long) {
        val app = context.applicationContext as LindaApp
        val verified = app.detector.verifiedSenders
        if (!ReportPolicy.shouldReport(verdict.level, sender, verified, Prefs.reportingConsent(context))) return
        val msisdn = PhoneNumbers.toMsisdn(sender) ?: return
        app.database.reportQueueDao().insert(
            ReportQueueEntity(
                sender = msisdn, category = verdict.category, confidence = verdict.score.toDouble(),
                fingerprint = verdict.fingerprint, modelVersion = verdict.modelVersion, sentAt = receivedAt,
            ),
        )
        ReportWorker.schedule(context)
    }
}
