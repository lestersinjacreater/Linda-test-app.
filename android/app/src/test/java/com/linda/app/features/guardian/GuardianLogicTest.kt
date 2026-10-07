package com.linda.app.features.guardian

import com.linda.app.features.detection.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianLogicTest {
    private val hour = 60 * 60 * 1000L
    private val now = 100 * hour
    private val verified = setOf("MPESA", "KPLC")

    private fun decide(
        level: RiskLevel = RiskLevel.SCAM, enabled: Boolean = true, guardian: String? = "0712345678", name: String? = "Mum",
        permission: Boolean = true, sender: String? = "254700000777", last: Long? = null,
    ) = GuardianPolicy.decide(level, enabled, guardian, name, permission, sender, verified, last, now)

    private fun skip(reason: SkipReason) = AlertDecision.Skip(reason)

    @Test fun aScamWithEverythingSetUpIsSent() = assertEquals(AlertDecision.Send, decide())

    @Test fun onlyScamLevelAlertsTheGuardian() {
        assertEquals(skip(SkipReason.NOT_A_SCAM), decide(level = RiskLevel.CAUTION))
        assertEquals(skip(SkipReason.NOT_A_SCAM), decide(level = RiskLevel.SAFE))
    }

    @Test fun itNeverSendsUnlessThePersonOptedIn() = assertEquals(skip(SkipReason.GUARDIAN_OFF), decide(enabled = false))

    @Test fun itNeedsAValidGuardianNumberANameAndTheSmsPermission() {
        assertEquals(skip(SkipReason.NO_GUARDIAN_NUMBER), decide(guardian = null))
        assertEquals(skip(SkipReason.NO_GUARDIAN_NUMBER), decide(guardian = "MPESA"))
        assertEquals(skip(SkipReason.NO_NAME), decide(name = "  "))
        assertEquals(skip(SkipReason.NO_SMS_PERMISSION), decide(permission = false))
    }

    @Test fun pastedTextAndVerifiedSendersNeverAlert() {
        assertEquals(skip(SkipReason.NO_SENDER), decide(sender = null))
        assertEquals(skip(SkipReason.VERIFIED_SENDER), decide(sender = " mpesa "))
    }

    @Test fun oneAlertPerSenderPerSixHours() {
        assertEquals(skip(SkipReason.RATE_LIMITED), decide(last = now - 1))
        assertEquals(skip(SkipReason.RATE_LIMITED), decide(last = now - 5 * hour - 59 * 60_000))
        assertEquals("exactly six hours later is allowed again", AlertDecision.Send, decide(last = now - 6 * hour))
        assertEquals(AlertDecision.Send, decide(last = now - 7 * hour))
        assertEquals("no earlier alert for this sender", AlertDecision.Send, decide(last = null))
    }

    @Test fun theSameScammerInDifferentFormatsSharesOneRateLimit() {
        assertEquals(GuardianPolicy.senderKey("+254700000777"), GuardianPolicy.senderKey("0700000777"))
        assertEquals(GuardianPolicy.senderKey("promo"), GuardianPolicy.senderKey(" PROMO "))
    }

    @Test fun englishAlertMatchesTheSpecAndIsGenderNeutral() {
        val text = GuardianMessage.alert("en", "Mum", "sent_by_mistake", "4:12 PM")
        assertEquals("Linda alert: Mum received a suspected \"sent by mistake\" scam at 4:12 PM. Consider calling them.", text)
        for (word in listOf(" her ", " him ", " she ", " he ", " his ")) assertFalse(word, word in " ${text.lowercase()} ")
    }

    @Test fun swahiliAlertExists() {
        val text = GuardianMessage.alert("sw", "Mama", "fake_mpesa", "4:12 PM")
        assertTrue("Onyo la Linda" in text && "Mama" in text && "4:12 PM" in text)
        assertFalse("Linda alert" in text)
    }

    @Test fun everyAlertFitsInOneSmsEvenWithALongNameAndTheLongestScamType() {
        val longName = "Grandmother Wanjiku of Nyeri County"
        for (lang in listOf("en", "sw")) for (cat in listOf("sent_by_mistake", "fake_mpesa", "fuliza_upgrade", "phishing_link", "other")) {
            val text = GuardianMessage.alert(lang, longName, cat, "12:59 PM")
            assertTrue("$lang/$cat is ${text.length} characters", text.length <= 160)
        }
        assertTrue(GuardianMessage.test("en", longName).length <= 160 && GuardianMessage.test("sw", longName).length <= 160)
    }

    @Test fun unknownCategoryStillMakesASensibleAlert() {
        assertTrue("suspected scam message" in GuardianMessage.alert("en", "Mum", "other", "4:12 PM"))
    }

    @Test fun theAlertNeverContainsAMessageBodyOrANumber() {
        val text = GuardianMessage.alert("en", "Mum", "fake_mpesa", "4:12 PM")
        assertFalse(Regex("\\d{9,}").containsMatchIn(text)) // no phone number
        assertFalse("Confirmed" in text || "Ksh" in text)
    }

    @Test fun longNamesAreShortenedCleanly() {
        assertEquals("Mum", GuardianMessage.shortName("  Mum  "))
        assertTrue(GuardianMessage.shortName("A".repeat(50)).length <= GuardianRules.MAX_NAME_LENGTH)
    }

    @Test fun theRateLimitIsSixHours() = assertEquals(6 * hour, GuardianRules.RATE_LIMIT_MS)
}
