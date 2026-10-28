package com.linda.app.features.detection

/**
 * The plain-language reasons shown with every warning, in English and Swahili (rule: every warning
 * explains itself). The Kiswahili was checked for grammar and natural wording on 25 Oct (by Claude, not a native
 * speaker): a native speaker should still read it once before the demo.
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
            // Spoken aloud too: short, and no digits (VoiceLogicTest). The 456 detail is in the memory reasons below.
            "Scammers say they sent money \"by mistake\" and beg you to send it back. Never send it back yourself: a real mistake is reversed through Safaricom.",
            "Matapeli hudai wamekutumia pesa \"kimakosa\" na kukusihi uzirudishe. Usizirudishe wewe mwenyewe: makosa halisi hurekebishwa kupitia Safaricom.",
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
            "Ujumbe huu unaomba PIN yako. Hakuna mtu wa Safaricom wala wa benki atakayekuomba PIN yako.",
        ),
        "phishing_link" to Reason(
            "phishing_link",
            "It pushes you to open a suspicious link. Do not tap it or enter any details.",
            "Ujumbe huu unakushinikiza ufungue kiungo cha kutiliwa shaka. Usikibofye, na usiweke taarifa zako.",
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
        "Ujumbe huu umetoka kwa nambari ya kawaida ya simu, si kwa jina rasmi la mtumaji.",
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
        "Your real M-Pesa messages show no $amount received in the last 2 days. Nobody sent you this money. Do not send anything back; you can forward this message to 333.",
        "Jumbe zako halisi za M-Pesa hazionyeshi kwamba ulipokea $amount katika siku 2 zilizopita. Hakuna aliyekutumia pesa hizi. Usirudishe chochote; unaweza kutuma ujumbe huu kwa 333.",
    )

    /** Pasted text has no date, so the check covers the whole 30-day memory. */
    fun memoryNoPaymentPasted(amount: String) = Reason(
        "memory_no_payment",
        "Linda checked your real M-Pesa messages from the last 30 days: no $amount arrived like this.",
        "Linda imeangalia jumbe zako halisi za M-Pesa za siku 30 zilizopita: hakuna malipo ya $amount kama haya yaliyoingia.",
    )

    /** A real payment of the claimed amount did arrive, and the message asks for it back. */
    fun memoryRealPayment(amount: String) = Reason(
        "memory_real_payment",
        "A real $amount did arrive on your M-Pesa. Do not send it back yourself, even if they ask. If it truly was a mistake, the sender forwards their M-PESA message to 456 or calls Safaricom.",
        "Malipo halisi ya $amount yaliingia kwenye M-Pesa yako. Usiyarudishe wewe mwenyewe, hata wakikuomba. Ikiwa kweli ni makosa, mtumaji atume ujumbe wake wa M-PESA kwa 456 au apige simu Safaricom.",
    )

    /** Pasted text with the transaction code of a genuine M-Pesa message on this phone. */
    val MEMORY_SAME_AS_REAL = Reason(
        "memory_same_as_real",
        "This matches a real M-Pesa message on your phone (same transaction code).",
        "Ujumbe huu unalingana na ujumbe halisi wa M-Pesa ulio kwenye simu yako (nambari ileile ya muamala).",
    )

    // ---- Know your senders (features/learning) ----

    /** A number the person has been in touch with for a while: a wording-only warning is softened to a caution. */
    val KNOWN_SENDER = Reason(
        "known_sender",
        "You have been in touch with this number for a while, so this is only a caution. If it asks for money, call them first.",
        "Umekuwa ukiwasiliana na nambari hii kwa muda, kwa hivyo hii ni tahadhari tu. Ukiombwa pesa, mpigie simu kwanza.",
    )

    val FIRST_ASKS_MONEY = Reason(
        "first_asks_money",
        "This is the first message from this number, and it asks you to send money.",
        "Huu ni ujumbe wa kwanza kutoka kwa nambari hii, na unakuomba utume pesa.",
    )

    val NEW_NUMBER_TRICK = Reason(
        "new_number_trick",
        "Someone says this is their new number and asks for money. Call their old number, or meet them, before you send anything.",
        "Mtu anasema hii ni nambari yake mpya na anaomba pesa. Piga nambari yake ya zamani, au mkutane, kabla hujatuma chochote.",
    )

    /**
     * The text says it is from [name], a saved contact, but came from a number that is not saved (ContactClaims). The
     * saved number is not repeated here: the person already has it, and the reason is kept in history and read aloud.
     */
    fun claimsContact(name: String) = Reason(
        "claims_contact",
        "It says it is from $name, but $name is saved in your contacts with a different number. Call $name on the saved number before you send anything.",
        "Ujumbe huu unasema ni $name, lakini $name amehifadhiwa kwenye anwani zako kwa nambari tofauti. Mpigie $name kwa nambari iliyohifadhiwa kabla hujatuma chochote.",
    )

    // ---- The inbox story (features/learning) ----

    val PAYMENT_CHANGED = Reason(
        "payment_changed",
        "This says the way to pay has changed. Scammers do this to take rent, fees or a supplier's money. Call the number you already know before you pay.",
        "Ujumbe huu unasema njia ya kulipa imebadilika. Walaghai hufanya hivi kuchukua kodi, karo au pesa za msambazaji. Piga nambari unayoijua kabla ya kulipa.",
    )

    val STORY_NEW_LINK = Reason(
        "story_new_link",
        "This sender often texts you, but never with this web address before.",
        "Mtumaji huyu hukutumia ujumbe mara kwa mara, lakini hajawahi kutuma anwani hii ya mtandao.",
    )

    // ---- Learn from your taps (features/learning) ----

    val TAPS_SAFE = Reason(
        "taps_safe",
        "You marked messages like this as safe before, so Linda is less worried about this one.",
        "Awali ulisema ujumbe kama huu ni salama, kwa hivyo Linda haina wasiwasi mwingi kuhusu huu.",
    )

    val TAPS_SCAM = Reason(
        "taps_scam",
        "You reported messages like this one before.",
        "Awali uliripoti ujumbe kama huu.",
    )

    fun forCategory(category: String): Reason = BY_CATEGORY.getValue(if (category in BY_CATEGORY) category else "other")

    /**
     * The category reason for this very message. A job or loan text is named by its topic words ("hiring", "loan"), but
     * the usual reason says it asks for a fee: when the text asks for no fee or payment, that would be untrue, so it
     * gets a "be careful" reason instead. Same code, so the trace and the radar category do not change.
     */
    fun forCategory(category: String, body: String): Reason = when {
        category == "job_fee" && !mentionsFee(body) -> JOB_NO_FEE
        category == "loan_fee" && !mentionsFee(body) -> LOAN_NO_FEE
        else -> forCategory(category)
    }

    private val JOB_NO_FEE = Reason(
        "job_fee",
        "Be careful: scammers send job offers like this and ask for a fee later. Real employers never ask you to pay to be hired.",
        "Kuwa mwangalifu: matapeli hutuma ofa za kazi kama hii, kisha huomba ada baadaye. Waajiri halisi kamwe hawakuombi ulipe ili uajiriwe.",
    )
    private val LOAN_NO_FEE = Reason(
        "loan_fee",
        "Be careful: scammers send loan offers like this and ask for a fee first. Real lenders never ask for a fee before the loan.",
        "Kuwa mwangalifu: matapeli hutuma ofa za mkopo kama hii, kisha huomba ada kwanza. Wakopeshaji halisi kamwe hawaombi ada kabla ya mkopo.",
    )

    // Asking the reader to pay, on Normalizer output. Not a bare "pay": job texts say "good pay" or "pay: 3000 daily".
    private val FEE_WORDS = listOf(
        "fee", "fees", "ada", "registration", "deposit", "charge", "charges", "processing", "kiingilio", "gharama",
        "lipa", "kulipa", "ulipe", "lipia", "to pay", "pay a", "pay the", "pay ksh", "pay kes", "tuma pesa", "tuma ksh",
        "send money", "send ksh", "send kes",
    )

    private fun mentionsFee(body: String): Boolean {
        val n = Normalizer.normalize(body)
        return FEE_WORDS.any { Regex("(?<![a-z0-9])" + Regex.escape(it) + "(?![a-z0-9])").containsMatchIn(n) }
    }

    /** "Words like ..." built from the n-grams that pushed the score up most. */
    fun wordsReason(words: List<String>): Reason = Reason(
        "ngrams",
        "Typical scam wording: " + words.joinToString(", ") { "\"$it\"" } + ".",
        "Maneno yanayotumika sana kwenye ulaghai: " + words.joinToString(", ") { "\"$it\"" } + ".",
    )
}
