package com.linda.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorTest {
    private val detector = TestData.detector

    private fun analyse(text: String, sender: String?, inContacts: Boolean? = null, first: Boolean? = null) =
        detector.analyse(MessageInput(text, sender, 0L, inContacts, first))

    /** A real M-Pesa, bank, KPLC, KRA or OTP message flagged as a scam is a critical bug. */
    @Test fun noSafeVectorIsEverWarnedOn() {
        for (v in TestData.vectors.filter { it.label == "SAFE" }) {
            val verdict = analyse(v.text, v.sender, v.inContacts)
            assertEquals(v.id, RiskLevel.SAFE, verdict.level)
        }
    }

    @Test fun realMpesaIsNeverWarnedOnEvenWithUnknownSenderContext() {
        for (v in TestData.vectors.filter { it.sender == "MPESA" || it.sender == "M-PESA" }) {
            assertEquals(v.id, RiskLevel.SAFE, analyse(v.text, v.sender, inContacts = false, first = true).level)
        }
    }

    @Test fun scamVectorsAreCaughtExceptKnownMisses() {
        val knownMisses = setOf("job-fee-sw-01") // see ml/reports/metrics.md: a Swahili job-fee wording the model misses
        for (v in TestData.vectors.filter { it.label == "SCAM" && it.id !in knownMisses }) {
            assertTrue("${v.id} should warn", analyse(v.text, v.sender, v.inContacts).level != RiskLevel.SAFE)
        }
    }

    @Test fun sameConfirmationTextFlipsWithTheSender() {
        val text = "QK7RT2XY9P Confirmed. You have received Ksh2,500.00 from JOHN KAMAU on 12/10/26 at 4:12 PM. New M-PESA balance is Ksh3,140.00."
        val real = analyse(text, "MPESA")
        val fake = analyse(text, "254700000005")
        assertEquals(RiskLevel.SAFE, real.level)
        assertEquals(RiskLevel.SCAM, fake.level)
        assertEquals("fake_mpesa", fake.category)
        assertTrue(fake.reasons.first().english.startsWith("This looks like an M-Pesa message but did not come from M-Pesa"))
    }

    @Test fun everyWarningExplainsItselfInBothLanguages() {
        for (v in TestData.vectors.filter { it.label == "SCAM" }) {
            val verdict = analyse(v.text, v.sender)
            if (verdict.level == RiskLevel.SAFE) continue
            assertTrue("${v.id} has no reasons", verdict.reasons.isNotEmpty())
            assertTrue(verdict.reasons.all { it.english.isNotBlank() && it.swahili.isNotBlank() })
        }
    }

    @Test fun safeVerdictsCarryNoReasonsAndNoCategory() {
        val v = analyse("Hey, are we still meeting at 6?", "254700000026")
        assertEquals(RiskLevel.SAFE, v.level)
        assertTrue(v.reasons.isEmpty())
        assertEquals("none", v.category)
    }

    @Test fun contextCanPushABorderlineMessageUpButNeverMakesAnInnocentOneAScam() {
        val borderline = "Hi, I saw your number online. Can you call me? I have a business proposal for you."
        val plain = analyse(borderline, "254700000021")
        val unknown = analyse(borderline, "254700000021", inContacts = false, first = true)
        assertTrue(unknown.score > plain.score)
        assertFalse(unknown.level == RiskLevel.SCAM)
        val chat = analyse("Hey, are we still meeting at 6?", "254700000026", inContacts = false, first = true)
        assertEquals(RiskLevel.SAFE, chat.level)
    }

    @Test fun knownContactLowersTheScoreOfAMoneyChat() {
        val text = "Nimekutumia pesa ya shule, angalia M-Pesa yako."
        val stranger = analyse(text, "254700000043", inContacts = false, first = true)
        val friend = analyse(text, "254700000043", inContacts = true)
        assertTrue(friend.score < stranger.score)
    }

    @Test fun pastedTextWithNoSenderStillWorks() {
        val v = analyse("Hongera! Umeshinda Ksh 50,000. Tuma registration fee ya Ksh 500 upate zawadi yako.", null)
        assertEquals(RiskLevel.SCAM, v.level)
        assertEquals("prize", v.category)
    }

    @Test fun fingerprintIsTheSimHashOfTheNormalisedText() {
        val text = "Nimekutumia 2,500 kimakosa, tafadhali nirudishie haraka"
        assertEquals(SimHash.simhash64(Normalizer.normalize(text)), analyse(text, "254700000001").fingerprint)
    }

    @Test fun categoriesNameMostScamVectors() {
        val names = mapOf("reversal" to "sent_by_mistake")
        val wrong = TestData.vectors.filter { it.label == "SCAM" }.filter {
            Categories.categorise(it.text, it.sender, TestData.scorer.verifiedSenders) != (names[it.campaign] ?: it.campaign)
        }
        assertTrue("category misses: ${wrong.map { it.id }}", wrong.size <= 3)
    }
}
