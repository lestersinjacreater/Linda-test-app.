package com.linda.app.features.detection

/**
 * The detection engine's public API (root CLAUDE.md and android/CLAUDE.md section 5).
 * Everything else in the app (SMS receiver, checker, inbox scan, demo mode) calls [analyse].
 * Nothing else is allowed to score messages.
 */
interface ScamDetector {
    fun analyse(input: MessageInput): Verdict

    /** Upper-case IDs of senders that are never warned on and never reported (MPESA, banks, KPLC, KRA). */
    val verifiedSenders: Set<String>

    /** Version of the model file in use (shown in the developer screen). */
    val modelVersion: String
}

data class MessageInput(
    val body: String,
    val sender: String?,                 // null when pasted or shared
    val receivedAt: Long,
    val senderInContacts: Boolean? = null,
    val firstMessageFromSender: Boolean? = null,
)

enum class RiskLevel { SAFE, CAUTION, SCAM }

/** One human-readable reason, in both languages, so History can show it in whichever language is chosen. */
data class Reason(val code: String, val english: String, val swahili: String)

data class Verdict(
    val level: RiskLevel,
    val score: Float,                    // 0.0 to 1.0
    val reasons: List<Reason>,           // never empty for CAUTION or SCAM
    val modelVersion: String,
    val category: String,                // sent_by_mistake, fake_mpesa, ... or "other"; "none" for SAFE
    val fingerprint: String,             // SimHash of the normalised text, for the radar (never the text itself)
)
