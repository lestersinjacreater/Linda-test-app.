package com.linda.app.features.learning

import com.linda.app.features.detection.Normalizer

/**
 * What the person's own messages say about one phone number: since when they have been in touch, how many texts came
 * from it, how many the person sent to it, and how many of its texts Linda warned on.
 *
 * The strongest sign of a real relationship is that the person texted the number themselves, long enough ago that it is
 * not a scam conversation happening right now.
 */
data class SenderHistory(
    val firstContact: Long,    // the earliest text either way
    val received: Int,
    val sentByYou: Int,
    val firstSentByYou: Long?, // when the person first texted this number
    val warned: Int,           // texts from it that Linda warned on
) {
    /**
     * A number the person really knows: they texted it a week or more ago, or it has texted them for two weeks or more
     * (5+ texts), and Linda never had to warn about it. A number that was ever warned on is never "known".
     */
    fun isEstablished(at: Long): Boolean {
        if (warned > 0) return false
        val texted = firstSentByYou != null && at - firstSentByYou >= YOU_TEXTED_MS
        val regular = received >= REGULAR_TEXTS && at - firstContact >= REGULAR_MS
        return texted || regular
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        const val YOU_TEXTED_MS = 7 * DAY_MS
        const val REGULAR_MS = 14 * DAY_MS
        const val REGULAR_TEXTS = 5

        /** Two histories of the same number (Linda's own count, and the inbox) put together. */
        fun merge(a: SenderHistory?, b: SenderHistory?): SenderHistory? {
            if (a == null) return b
            if (b == null) return a
            val sent = listOfNotNull(a.firstSentByYou, b.firstSentByYou).minOrNull()
            return SenderHistory(
                minOf(a.firstContact, b.firstContact), maxOf(a.received, b.received), maxOf(a.sentByYou, b.sentByYou),
                sent, maxOf(a.warned, b.warned),
            )
        }
    }
}

/** Two patterns in a message that matter more when the sender is a stranger. */
object SenderSignals {
    // On Normalizer output: lower case, amounts without commas, punctuation turned into spaces.
    // A PERSON asking the reader to send them money. Not "lipa" or "fee": genuine Lipa na M-Pesa and business texts use
    // those, and fee scams are already caught by the model and the scam categories.
    private val ASKS_FOR_MONEY = listOf(
        "tuma pesa", "nitumie pesa", "nitumie ksh", "tuma ksh", "naomba pesa", "nisaidie pesa", "niongezee pesa",
        "nilipie", "niwekee", "nikopeshe", "send me money", "send me ksh", "send me some", "please send me",
        "kindly send me", "send money to", "lend me", "mpesa me", "can you send me",
    )
    private val NEW_NUMBER = listOf(
        "my new number", "my new line", "this is my new", "changed my number", "new phone number", "lost my phone",
        "namba yangu mpya", "nambari yangu mpya", "namba mpya", "laini mpya", "line mpya", "nimebadilisha namba",
        "simu yangu imepotea", "nimepoteza simu",
    )

    private fun has(normalized: String, phrase: String) =
        Regex("(?<![a-z0-9])" + Regex.escape(phrase) + "(?![a-z0-9])").containsMatchIn(normalized)

    fun asksForMoney(body: String): Boolean = Normalizer.normalize(body).let { n -> ASKS_FOR_MONEY.any { has(n, it) } }

    fun claimsNewNumber(body: String): Boolean = Normalizer.normalize(body).let { n -> NEW_NUMBER.any { has(n, it) } }
}
