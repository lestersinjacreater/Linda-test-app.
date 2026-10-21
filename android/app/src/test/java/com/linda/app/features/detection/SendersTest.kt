package com.linda.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendersTest {
    private val verified = TestData.scorer.verifiedSenders
    private val noMeta = mapOf("has_link" to 0.0, "fake_mpesa" to 0.0)

    @Test fun sendersAreSortedIntoKinds() {
        assertEquals(SenderKind.NONE, Senders.kindOf(null, verified))
        assertEquals(SenderKind.NONE, Senders.kindOf("  ", verified))
        assertEquals(SenderKind.VERIFIED, Senders.kindOf("MPESA", verified))
        assertEquals(SenderKind.VERIFIED, Senders.kindOf("Safaricom", verified))
        assertEquals(SenderKind.PERSONAL, Senders.kindOf("+254712345678", verified))
        assertEquals(SenderKind.PERSONAL, Senders.kindOf("0712345678", verified))
        assertEquals(SenderKind.PERSONAL, Senders.kindOf("254112345678", verified))
        assertEquals(SenderKind.SHORT_CODE, Senders.kindOf("22444", verified))
        assertEquals(SenderKind.BUSINESS, Senders.kindOf("BETIKA", verified))
        assertEquals(SenderKind.BUSINESS, Senders.kindOf("eCitizen", verified))
        assertEquals(SenderKind.OTHER, Senders.kindOf("+14155550100", verified))
    }

    @Test fun namesThatCopyAProtectedBrandAreLookalikes() {
        for (s in listOf("MPESA_CARE", "M-PESA.", "SAFARICOM1", "Safaricom-Care", "Equity Bank", "KCB Bank", "M-Shwari")) {
            assertEquals(s, SenderKind.LOOKALIKE, Senders.kindOf(s, verified))
        }
    }

    @Test fun onlyNamesAndShortCodesCountAsRegistered() {
        assertTrue(Senders.isRegistered(SenderKind.BUSINESS))
        assertTrue(Senders.isRegistered(SenderKind.SHORT_CODE))
        assertTrue(Senders.isRegistered(SenderKind.LOOKALIKE))
        for (k in listOf(SenderKind.NONE, SenderKind.VERIFIED, SenderKind.PERSONAL, SenderKind.OTHER)) {
            assertFalse(k.name, Senders.isRegistered(k))
        }
    }

    @Test fun strongSignsAreFound() {
        assertTrue(Senders.hasStrongSign("Tafadhali tuma PIN yako", noMeta))
        assertTrue(Senders.hasStrongSign("You won in the Safaricom Mega Draw", noMeta))
        assertTrue(Senders.hasStrongSign("Boost your M-Pesa limit today", noMeta))
        assertTrue(Senders.hasStrongSign("Pay Ksh 250 tax to 0700000363 to receive it", noMeta))
        assertTrue(Senders.hasStrongSign("Send it to +254 712 345 678 now", noMeta))
        assertTrue(Senders.hasStrongSign("anything", mapOf("has_link" to 1.0, "fake_mpesa" to 0.0)))
        assertTrue(Senders.hasStrongSign("anything", mapOf("has_link" to 0.0, "fake_mpesa" to 1.0)))
    }

    @Test fun ordinaryBusinessTextsHaveNoStrongSign() {
        assertFalse(Senders.hasStrongSign("Deposit now and get 100% bonus up to Ksh 5,000! Paybill 290290.", noMeta))
        assertFalse(Senders.hasStrongSign("Your Tala limit has been increased to Ksh 8,000. Open the app to apply.", noMeta))
        assertFalse(Senders.hasStrongSign("Your payment of KES 1,050 for passport application has been received.", noMeta))
        assertFalse(Senders.hasStrongSign("Pinned: your spinning class is at 6", noMeta)) // "pin" only as a whole word
    }
}
