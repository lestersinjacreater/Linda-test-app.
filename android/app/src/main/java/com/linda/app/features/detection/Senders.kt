package com.linda.app.features.detection

import com.linda.app.core.util.PhoneNumbers

/** Who a message came from, as far as the sender field shows. */
enum class SenderKind {
    /** Pasted or shared text: there is no sender. */
    NONE,
    /** On the verified list, e.g. MPESA, Safaricom, KCB. */
    VERIFIED,
    /** An ordinary Kenyan mobile number (07XX, 01XX, +254...). */
    PERSONAL,
    /** A short code of 3 to 6 digits, e.g. 22444. */
    SHORT_CODE,
    /** A sender name with letters, e.g. BETIKA, TALA, HELB. */
    BUSINESS,
    /** A sender name that copies a brand on the verified list but is not on it, e.g. MPESA_CARE, "Equity Bank". */
    LOOKALIKE,
    /** Anything else, e.g. a foreign number. Treated like a personal number. */
    OTHER,
}

/**
 * Why this exists: in Kenya a sender NAME (like BETIKA) or a short code (like 22444) has to be registered before
 * it can send, and the scams we know come from ordinary 07XX and 01XX numbers. Our training data had very few
 * genuine business texts, so the model learned "money words from anyone not on the verified list = scam" and
 * warned on real bank alerts, loan reminders and promos. So for a registered sender, the wording ALONE is not
 * enough to warn: Linda warns only when there is also a sign that is a scam whoever sends it (see [hasStrongSign]).
 * A sender name that copies a protected brand is treated more strictly, because that is impersonation.
 */
object Senders {
    /** Brand names a scammer would copy. A sender NAME containing one, but not on the verified list, is a lookalike. */
    private val BRANDS = listOf("MPESA", "SAFARICOM", "FULIZA", "MSHWARI", "EQUITY", "KCB", "NCBA", "COOP", "KPLC", "KRA")
    private val SHORT_CODE = Regex("[0-9]{3,6}")
    private val LETTER = Regex("[A-Za-z]")

    // Signs that point to a scam whoever sends the message.
    private val PIN = Regex("(?<![a-z0-9])(?:pin|nambari yako ya siri)(?![a-z0-9])")
    private val BRAND_IN_TEXT = Regex("(?<![a-z0-9])(?:mpesa|safaricom|fuliza)(?![a-z0-9])")
    private val PERSONAL_NUMBER_IN_TEXT = Regex("(?:\\+?254|(?<![0-9])0)[17][0-9]{8}(?![0-9])")

    fun kindOf(sender: String?, verified: Set<String>): SenderKind {
        val s = (sender ?: "").trim()
        return when {
            s.isEmpty() -> SenderKind.NONE
            s.uppercase() in verified -> SenderKind.VERIFIED
            PhoneNumbers.toMsisdn(s) != null -> SenderKind.PERSONAL
            SHORT_CODE.matches(s) -> SenderKind.SHORT_CODE
            LETTER.containsMatchIn(s) -> {
                val key = s.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' } // "M-Pesa Care" -> "MPESACARE"
                if (BRANDS.any { it in key }) SenderKind.LOOKALIKE else SenderKind.BUSINESS
            }
            else -> SenderKind.OTHER
        }
    }

    /** A registered sender: a business name, a short code, or a lookalike name. Not a phone number. */
    fun isRegistered(kind: SenderKind) =
        kind == SenderKind.BUSINESS || kind == SenderKind.SHORT_CODE || kind == SenderKind.LOOKALIKE

    /**
     * True if the message has a sign that is a scam whoever sends it: a link, a fake M-Pesa confirmation,
     * a mention of a PIN, the Safaricom / M-Pesa / Fuliza name used by someone else, or a request
     * involving a personal phone number (a real business is paid by Paybill or Till, not to a person).
     * [meta] is the model's sender facts (MetadataFeatures), so the link and fake M-Pesa checks match the model's.
     */
    fun hasStrongSign(text: String, meta: Map<String, Double>): Boolean {
        if (meta["has_link"] == 1.0 || meta["fake_mpesa"] == 1.0) return true
        val normalized = Normalizer.normalize(text)
        return PIN.containsMatchIn(normalized) ||
            BRAND_IN_TEXT.containsMatchIn(normalized) ||
            PERSONAL_NUMBER_IN_TEXT.containsMatchIn(text.replace(" ", ""))
    }
}
