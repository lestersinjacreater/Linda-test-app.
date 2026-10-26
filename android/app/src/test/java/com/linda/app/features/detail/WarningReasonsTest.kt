package com.linda.app.features.detail

import com.linda.app.features.detection.MessageInput
import com.linda.app.features.detection.Reason
import com.linda.app.features.detection.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningReasonsTest {
    private fun r(code: String) = Reason(code, "en $code", "sw $code")

    @Test fun theModelsLetterPiecesAreNeverShown() {
        val shown = WarningReasons.shown(listOf(r("other"), r("ngrams"), r("personal_number")), fakeMpesaBadge = false)
        assertEquals(listOf("other", "personal_number"), shown.map { it.code })
    }

    @Test fun theBadgeAndTheFakeMpesaReasonAreNotBothShown() {
        val reasons = listOf(r("fake_mpesa"), r("personal_number"))
        assertEquals(listOf("personal_number"), WarningReasons.shown(reasons, fakeMpesaBadge = true).map { it.code })
        assertEquals(listOf("fake_mpesa", "personal_number"), WarningReasons.shown(reasons, fakeMpesaBadge = false).map { it.code })
    }

    @Test fun atMostThreeInTheDetectorsOrder() {
        val reasons = listOf("memory_no_payment", "sent_by_mistake", "personal_number", "unknown_sender", "taps_scam").map { r(it) }
        assertEquals(listOf("memory_no_payment", "sent_by_mistake", "personal_number"), WarningReasons.shown(reasons, false).map { it.code })
    }

    @Test fun theDemoScamsStillExplainThemselves() {
        val now = 1_800_000_000_000L
        val texts = listOf(
            "254700000777" to "QK7RT2XY9P Confirmed. You have received Ksh2,500.00 from JOHN KAMAU on 12/10/26 at 4:12 PM. New M-PESA balance is Ksh3,140.00.",
            "254700000778" to "Nimekutumia 2,500 kimakosa, tafadhali nirudishie haraka",
            "254700000779" to "Hongera! Umeshinda Ksh 50,000. Tuma registration fee ya Ksh 500 upate zawadi yako.",
        )
        for ((sender, text) in texts) {
            val v = TestData.detector.analyse(MessageInput(text, sender, now, false, firstMessageFromSender = true))
            val shown = WarningReasons.shown(v.reasons, v.category == "fake_mpesa")
            assertTrue("a warning with no reason left: $text", shown.isNotEmpty())
            assertFalse(shown.any { it.code == "ngrams" })
        }
    }
}
