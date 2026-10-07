package com.linda.app.features.trace

import com.linda.app.features.detection.Normalizer
import com.linda.app.features.detection.Reason

/**
 * The Layer Trace (docs/design-system.md 7.1): which of the five layers noticed something, and what.
 * Pure Kotlin so it can be unit tested. It only reports what Linda really computed for this message.
 * It never makes up a figure: every line comes from the verdict, the reasons or the sender checks.
 */
enum class Layer(val letter: String) { LANGUAGE("L"), INTELLIGENCE("I"), NETWORK("N"), DECISION("D"), ALERT("A") }

/** One finding. [code] is turned into words (English or Kiswahili) by the screen; [args] fill in the blanks. */
data class TraceLine(val code: String, val args: List<String> = emptyList())

/** [flagged] = this layer found a risk, so the segment takes the verdict's colour instead of its green tint. */
data class LayerResult(val layer: Layer, val flagged: Boolean, val lines: List<TraceLine>)

data class TraceInput(
    val level: String,                 // "SAFE", "CAUTION" or "SCAM"
    val score: Float,                  // 0..1, as in the verdict
    val category: String,              // "none" for SAFE
    val reasonCodes: Set<String>,      // codes of the reasons (see ReasonCatalogue)
    val scamWords: List<String>,       // wording the model leaned on
    val body: String,
    val senderVerified: Boolean,
    val onBlocklist: Boolean,          // the radar has confirmed this number (synced to the phone)
    val reported: Boolean,             // this number was reported to the radar
    val guardianAlerted: Boolean,
    val isPasted: Boolean,             // checked text with no sender
)

object LayerTraceBuilder {
    private const val MAX_DISGUISES = 2
    private val LEET_CHARS = "30145@$"
    private val QUOTED = Regex("\"([^\"]+)\"")

    fun build(input: TraceInput): List<LayerResult> {
        val risky = input.level != "SAFE"
        return listOf(language(input, risky), intelligence(input, risky), network(input, risky), decision(input, risky), alert(input, risky))
    }

    // L: what the normaliser undid before the text was read.
    private fun language(input: TraceInput, risky: Boolean): LayerResult {
        val found = disguises(input.body)
        val lines = if (found.isEmpty()) listOf(TraceLine("l_clean")) else found.map { TraceLine("l_disguise", listOf(it.first, it.second)) }
        return LayerResult(Layer.LANGUAGE, flagged = risky && found.isNotEmpty(), lines = lines)
    }

    // I: the on-device model's view of the wording.
    private fun intelligence(input: TraceInput, risky: Boolean): LayerResult {
        if (!risky) return LayerResult(Layer.INTELLIGENCE, false, listOf(TraceLine("i_none")))
        val lines = mutableListOf(TraceLine("i_category", listOf(input.category)))
        if (input.scamWords.isNotEmpty()) lines += TraceLine("i_words", listOf(input.scamWords.joinToString(", ")))
        lines += TraceLine("i_score", listOf(percent(input.score).toString()))
        return LayerResult(Layer.INTELLIGENCE, true, lines)
    }

    // N: who sent it and how.
    private fun network(input: TraceInput, risky: Boolean): LayerResult {
        if (input.senderVerified) return LayerResult(Layer.NETWORK, false, listOf(TraceLine("n_verified")))
        val lines = mutableListOf<TraceLine>()
        if (input.category == "fake_mpesa" || "fake_mpesa" in input.reasonCodes) lines += TraceLine("n_fake_mpesa")
        if ("personal_number" in input.reasonCodes) lines += TraceLine("n_personal")
        if ("unknown_sender" in input.reasonCodes) lines += TraceLine("n_unknown")
        if ("link" in input.reasonCodes || input.category == "phishing_link") lines += TraceLine("n_link")
        if (input.onBlocklist) lines += TraceLine("n_blocklist")
        if (lines.isEmpty()) lines += TraceLine(if (input.isPasted) "n_pasted" else "n_none")
        val signals = lines.none { it.code == "n_none" || it.code == "n_pasted" }
        return LayerResult(Layer.NETWORK, flagged = risky && signals, lines = lines)
    }

    // D: everything weighed together.
    private fun decision(input: TraceInput, risky: Boolean) =
        LayerResult(Layer.DECISION, risky, listOf(TraceLine("d_verdict", listOf(input.level))))

    // A: what Linda did about it. Never "flagged": it is the protection itself.
    private fun alert(input: TraceInput, risky: Boolean): LayerResult {
        if (!risky) return LayerResult(Layer.ALERT, false, listOf(TraceLine("a_nothing")))
        val lines = mutableListOf(TraceLine("a_warned"))
        if (input.reported) lines += TraceLine("a_reported")
        if (input.guardianAlerted) lines += TraceLine("a_guardian")
        return LayerResult(Layer.ALERT, false, lines)
    }

    private fun percent(score: Float): Int = (score * 100f + 0.5f).toInt().coerceIn(0, 100)

    /**
     * Words the scammer disguised, as (what was written, how Linda read it): "M-P3SA" to "mpesa".
     * Only whole words that turn into plain letters count; amounts ("Ksh3,140") and codes keep their digits and are skipped.
     */
    fun disguises(body: String): List<Pair<String, String>> {
        val found = LinkedHashMap<String, String>()
        for (raw in body.split(Regex("\\s+"))) {
            val token = raw.trim { !it.isLetterOrDigit() && it != '@' && it != '$' }
            if (token.length < 3 || token.count { it.isLetter() } < 2 || token.none { it in LEET_CHARS }) continue
            val read = Normalizer.normalize(token)
            if (read.isEmpty() || read.any { it in '0'..'9' || it == '@' || it == '$' } || ' ' in read) continue
            if (read != token.lowercase()) found.putIfAbsent(token, read)
            if (found.size >= MAX_DISGUISES) break
        }
        return found.map { it.key to it.value }
    }

    /** The words quoted inside the model's "Typical scam wording" reason, if it has one. */
    fun scamWords(reasons: List<Reason>): List<String> =
        reasons.firstOrNull { it.code == "ngrams" }?.let { r -> QUOTED.findAll(r.english).map { it.groupValues[1] }.toList() } ?: emptyList()
}
