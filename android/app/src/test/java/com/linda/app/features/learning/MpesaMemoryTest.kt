package com.linda.app.features.learning

import com.linda.app.features.detection.MessageInput
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M-Pesa memory: reading real receipts, finding "money arrived" claims, and checking one against the other. */
class MpesaMemoryTest {
    private val hour = MpesaMemory.HOUR_MS
    private val now = 1_800_000_000_000L
    private val realReceived =
        "SJ34K9L2QW Confirmed. You have received Ksh2,500.00 from JOHN KAMAU 0700000024 on 12/10/26 at 4:12 PM. " +
            "New M-PESA balance is Ksh3,140.00. Transaction cost, Ksh0.00."
    private val realReceivedSw =
        "SJ34K9L2QX Imethibitishwa. Umepokea Ksh1,000.00 kutoka JOHN KAMAU 0700000024 mnamo 12/10/26 saa 4:12 PM. " +
            "Salio lako jipya la M-PESA ni Ksh3,140.00."
    private val fakeConfirmation =
        "QK7RT2XY9P Confirmed. You have received Ksh2,500.00 from JOHN KAMAU on 12/10/26 at 4:12 PM. New M-PESA balance is Ksh3,140.00."
    private val mistake = "Nimekutumia 2,500 kimakosa, tafadhali nirudishie haraka"

    // ---------- reading genuine M-Pesa messages ----------
    @Test fun aRealReceiptKeepsOnlyTimeAmountAndAHashedCode() {
        val r = MpesaReceipts.parse("MPESA", realReceived, now)!!
        assertEquals(250_000L, r.amountCents)
        assertEquals(now, r.at)
        assertEquals(Money.codeHash("SJ34K9L2QW"), r.codeHash)
        assertTrue("the code itself is never stored", "SJ34K9L2QW" !in r.codeHash)
    }

    @Test fun kiswahiliReceiptsAreReadToo() {
        assertEquals(100_000L, MpesaReceipts.parse("M-PESA", realReceivedSw, now)!!.amountCents)
    }

    @Test fun onlyTheRealMpesaSenderTeachesTheMemory() {
        assertNull("the same words from a phone number are a fake", MpesaReceipts.parse("254700000777", realReceived, now))
        assertNull("pasted text has no sender", MpesaReceipts.parse(null, realReceived, now))
    }

    @Test fun sentPaidAndOtherMpesaMessagesAreNotReceipts() {
        assertNull(MpesaReceipts.parse("MPESA", "SJ34K9L2QW Confirmed. Ksh500.00 sent to JANE DOE 0700000025 on 12/10/26 at 4:12 PM.", now))
        assertNull(MpesaReceipts.parse("MPESA", "SJ34K9L2QW Confirmed. Ksh200.00 paid to SHOP on 12/10/26 at 4:12 PM.", now))
        assertNull(MpesaReceipts.parse("MPESA", "Dear customer, M-PESA will be unavailable tonight from 1 AM.", now))
    }

    // ---------- finding claims ----------
    @Test fun theSentByMistakeScamIsAClaimWithItsAmount() {
        val c = MoneyClaims.find(mistake)!!
        assertEquals(MoneyClaim.Kind.SENT_BY_MISTAKE, c.kind)
        assertEquals(250_000L, c.amountCents)
        assertEquals(150_000L, MoneyClaims.find("Hi, I sent you Ksh 1,500 by mistake. Please send it back to this number")!!.amountCents)
        assertEquals(300_000L, MoneyClaims.find("Nimekutumia 3000/= kwa makosa naomba unirudishie")!!.amountCents)
    }

    @Test fun aFakeConfirmationIsAClaimWithItsCode() {
        val c = MoneyClaims.find(fakeConfirmation)!!
        assertEquals(MoneyClaim.Kind.CONFIRMATION, c.kind)
        assertEquals(250_000L, c.amountCents)
        assertEquals("QK7RT2XY9P", c.code)
    }

    @Test fun anHonestSentYouMessageIsNotAClaim() {
        // No "by mistake" and no "send it back": a friend paying for lunch must never be flagged.
        assertNull(MoneyClaims.find("I sent you 500 for lunch, thanks!"))
        assertNull(MoneyClaims.find("Nimekutumia 2,000 ya nauli"))
        assertNull(MoneyClaims.find("Hey, are we still meeting at 6?"))
    }

    @Test fun timesDatesAndPhoneNumbersAreNotAmounts() {
        assertNull(MoneyClaims.find("I sent you money by mistake at 4:12 on 12/10/26, call 0700000777"))
    }

    // ---------- checking a claim against the memory ----------
    private fun memory(vararg receipts: Receipt, completeSince: Long = now - 30 * 24 * hour) = MpesaMemory(receipts.toList(), completeSince)
    private fun receipt(amount: Long, at: Long, code: String = "SJ34K9L2QW") = Receipt(at, amount, Money.codeHash(code))

    @Test fun noMatchingPaymentInTheLastTwoDays() {
        val m = memory(receipt(250_000, now - 72 * hour)) // too old to be "the money I just sent you"
        assertEquals(MpesaMemory.Answer.NO_REAL_PAYMENT, m.check(MoneyClaims.find(mistake)!!, now))
    }

    @Test fun aMatchingRealPaymentIsFound() {
        val m = memory(receipt(250_000, now - 3 * hour))
        assertEquals(MpesaMemory.Answer.REAL_PAYMENT, m.check(MoneyClaims.find(mistake)!!, now))
    }

    @Test fun lindaNeverClaimsNoPaymentForATimeItWasNotWatching() {
        val m = memory(completeSince = now - 5 * hour) // installed 5 hours ago, inbox not read
        assertEquals(MpesaMemory.Answer.CANNOT_TELL, m.check(MoneyClaims.find(mistake)!!, now))
    }

    @Test fun aPastedCopyOfARealMessageIsRecognisedByItsCode() {
        val m = memory(receipt(250_000, now - 10 * 24 * hour, code = "SJ34K9L2QW"))
        val pasted = MoneyClaims.find(realReceived)!!
        assertEquals(MpesaMemory.Answer.SAME_AS_REAL_MESSAGE, m.check(pasted, now, MpesaMemory.RETENTION_MS))
    }

    // ---------- the detector with the memory ----------
    private val detector = TestData.detector
    private fun analyse(text: String, sender: String?, m: MpesaMemory?, inContacts: Boolean? = false) =
        detector.analyse(MessageInput(text, sender, now, inContacts, firstMessageFromSender = true, mpesaMemory = m))

    @Test fun noRealPaymentMakesTheScamCertainAndSaysWhy() {
        val v = analyse(mistake, "254700000778", memory())
        assertEquals(RiskLevel.SCAM, v.level)
        assertEquals("memory_no_payment", v.reasons.first().code)
        assertTrue(v.reasons.first().english.contains("Ksh 2,500"))
        assertEquals("sent_by_mistake", v.category)
    }

    @Test fun aRealPaymentStillWarnsNotToSendItBackYourself() {
        val v = analyse(mistake, "254700000778", memory(receipt(250_000, now - hour)))
        assertTrue(v.level != RiskLevel.SAFE)
        assertEquals("memory_real_payment", v.reasons.first().code)
    }

    @Test fun withoutMemoryLindaBehavesExactlyAsBefore() {
        val before = analyse(mistake, "254700000778", null)
        val cannotTell = analyse(mistake, "254700000778", memory(completeSince = now))
        assertEquals(before.level, cannotTell.level)
        assertEquals(before.reasons, cannotTell.reasons)
    }

    @Test fun aContactOrPastedTextIsWarnedWithTheMemoryReason() {
        val contact = analyse("Hi, I sent you Ksh 1,500 by mistake, please send it back", "254700000030", memory(), inContacts = true)
        assertTrue(contact.level != RiskLevel.SAFE)
        assertEquals("memory_no_payment", contact.reasons.first().code)
        val pasted = detector.analyse(MessageInput("Nimekutumia 2,500 kimakosa, nirudishie", null, now, mpesaMemory = memory()))
        assertTrue(pasted.level != RiskLevel.SAFE)
        assertEquals("memory_no_payment", pasted.reasons.first().code)
    }

    @Test fun aPastedRealMpesaMessageIsSafe() {
        val m = memory(receipt(250_000, now - 10 * 24 * hour, code = "SJ34K9L2QW"))
        val v = detector.analyse(MessageInput(realReceived, null, now, mpesaMemory = m))
        assertEquals(RiskLevel.SAFE, v.level)
        assertEquals("memory_same_as_real", v.reasons.single().code)
    }

    @Test fun realMpesaAndBusinessesAreNeverTouchedByTheMemory() {
        assertEquals(RiskLevel.SAFE, analyse(realReceived, "MPESA", memory()).level)
        // A short code (a registered business) really does announce payments: the memory leaves it alone.
        assertEquals(RiskLevel.SAFE, analyse("Dear customer, you have received Ksh 5,000 from your employer. Ref SJ34K9L2QZ", "22444", memory()).level)
    }

    @Test fun everySafeTestVectorStaysSafeWithAnEmptyMemory() {
        for (v in TestData.vectors.filter { it.label == "SAFE" }) {
            val verdict = detector.analyse(MessageInput(v.text, v.sender, now, v.inContacts, null, memory()))
            assertEquals(v.id, RiskLevel.SAFE, verdict.level)
        }
    }

    @Test fun amountsAreShownTheKenyanWay() {
        assertEquals("Ksh 2,500", Money.format(250_000))
        assertEquals("Ksh 2,500.50", Money.format(250_050))
        assertNotNull(Money.cents("2,500.00"))
        assertNull("too small to reason about", Money.cents("5"))
    }
}
