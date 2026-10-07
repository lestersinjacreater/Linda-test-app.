package com.linda.app.features.detection

import com.linda.app.core.util.PhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataFeaturesTest {
    private val verified = setOf("MPESA", "M-PESA", "KPLC")
    private val real = "QK7RT2XY9P Confirmed. You have received Ksh2,500.00 from JOHN KAMAU on 12/10/26 at 4:12 PM."
    private fun f(text: String, sender: String?) = MetadataFeatures.compute(text, sender, verified)

    @Test fun realMpesaIsNotFake() {
        val m = f(real, "MPESA")
        assertEquals(1.0, m["mpesa_style"]!!, 0.0); assertEquals(1.0, m["sender_verified"]!!, 0.0); assertEquals(0.0, m["fake_mpesa"]!!, 0.0)
    }

    @Test fun sameTextFromAPersonalNumberOrSpoofedIdIsFake() {
        assertEquals(1.0, f(real, "254700000005")["fake_mpesa"]!!, 0.0)
        assertEquals(1.0, f(real, "MPESA_CARE")["fake_mpesa"]!!, 0.0)
    }

    @Test fun senderMatchIgnoresCaseAndSpaces() = assertEquals(1.0, f(real, " mpesa ")["sender_verified"]!!, 0.0)

    @Test fun pastedTextNeverFiresFakeMpesa() = assertEquals(0.0, f(real, null)["fake_mpesa"]!!, 0.0)

    @Test fun linksAndShorteners() {
        assertEquals(1.0, f("pay at http://x.xyz now", "254700000001")["has_link"]!!, 0.0)
        assertEquals(1.0, f("open bit.ly/abc", "254700000001")["has_link"]!!, 0.0)
        assertEquals(0.0, f("the bit.ly company", "254700000001")["has_link"]!!, 0.0)
        assertEquals(0.0, f("see itax.kra.go.ke", "KRA")["has_link"]!!, 0.0)
    }

    @Test fun personalNumberFormats() {
        for (s in listOf("254712345678", "0712345678", "+254112345678")) assertEquals(s, 1.0, f("hi", s)["sender_personal_number"]!!, 0.0)
        assertEquals(0.0, f("hi", "EQUITY")["sender_personal_number"]!!, 0.0)
    }

    @Test fun phoneNumbersBecomeMsisdn() {
        assertEquals("254712345678", PhoneNumbers.toMsisdn("+254712345678"))
        assertEquals("254712345678", PhoneNumbers.toMsisdn("0712 345 678"))
        assertEquals("254112345678", PhoneNumbers.toMsisdn("254112345678"))
        assertNull(PhoneNumbers.toMsisdn("MPESA"))
        assertNull(PhoneNumbers.toMsisdn(null))
    }
}
