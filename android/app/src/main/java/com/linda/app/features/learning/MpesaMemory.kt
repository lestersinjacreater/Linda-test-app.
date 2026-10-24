package com.linda.app.features.learning

/**
 * What Linda knows about the person's real M-Pesa payments, handed to the detector with a message.
 *
 * [completeSince] is the moment from which Linda has seen EVERY M-Pesa message (it read the inbox back to then, or
 * has been listening since then). Before that moment the memory has gaps, so it cannot say "no money arrived".
 */
data class MpesaMemory(val receipts: List<Receipt>, val completeSince: Long) {

    /** The answer to "did this money really arrive?". */
    enum class Answer {
        /** A genuine M-Pesa message with this very transaction code is on the phone. */
        SAME_AS_REAL_MESSAGE,

        /** A real payment of this amount arrived recently. */
        REAL_PAYMENT,

        /** Linda saw every M-Pesa message in the window and none of them brought this amount. */
        NO_REAL_PAYMENT,

        /** Linda was not watching the whole window, so it does not know. */
        CANNOT_TELL,
    }

    /**
     * [at] is when the claim arrived. A real payment counts from [windowMs] before it to a few minutes after it
     * (messages can arrive out of order). A text message uses the last 2 days; pasted text has no date of its own,
     * so the Check screen looks across the whole 30-day memory.
     */
    fun check(claim: MoneyClaim, at: Long, windowMs: Long = WINDOW_MS): Answer {
        if (claim.code != null && receipts.any { it.codeHash == Money.codeHash(claim.code) }) return Answer.SAME_AS_REAL_MESSAGE
        val from = at - windowMs
        if (receipts.any { it.amountCents == claim.amountCents && it.at in from..(at + LATE_MS) }) return Answer.REAL_PAYMENT
        return if (completeSince <= from) Answer.NO_REAL_PAYMENT else Answer.CANNOT_TELL
    }

    companion object {
        const val HOUR_MS = 60 * 60 * 1000L
        const val WINDOW_MS = 48 * HOUR_MS        // "the last 2 days": a sent-by-mistake scam follows within minutes or hours
        const val LATE_MS = 10 * 60 * 1000L       // a real confirmation can arrive a little after the claim
        const val RETENTION_MS = 30 * 24 * HOUR_MS // receipts older than 30 days are forgotten
    }
}
