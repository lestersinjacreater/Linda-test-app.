package com.linda.app.features.sms

import android.content.Context
import com.linda.app.LindaApp
import com.linda.app.core.data.DetectionEntity
import com.linda.app.core.data.SeenSenderEntity
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.features.alerts.AlertNotifier
import com.linda.app.features.alerts.VoiceWarnings
import com.linda.app.features.detection.MessageInput
import com.linda.app.features.detection.ReasonsJson
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.Verdict
import com.linda.app.features.guardian.GuardianService
import com.linda.app.features.reporting.ReportingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * What happened to one message. [detectionId] is null when nothing was saved (SAFE, or an allowed sender).
 * [speech] is the voice warning still being read aloud, or null; the SMS receiver waits for it so the app is not stopped mid-sentence.
 */
data class ProcessResult(val verdict: Verdict, val detectionId: Long?, val speech: Job? = null)

/**
 * The single path every message takes: real SMS, pasted text, and demo mode all come through here,
 * so a demo behaves exactly like a real message. Order: allow-list, context, ScamDetector, save, warn.
 */
class MessageProcessor(private val context: Context) {
    private val app = context.applicationContext as LindaApp

    suspend fun process(body: String, sender: String?, receivedAt: Long, source: String, notify: Boolean): ProcessResult =
        withContext(Dispatchers.Default) {
            val senders = app.database.senderDao()
            if (sender != null && senders.isAllowed(sender)) {
                // The user said this sender is fine. Skip scoring (a SAFE verdict with no reasons).
                return@withContext ProcessResult(Verdict(RiskLevel.SAFE, 0f, emptyList(), "allow-list", "none", ""), null)
            }
            val inContacts = ContactsLookup.isInContacts(context, sender)
            val first = sender?.let { !senders.hasSeen(it) }
            val verdict = app.detector.analyse(MessageInput(body, sender, receivedAt, inContacts, first))
            if (sender != null) senders.markSeen(SeenSenderEntity(sender, receivedAt))

            if (verdict.level == RiskLevel.SAFE) return@withContext ProcessResult(verdict, null)

            val id = app.database.detectionDao().insert(
                DetectionEntity(
                    sender = sender, senderMsisdn = PhoneNumbers.toMsisdn(sender), body = body, level = verdict.level.name, score = verdict.score,
                    category = verdict.category, fingerprint = verdict.fingerprint,
                    reasonsJson = ReasonsJson.encode(verdict.reasons), modelVersion = verdict.modelVersion,
                    receivedAt = receivedAt, source = source,
                ),
            )
            if (notify) AlertNotifier.show(context, id, verdict)
            // Family Guardian: tells a family member (only if the person opted in) about a SCAM from a real or demo text, never a pasted one.
            if (verdict.level == RiskLevel.SCAM && (source == "sms" || source == "demo")) GuardianService.maybeAlert(context, verdict, sender, receivedAt)
            if (source == "sms") ReportingService.maybeEnqueue(context, verdict, sender, receivedAt) // only real texts are reported, never pasted or demo ones
            // Voice warning (F12): only if the rules allow it (opted in, phone not silent or on a call, not repeated within 30 s).
            val speech = VoiceWarnings.speak(context, verdict, source)
            ProcessResult(verdict, id, speech)
        }
}
