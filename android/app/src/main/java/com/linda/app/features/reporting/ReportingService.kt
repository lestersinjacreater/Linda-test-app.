package com.linda.app.features.reporting

import android.content.Context
import com.linda.app.LindaApp
import com.linda.app.core.data.ReportQueueEntity
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.core.util.Prefs
import com.linda.app.features.detection.Verdict

/** Decides whether a verdict is reported, puts it in the offline queue, and wakes the sender. */
object ReportingService {
    suspend fun maybeEnqueue(context: Context, verdict: Verdict, sender: String?, receivedAt: Long, detectionId: Long) {
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
        app.database.detectionDao().markReported(detectionId, System.currentTimeMillis()) // so the Report button says "Reported"
        ReportWorker.schedule(context)
    }

    /** The Report button: the person has seen and confirmed [payload] (see ManualReport). It is sent even if automatic reporting is off. */
    suspend fun enqueueManual(context: Context, detectionId: Long, payload: ReportPayload) {
        val app = context.applicationContext as LindaApp
        app.database.reportQueueDao().insert(
            ReportQueueEntity(
                sender = payload.sender, category = payload.category, confidence = payload.confidence,
                fingerprint = payload.fingerprint, modelVersion = payload.modelVersion, sentAt = payload.sentAt, userConfirmed = true,
            ),
        )
        app.database.detectionDao().markReported(detectionId, System.currentTimeMillis())
        ReportWorker.schedule(context)
    }
}
