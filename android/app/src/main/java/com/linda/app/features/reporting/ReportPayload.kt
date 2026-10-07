package com.linda.app.features.reporting

import com.linda.app.core.util.PhoneNumbers
import com.linda.app.features.detection.RiskLevel
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One report as the radar receives it (root CLAUDE.md contract 5.1). There is NO message text in it, by design. */
data class ReportPayload(
    val sender: String,        // 2547XXXXXXXX
    val category: String,
    val confidence: Double,
    val fingerprint: String,   // SimHash of the normalised text: it cannot be turned back into the message
    val modelVersion: String,
    val deviceId: String,      // random and hashed, never the phone number
    val sentAt: Long,
) {
    fun toJson(): String = JSONObject()
        .put("sender", sender)
        .put("category", category)
        .put("confidence", confidence)
        .put("fingerprint", fingerprint)
        .put("model_version", modelVersion)
        .put("device_id", deviceId)
        .put("sent_at", isoUtc(sentAt))
        .toString()

    companion object {
        /** The only keys a report may have. A test checks the JSON against this list. */
        val ALLOWED_KEYS = setOf("sender", "category", "confidence", "fingerprint", "model_version", "device_id", "sent_at")

        fun isoUtc(millis: Long): String {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            return f.format(Date(millis))
        }
    }
}

/** When is a message worth reporting? Only confident scams from a real phone number, and only if the user opted in. */
object ReportPolicy {
    fun shouldReport(level: RiskLevel, sender: String?, verifiedSenders: Set<String>, consent: Boolean): Boolean {
        if (!consent || level != RiskLevel.SCAM) return false
        if (sender == null || sender.trim().uppercase() in verifiedSenders) return false // real M-Pesa, banks, ... are never reported
        return PhoneNumbers.toMsisdn(sender) != null // a name like "PROMO" has no number the radar could block
    }
}

object DeviceIds {
    /** An anonymous device id: the first 16 hex characters of SHA-256 over a random value made at install. */
    fun fromSeed(seed: String): String =
        MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
}
