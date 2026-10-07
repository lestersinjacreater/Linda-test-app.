package com.linda.app.features.inbox

import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxScannerTest {
    private val detector = TestData.detector
    private val knownMisses = setOf("job-fee-sw-01") // see ml/reports/metrics.md

    private fun scanner(allowed: Set<String> = emptySet(), contacts: (String?) -> Boolean? = { null }) =
        InboxScanner(detector, contacts, { it in allowed })

    private fun vectorSms(repeat: Int = 1) = (0 until repeat).flatMap { r ->
        TestData.vectors.map { RawSms(it.sender, it.text, 1_000_000L + r * 10_000L + TestData.vectors.indexOf(it)) }
    }

    @Test fun flagsTheScamsAndNeverASafeMessage() {
        val rows = TestData.vectors.map { RawSms(it.sender, it.text, 1L) }
        val flagged = scanner().scan(rows.asSequence())
        val flaggedTexts = flagged.map { it.body }.toSet()
        for (v in TestData.vectors) {
            if (v.label == "SAFE") assertFalse("${v.id} must not be flagged", v.text in flaggedTexts)
            if (v.label == "SCAM" && v.id !in knownMisses) assertTrue("${v.id} should be flagged", v.text in flaggedTexts)
        }
    }

    @Test fun realMpesaInAnInboxIsNeverFlagged() {
        val rows = TestData.vectors.filter { it.sender == "MPESA" || it.sender == "M-PESA" }.map { RawSms(it.sender, it.text, 1L) }
        assertTrue(rows.isNotEmpty())
        assertEquals(0, scanner().scan(rows.asSequence()).size)
    }

    @Test fun twoThousandMessagesAreScoredFastEnough() {
        val rows = vectorSms(repeat = 32).take(2000)
        assertEquals(2000, rows.size)
        val start = System.nanoTime()
        val flagged = scanner().scan(rows.asSequence())
        val seconds = (System.nanoTime() - start) / 1e9
        println("INBOX BENCHMARK: 2000 messages scored in %.2f s on this machine, %d flagged".format(seconds, flagged.size))
        // The spec target is 20 s on a LOW-END PHONE. This bound is for a CI machine and only guards against a big regression.
        assertTrue("took $seconds s", seconds < 15.0)
        assertTrue(flagged.isNotEmpty())
    }

    @Test fun progressNeverGoesBackwardsAndEndsAtTheTotal() {
        val rows = vectorSms(repeat = 3)
        val seen = mutableListOf<Pair<Int, Int>>()
        scanner().scan(rows.asSequence(), progressEvery = 20) { checked, flagged -> seen += checked to flagged }
        assertTrue(seen.size >= 2)
        assertEquals(rows.size, seen.last().first)
        assertTrue(seen.zipWithNext().all { (a, b) -> b.first >= a.first && b.second >= a.second })
    }

    @Test fun cancellingKeepsWhatWasFoundSoFar() {
        val rows = vectorSms(repeat = 4)
        var count = 0
        val flagged = scanner().scan(rows.asSequence(), isCancelled = { ++count > 70 })
        assertTrue(flagged.isNotEmpty())
        assertTrue(count <= 71)
    }

    @Test fun sendersMarkedSafeAreSkipped() {
        val rows = TestData.vectors.filter { it.label == "SCAM" }.map { RawSms(it.sender, it.text, 1L) }
        val all = scanner().scan(rows.asSequence()).size
        val skipped = scanner(allowed = setOf(rows.first().sender!!)).scan(rows.asSequence()).size
        assertTrue(skipped < all)
    }

    @Test fun contactLookupIsDoneOncePerSenderNotOncePerMessage() {
        var lookups = 0
        val rows = (1..300).map { RawSms("254700000050", "Hey, are we still meeting at 6?", it.toLong()) }
        scanner(contacts = { lookups++; true }).scan(rows.asSequence())
        assertEquals(1, lookups)
    }

    @Test fun aKnownContactIsLessLikelyToBeFlaggedThanAStranger() {
        val text = "Nimekutumia pesa ya shule, angalia M-Pesa yako."
        val asContact = scanner(contacts = { true }).scan(sequenceOf(RawSms("254700000043", text, 1L)))
        val asStranger = scanner(contacts = { false }).scan(sequenceOf(RawSms("254700000043", text, 1L)))
        assertTrue(asContact.size <= asStranger.size)
    }

    @Test fun messagesWithNoSenderAreStillScored() {
        val flagged = scanner().scan(sequenceOf(RawSms(null, "Hongera! Umeshinda Ksh 50,000. Tuma registration fee ya Ksh 500 upate zawadi yako.", 1L)))
        assertEquals(1, flagged.size)
    }

    @Test fun groupsAreWorstFirstThenBiggestThenNewest() {
        fun f(sender: String, level: RiskLevel, date: Long) =
            Flagged(sender, "x", date, TestData.detector.analyse(com.linda.app.features.detection.MessageInput("hi", null, 0L)).copy(level = level))
        val groups = InboxGrouping.group(
            listOf(f("A", RiskLevel.CAUTION, 5), f("A", RiskLevel.CAUTION, 6), f("A", RiskLevel.CAUTION, 7),
                f("B", RiskLevel.SCAM, 1), f("C", RiskLevel.SCAM, 9), f("C", RiskLevel.SCAM, 8)),
        )
        assertEquals(listOf("C", "B", "A"), groups.map { it.sender })   // SCAM groups first; C has 2 messages, B has 1
        assertEquals(RiskLevel.SCAM, groups[0].worst)
        assertEquals(RiskLevel.CAUTION, groups[2].worst)
        assertEquals(9L, groups[0].newest.date)
        assertEquals(listOf(9L, 8L), groups[0].messages.map { it.date })
    }

    @Test fun theWindowIsNinetyDays() {
        val now = 100L * 24 * 60 * 60 * 1000
        assertEquals(10L * 24 * 60 * 60 * 1000, InboxWindow.startMillis(now))
    }
}
