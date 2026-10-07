package com.linda.app.features.inbox

import com.linda.app.features.detection.MessageInput
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.ScamDetector
import com.linda.app.features.detection.Verdict

/** One message read from the phone's inbox. */
data class RawSms(val sender: String?, val body: String, val date: Long)

/** A message the scan flagged. */
data class Flagged(val sender: String?, val body: String, val date: Long, val verdict: Verdict)

/** Messages from one sender, for the "grouped list". */
data class SenderGroup(val sender: String?, val messages: List<Flagged>) {
    val worst: RiskLevel get() = if (messages.any { it.verdict.level == RiskLevel.SCAM }) RiskLevel.SCAM else RiskLevel.CAUTION
    val newest: Flagged get() = messages.maxByOrNull { it.date }!!
}

/**
 * "Scan my inbox" (F8), free of Android so it can be unit tested and timed. It scores every message with the same
 * [ScamDetector] as a live SMS. Differences from a live message, on purpose:
 *  - the "first message from this sender" signal is NOT used: a 90-day window cannot tell what was really first;
 *  - nothing is notified, spoken, reported or sent to a guardian: these are old messages, and a backlog would be a flood.
 */
class InboxScanner(
    private val detector: ScamDetector,
    private val inContacts: (String?) -> Boolean?,   // the slow part on a phone, so it is asked once per sender
    private val isAllowed: (String) -> Boolean,      // senders the person marked as safe
) {
    private val contactsCache = HashMap<String, Boolean?>()

    /**
     * Scores [rows]. [onProgress] gets (checked so far, flagged so far) every [progressEvery] messages and at the end.
     * Stops early (keeping what it found) when [isCancelled] turns true.
     */
    fun scan(
        rows: Sequence<RawSms>,
        progressEvery: Int = 50,
        isCancelled: () -> Boolean = { false },
        onProgress: (checked: Int, flagged: Int) -> Unit = { _, _ -> },
    ): List<Flagged> {
        val found = ArrayList<Flagged>()
        var checked = 0
        for (row in rows) {
            if (isCancelled()) break
            checked++
            val sender = row.sender?.takeIf { it.isNotBlank() }
            if (sender == null || !isAllowed(sender)) {
                val contact = if (sender == null) null else contactsCache.getOrPut(sender) { inContacts(sender) }
                val verdict = detector.analyse(MessageInput(row.body, sender, row.date, senderInContacts = contact, firstMessageFromSender = null))
                if (verdict.level != RiskLevel.SAFE) found += Flagged(sender, row.body, row.date, verdict)
            }
            if (checked % progressEvery == 0) onProgress(checked, found.size)
        }
        onProgress(checked, found.size)
        return found
    }
}

object InboxGrouping {
    /** Groups flagged messages by sender. Worst first (any SCAM before CAUTION-only), then most messages, then newest. */
    fun group(flagged: List<Flagged>): List<SenderGroup> =
        flagged.groupBy { it.sender ?: "" }
            .map { (_, messages) -> SenderGroup(messages.first().sender, messages.sortedByDescending { it.date }) }
            .sortedWith(
                compareByDescending<SenderGroup> { it.worst == RiskLevel.SCAM }
                    .thenByDescending { it.messages.size }
                    .thenByDescending { it.newest.date },
            )
}

object InboxWindow {
    /** The scan covers this long (spec: the last 90 days). */
    const val DAYS = 90
    fun startMillis(now: Long): Long = now - DAYS * 24L * 60 * 60 * 1000
}
