package com.linda.app.features.learning

import com.linda.app.features.detection.Normalizer

/** A message that says money reached the person. */
data class MoneyClaim(val kind: Kind, val amountCents: Long, val code: String? = null) {
    enum class Kind {
        /** Looks like an M-Pesa "you have received" confirmation (code, "Confirmed", amount). */
        CONFIRMATION,

        /** "I sent you 2,500 by mistake, please send it back" (the sent-by-mistake scam). */
        SENT_BY_MISTAKE,
    }
}

/**
 * Finds a claim that money arrived, so Linda can check it against the person's real M-Pesa messages.
 *
 * A "sent you money" message only counts when it ALSO says it was a mistake or asks for the money back. A friend's
 * "I sent you 500 for lunch" is not a claim to check: flagging it would be a false alarm.
 */
object MoneyClaims {
    // Phrases are matched on Normalizer output: lower case, amounts without commas, punctuation turned into spaces.
    private val SENT_TO_YOU = listOf(
        "nimekutumia", "nimekutuma", "nimetuma", "nilikutumia", "nimekuwekea", "imeingia kwako", "zimeingia kwako",
        "i sent you", "i have sent you", "i ve sent you", "sent you", "sent to you", "sent to your number",
        "sent money to you", "sent the money to you", "transferred to you", "mistakenly sent", "accidentally sent",
    )
    private val MISTAKE_OR_RETURN = listOf(
        "kimakosa", "kwa makosa", "nimekosea", "by mistake", "mistakenly", "accidentally", "in error", "wrong number",
        "nirudishie", "rudisha", "zirudishe", "irudishe", "nirudishiwe", "send it back", "send back", "send them back",
        "return it", "return the money", "refund me", "reverse it",
    )
    private val RECEIVED = listOf("you have received", "umepokea")
    private val CONFIRMED = Regex("confirmed|imethibitishwa", RegexOption.IGNORE_CASE)

    /** "2,500/=", "2500 bob", "500 shillings". */
    private val SUFFIX_AMOUNT = Regex("([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{2,6})\\s*(?:/=|/-|bob|shillings|shilingi)", RegexOption.IGNORE_CASE)

    /** A plain "2,500" or "2500", but not part of a time (4:12), a date (12/10/26), a phone number or a code. */
    private val BARE_AMOUNT = Regex("(?<![0-9A-Za-z,.:/])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{2,6})(?![0-9A-Za-z,:/])")

    private fun has(normalized: String, phrase: String) =
        Regex("(?<![a-z0-9])" + Regex.escape(phrase) + "(?![a-z0-9])").containsMatchIn(normalized)

    fun find(body: String): MoneyClaim? {
        val normalized = Normalizer.normalize(body)
        val code = Money.TRANSACTION_CODE.find(body)?.value
        if (code != null && CONFIRMED.containsMatchIn(body) && RECEIVED.any { has(normalized, it) }) {
            val amount = Money.KSH_AMOUNT.find(body)?.groupValues?.get(1)?.let(Money::cents)
            if (amount != null) return MoneyClaim(MoneyClaim.Kind.CONFIRMATION, amount, code)
        }
        if (SENT_TO_YOU.any { has(normalized, it) } && MISTAKE_OR_RETURN.any { has(normalized, it) }) {
            val amount = sequenceOf(Money.KSH_AMOUNT, SUFFIX_AMOUNT, BARE_AMOUNT)
                .mapNotNull { re -> re.findAll(body).mapNotNull { m -> Money.cents(m.groupValues[1]) }.firstOrNull() }
                .firstOrNull()
            if (amount != null) return MoneyClaim(MoneyClaim.Kind.SENT_BY_MISTAKE, amount)
        }
        return null
    }
}
