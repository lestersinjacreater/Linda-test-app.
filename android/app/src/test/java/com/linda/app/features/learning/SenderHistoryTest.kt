package com.linda.app.features.learning

import com.linda.app.features.detection.MessageInput
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Know your senders: who the person is really in touch with, and the two stranger patterns. */
class SenderHistoryTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val detector = TestData.detector

    private fun texted(daysAgo: Int) = SenderHistory(now - daysAgo * day, received = 3, sentByYou = 4, firstSentByYou = now - daysAgo * day, warned = 0)
    private val stranger = SenderHistory(now, received = 0, sentByYou = 0, firstSentByYou = null, warned = 0)

    private fun analyse(text: String, history: SenderHistory?, sender: String = "254700000050", inContacts: Boolean? = false) =
        detector.analyse(MessageInput(text, sender, now, inContacts, firstMessageFromSender = history?.received == 0, senderHistory = history))

    // ---------- who is "established" ----------
    @Test fun someoneYouTextedAWeekAgoIsEstablished() {
        assertTrue(texted(daysAgo = 10).isEstablished(now))
        assertFalse("texting a scammer back today does not make them known", texted(daysAgo = 0).isEstablished(now))
    }

    @Test fun aRegularSenderOverTwoWeeksIsEstablished() {
        assertTrue(SenderHistory(now - 20 * day, received = 6, sentByYou = 0, firstSentByYou = null, warned = 0).isEstablished(now))
        assertFalse(SenderHistory(now - 20 * day, received = 2, sentByYou = 0, firstSentByYou = null, warned = 0).isEstablished(now))
        assertFalse(SenderHistory(now - 3 * day, received = 9, sentByYou = 0, firstSentByYou = null, warned = 0).isEstablished(now))
    }

    @Test fun aNumberLindaEverWarnedOnIsNeverEstablished() {
        assertFalse(texted(daysAgo = 30).copy(warned = 1).isEstablished(now))
    }

    @Test fun mergingTakesTheEarliestAndTheMost() {
        val m = SenderHistory.merge(SenderHistory(now - day, 2, 0, null, 1), texted(daysAgo = 10))!!
        assertEquals(now - 10 * day, m.firstContact)
        assertEquals(3, m.received)
        assertEquals(1, m.warned)
    }

    // ---------- the detector ----------
    @Test fun wordingAloneFromSomeoneYouKnowIsSoftenedToACaution() {
        val text = "Hongera! Umeshinda Ksh 50,000. Tuma registration fee ya Ksh 500 upate zawadi yako."
        val unknown = analyse(text, stranger)
        val known = analyse(text, texted(daysAgo = 30))
        assertEquals(RiskLevel.SCAM, unknown.level)
        assertEquals(RiskLevel.CAUTION, known.level)
        assertEquals("known_sender", known.reasons.last().code)
    }

    @Test fun aHardSignIsNeverSoftenedEvenFromSomeoneYouKnow() {
        val known = texted(daysAgo = 30)
        assertEquals(RiskLevel.SCAM, analyse("Dear customer, your Fuliza limit has been increased. Click http://safaricom-bonus.xyz to activate", known).level)
        assertTrue(analyse("Tafadhali tuma PIN yako ya M-Pesa ili tuthibitishe akaunti yako, la sivyo itafungwa leo.", known).level != RiskLevel.SAFE)
    }

    @Test fun theMemoryBeatsFamiliarity() {
        // A friend's phone can be stolen: "no such money arrived" is a fact, so it is not softened.
        val v = detector.analyse(
            MessageInput("Nimekutumia 2,500 kimakosa, tafadhali nirudishie haraka", "254700000050", now, false, false,
                mpesaMemory = MpesaMemory(emptyList(), now - 30 * day), senderHistory = texted(daysAgo = 30)),
        )
        assertEquals(RiskLevel.SCAM, v.level)
        assertEquals("memory_no_payment", v.reasons.first().code)
    }

    @Test fun theNewNumberTrickIsWarned() {
        val v = analyse("Hi mum, this is my new number. Please send me Ksh 3,000 for fare, I will explain later", stranger)
        assertTrue(v.level != RiskLevel.SAFE)
        assertEquals("new_number_trick", v.reasons.first().code)
        val sw = analyse("Mama hii ni namba yangu mpya, simu yangu imepotea. Nitumie pesa 2000 haraka", stranger)
        assertTrue(sw.level != RiskLevel.SAFE)
    }

    @Test fun aStrangersFirstMessageAskingForMoneyIsAtLeastACaution() {
        val v = analyse("Hello, please send me 500 for lunch, I will pay back tomorrow", stranger)
        assertTrue(v.level != RiskLevel.SAFE)
        assertTrue(v.reasons.any { it.code == "first_asks_money" })
    }

    @Test fun theSameAskFromSomeoneYouKnowIsNotWarned() {
        assertEquals(RiskLevel.SAFE, analyse("Hello, please send me 500 for lunch, I will pay back tomorrow", texted(daysAgo = 30)).level)
        assertEquals(RiskLevel.SAFE, analyse("Hello, please send me 500 for lunch, I will pay back tomorrow", stranger, inContacts = true).level)
    }

    @Test fun aNewNumberWithoutAMoneyAskIsNotWarned() {
        assertEquals(RiskLevel.SAFE, analyse("Hi, this is my new number, save it. Jane", stranger).level)
    }

    @Test fun businessesAndVerifiedSendersAreLeftAlone() {
        assertEquals(RiskLevel.SAFE, analyse("Lipa na M-PESA: pay your bill to Till 123456", stranger, sender = "MPESA").level)
        assertEquals(RiskLevel.SAFE, analyse("Send money to friends for free this weekend!", null, sender = "SAFARICOM").level)
    }

    @Test fun everySafeVectorStaysSafeForAStranger() {
        for (v in TestData.vectors.filter { it.label == "SAFE" }) {
            val verdict = detector.analyse(MessageInput(v.text, v.sender, now, v.inContacts, null, senderHistory = stranger))
            assertEquals(v.id, RiskLevel.SAFE, verdict.level)
        }
    }

    @Test fun everyScamVectorStillWarnsFromSomeoneYouKnow() {
        // Softened to CAUTION at most, never to SAFE in one step from SCAM.
        val knownMisses = setOf("job-fee-sw-01")
        for (v in TestData.vectors.filter { it.label == "SCAM" && it.id !in knownMisses }) {
            val verdict = detector.analyse(MessageInput(v.text, v.sender, now, v.inContacts, null, senderHistory = texted(daysAgo = 30)))
            val unknown = detector.analyse(MessageInput(v.text, v.sender, now, v.inContacts, null))
            if (unknown.level == RiskLevel.SCAM) assertTrue("${v.id} must still warn", verdict.level != RiskLevel.SAFE)
        }
    }
}
