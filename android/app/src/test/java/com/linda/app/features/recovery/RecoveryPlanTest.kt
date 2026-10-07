package com.linda.app.features.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPlanTest {
    private fun steps(t: Timing, amount: Int?, bank: Boolean = false) = RecoveryPlan.steps(RecoveryInput(t, amount, bank))

    @Test fun justSentLargeAmountGetsTheFullUrgentPlanInOrder() {
        assertEquals(
            listOf(StepId.STOP, StepId.EVIDENCE, StepId.REVERSAL, StepId.CALL_CARE, StepId.REPORT_SCAM, StepId.POLICE, StepId.BEWARE_RECOVERY),
            steps(Timing.JUST_NOW, 25_000),
        )
    }

    @Test fun stoppingAndKeepingEvidenceAlwaysComeFirstAndRecoveryScamWarningLast() {
        for (t in Timing.values()) for (a in listOf(null, 100, 50_000)) {
            val s = steps(t, a, bank = true)
            assertEquals(listOf(StepId.STOP, StepId.EVIDENCE), s.take(2))
            assertEquals(StepId.BEWARE_RECOVERY, s.last())
        }
    }

    @Test fun reversalIsOnlyOfferedInsideTheWindow() {
        assertTrue(StepId.REVERSAL in steps(Timing.JUST_NOW, 500))
        assertTrue(StepId.REVERSAL in steps(Timing.WITHIN_24H, 500))
        assertFalse(StepId.REVERSAL in steps(Timing.OVER_24H, 500))
        assertTrue(StepId.CALL_CARE_LATE in steps(Timing.OVER_24H, 500))
        assertFalse(StepId.CALL_CARE in steps(Timing.OVER_24H, 500))
    }

    @Test fun everyoneIsToldToReportTheScamMessage() {
        for (t in Timing.values()) assertTrue(StepId.REPORT_SCAM in steps(t, null))
    }

    @Test fun bankStepOnlyWhenPaidFromABank() {
        assertTrue(StepId.BANK in steps(Timing.JUST_NOW, 1000, bank = true))
        assertFalse(StepId.BANK in steps(Timing.JUST_NOW, 1000, bank = false))
    }

    @Test fun policeIsImportantFromTheConfiguredAmountAndOptionalBelowIt() {
        val limit = RecoveryConfig.POLICE_REPORT_FROM_KES
        assertTrue(StepId.POLICE in steps(Timing.WITHIN_24H, limit))
        assertTrue(StepId.POLICE_OPTIONAL in steps(Timing.WITHIN_24H, limit - 1))
        assertTrue("unknown amount is treated as small", StepId.POLICE_OPTIONAL in steps(Timing.WITHIN_24H, null))
        assertFalse(StepId.POLICE in steps(Timing.WITHIN_24H, limit - 1))
    }

    @Test fun urgencyEndsWhenTheWindowPasses() {
        assertTrue(RecoveryPlan.isUrgent(Timing.JUST_NOW))
        assertTrue(RecoveryPlan.isUrgent(Timing.WITHIN_24H))
        assertFalse(RecoveryPlan.isUrgent(Timing.OVER_24H))
    }

    @Test fun everyConfiguredNumberIsFilledInAndEveryEntryStatesItsVerificationStatus() {
        for (e in RecoveryConfig.ENTRIES) {
            assertTrue(e.name, e.value.isNotBlank() && e.status.isNotBlank() && e.source.isNotBlank())
        }
        assertTrue(RecoveryConfig.ENTRIES.none { it.status.startsWith("verified") }) // nothing is claimed verified until a human does it
        assertEquals(24, RecoveryConfig.REVERSAL_WINDOW_HOURS)
    }

    private val facts = ReportFacts(
        scammerNumber = "254700000777", amountKes = 2500, paidToNumber = "254700000777", transactionCode = "QK7RT2XY9P",
        scamReceivedAt = "12 Oct 2026, 4:12 PM", scamCategory = "fake_mpesa", scamMessage = null,
    )

    @Test fun reportTextContainsEveryFactInEnglish() {
        val text = ReportText.build("en", facts)
        for (needle in listOf("254700000777", "Ksh 2,500", "QK7RT2XY9P", "12 Oct 2026, 4:12 PM", "fake M-PESA confirmation")) {
            assertTrue(needle, needle in text)
        }
        assertFalse("the scam message is left out unless the person chose to include it", "The message I received" in text)
    }

    @Test fun reportTextExistsInSwahiliToo() {
        val text = ReportText.build("sw", facts)
        assertTrue("Ninaripoti" in text && "QK7RT2XY9P" in text && "Ksh 2,500" in text)
        assertFalse("I am reporting" in text)
    }

    @Test fun scamMessageIsIncludedOnlyWhenGiven() {
        val text = ReportText.build("en", facts.copy(scamMessage = "Nimekutumia 2,500 kimakosa"))
        assertTrue("The message I received: \"Nimekutumia 2,500 kimakosa\"" in text)
    }

    @Test fun missingFactsAreShownAsNotProvidedInsteadOfBlankOrInvented() {
        val text = ReportText.build("en", ReportFacts(null, null, null, null, null, null, null))
        assertEquals(5, "(not provided)".toRegex().findAll(text).count())
        assertTrue("a scam message" in text)
        assertTrue("(sijaitoa)" in ReportText.build("sw", ReportFacts(null, null, null, null, null, null, null)))
    }

    @Test fun transactionCodesAreCleanedAndValidated() {
        assertEquals("QK7RT2XY9P", ReportText.cleanTransactionCode(" qk7rt2xy9p "))
        assertNull(ReportText.cleanTransactionCode("12345"))
        assertNull(ReportText.cleanTransactionCode("ABCDEFGHIJ")) // letters only: not a transaction code
        assertNull(ReportText.cleanTransactionCode("1234567890"))
    }
}
