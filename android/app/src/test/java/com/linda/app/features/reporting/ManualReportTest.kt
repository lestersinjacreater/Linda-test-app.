package com.linda.app.features.reporting

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualReportTest {
    private val verified = setOf("MPESA", "M-PESA", "KPLC", "EQUITY")
    private val base = ReportableDetection(
        level = "SCAM", sender = "+254700000777", category = "fake_mpesa", score = 0.97f, fingerprint = "a3f09c1e7b2d4f80",
        modelVersion = "2026.10.07-1", receivedAt = 1_800_000_000_000L, alreadyReported = false,
    )

    @Test fun aScamFromARealNumberCanBeReported() = assertNull(ManualReport.blockedReason(base, verified))

    @Test fun aCautionMessageCanBeReportedToo() = assertNull(ManualReport.blockedReason(base.copy(level = "CAUTION", score = 0.6f), verified))

    @Test fun safeMessagesVerifiedSendersAndNamesWithoutANumberCannotBeReported() {
        assertEquals(CannotReport.SAFE, ManualReport.blockedReason(base.copy(level = "SAFE"), verified))
        for (s in listOf("MPESA", " mpesa ", "KPLC")) assertEquals(s, CannotReport.VERIFIED_SENDER, ManualReport.blockedReason(base.copy(sender = s), verified))
        assertEquals(CannotReport.NOT_A_PHONE_NUMBER, ManualReport.blockedReason(base.copy(sender = "PROMO"), verified))
        assertEquals(CannotReport.NOT_A_PHONE_NUMBER, ManualReport.blockedReason(base.copy(sender = null), verified))
    }

    @Test fun aMessageIsReportedOnlyOnce() = assertEquals(CannotReport.ALREADY_REPORTED, ManualReport.blockedReason(base.copy(alreadyReported = true), verified))

    @Test fun aMissingFingerprintBlocksTheReportInsteadOfSendingJunk() {
        assertEquals(CannotReport.NO_FINGERPRINT, ManualReport.blockedReason(base.copy(fingerprint = ""), verified))
        assertEquals(CannotReport.NO_FINGERPRINT, ManualReport.blockedReason(base.copy(fingerprint = "xyz"), verified))
    }

    @Test fun thePayloadIsExactlyTheContractFieldsWithTheNumberNormalised() {
        val p = ManualReport.payload(base, "9c1f0a2b3d4e5f60", verified)!!
        assertEquals("254700000777", p.sender)
        assertEquals(ReportPayload.ALLOWED_KEYS, JSONObject(p.toJson()).keys().asSequence().toSet())
        assertEquals(0.97, p.confidence, 1e-6)
    }

    @Test fun thePayloadNeverContainsTheMessageBecauseTheInputHasNone() {
        val json = ManualReport.payload(base, "9c1f0a2b3d4e5f60", verified)!!.toJson()
        assertFalse("text" in json || "body" in json || "message" in json)
    }

    @Test fun noPayloadIsBuiltWhenReportingIsNotAllowed() {
        assertNull(ManualReport.payload(base.copy(sender = "MPESA"), "9c1f0a2b3d4e5f60", verified))
        assertNull(ManualReport.payload(base.copy(alreadyReported = true), "9c1f0a2b3d4e5f60", verified))
    }

    @Test fun confidenceStaysBetweenZeroAndOne() {
        assertNotNull(ManualReport.payload(base.copy(score = 1.0f), "9c1f0a2b3d4e5f60", verified))
        assertTrue(ManualReport.payload(base.copy(score = 1.2f), "9c1f0a2b3d4e5f60", verified)!!.confidence <= 1.0)
    }
}
