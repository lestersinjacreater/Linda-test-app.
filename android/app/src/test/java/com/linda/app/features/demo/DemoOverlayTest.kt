package com.linda.app.features.demo

import com.linda.app.features.detection.Reason
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoOverlayTest {

    // --- the hidden switch: tap the logo 7 times ---

    @Test
    fun sevenQuickTaps_completeTheSequence_onTheSeventh() {
        val taps = TapCounter()
        val results = (0 until 7).map { taps.tap(1000L + it * 300L) }
        assertEquals(List(6) { false } + true, results)
    }

    @Test
    fun aPauseRestartsTheCount() {
        val taps = TapCounter()
        repeat(6) { taps.tap(1000L + it * 300L) }
        assertFalse(taps.tap(1000L + 6 * 300L + 5000L))   // too long a gap: this is tap 1 again
    }

    @Test
    fun slowTapping_neverSwitchesItOn() {
        val taps = TapCounter()
        assertTrue((0 until 20).none { taps.tap(it * 2000L) })
    }

    @Test
    fun afterSwitching_thenextSequenceStartsFromZero() {
        val taps = TapCounter()
        repeat(7) { taps.tap(it * 200L) }
        assertFalse(taps.tap(1500L))
    }

    // --- what the panel shows ---

    private val scam = Verdict(
        RiskLevel.SCAM, 0.94f,
        listOf(
            Reason("sent_by_mistake", "x", "y"),
            Reason("personal_number", "a", "b"),
            Reason("ngrams", "Typical scam wording: \"kimakosa\", \"rudisha\".", "Maneno: \"kimakosa\", \"rudisha\"."),
        ),
        "2026.10.07-1", "sent_by_mistake", "a3f09c1e7b2d4f80",
    )

    private fun scamSnapshot(actions: List<String>) = DemoSnapshots.create(
        sequence = 1, body = "Nimekutumia pesa kimakosa, rudisha Ksh3,000 M-P3SA", sender = "254700000111", source = "demo",
        inContacts = false, firstMessage = true, verdict = scam, verifiedSenders = setOf("MPESA"), warnThreshold = 0.55, scamThreshold = 0.80,
        onBlocklist = false, allowListed = false, actions = actions,
    )

    @Test
    fun fiveLines_oneForEachLayer_inOrder() {
        val lines = DemoOverlayText.lines(scamSnapshot(listOf("notified")))
        assertEquals(listOf("L", "I", "N", "D", "A"), lines.map { it.first })
    }

    @Test
    fun layerLines_showTheRealRawOutputs() {
        val text = DemoOverlayText.lines(scamSnapshot(listOf("notified", "guardian:sent", "reported:queued", "voiced"))).toMap()
        assertTrue(text.getValue("L").contains("mpesa"))                                   // the disguise was undone
        assertTrue(text.getValue("L").contains("M-P3SA->mpesa"))
        assertTrue(text.getValue("I").contains("score=0.94 warn=0.55 scam=0.80"))
        assertTrue(text.getValue("I").contains("type=sent_by_mistake"))
        assertTrue(text.getValue("I").contains("words=kimakosa,rudisha"))
        assertTrue(text.getValue("N").contains("contacts=no first_msg=yes"))
        assertTrue(text.getValue("N").contains("personal_no=yes"))
        assertTrue(text.getValue("N").contains("verified=no"))
        assertEquals("level=SCAM score=0.94", text.getValue("D"))
        assertEquals("actions=notified,guardian:sent,reported:queued,voiced", text.getValue("A"))
    }

    @Test
    fun theTraceInsideTheSnapshot_knowsWhatLindaDid() {
        val snapshot = scamSnapshot(listOf("notified", "guardian:sent", "reported:queued"))
        val alert = snapshot.trace.first { it.layer.letter == "A" }
        assertEquals(listOf("a_warned", "a_reported", "a_guardian"), alert.lines.map { it.code })
    }

    @Test
    fun anAllowListedSender_isShownAsSkipped_withNoActions() {
        val verdict = Verdict(RiskLevel.SAFE, 0f, emptyList(), "allow-list", "none", "")
        val snapshot = DemoSnapshots.create(2, "hello", "254700000222", "sms", null, null, verdict, emptySet(), 0.55, 0.80, false, true, emptyList())
        val text = DemoOverlayText.lines(snapshot).toMap()
        assertTrue(text.getValue("I").startsWith("skipped"))
        assertEquals("actions=none", text.getValue("A"))
    }

    @Test
    fun pastedText_hasNoSender_andLongTextIsCut() {
        val verdict = Verdict(RiskLevel.SAFE, 0.02f, emptyList(), "2026.10.07-1", "none", "")
        val long = "word ".repeat(100)
        val snapshot = DemoSnapshots.create(3, long, null, "checker", null, null, verdict, emptySet(), 0.55, 0.80, false, false, emptyList())
        val text = DemoOverlayText.lines(snapshot).toMap()
        assertTrue(text.getValue("N").startsWith("sender=pasted"))
        assertTrue(text.getValue("N").contains("contacts=n/a"))
        assertTrue(text.getValue("L").length < 220)
        assertTrue(text.getValue("L").contains("..."))
    }
}
