package com.linda.app.features.calls

import com.linda.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** The card over a ringing call (and Ask Linda's number answer) says the right thing in the right colour. */
class CallCardLookTest {
    @Test fun reportedNumberIsRed() {
        val look = CallCardOverlay.look(CallWarning.ReportedNumber("sent_by_mistake"))
        assertEquals(R.string.call_card_reported, look.pill)
        assertEquals(CallCardOverlay.Tone.SCAM, look.tone)
    }

    @Test fun recentScamMessageSaysHowLongAgo() {
        val look = CallCardOverlay.look(CallWarning.RecentScamMessage(8))
        assertEquals(CallCardOverlay.Tone.SCAM, look.tone)
        assertEquals(8, look.lineArg)
    }

    @Test fun suspectedIsAmber() =
        assertEquals(CallCardOverlay.Tone.CAUTION, CallCardOverlay.look(CallWarning.SuspectedNumber(null)).tone)

    @Test fun verifiedIsGreen() {
        val caller = VerifiedCaller("254722000000", "Safaricom Customer Care", "Huduma kwa Wateja ya Safaricom", "safaricom")
        val look = CallCardOverlay.look(VerifiedCall(caller))
        assertEquals(R.string.call_card_verified, look.pill)
        assertEquals(CallCardOverlay.Tone.SAFE, look.tone)
    }

    @Test fun aSavedContactIsNeutralNeverVerifiedGreen() {
        val look = CallCardOverlay.look(SavedContact)
        assertEquals(R.string.call_card_contact, look.pill)
        assertEquals(R.string.call_contact_text, look.line)
        assertEquals(CallCardOverlay.Tone.NEUTRAL, look.tone)
    }
}
