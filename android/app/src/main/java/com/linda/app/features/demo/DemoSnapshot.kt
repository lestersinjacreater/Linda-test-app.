package com.linda.app.features.demo

import com.linda.app.features.detection.MetadataFeatures
import com.linda.app.features.detection.Normalizer
import com.linda.app.features.detection.Verdict
import com.linda.app.features.trace.LayerResult
import com.linda.app.features.trace.LayerTraceBuilder
import com.linda.app.features.trace.TraceInput

/**
 * Everything the demo overlay shows about the last message Linda analysed (docs/design-system.md 7.8): what each of
 * the five layers produced. It is kept in memory only, only while the overlay is switched on, and never leaves the phone.
 */
data class DemoSnapshot(
    val sequence: Long,
    val source: String,                 // "sms", "checker" or "demo"
    val sender: String?,
    val normalised: String,
    val disguises: List<Pair<String, String>>,
    val score: Float,
    val level: String,
    val category: String,
    val modelVersion: String,
    val warnThreshold: Double,
    val scamThreshold: Double,
    val scamWords: List<String>,
    val inContacts: Boolean?,
    val firstMessage: Boolean?,
    val features: Map<String, Double>,  // the five 0/1 sender and wording features the model uses
    val onBlocklist: Boolean,
    val allowListed: Boolean,
    val actions: List<String>,          // what Linda did, e.g. "notified", "voiced", "reported:queued", "guardian:sent"
    val trace: List<LayerResult>,
)

object DemoSnapshots {
    fun create(
        sequence: Long, body: String, sender: String?, source: String, inContacts: Boolean?, firstMessage: Boolean?,
        verdict: Verdict, verifiedSenders: Set<String>, warnThreshold: Double, scamThreshold: Double,
        onBlocklist: Boolean, allowListed: Boolean, actions: List<String>,
    ): DemoSnapshot {
        val features = MetadataFeatures.compute(body, sender, verifiedSenders)
        val words = LayerTraceBuilder.scamWords(verdict.reasons)
        val trace = LayerTraceBuilder.build(
            TraceInput(
                level = verdict.level.name, score = verdict.score, category = verdict.category,
                reasonCodes = verdict.reasons.map { it.code }.toSet(), scamWords = words, body = body,
                senderVerified = features.getValue("sender_verified") == 1.0, onBlocklist = onBlocklist,
                reported = actions.contains("reported:queued"), guardianAlerted = actions.contains("guardian:sent"), isPasted = sender == null,
            ),
        )
        return DemoSnapshot(
            sequence = sequence, source = source, sender = sender, normalised = Normalizer.normalize(body),
            disguises = LayerTraceBuilder.disguises(body), score = verdict.score, level = verdict.level.name, category = verdict.category,
            modelVersion = verdict.modelVersion, warnThreshold = warnThreshold, scamThreshold = scamThreshold, scamWords = words,
            inContacts = inContacts, firstMessage = firstMessage, features = features, onBlocklist = onBlocklist,
            allowListed = allowListed, actions = actions, trace = trace,
        )
    }
}

/**
 * The raw, technical lines under the Layer Trace, one per layer. They are log-style key=value text on purpose
 * (shown in a monospaced font for the judges), so they are not translated like the rest of the app.
 */
object DemoOverlayText {
    private const val MAX_TEXT = 140

    fun lines(s: DemoSnapshot): List<Pair<String, String>> {
        val shown = if (s.normalised.length > MAX_TEXT) s.normalised.take(MAX_TEXT) + "..." else s.normalised
        val l = buildString {
            append("normalised=\"").append(shown).append('"')
            if (s.disguises.isNotEmpty()) append("\ndisguises=").append(s.disguises.joinToString(", ") { it.first + "->" + it.second })
        }
        val i = if (s.allowListed) "skipped (sender marked safe)" else buildString {
            append("score=").append("%.2f".format(java.util.Locale.US, s.score))
            append(" warn=").append("%.2f".format(java.util.Locale.US, s.warnThreshold))
            append(" scam=").append("%.2f".format(java.util.Locale.US, s.scamThreshold))
            append("\ntype=").append(s.category)
            if (s.scamWords.isNotEmpty()) append(" words=").append(s.scamWords.joinToString(","))
            append("\nmodel=").append(s.modelVersion)
        }
        val n = buildString {
            append("sender=").append(s.sender ?: "pasted")
            append("\ncontacts=").append(yesNo(s.inContacts)).append(" first_msg=").append(yesNo(s.firstMessage))
            append("\nverified=").append(flag("sender_verified", s)).append(" personal_no=").append(flag("sender_personal_number", s))
            append(" link=").append(flag("has_link", s)).append(" mpesa_style=").append(flag("mpesa_style", s))
            append(" fake_mpesa=").append(flag("fake_mpesa", s)).append(" blocklist=").append(if (s.onBlocklist) "yes" else "no")
        }
        val d = "level=${s.level} score=" + "%.2f".format(java.util.Locale.US, s.score)
        val a = "actions=" + if (s.actions.isEmpty()) "none" else s.actions.joinToString(",")
        return listOf("L" to l, "I" to i, "N" to n, "D" to d, "A" to a)
    }

    private fun yesNo(v: Boolean?) = when (v) { true -> "yes"; false -> "no"; null -> "n/a" }
    private fun flag(name: String, s: DemoSnapshot) = if (s.features[name] == 1.0) "yes" else "no"
}
