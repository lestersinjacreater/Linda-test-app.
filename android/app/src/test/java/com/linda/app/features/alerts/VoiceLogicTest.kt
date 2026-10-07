package com.linda.app.features.alerts

import com.linda.app.features.detection.ReasonCatalogue
import com.linda.app.features.detection.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLogicTest {
    private val now = 1_000_000L

    private fun speak(
        level: RiskLevel = RiskLevel.SCAM, enabled: Boolean = true, source: String = "sms",
        ringer: Ringer = Ringer.NORMAL, inCall: Boolean = false, last: Long? = null,
    ) = VoicePolicy.shouldSpeak(level, enabled, source, ringer, inCall, last, now)

    @Test fun aScamTextWithVoiceOnIsSpoken() = assertTrue(speak())

    @Test fun onlyScamsAreSpokenNeverCautionOrSafe() {
        assertFalse(speak(level = RiskLevel.CAUTION))
        assertFalse(speak(level = RiskLevel.SAFE))
    }

    @Test fun nothingIsSpokenWhenVoiceIsOff() = assertFalse(speak(enabled = false))

    @Test fun demoIsSpokenButPastedTextIsNot() {
        assertTrue(speak(source = "demo"))
        assertFalse(speak(source = "checker"))
    }

    @Test fun silentAndVibrateMeanQuiet() = assertFalse(speak(ringer = Ringer.QUIET))

    @Test fun neverTalksOverACall() = assertFalse(speak(inCall = true))

    @Test fun burstsOfScamsAreSpokenOnceEveryThirtySeconds() {
        assertFalse(speak(last = now - 1))
        assertFalse(speak(last = now - 29_999))
        assertTrue("exactly 30 s later is allowed", speak(last = now - 30_000))
        assertTrue(speak(last = null))
    }

    @Test fun voiceIsOnByDefaultOnlyForGuardianProtectedPeopleAndTheirOwnChoiceWins() {
        assertTrue(VoiceDefault.resolve(null, guardianEnabled = true))
        assertFalse(VoiceDefault.resolve(null, guardianEnabled = false))
        assertFalse("the person turned it off", VoiceDefault.resolve(false, guardianEnabled = true))
        assertTrue("the person turned it on", VoiceDefault.resolve(true, guardianEnabled = false))
    }

    @Test fun swahiliOnlyWhenWantedAndTheVoiceExistsOtherwiseEnglish() {
        assertEquals("sw", VoiceLanguage.choose("sw", swahiliVoiceAvailable = true))
        assertEquals("en", VoiceLanguage.choose("sw", swahiliVoiceAvailable = false))
        assertEquals("en", VoiceLanguage.choose("en", swahiliVoiceAvailable = true))
    }

    @Test fun theScriptSaysTheReasonAndOneClearAction() {
        val reason = ReasonCatalogue.forCategory("fake_mpesa")
        val en = VoiceScript.warning("en", reason.english)
        assertTrue(en.startsWith("Linda warning."))
        assertTrue("You have not received any money" in en && "Do not send money" in en)
        val sw = VoiceScript.warning("sw", reason.swahili)
        assertTrue(sw.startsWith("Onyo la Linda.") && "Hujapokea pesa yoyote" in sw && "Usitume pesa" in sw)
    }

    @Test fun theScriptStillWorksWithoutAReasonAndNeverHasDoubleStops() {
        assertEquals("Linda warning. This message is probably a scam. Do not send money and do not share your PIN.", VoiceScript.warning("en", null))
        assertFalse(".." in VoiceScript.warning("en", "Ends with a stop."))
    }

    @Test fun everySpokenWarningIsShortEnoughToListenTo() {
        for (category in listOf("fake_mpesa", "sent_by_mistake", "prize", "fuliza_upgrade", "kra_refund", "job_fee", "loan_fee", "pin_request", "phishing_link", "other")) {
            val r = ReasonCatalogue.forCategory(category)
            assertTrue(category, VoiceScript.warning("en", r.english).length <= 260)
            assertTrue(category, VoiceScript.warning("sw", r.swahili).length <= 260)
        }
    }

    @Test fun theScriptNeverContainsDigitsOrMessageContent() {
        for (lang in listOf("en", "sw")) {
            val text = VoiceScript.warning(lang, ReasonCatalogue.forCategory("fake_mpesa").let { if (lang == "sw") it.swahili else it.english })
            assertFalse(Regex("\\d").containsMatchIn(text))
        }
    }
}
