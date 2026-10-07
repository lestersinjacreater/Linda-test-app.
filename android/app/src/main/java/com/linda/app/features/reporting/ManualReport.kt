package com.linda.app.features.reporting

import com.linda.app.core.util.PhoneNumbers

/** The facts of a saved warning that a report is built from. There is deliberately no message text here. */
data class ReportableDetection(
    val level: String,           // "CAUTION" or "SCAM" (SAFE messages are never saved)
    val sender: String?,
    val category: String,
    val score: Float,
    val fingerprint: String,
    val modelVersion: String,
    val receivedAt: Long,
    val alreadyReported: Boolean,
)

enum class CannotReport { SAFE, NOT_A_PHONE_NUMBER, VERIFIED_SENDER, ALREADY_REPORTED, NO_FINGERPRINT }

/**
 * The "Report" button (F6). Free of Android so it can be unit tested. A manual report carries exactly the same
 * fields as an automatic one (contract 5.1): no message text, ever. The person confirms it on a screen that lists
 * those fields, and that confirmation is their consent for THIS report even if automatic reporting is switched off.
 */
object ManualReport {
    /** null means "allowed". Otherwise why not, so the screen can hide the button or show "Reported". */
    fun blockedReason(d: ReportableDetection, verifiedSenders: Set<String>): CannotReport? = when {
        d.level != "SCAM" && d.level != "CAUTION" -> CannotReport.SAFE
        d.alreadyReported -> CannotReport.ALREADY_REPORTED
        d.sender != null && d.sender.trim().uppercase() in verifiedSenders -> CannotReport.VERIFIED_SENDER // real M-Pesa, banks... are never reported
        PhoneNumbers.toMsisdn(d.sender) == null -> CannotReport.NOT_A_PHONE_NUMBER // pasted text or a sender name: nothing the radar could block
        !Regex("[0-9a-f]{16}").matches(d.fingerprint) -> CannotReport.NO_FINGERPRINT
        else -> null
    }

    /** The report exactly as it will be sent, or null if it may not be reported. */
    fun payload(d: ReportableDetection, deviceId: String, verifiedSenders: Set<String>): ReportPayload? {
        if (blockedReason(d, verifiedSenders) != null) return null
        return ReportPayload(
            sender = PhoneNumbers.toMsisdn(d.sender)!!,
            category = d.category,
            confidence = d.score.toDouble().coerceIn(0.0, 1.0),
            fingerprint = d.fingerprint,
            modelVersion = d.modelVersion,
            deviceId = deviceId,
            sentAt = d.receivedAt,
        )
    }
}
