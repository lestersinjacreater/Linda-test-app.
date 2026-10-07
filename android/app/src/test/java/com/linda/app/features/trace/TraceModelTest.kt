package com.linda.app.features.trace

import com.linda.app.core.ui.theme.SegmentState
import com.linda.app.core.ui.theme.SweepTiming
import com.linda.app.features.detection.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceModelTest {

    private fun input(
        level: String = "SCAM", score: Float = 0.94f, category: String = "sent_by_mistake",
        codes: Set<String> = emptySet(), words: List<String> = emptyList(), body: String = "Please send it back",
        verified: Boolean = false, blocklist: Boolean = false, reported: Boolean = false, guardian: Boolean = false, pasted: Boolean = false,
    ) = TraceInput(level, score, category, codes, words, body, verified, blocklist, reported, guardian, pasted)

    private fun byLayer(i: TraceInput) = LayerTraceBuilder.build(i).associateBy { it.layer }

    @Test
    fun alwaysFiveLayersInOrder() {
        val layers = LayerTraceBuilder.build(input()).map { it.layer.letter }
        assertEquals(listOf("L", "I", "N", "D", "A"), layers)
    }

    @Test
    fun disguisedWords_areReportedWithHowTheyWereRead() {
        val found = LayerTraceBuilder.disguises("Your M-P3SA a/c, c0nf1rmed. Ksh3,140 sent. Ref QJ47K2MD91")
        assertTrue(found.contains("M-P3SA" to "mpesa"))
        assertTrue(found.contains("c0nf1rmed" to "confirmed"))
        assertTrue(found.size <= 2)
    }

    @Test
    fun amountsAndCodes_areNotDisguises() {
        assertEquals(emptyList<Pair<String, String>>(), LayerTraceBuilder.disguises("Ksh3,140 Ksh500 QJ47K2MD91 2,500 4G"))
    }

    @Test
    fun languageLayer_flagsOnlyWhenADisguiseWasUndone() {
        val plain = byLayer(input(body = "Send the money back please"))
        assertFalse(plain.getValue(Layer.LANGUAGE).flagged)
        assertEquals("l_clean", plain.getValue(Layer.LANGUAGE).lines.single().code)
        val disguised = byLayer(input(body = "K1m@kosa tuma"))
        assertTrue(disguised.getValue(Layer.LANGUAGE).flagged)
        assertEquals("l_disguise", disguised.getValue(Layer.LANGUAGE).lines.first().code)
    }

    @Test
    fun intelligenceLayer_showsTypeWordsAndScoreForAScam() {
        val i = byLayer(input(words = listOf("mistake", "return"))).getValue(Layer.INTELLIGENCE)
        assertTrue(i.flagged)
        assertEquals(listOf("i_category", "i_words", "i_score"), i.lines.map { it.code })
        assertEquals("mistake, return", i.lines[1].args.single())
        assertEquals("94", i.lines[2].args.single())
    }

    @Test
    fun networkLayer_listsEverySenderSignal() {
        val n = byLayer(input(codes = setOf("personal_number", "unknown_sender", "link"), blocklist = true)).getValue(Layer.NETWORK)
        assertTrue(n.flagged)
        assertEquals(listOf("n_personal", "n_unknown", "n_link", "n_blocklist"), n.lines.map { it.code })
    }

    @Test
    fun networkLayer_fakeMpesaIsCalledOut() {
        val n = byLayer(input(category = "fake_mpesa", codes = setOf("fake_mpesa", "personal_number"))).getValue(Layer.NETWORK)
        assertEquals("n_fake_mpesa", n.lines.first().code)
    }

    @Test
    fun verifiedSender_isNeverFlaggedAnywhere() {
        val r = LayerTraceBuilder.build(input(level = "SAFE", score = 0.02f, category = "none", verified = true))
        assertTrue(r.none { it.flagged })
        assertEquals("n_verified", r.first { it.layer == Layer.NETWORK }.lines.single().code)
    }

    @Test
    fun safeMessage_flagsNothingAndSaysNoWarningNeeded() {
        val r = LayerTraceBuilder.build(input(level = "SAFE", score = 0.03f, category = "none", body = "See you at 5"))
        assertTrue(r.none { it.flagged })
        assertEquals("a_nothing", r.first { it.layer == Layer.ALERT }.lines.single().code)
        assertEquals("i_none", r.first { it.layer == Layer.INTELLIGENCE }.lines.single().code)
    }

    @Test
    fun pastedText_saysThereIsNoSenderToCheck() {
        val n = byLayer(input(pasted = true)).getValue(Layer.NETWORK)
        assertFalse(n.flagged)
        assertEquals("n_pasted", n.lines.single().code)
    }

    @Test
    fun alertLayer_listsWhatLindaDid_andIsNeverFlagged() {
        val a = byLayer(input(reported = true, guardian = true)).getValue(Layer.ALERT)
        assertFalse(a.flagged)
        assertEquals(listOf("a_warned", "a_reported", "a_guardian"), a.lines.map { it.code })
    }

    @Test
    fun decisionLayer_carriesTheLevel() {
        val d = byLayer(input(level = "CAUTION", score = 0.6f)).getValue(Layer.DECISION)
        assertTrue(d.flagged)
        assertEquals(listOf("CAUTION"), d.lines.single().args)
    }

    @Test
    fun scamWords_areReadFromTheNgramReason() {
        val reasons = listOf(
            Reason("sent_by_mistake", "x", "y"),
            Reason("ngrams", "Typical scam wording: \"mistake\", \"refund\".", "Maneno: \"mistake\", \"refund\"."),
        )
        assertEquals(listOf("mistake", "refund"), LayerTraceBuilder.scamWords(reasons))
        assertEquals(emptyList<String>(), LayerTraceBuilder.scamWords(listOf(Reason("link", "a", "b"))))
    }

    // --- the sweep (docs/design-system.md section 8) ---

    @Test
    fun sweep_finishesUnder600ms_andLightsLayersLeftToRight() {
        assertTrue(SweepTiming.TOTAL_MS < 600)
        assertEquals(480, SweepTiming.TOTAL_MS)
        for (i in 0 until 4) assertEquals(70, SweepTiming.startMs(i + 1) - SweepTiming.startMs(i))
    }

    @Test
    fun sweep_stateGoesIdleScanningThenPassedOrFlagged() {
        assertEquals(SegmentState.IDLE, SweepTiming.stateAt(2, 100f, false))   // layer N starts at 140ms
        assertEquals(SegmentState.SCANNING, SweepTiming.stateAt(2, 200f, false))
        assertEquals(SegmentState.PASSED, SweepTiming.stateAt(2, 400f, false))
        assertEquals(SegmentState.FLAGGED, SweepTiming.stateAt(2, 400f, true))
    }

    @Test
    fun sweep_shakeIsSmall_andOnlyJustAfterLanding() {
        val landed = SweepTiming.endMs(1).toFloat()
        assertEquals(0f, SweepTiming.shakeDp(1, landed - 1f), 0f)
        assertEquals(0f, SweepTiming.shakeDp(1, landed + SweepTiming.SHAKE_MS), 0f)
        var peak = 0f
        var t = 0f
        while (t < SweepTiming.SHAKE_MS) { peak = maxOf(peak, kotlin.math.abs(SweepTiming.shakeDp(1, landed + t))); t += 1f }
        assertTrue(peak > 0.5f && peak <= 2f)
    }

    @Test
    fun haptics_followTheVerdict() {
        assertEquals(0, HapticPlan.pulses("SAFE"))
        assertEquals(1, HapticPlan.pulses("CAUTION"))
        assertEquals(2, HapticPlan.pulses("SCAM"))
    }

    @Test
    fun buzz_startsWhenTheFirstFlaggedLayerLands() {
        // A scam flags L (disguise), I, N, D: the first flagged layer is L (index 0), which lands at 200ms.
        val withDisguise = LayerTraceBuilder.build(input(body = "K1m@kosa tuma", codes = setOf("personal_number")))
        assertEquals(SweepTiming.endMs(0), HapticPlan.fireAtMs(withDisguise, "SCAM"))
        // No disguise: L is not flagged, so I (index 1) is the first.
        val plain = LayerTraceBuilder.build(input(body = "Send it back please"))
        assertEquals(SweepTiming.endMs(1), HapticPlan.fireAtMs(plain, "SCAM"))
    }

    @Test
    fun buzz_neverFiresForASafeMessage() {
        val safe = LayerTraceBuilder.build(input(level = "SAFE", score = 0.02f, category = "none", body = "See you at 5"))
        assertEquals(null, HapticPlan.fireAtMs(safe, "SAFE"))
    }
}
