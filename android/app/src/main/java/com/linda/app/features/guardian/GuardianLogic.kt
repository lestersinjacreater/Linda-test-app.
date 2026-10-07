package com.linda.app.features.guardian

import com.linda.app.core.util.PhoneNumbers
import com.linda.app.features.detection.RiskLevel

/**
 * Family Guardian rules, kept free of Android so they can be unit tested (android/CLAUDE.md F11).
 *
 * The idea: a parent (the protected person) names a guardian. When the parent receives a SCAM-level message, Linda
 * sends the guardian ONE short SMS: who, what kind of scam, and when. Never the message text, never the scammer's number.
 */
object GuardianRules {
    /** At most one alert per scammer number in this window, so one scammer sending ten texts does not spam the guardian. */
    const val RATE_LIMIT_MS = 6L * 60 * 60 * 1000

    /** A short name keeps the alert inside one 160-character SMS. */
    const val MAX_NAME_LENGTH = 20
}

/** Why an alert was or was not sent. The screen shows the reason, and tests check each one. */
sealed class AlertDecision {
    object Send : AlertDecision()
    data class Skip(val reason: SkipReason) : AlertDecision()
}

enum class SkipReason { NOT_A_SCAM, GUARDIAN_OFF, NO_GUARDIAN_NUMBER, NO_NAME, NO_SMS_PERMISSION, NO_SENDER, VERIFIED_SENDER, RATE_LIMITED }

object GuardianPolicy {
    fun decide(
        level: RiskLevel,
        enabled: Boolean,
        guardianNumber: String?,
        protectedName: String?,
        hasSmsPermission: Boolean,
        sender: String?,
        verifiedSenders: Set<String>,
        lastAlertForSenderAt: Long?,
        now: Long,
    ): AlertDecision {
        if (level != RiskLevel.SCAM) return AlertDecision.Skip(SkipReason.NOT_A_SCAM)
        if (!enabled) return AlertDecision.Skip(SkipReason.GUARDIAN_OFF)
        if (PhoneNumbers.toMsisdn(guardianNumber) == null) return AlertDecision.Skip(SkipReason.NO_GUARDIAN_NUMBER)
        if (protectedName.isNullOrBlank()) return AlertDecision.Skip(SkipReason.NO_NAME)
        if (!hasSmsPermission) return AlertDecision.Skip(SkipReason.NO_SMS_PERMISSION)
        if (sender.isNullOrBlank()) return AlertDecision.Skip(SkipReason.NO_SENDER) // pasted text: nothing to rate-limit on, and the person checked it themselves
        if (sender.trim().uppercase() in verifiedSenders) return AlertDecision.Skip(SkipReason.VERIFIED_SENDER)
        if (lastAlertForSenderAt != null && now - lastAlertForSenderAt in 0 until GuardianRules.RATE_LIMIT_MS) {
            return AlertDecision.Skip(SkipReason.RATE_LIMITED)
        }
        return AlertDecision.Send
    }

    /** The key the rate limit is kept under: the phone number when there is one, else the sender name, upper case. */
    fun senderKey(sender: String): String = PhoneNumbers.toMsisdn(sender) ?: sender.trim().uppercase()
}

/** The text the guardian receives. */
object GuardianMessage {
    private val SCAM_TYPE = mapOf(
        "sent_by_mistake" to ("\"sent by mistake\"" to "\"kutuma kimakosa\""),
        "fake_mpesa" to ("fake M-PESA message" to "ujumbe wa M-PESA wa kughushi"),
        "prize" to ("prize" to "zawadi"),
        "fuliza_upgrade" to ("Fuliza limit" to "kiwango cha Fuliza"),
        "kra_refund" to ("KRA refund" to "marejesho ya KRA"),
        "job_fee" to ("job fee" to "ada ya kazi"),
        "loan_fee" to ("loan fee" to "ada ya mkopo"),
        "pin_request" to ("PIN request" to "ombi la PIN"),
        "phishing_link" to ("phishing link" to "kiungo cha ulaghai"),
    )

    /** Cut a name to fit, at the end of a word where possible. */
    fun shortName(name: String): String {
        val n = name.trim().replace(Regex("\\s+"), " ")
        return if (n.length <= GuardianRules.MAX_NAME_LENGTH) n else n.take(GuardianRules.MAX_NAME_LENGTH).trimEnd()
    }

    /**
     * "Linda alert: Mum received a suspected "sent by mistake" scam at 4:12 PM. Consider calling them."
     * English is worded without he/she/her/him; Swahili has no gendered pronoun.
     * [timeText] is already formatted (e.g. "4:12 PM"). No message text and no scammer number, by design.
     */
    fun alert(language: String, protectedName: String, category: String, timeText: String): String {
        val name = shortName(protectedName)
        val type = SCAM_TYPE[category]
        return if (language == "sw") {
            val what = type?.let { "ulaghai unaoshukiwa wa ${it.second}" } ?: "ujumbe unaoshukiwa kuwa ulaghai"
            "Onyo la Linda: $name amepokea $what saa $timeText. Fikiria kumpigia simu."
        } else {
            val what = type?.let { "a suspected ${it.first} scam" } ?: "a suspected scam message"
            "Linda alert: $name received $what at $timeText. Consider calling them."
        }
    }

    fun test(language: String, protectedName: String): String {
        val name = shortName(protectedName)
        return if (language == "sw") "Jaribio la Linda: $name amekuweka kuwa mlezi wake. Hili ni jaribio, hakuna ulaghai."
        else "Linda test: $name chose you as their guardian. This is only a test, there is no scam."
    }
}
