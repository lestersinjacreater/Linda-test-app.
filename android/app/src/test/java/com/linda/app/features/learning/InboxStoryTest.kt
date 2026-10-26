package com.linda.app.features.learning

import com.linda.app.features.detection.MessageInput
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.SenderKind
import com.linda.app.features.detection.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The inbox story: a business's routine, a payment seen by two senders, and "the way to pay has changed". */
class InboxStoryTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val detector = TestData.detector
    private val business = "SAF_Info" // an invented sender name, not on the verified list

    private fun bonus(mb: Int, link: String = "https://safaricom.com/offers") =
        "Hongera! Umeshinda bonus ya ${mb}MB kutoka Safaricom. Bonyeza $link kudai zawadi yako leo"

    /** A story of [n] texts from [business], one a day, ending yesterday. */
    private fun bookWith(n: Int = 6, text: (Int) -> String = { bonus(100 * (it + 1)) }): StoryBook {
        val book = StoryBook()
        for (i in 0 until n) book.remember(business, SenderKind.BUSINESS, text(i), now - (n - i) * 3 * day)
        return book
    }

    private fun analyse(text: String, book: StoryBook?, sender: String = business, history: SenderHistory? = null) =
        detector.analyse(MessageInput(text, sender, now, senderInContacts = null, firstMessageFromSender = false, senderHistory = history, story = book?.facts(sender, text)))

    // ---------- 1. a business's routine ----------
    @Test fun aBusinessesRoutineTextIsNotWarnedOn() {
        val text = bonus(500)
        assertNotEquals("without a story the link and 'Safaricom' warn", RiskLevel.SAFE, analyse(text, null).level)
        assertEquals(RiskLevel.SAFE, analyse(text, bookWith()).level)
    }

    @Test fun aRoutineNeedsFiveTextsOverTwoWeeks() {
        assertNotEquals(RiskLevel.SAFE, analyse(bonus(500), bookWith(n = 3)).level) // too few texts
        val recent = StoryBook().apply { repeat(6) { remember(business, SenderKind.BUSINESS, bonus(100 * (it + 1)), now - (6 - it) * 3600_000L) } }
        assertNotEquals("six texts in six hours is not a routine", RiskLevel.SAFE, analyse(bonus(500), recent).level)
    }

    @Test fun aNewWebAddressFromAKnownSenderStillWarnsAndSaysWhy() {
        val v = analyse(bonus(500, "https://safaricom-bonus.xyz"), bookWith())
        assertNotEquals(RiskLevel.SAFE, v.level)
        assertTrue(v.reasons.any { it.code == "story_new_link" })
    }

    @Test fun aPinRequestFromAKnownSenderStillWarns() {
        val text = "Hongera! Umeshinda bonus ya 500MB kutoka Safaricom. Weka PIN yako kwa https://safaricom.com/offers kudai zawadi yako leo"
        assertNotEquals(RiskLevel.SAFE, analyse(text, bookWith()).level)
    }

    @Test fun aReportedSenderIsNeverRoutine() {
        val story = InboxStory.remember(null, bonus(100), now - 20 * day).copy(count = 9, reported = true)
        assertFalse(InboxStory.established(story, SenderKind.BUSINESS, now))
        assertEquals(null, InboxStory.clears(StoryFacts(story, codeMatch = true), SenderKind.BUSINESS, bonus(500), false, now))
    }

    @Test fun aBrandLookalikeMustEarnItForLonger() {
        val story = SenderStory(now - 20 * day, 6, emptyList(), emptySet())
        assertTrue(InboxStory.established(story, SenderKind.BUSINESS, now))
        assertFalse(InboxStory.established(story, SenderKind.LOOKALIKE, now))
    }

    @Test fun theStoryKeepsOnlyTheLatestShapes() {
        var s: SenderStory? = null
        repeat(40) { s = InboxStory.remember(s, bonus(it), now + it) }
        assertEquals(InboxStory.MAX_TEMPLATES, s!!.templates.size)
        assertEquals(40, s!!.count)
        assertEquals(setOf("safaricom.com"), s!!.domains)
    }

    @Test fun webAddressesAreRealOnesOnly() {
        assertEquals(setOf("myaccount.safaricom.co.ke", "bit.ly"), InboxStory.hosts("See https://myaccount.safaricom.co.ke or www.bit.ly/x2"))
        assertTrue("sentences glued without a space are not links", InboxStory.hosts("Confirmed.You have Ksh5.00.Amount").isEmpty())
    }

    // ---------- 2. two senders, one payment ----------
    @Test fun aBankTextAboutARealMpesaPaymentIsNotWarnedOn() {
        val bank = "Dear customer, Ksh 2,000.00 has been moved from your M-PESA to your account. Ref QK7RT2XY9P. Claim your bonus at https://familybank.example.co.ke"
        val book = StoryBook()
        assertNotEquals(RiskLevel.SAFE, analyse(bank, book, sender = "FAMILYBNK").level)
        book.remember("MPESA", SenderKind.VERIFIED, "QK7RT2XY9P Confirmed. Ksh2,000.00 transferred to Family Bank account. New M-PESA balance is Ksh140.00.", now - 60_000)
        assertEquals(RiskLevel.SAFE, analyse(bank, book, sender = "FAMILYBNK").level)
    }

    @Test fun onlyTheRealMpesaSenderTeachesCodes() {
        val book = StoryBook()
        book.remember("254700000080", SenderKind.PERSONAL, "QK7RT2XY9P Confirmed. You have received Ksh2,000.00", now)
        assertFalse(book.facts("FAMILYBNK", "Ref QK7RT2XY9P").codeMatch)
    }

    // ---------- 3. the way to pay has changed ----------
    @Test fun aStrangerSayingThePaybillChangedIsACaution() {
        val text = "Hello, kindly note our new till number for rent is 556677. Thank you."
        val v = analyse(text, null, sender = "254700000071")
        assertEquals(RiskLevel.CAUTION, v.level)
        assertEquals("payment_changed", v.reasons.first().code)
        assertTrue(InboxStory.paymentChanged("Habari, kodi sasa ilipwe kwa paybill mpya 400200, akaunti 12B."))
    }

    @Test fun someoneYouAreInTouchWithCanChangeTheirTill() {
        val text = "Hello, kindly note our new till number for rent is 556677. Thank you."
        val known = SenderHistory(now - 60 * day, received = 9, sentByYou = 5, firstSentByYou = now - 60 * day, warned = 0)
        assertEquals(RiskLevel.SAFE, analyse(text, null, sender = "254700000071", history = known).level)
    }

    @Test fun anOrdinaryPaymentTextIsNotAChange() {
        assertFalse(InboxStory.paymentChanged("Please pay to till 556677 as usual, thanks"))
        assertFalse(InboxStory.paymentChanged("My plans have changed, see you tomorrow"))
    }
}
