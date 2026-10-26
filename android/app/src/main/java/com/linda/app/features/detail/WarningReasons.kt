package com.linda.app.features.detail

import com.linda.app.features.detection.Reason

/**
 * Which reasons a person reads under a verdict. The detector keeps every reason (the trace, demo mode and the
 * voice use them); the card shows only the few that help someone decide, in the detector's order (strongest first).
 */
object WarningReasons {
    /** Three plain reasons are enough to decide; more turns a warning into reading. */
    const val MAX_SHOWN = 3

    /**
     * Reasons for the trace and demo mode only. "ngrams" quotes the letter pieces the model weighed ("nant", "enan"):
     * honest, but meaningless to a person. It still shows under "How Linda decided" (the I layer).
     */
    private val TECHNICAL = setOf("ngrams")

    /** [fakeMpesaBadge]: the Fake M-PESA badge is on the card and already says what the fake_mpesa reason says. */
    fun shown(reasons: List<Reason>, fakeMpesaBadge: Boolean): List<Reason> =
        reasons
            .filter { it.code !in TECHNICAL }
            .filterNot { fakeMpesaBadge && it.code == "fake_mpesa" }
            .take(MAX_SHOWN)
}
