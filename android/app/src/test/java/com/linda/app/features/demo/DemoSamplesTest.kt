package com.linda.app.features.demo

import com.linda.app.features.detection.MessageInput
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Demo mode on stage (docs/demo-script.md): every scam warns, the last two never do. */
class DemoSamplesTest {
    private fun analyse(s: DemoSample) =
        TestData.detector.analyse(MessageInput(s.body, s.sender, 1_800_000_000_000L, senderInContacts = false, firstMessageFromSender = true))

    @Test fun everyScamWarnsAndTheLastTwoDoNot() {
        val all = DemoSamples.all
        for (s in all.dropLast(2)) assertNotEquals(s.body, RiskLevel.SAFE, analyse(s).level)
        for (s in all.takeLast(2)) assertEquals(s.body, RiskLevel.SAFE, analyse(s).level)
    }

    @Test fun theLearningMomentsLeadWithTheirOwnReason() {
        val first = DemoSamples.all.associate { it.sender to analyse(it).reasons.firstOrNull()?.code }
        assertEquals("new_number_trick", first["254700000782"])
        assertEquals("payment_changed", first["254700000783"])
    }
}
