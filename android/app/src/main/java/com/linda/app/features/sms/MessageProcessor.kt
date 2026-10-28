package com.linda.app.features.sms

import android.content.Context
import com.linda.app.LindaApp
import com.linda.app.core.data.DetectionEntity
import com.linda.app.core.util.PhoneHash
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.features.alerts.AlertNotifier
import com.linda.app.features.alerts.VoiceWarnings
import com.linda.app.features.detection.MessageInput
import com.linda.app.features.detection.ReasonsJson
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.Verdict
import com.linda.app.features.demo.DemoOverlay
import com.linda.app.features.demo.DemoSnapshots
import com.linda.app.features.guardian.AlertDecision
import com.linda.app.features.guardian.GuardianService
import com.linda.app.features.learning.LearningStore
import com.linda.app.features.learning.MoneyClaims
import com.linda.app.features.learning.MpesaMemory
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
 * so a demo behaves exactly like a real message. Order: M-Pesa memory, allow-list, context and inbox story, ScamDetector,
 * save, warn.
 */
class MessageProcessor(private val context: Context) {
    private val app = context.applicationContext as LindaApp

    suspend fun process(body: String, sender: String?, receivedAt: Long, source: String, notify: Boolean): ProcessResult =
        withContext(Dispatchers.Default) {
            // M-Pesa memory: a genuine M-Pesa "you have received" text is remembered (time, amount, hashed code only).
            if (source == "sms" || source == "demo") LearningStore.learn(context, sender, body, receivedAt)
            val senders = app.database.senderDao()
            if (sender != null && senders.isAllowed(sender)) {
                // The user said this sender is fine. Skip scoring (a SAFE verdict with no reasons).
                val skipped = Verdict(RiskLevel.SAFE, 0f, emptyList(), "allow-list", "none", "")
                recordForDemoOverlay(body, sender, source, null, null, skipped, allowListed = true, actions = emptyList())
                return@withContext ProcessResult(skipped, null)
            }
            val inContacts = ContactsLookup.isInContacts(context, sender)
            val first = sender?.let { !senders.hasSeen(it) }
            // Only a message that claims money arrived needs the memory (most messages do not, so most skip this).
            val memory = if (MoneyClaims.find(body) != null) {
                LearningStore.snapshot(context, receivedAt, if (sender == null) MpesaMemory.RETENTION_MS else MpesaMemory.WINDOW_MS)
            } else null
            // Know your senders: what the person's own messages say about this phone number (null for business names).
            val history = LearningStore.senderHistory(context, sender)
            // The inbox story: what this business usually sends, and whether a quoted M-Pesa code is real (null otherwise).
            val story = LearningStore.storyFacts(context, sender, body, receivedAt)
            // "Hi, it's Mum" from a number that is not saved: only then are the contacts asked about the claimed name.
            val claimed = if (inContacts == false) ContactsLookup.claimedContact(context, body) else null
            val verdict = app.detector.analyse(
                MessageInput(body, sender, receivedAt, inContacts, first, memory, history, LearningStore.personalModel(context), story, claimed),
            )
            if (sender != null) LearningStore.countMessage(context, sender, receivedAt, warned = verdict.level != RiskLevel.SAFE)
            if (source == "sms" || source == "demo") LearningStore.rememberStory(context, sender, body, receivedAt)

            if (verdict.level == RiskLevel.SAFE) {
                recordForDemoOverlay(body, sender, source, inContacts, first, verdict, allowListed = false, actions = emptyList())
                return@withContext ProcessResult(verdict, null)
            }

            val id = app.database.detectionDao().insert(
                DetectionEntity(
                    sender = sender, senderMsisdn = PhoneNumbers.toMsisdn(sender), body = body, level = verdict.level.name, score = verdict.score,
                    category = verdict.category, fingerprint = verdict.fingerprint,
                    reasonsJson = ReasonsJson.encode(verdict.reasons), modelVersion = verdict.modelVersion,
                    receivedAt = receivedAt, source = source,
                ),
            )
            val actions = mutableListOf<String>()
            if (notify) { AlertNotifier.show(context, id, verdict); actions += "notified" }
            // Family Guardian: tells a family member (only if the person opted in) about a SCAM from a real or demo text, never a pasted one.
            if (verdict.level == RiskLevel.SCAM && (source == "sms" || source == "demo")) {
                val decision = GuardianService.maybeAlert(context, verdict, sender, receivedAt)
                actions += if (decision is AlertDecision.Send) "guardian:sent" else "guardian:skipped(" + (decision as AlertDecision.Skip).reason.name.lowercase() + ")"
            }
            // only real texts are reported, never pasted or demo ones
            if (source == "sms") actions += if (ReportingService.maybeEnqueue(context, verdict, sender, receivedAt, id)) "reported:queued" else "reported:no"
            // Voice warning (F12): only if the rules allow it (opted in, phone not silent or on a call, not repeated within 30 s).
            val speech = VoiceWarnings.speak(context, verdict, source)
            if (speech != null) actions += "voiced"
            recordForDemoOverlay(body, sender, source, inContacts, first, verdict, allowListed = false, actions = actions)
            ProcessResult(verdict, id, speech)
        }

    /** Feeds the demo overlay (docs/design-system.md 7.8). Does nothing, and keeps nothing, unless the overlay is switched on. */
    private suspend fun recordForDemoOverlay(
        body: String, sender: String?, source: String, inContacts: Boolean?, first: Boolean?,
        verdict: Verdict, allowListed: Boolean, actions: List<String>,
    ) {
        if (!DemoOverlay.isOn()) return
        val onBlocklist = PhoneNumbers.toMsisdn(sender)?.let { app.database.blockedNumberDao().find(PhoneHash.of(it)) } != null
        DemoOverlay.record(
            DemoSnapshots.create(
                sequence = DemoOverlay.nextSequence(), body = body, sender = sender, source = source, inContacts = inContacts, firstMessage = first,
                verdict = verdict, verifiedSenders = app.detector.verifiedSenders, warnThreshold = app.detector.warnThreshold,
                scamThreshold = app.detector.scamThreshold, onBlocklist = onBlocklist, allowListed = allowListed, actions = actions,
            ),
        )
    }
}
