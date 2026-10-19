package com.linda.app.features.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallRiskTest {
    private val now = 10_000_000_000L
    private val minute = 60_000L

    @Test fun aScamMessageEightMinutesAgoWarnsWithTheMinutes() {
        assertEquals(CallWarning.RecentScamMessage(8), CallRisk.decide(null, now - 8 * minute, now))
    }

    @Test fun aScamMessageOverTwoHoursAgoNoLongerCounts() {
        assertNull(CallRisk.decide(null, now - 121 * minute, now))
    }

    @Test fun aConfirmedNumberWarnsEvenWithoutAMessage() {
        assertEquals(CallWarning.ReportedNumber("fake_mpesa"), CallRisk.decide("fake_mpesa", null, now))
    }

    @Test fun theRecentMessageIsMoreSpecificSoItWins() {
        assertEquals(CallWarning.RecentScamMessage(30), CallRisk.decide("fake_mpesa", now - 30 * minute, now))
    }

    @Test fun anUnknownNumberDoesNotWarn() {
        assertNull(CallRisk.decide(null, null, now))
    }

    private val care = VerifiedCaller("254722000000", "Safaricom Customer Care", "Huduma kwa Wateja ya Safaricom", "safaricom")

    @Test fun aVerifiedNumberGetsTheBadge() {
        assertEquals(VerifiedCall(care), CallRisk.decide(null, null, now, care))
    }

    @Test fun aScamSignalAlwaysBeatsTheBadge() {
        assertEquals(CallWarning.ReportedNumber("fake_mpesa"), CallRisk.decide("fake_mpesa", null, now, care))
        assertEquals(CallWarning.RecentScamMessage(5), CallRisk.decide(null, now - 5 * minute, now, care))
    }

    @Test fun theBadgeNameFollowsTheLanguage() {
        assertEquals("Huduma kwa Wateja ya Safaricom", care.name("sw"))
        assertEquals("Safaricom Customer Care", care.name("en"))
    }
}
