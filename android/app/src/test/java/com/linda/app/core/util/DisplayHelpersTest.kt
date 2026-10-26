package com.linda.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayHelpersTest {
    @Test fun phoneNumbersReadTheWayKenyansWriteThem() {
        assertEquals("0700 000 777", PhoneNumbers.pretty("254700000777"))
        assertEquals("0712 345 678", PhoneNumbers.pretty("+254712345678"))
        assertEquals("0110 123 456", PhoneNumbers.pretty("0110123456"))
        assertEquals("MPESA", PhoneNumbers.pretty("MPESA"))
    }

    @Test fun agesRoundDownToTheBiggestWholeUnit() {
        val now = 1_800_000_000_000L
        val minute = 60_000L
        assertEquals(RelativeTime.Unit.NOW to 0, RelativeTime.of(now - 30_000, now))
        assertEquals(RelativeTime.Unit.MINUTES to 6, RelativeTime.of(now - 6 * minute, now))
        assertEquals(RelativeTime.Unit.HOURS to 3, RelativeTime.of(now - 200 * minute, now))
        assertEquals(RelativeTime.Unit.DAYS to 2, RelativeTime.of(now - 50 * 60 * minute, now))
        assertEquals(RelativeTime.Unit.NOW to 0, RelativeTime.of(now + minute, now)) // a clock a little ahead: never "-1 min"
    }
}
