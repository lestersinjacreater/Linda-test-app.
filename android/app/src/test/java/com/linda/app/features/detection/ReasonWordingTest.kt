package com.linda.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the warnings SAY: never a claim the message does not support, and Safaricom's own advice (456, 333). */
class ReasonWordingTest {
    private val detector = TestData.detector

    @Test fun aJobTextWithoutAFeeIsNotSaidToAskForAFee() {
        val r = ReasonCatalogue.forCategory("job_fee", "Hello, we are hiring drivers for Uber in Nairobi. Earn up to 3000 daily.")
        assertEquals("job_fee", r.code) // same category and code for the trace and the radar
        assertFalse(r.english.contains("asks you to pay"))
        assertTrue(r.english.startsWith("Be careful"))
    }

    @Test fun aJobOrLoanTextThatAsksForAFeeKeepsTheFeeReason() {
        for (v in TestData.vectors.filter { it.campaign == "job_fee" || it.campaign == "loan_fee" }) {
            val r = ReasonCatalogue.forCategory(v.campaign, v.text)
            assertFalse(v.id, r.english.startsWith("Be careful"))
        }
    }

    @Test fun goodPayIsNotAFee() {
        assertTrue(ReasonCatalogue.forCategory("job_fee", "Vacancy: shop attendant, good pay. Pay: 600 daily.").english.startsWith("Be careful"))
    }

    @Test fun sentByMistakeAdviceFollowsSafaricom() {
        val r = ReasonCatalogue.forCategory("sent_by_mistake")
        assertTrue(r.english.contains("Never send it back yourself") && r.swahili.contains("Usizirudishe"))
        assertFalse("spoken aloud: no digits", r.english.any { it.isDigit() } || r.swahili.any { it.isDigit() })
        val real = ReasonCatalogue.memoryRealPayment("Ksh 2,500")
        assertTrue(real.english.contains("456") && real.english.contains("Do not send it back yourself"))
        assertTrue(ReasonCatalogue.memoryNoPayment("Ksh 2,500").english.contains("333"))
    }

    @Test fun everyReasonHasBothLanguages() {
        for (c in listOf("fake_mpesa", "sent_by_mistake", "prize", "fuliza_upgrade", "kra_refund", "job_fee", "loan_fee", "pin_request", "phishing_link", "other")) {
            val r = ReasonCatalogue.forCategory(c, "")
            assertTrue(c, r.english.isNotBlank() && r.swahili.isNotBlank() && r.english != r.swahili)
        }
    }

    @Test fun theDetectorUsesTheMessageSpecificReason() {
        val v = detector.analyse(MessageInput("Hello, we are hiring drivers for Uber in Nairobi. Earn up to 3000 daily. Reply YES to join our team today", "254700000060", 0L, false, true))
        if (v.category == "job_fee") assertTrue(v.reasons.first().english.startsWith("Be careful"))
    }
}
