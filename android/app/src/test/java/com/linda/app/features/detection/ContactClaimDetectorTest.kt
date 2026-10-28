package com.linda.app.features.detection

import com.linda.app.features.learning.SenderHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Hi, it's Mum" from a number that is not Mum's saved one: at least a CAUTION, explained in English and Swahili. */
class ContactClaimDetectorTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val detector = TestData.detector
    private val text = "Hi, it's Mum. My phone is off, send me 2000 on this line please"

    private fun analyse(
        body: String = text, sender: String = "254700000050", inContacts: Boolean? = false,
        claimed: String? = "Mum", history: SenderHistory? = null,
    ) = detector.analyse(MessageInput(body, sender, now, inContacts, firstMessageFromSender = false, senderHistory = history, claimedContact = claimed))

    @Test fun aClaimedContactFromAnotherNumberIsACautionWithTheReasonFirst() {
        val v = analyse()
        assertNotEquals(RiskLevel.SAFE, v.level)
        val r = v.reasons.first()
        assertEquals("claims_contact", r.code)
        assertTrue(r.english.contains("It says it is from Mum") && r.english.contains("different number"))
        assertTrue(r.swahili.contains("Mum") && r.swahili.contains("nambari tofauti"))
        assertFalse("spoken aloud: no digits", r.english.any { it.isDigit() } || r.swahili.any { it.isDigit() })
    }

    @Test fun evenHarmlessWordingIsACaution() {
        assertEquals(RiskLevel.CAUTION, analyse("Hi, it's Mum. Call me when you are free").level)
    }

    @Test fun noClaimMeansNoChange() {
        val body = "Call me when you are free"
        assertEquals(analyse(body, claimed = null).level, detector.analyse(MessageInput(body, "254700000050", now, false, false)).level)
    }

    @Test fun theSavedNumberItselfIsNotWarned() {
        assertTrue(analyse("Hi, it's Mum. Call me when you are free", inContacts = true).reasons.none { it.code == "claims_contact" })
    }

    @Test fun unknownContactsAccessIsNotTreatedAsAClaim() {
        assertTrue(analyse(inContacts = null).reasons.none { it.code == "claims_contact" })
    }

    @Test fun aNumberThePersonIsAlreadyInTouchWithIsNotWarnedForTheClaim() {
        val texted = SenderHistory(now - 30 * day, received = 3, sentByYou = 4, firstSentByYou = now - 30 * day, warned = 0)
        assertTrue(analyse("Hi, it's Mum. Call me when you are free", history = texted).reasons.none { it.code == "claims_contact" })
    }

    @Test fun businessAndVerifiedSendersAreLeftAlone() {
        assertTrue(analyse("This is Mum's favourite offer!", sender = "NAIVAS").reasons.none { it.code == "claims_contact" })
        assertTrue(analyse("Confirmed. Ksh500 sent. This is Mum", sender = "MPESA").reasons.none { it.code == "claims_contact" })
    }
}
