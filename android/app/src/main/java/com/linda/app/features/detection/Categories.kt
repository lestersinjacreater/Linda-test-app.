package com.linda.app.features.detection

/**
 * Names the TYPE of scam. Keyword rules on the normalised text, first match wins. MUST match
 * ml/src/features/scoring/categories.py. The model says WHETHER it is a scam; this says WHICH kind.
 */
object Categories {
    private val RULES = listOf(
        "sent_by_mistake" to listOf("kimakosa", "kwa makosa", "by mistake", "in error", "wrong number", "isiyo sahihi", "sent in error"),
        "prize" to listOf("umeshinda", "mshindi", "you have won", "have won", "lucky draw", "mega draw", "won ksh"),
        "fuliza_upgrade" to listOf("fuliza", "your mpesa limit", "boost your mpesa"),
        "kra_refund" to listOf("kra"),
        "job_fee" to listOf("job offer", "vacancy", "hiring", "interview fee", "kazi", "mshahara", "nafasi za kazi", "wafanyakazi"),
        "loan_fee" to listOf("loan", "mkopo"),
        "pin_request" to listOf("pin", "nambari yako ya siri"),
    )

    private fun has(text: String, phrase: String) =
        Regex("(?<![a-z0-9])" + Regex.escape(phrase) + "(?![a-z0-9])").containsMatchIn(text)

    fun categorise(text: String, sender: String?, verified: Set<String>): String {
        val meta = MetadataFeatures.compute(text, sender, verified)
        if (meta.getValue("fake_mpesa") == 1.0) return "fake_mpesa"
        val normalized = Normalizer.normalize(text)
        for ((category, phrases) in RULES) {
            if (phrases.any { has(normalized, it) }) return category
        }
        return if (meta.getValue("has_link") == 1.0) "phishing_link" else "other"
    }
}
