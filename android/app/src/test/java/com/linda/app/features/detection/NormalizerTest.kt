package com.linda.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizerTest {
    private fun n(s: String) = Normalizer.normalize(s)

    @Test fun disguisesAreUndone() {
        assertEquals("kimakosa", n("K1m@kosa"))
        assertEquals("mpesa", n("M-P3SA"))
        assertEquals("mpesa", n("m pesa"))
        assertEquals("mpesa", n("M.PESA"))
        assertEquals("tuma leo", n("t u m a leo"))
        assertEquals("password", n("p@\$\$w0rd"))
        assertEquals("zawadi yako", n("zawadi yak0."))
        assertEquals("tuma sasa", n("tum4 sasa"))
        assertEquals("hi", n("😀 Hi!!"))
        assertEquals("", n(""))
    }

    @Test fun amountsAndNumbersSurvive() {
        assertEquals("2500", n("2,500"))
        assertEquals("ksh 2500 00", n("Ksh 2,500.00"))
        assertEquals("ksh3140 00", n("Ksh3,140.00"))
        assertEquals("ksh500", n("Ksh500"))
        assertEquals("ksh3 50 only", n("Ksh3.50 only"))
        assertEquals("otp is 482913", n("OTP is 482913"))
    }

    @Test fun transactionCodeDigitsAreNotLeetspeak() {
        assertEquals("sj34k9l2qw confirmed", n("SJ34K9L2QW Confirmed"))
        assertEquals("rk81mn3pq4 confirmed", n("RK81MN3PQ4 Confirmed"))
    }
}
