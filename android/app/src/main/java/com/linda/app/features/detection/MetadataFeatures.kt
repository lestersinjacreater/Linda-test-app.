package com.linda.app.features.detection

/**
 * Five 0/1 "context" features computed from the raw message and its sender. MUST match
 * ml/src/features/scoring/metadata.py. A real and a fake M-Pesa confirmation have identical
 * words; only the sender differs, so the model gets the sender facts as inputs.
 */
object MetadataFeatures {
    val NAMES = listOf("sender_verified", "sender_personal_number", "has_link", "mpesa_style", "fake_mpesa")

    private val PERSONAL_NUMBER = Regex("(?:\\+?254|0)[17][0-9]{8}")
    private val LINK = Regex("https?://|www\\.|\\b(?:bit\\.ly|tinyurl\\.com|t\\.co|cutt\\.ly|goo\\.gl)/", RegexOption.IGNORE_CASE)
    private val TRANSACTION_CODE =
        Regex("(?<![A-Za-z0-9])(?=[A-Z0-9]{10}(?![A-Za-z0-9]))(?=[A-Z0-9]*[A-Z])(?=[A-Z0-9]*[0-9])[A-Z0-9]{10}")
    private val CONFIRMED = Regex("confirmed|imethibitishwa", RegexOption.IGNORE_CASE)
    private val KSH = Regex("ksh", RegexOption.IGNORE_CASE)

    /** [sender] is null for pasted or shared text. [verified] holds upper-case sender IDs. */
    fun compute(text: String, sender: String?, verified: Set<String>): Map<String, Double> {
        val s = (sender ?: "").trim()
        val isVerified = s.uppercase() in verified
        val isPersonal = PERSONAL_NUMBER.matches(s)
        val hasLink = LINK.containsMatchIn(text)
        val mpesaStyle = TRANSACTION_CODE.containsMatchIn(text) && CONFIRMED.containsMatchIn(text) && KSH.containsMatchIn(text)
        // Looks like an M-Pesa confirmation but did not come from M-Pesa. Pasted text (no sender)
        // is judged on its words alone, so it never fires this feature.
        val fakeMpesa = mpesaStyle && s.isNotEmpty() && !isVerified
        return mapOf(
            "sender_verified" to isVerified.toDouble(),
            "sender_personal_number" to isPersonal.toDouble(),
            "has_link" to hasLink.toDouble(),
            "mpesa_style" to mpesaStyle.toDouble(),
            "fake_mpesa" to fakeMpesa.toDouble(),
        )
    }

    private fun Boolean.toDouble() = if (this) 1.0 else 0.0
}
