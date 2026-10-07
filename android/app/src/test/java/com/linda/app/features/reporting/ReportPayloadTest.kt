package com.linda.app.features.reporting

import com.linda.app.features.detection.RiskLevel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPayloadTest {
    private val verified = setOf("MPESA", "M-PESA", "KPLC", "EQUITY")
    private val payload = ReportPayload(
        sender = "254712345678", category = "fake_mpesa", confidence = 0.93, fingerprint = "a3f09c1e7b2d4f80",
        modelVersion = "2026.10.07-1", deviceId = "9c1f0a2b3d4e5f60", sentAt = 1_800_000_000_000L,
    )

    @Test fun reportHasExactlyTheContractFieldsAndNoMessageText() {
        val json = JSONObject(payload.toJson())
        assertEquals(ReportPayload.ALLOWED_KEYS, json.keys().asSequence().toSet())
        assertEquals("254712345678", json.getString("sender"))
        assertEquals("2027-01-15T08:00:00Z", json.getString("sent_at"))
        assertFalse(payload.toJson().contains("text") || payload.toJson().contains("body"))
    }

    @Test fun onlyConfidentScamsFromRealNumbersAreReportedAndOnlyWithConsent() {
        assertTrue(ReportPolicy.shouldReport(RiskLevel.SCAM, "+254712345678", verified, consent = true))
        assertFalse("no consent", ReportPolicy.shouldReport(RiskLevel.SCAM, "254712345678", verified, consent = false))
        assertFalse("caution is not reported", ReportPolicy.shouldReport(RiskLevel.CAUTION, "254712345678", verified, consent = true))
        assertFalse("safe is not reported", ReportPolicy.shouldReport(RiskLevel.SAFE, "254712345678", verified, consent = true))
        assertFalse("pasted text has no sender", ReportPolicy.shouldReport(RiskLevel.SCAM, null, verified, consent = true))
    }

    @Test fun verifiedSendersAreNeverReported() {
        for (s in listOf("MPESA", "mpesa", " M-PESA ", "KPLC", "EQUITY")) {
            assertFalse(s, ReportPolicy.shouldReport(RiskLevel.SCAM, s, verified, consent = true))
        }
    }

    @Test fun senderNamesWithoutANumberCannotBeReported() {
        assertFalse(ReportPolicy.shouldReport(RiskLevel.SCAM, "PROMO", verified, consent = true))
    }

    @Test fun deviceIdIsAnonymousStableHex() {
        val id = DeviceIds.fromSeed("a-random-uuid")
        assertEquals(16, id.length)
        assertTrue(id.all { it in "0123456789abcdef" })
        assertEquals(id, DeviceIds.fromSeed("a-random-uuid"))
        assertFalse(id == DeviceIds.fromSeed("another-uuid"))
    }
}
