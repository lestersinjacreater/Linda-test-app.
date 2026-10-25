package com.linda.app.features.learning

import com.linda.app.features.detection.MessageInput
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Learn from your taps: the personal layer moves similar messages, a little, and never past the hard rules. */
class PersonalModelTest {
    private val scorer = TestData.scorer
    private val detector = TestData.detector

    // A recruitment message the model warns on, from a phone number, with no hard sign (no link, no PIN, no fake M-Pesa).
    private val recruit = "Hello, we are hiring drivers for Uber in Nairobi. Earn up to 3000 daily. Reply YES to join our team today"
    private val recruitAgain = "Hello, we are hiring drivers for Uber in Mombasa. Earn up to 2500 daily. Reply YES to join our team now"

    private fun tap(model: PersonalModel, text: String, sender: String, scam: Boolean): PersonalModel {
        val d = scorer.explain(text, sender)
        return model.learn(d.slots, d.logit, scam)
    }

    private fun analyse(text: String, sender: String, personal: PersonalModel?) =
        detector.analyse(MessageInput(text, sender, 0L, senderInContacts = null, firstMessageFromSender = null, personalModel = personal))

    @Test fun anEmptyPersonalLayerChangesNothing() {
        for (v in TestData.vectors) {
            val a = detector.analyse(MessageInput(v.text, v.sender, 0L, v.inContacts))
            val b = detector.analyse(MessageInput(v.text, v.sender, 0L, v.inContacts, personalModel = PersonalModel()))
            assertEquals(v.id, a.level, b.level)
            assertEquals(v.id, a.score, b.score, 1e-9f)
        }
    }

    @Test fun markingSafePullsTheSameAndSimilarMessagesTowardsSafe() {
        val m = tap(PersonalModel(), recruit, "254700000060", scam = false)
        val self = m.adjustment(scorer.explain(recruit, "254700000060").slots)
        val similar = m.adjustment(scorer.explain(recruitAgain, "254700000061").slots)
        val unrelated = m.adjustment(scorer.explain("Tafadhali tuma PIN yako ya M-Pesa", "254700000062").slots)
        assertTrue("same message pulled to safe: $self", self < -0.5)
        assertTrue("similar message pulled to safe too: $similar", similar < -0.2)
        assertTrue("an unrelated message is barely touched: $unrelated", abs(unrelated) < abs(similar))
        assertEquals(1, m.examples)
    }

    @Test fun reportingPushesSimilarMessagesTowardsScam() {
        val m = tap(PersonalModel(), recruit, "254700000060", scam = true)
        assertTrue(m.adjustment(scorer.explain(recruitAgain, "254700000061").slots) > 0.2)
    }

    @Test fun thePushIsAlwaysCapped() {
        var m = PersonalModel()
        repeat(50) { m = tap(m, recruit, "254700000060", scam = false) }
        assertTrue(m.adjustment(scorer.explain(recruit, "254700000060").slots) >= -PersonalModel.CAP)
    }

    @Test fun tapsMoveAVerdictAtMostOneStepAndSayWhy() {
        var m = PersonalModel()
        repeat(50) { m = tap(m, recruit, "254700000060", scam = true) }
        val before = analyse("Hey, are we still meeting at 6?", "254700000063", null)
        val after = analyse("Hey, are we still meeting at 6?", "254700000063", m)
        assertTrue(after.level.ordinal - before.level.ordinal <= 1)
        val similar = analyse(recruitAgain, "254700000061", m)
        if (similar.level != analyse(recruitAgain, "254700000061", null).level) assertEquals("taps_scam", similar.reasons.last().code)
    }

    @Test fun aHardSignIsNeverTalkedDownByTaps() {
        val fuliza = "Dear customer, your Fuliza limit has been increased. Click http://safaricom-bonus.xyz to activate"
        var m = PersonalModel()
        repeat(20) { m = tap(m, fuliza, "254700000064", scam = false) }
        assertEquals(analyse(fuliza, "254700000065", null).level, analyse(fuliza, "254700000065", m).level)
    }

    @Test fun verifiedSendersAreNeverTouched() {
        val v = TestData.vectors.first { it.sender == "MPESA" }
        var m = PersonalModel()
        repeat(20) { m = tap(m, v.text, "254700000066", scam = true) } // someone "reports" a lookalike of a real M-Pesa text
        assertEquals(RiskLevel.SAFE, analyse(v.text, "MPESA", m).level)
    }

    @Test fun everySafeVectorStaysSafeAfterOneReportOfEachScamVector() {
        var m = PersonalModel()
        for (v in TestData.vectors.filter { it.label == "SCAM" }) m = tap(m, v.text, v.sender, scam = true)
        for (v in TestData.vectors.filter { it.label == "SAFE" && it.sender.uppercase() in scorer.verifiedSenders }) {
            assertEquals(v.id, RiskLevel.SAFE, detector.analyse(MessageInput(v.text, v.sender, 0L, v.inContacts, personalModel = m)).level)
        }
    }
}
