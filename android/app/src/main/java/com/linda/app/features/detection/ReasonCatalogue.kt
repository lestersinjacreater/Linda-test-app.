package com.linda.app.features.detection

/**
 * The plain-language reasons shown with every warning, in English and Swahili (rule: every warning
 * explains itself). The Swahili text has NOT been reviewed by a native speaker yet: do that before the demo.
 * They live here rather than in strings.xml because a [Verdict] carries both languages at once.
 */
object ReasonCatalogue {
    private val BY_CATEGORY = mapOf(
        "fake_mpesa" to Reason(
            "fake_mpesa",
            "This looks like an M-Pesa message but did not come from M-Pesa. You have not received any money.",
            "Ujumbe huu unaonekana kama wa M-Pesa lakini haukutoka M-Pesa. Hujapokea pesa yoyote.",
        ),
        "sent_by_mistake" to Reason(
            "sent_by_mistake",
            "Scammers say they sent money \"by mistake\" and beg you to send it back. Check your real M-Pesa balance first.",
            "Matapeli hudai wamekutumia pesa \"kimakosa\" na kukusihi uzirudishe. Angalia salio lako halisi la M-Pesa kwanza.",
        ),
        "prize" to Reason(
            "prize",
            "You are told you won a prize but must pay a fee first. Real prizes never ask you to pay.",
            "Unaambiwa umeshinda zawadi lakini ulipe ada kwanza. Zawadi halisi hazikuombi ulipe.",
        ),
        "fuliza_upgrade" to Reason(
            "fuliza_upgrade",
            "A \"limit upgrade\" offer asking for money or a click. Safaricom does not sell limit increases.",
            "Ofa ya \"kuongeza kiwango\" inayoomba pesa au kubofya kiungo. Safaricom haiuzi nyongeza za kiwango.",
        ),
        "kra_refund" to Reason(
            "kra_refund",
            "A tax refund or penalty that asks for money or your details. KRA only uses iTax (itax.kra.go.ke).",
            "Marejesho au faini ya kodi inayoomba pesa au taarifa zako. KRA hutumia iTax pekee (itax.kra.go.ke).",
        ),
        "job_fee" to Reason(
            "job_fee",
            "A job offer that asks you to pay a fee. Real employers do not charge to hire you.",
            "Ofa ya kazi inayokutaka ulipe ada. Waajiri halisi hawatozi ili kukuajiri.",
        ),
        "loan_fee" to Reason(
            "loan_fee",
            "A loan that needs a \"fee\" paid first. Real lenders deduct fees from the loan, not before it.",
            "Mkopo unaohitaji \"ada\" kulipwa kwanza. Wakopeshaji halisi hukata ada kutoka kwenye mkopo, si kabla yake.",
        ),
        "pin_request" to Reason(
            "pin_request",
            "It asks for your PIN. Nobody from Safaricom or a bank will ever ask for your PIN.",
            "Inaomba PIN yako. Hakuna mtu wa Safaricom au benki atakayekuomba PIN yako.",
        ),
        "phishing_link" to Reason(
            "phishing_link",
            "It pushes you to open a suspicious link. Do not tap it or enter any details.",
            "Inakusukuma kufungua kiungo cha kutiliwa shaka. Usikibofye wala kuingiza taarifa zako.",
        ),
        "other" to Reason(
            "other",
            "The wording is very similar to known mobile-money scams.",
            "Maneno yake yanafanana sana na ulaghai wa pesa za simu unaojulikana.",
        ),
    )

    val LINK = Reason("link", "The message contains a link.", "Ujumbe una kiungo.")
    val PERSONAL_NUMBER = Reason(
        "personal_number",
        "It came from an ordinary phone number, not an official sender name.",
        "Umetoka kwa nambari ya kawaida ya simu, si jina rasmi la mtumaji.",
    )
    val UNKNOWN_SENDER = Reason(
        "unknown_sender",
        "The sender is not in your contacts and this is their first message.",
        "Mtumaji hayuko kwenye anwani zako na huu ni ujumbe wake wa kwanza.",
    )

    fun forCategory(category: String): Reason = BY_CATEGORY.getValue(if (category in BY_CATEGORY) category else "other")

    /** "Words like ..." built from the n-grams that pushed the score up most. */
    fun wordsReason(words: List<String>): Reason = Reason(
        "ngrams",
        "Typical scam wording: " + words.joinToString(", ") { "\"$it\"" } + ".",
        "Maneno yanayotumika sana kwenye ulaghai: " + words.joinToString(", ") { "\"$it\"" } + ".",
    )
}
