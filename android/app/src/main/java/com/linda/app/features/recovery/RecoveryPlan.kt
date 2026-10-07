package com.linda.app.features.recovery

/** How long ago the money was sent. Decides whether the 24-hour reversal request can still work. */
enum class Timing { JUST_NOW, WITHIN_24H, OVER_24H }

/** Everything the plan depends on. [amountKes] is null when the person did not say. */
data class RecoveryInput(val timing: Timing, val amountKes: Int?, val paidFromBank: Boolean)

/** One thing to do. The screen shows the words (strings.xml, English and Swahili) for each id. */
enum class StepId {
    STOP,               // do not reply, do not send more
    EVIDENCE,           // keep messages, take screenshots
    REVERSAL,           // forward YOUR M-PESA confirmation to the reversal short code (only inside the window)
    CALL_CARE,          // call customer care now
    CALL_CARE_LATE,     // the window has passed: still call, but set expectations
    REPORT_SCAM,        // forward the scam message to the fraud short code
    BANK,               // paid from a bank account or card
    POLICE,             // large amount: report to the police / DCI and get an OB number
    POLICE_OPTIONAL,    // small amount: reporting is still allowed and helpful
    BEWARE_RECOVERY,    // recovery scams
}

object RecoveryPlan {
    /** The steps, in the order to do them. Pure logic, so it is unit tested without a phone. */
    fun steps(input: RecoveryInput): List<StepId> = buildList {
        add(StepId.STOP)
        add(StepId.EVIDENCE)
        if (input.timing == Timing.OVER_24H) {
            add(StepId.CALL_CARE_LATE)
        } else {
            add(StepId.REVERSAL)
            add(StepId.CALL_CARE)
        }
        add(StepId.REPORT_SCAM)
        if (input.paidFromBank) add(StepId.BANK)
        val large = (input.amountKes ?: 0) >= RecoveryConfig.POLICE_REPORT_FROM_KES
        add(if (large) StepId.POLICE else StepId.POLICE_OPTIONAL)
        add(StepId.BEWARE_RECOVERY)
    }

    /** True while time is the main danger (the reversal window is still open). */
    fun isUrgent(timing: Timing) = timing != Timing.OVER_24H
}

/** The facts for the pre-filled report. Anything the person did not give stays null and is shown as "not provided". */
data class ReportFacts(
    val scammerNumber: String?,
    val amountKes: Int?,
    val paidToNumber: String?,
    val transactionCode: String?,
    val scamReceivedAt: String?,   // already formatted for people, e.g. "12 Oct 2026, 4:12 PM"
    val scamCategory: String?,     // one of the radar categories, e.g. "fake_mpesa"
    val scamMessage: String?,      // included ONLY when the person chose to include it
)

/**
 * Builds the text a person copies into an SMS, a call script, a bank form or a police statement.
 * Both languages exist side by side. It is only ever shown and copied on this phone; Linda never sends it anywhere.
 */
object ReportText {
    private val WHAT_IT_LOOKED_LIKE = mapOf(
        "fake_mpesa" to ("a fake M-PESA confirmation message sent from an ordinary phone number" to "ujumbe wa kughushi unaofanana na uthibitisho wa M-PESA uliotumwa kutoka nambari ya kawaida ya simu"),
        "sent_by_mistake" to ("a message saying money was sent to me by mistake and asking me to send it back" to "ujumbe unaosema pesa zilitumwa kwangu kimakosa na kuniomba nizirudishe"),
        "prize" to ("a message saying I had won a prize and must pay a fee" to "ujumbe unaosema nimeshinda zawadi na lazima nilipe ada"),
        "fuliza_upgrade" to ("a message offering a Fuliza limit increase for a fee or a link" to "ujumbe unaotoa nyongeza ya kiwango cha Fuliza kwa ada au kiungo"),
        "kra_refund" to ("a message claiming a KRA refund or penalty" to "ujumbe unaodai marejesho au faini ya KRA"),
        "job_fee" to ("a job offer that asked me to pay a fee" to "ofa ya kazi iliyonitaka nilipe ada"),
        "loan_fee" to ("a loan offer that asked me to pay a fee first" to "ofa ya mkopo iliyonitaka nilipe ada kwanza"),
        "pin_request" to ("a message asking for my M-PESA PIN" to "ujumbe ulioomba PIN yangu ya M-PESA"),
        "phishing_link" to ("a message with a suspicious link" to "ujumbe wenye kiungo cha kutiliwa shaka"),
    )

    fun build(language: String, facts: ReportFacts): String {
        val sw = language == "sw"
        val missing = if (sw) "(sijaitoa)" else "(not provided)"
        val amount = facts.amountKes?.let { String.format(java.util.Locale.US, "Ksh %,d", it) } ?: missing // fixed format: a phone's locale must not change the digits
        val looked = WHAT_IT_LOOKED_LIKE[facts.scamCategory ?: ""]?.let { if (sw) it.second else it.first }
            ?: if (sw) "ujumbe wa ulaghai" else "a scam message"
        val lines = mutableListOf<String>()
        if (sw) {
            lines += "Ninaripoti ulaghai unaoshukiwa wa pesa za simu (M-PESA)."
            lines += "- Tarehe na saa ya ujumbe wa ulaghai: ${facts.scamReceivedAt ?: missing}"
            lines += "- Nambari ya tapeli: ${facts.scammerNumber ?: missing}"
            lines += "- Nilituma $amount kwa nambari: ${facts.paidToNumber ?: missing}"
            lines += "- Msimbo wa muamala wa M-PESA: ${facts.transactionCode ?: missing}"
            lines += "- Kilichotokea: Nilipokea $looked. Niliuamini na nikatuma pesa."
            facts.scamMessage?.let { lines += "- Ujumbe niliopokea: \"$it\"" }
            lines += "Tafadhali chunguzeni, zuieni nambari hii na mnisaidie kurudisha pesa zangu. Nina picha za skrini."
        } else {
            lines += "I am reporting suspected mobile-money (M-PESA) fraud."
            lines += "- Date and time of the scam message: ${facts.scamReceivedAt ?: missing}"
            lines += "- Scammer's number: ${facts.scammerNumber ?: missing}"
            lines += "- I sent $amount to: ${facts.paidToNumber ?: missing}"
            lines += "- M-PESA transaction code: ${facts.transactionCode ?: missing}"
            lines += "- What happened: I received $looked. I believed it and sent the money."
            facts.scamMessage?.let { lines += "- The message I received: \"$it\"" }
            lines += "Please investigate, block this number and help me recover my money. I have screenshots."
        }
        return lines.joinToString("\n")
    }

    /** M-PESA transaction codes are 10 letters and digits. Returns the cleaned code, or null if it is not one. */
    fun cleanTransactionCode(raw: String): String? {
        val code = raw.trim().uppercase()
        return if (Regex("[A-Z0-9]{10}").matches(code) && code.any { it.isDigit() } && code.any { it.isLetter() }) code else null
    }
}
