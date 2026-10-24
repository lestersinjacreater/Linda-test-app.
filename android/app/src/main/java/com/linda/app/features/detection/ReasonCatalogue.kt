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

    // ---- M-Pesa memory (features/learning): the claim checked against the person's real M-Pesa messages ----
    // Codes start with "memory_" so the Layer Trace can show them.

    /** A text message says money arrived, and Linda saw every real M-Pesa message of the last 2 days: none brought it. */
    fun memoryNoPayment(amount: String) = Reason(
        "memory_no_payment",
        "Your real M-Pesa messages show no $amount received in the last 2 days. Nobody sent you this money. Do not send anything back.",
        "Ujumbe wako halisi wa M-Pesa hauonyeshi $amount iliyopokelewa katika siku 2 zilizopita. Hakuna aliyekutumia pesa hizi. Usirudishe chochote.",
    )

    /** Pasted text has no date, so the check covers the whole 30-day memory. */
    fun memoryNoPaymentPasted(amount: String) = Reason(
        "memory_no_payment",
        "Linda checked your real M-Pesa messages from the last 30 days: no $amount arrived like this.",
        "Linda imeangalia ujumbe wako halisi wa M-Pesa wa siku 30 zilizopita: hakuna $amount iliyoingia hivi.",
    )

    /** A real payment of the claimed amount did arrive, and the message asks for it back. */
    fun memoryRealPayment(amount: String) = Reason(
        "memory_real_payment",
        "A real $amount did arrive on your M-Pesa. If it truly was a mistake, the sender asks Safaricom to reverse it. Never send it back yourself.",
        "$amount halisi ziliingia kwenye M-Pesa yako. Ikiwa kweli ni makosa, mtumaji aombe Safaricom airudishe. Usizirudishe wewe mwenyewe.",
    )

    /** Pasted text with the transaction code of a genuine M-Pesa message on this phone. */
    val MEMORY_SAME_AS_REAL = Reason(
        "memory_same_as_real",
        "This matches a real M-Pesa message on your phone (same transaction code).",
        "Huu unalingana na ujumbe halisi wa M-Pesa kwenye simu yako (nambari ileile ya muamala).",
    )

    fun forCategory(category: String): Reason = BY_CATEGORY.getValue(if (category in BY_CATEGORY) category else "other")

    /** "Words like ..." built from the n-grams that pushed the score up most. */
    fun wordsReason(words: List<String>): Reason = Reason(
        "ngrams",
        "Typical scam wording: " + words.joinToString(", ") { "\"$it\"" } + ".",
        "Maneno yanayotumika sana kwenye ulaghai: " + words.joinToString(", ") { "\"$it\"" } + ".",
    )
}
