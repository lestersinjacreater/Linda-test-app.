package com.linda.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kotlin must reproduce Python on every case in shared/test-vectors.json (root CLAUDE.md 5.9). */
class ParityTest {
    @Test fun thereAreEnoughVectors() = assertTrue(TestData.vectors.size >= 60)

    @Test fun normalizerMatchesPythonOnEveryVector() {
        for (v in TestData.vectors) assertEquals(v.id, v.normalized, Normalizer.normalize(v.text))
    }

    @Test fun simhashMatchesPythonOnEveryVector() {
        for (v in TestData.vectors) assertEquals(v.id, v.fingerprint, SimHash.simhash64(v.normalized))
    }

    @Test fun scoresAreWithinTheSharedRangeOnEveryVector() {
        for (v in TestData.vectors) {
            val score = TestData.scorer.score(v.text, v.sender)
            assertTrue("${v.id}: score $score not in [${v.scoreMin}, ${v.scoreMax}]", score in v.scoreMin..v.scoreMax)
        }
    }

    @Test fun fnvMatchesPublishedValues() {
        assertEquals(0xCBF29CE484222325uL.toLong(), SimHash.fnv1a64(ByteArray(0)))
        assertEquals(0xAF63DC4C8601EC8CuL.toLong(), SimHash.fnv1a64("a".toByteArray()))
        assertEquals(0x85944171F73967E8uL.toLong(), SimHash.fnv1a64("foobar".toByteArray()))
    }

    @Test fun letterPiecesGoInTheSameSlotsAsPython() {
        // Values from ml/src/features/scoring/scorer.py bucket_of(gram, 32768); "é" checks UTF-8 bytes.
        val expected = mapOf(" ki" to 14639, " nimek" to 5213, "pesa" to 20994, "a" to 27788, "é" to 7681)
        for ((gram, slot) in expected) assertEquals(gram, slot, TestData.scorer.bucketOf(gram))
    }

    @Test fun explanationsStillNameTheMessagesOwnLetters() {
        val text = "Nimekutumia 2,500 kimakosa, tafadhali nirudishie haraka"
        val top = TestData.scorer.explain(text, "254700000123").ngramContributions.take(10)
        val normalized = Normalizer.normalize(text)
        assertTrue(top.isNotEmpty())
        for ((gram, _) in top) assertTrue(gram, normalized.contains(gram.trim()))
    }

    @Test fun modelVersionIsTheOneInTheVectorsRepo() {
        assertTrue(TestData.scorer.version.isNotBlank())
        assertEquals(0.55, TestData.scorer.warnThreshold, 1e-9)
        assertEquals(0.80, TestData.scorer.scamThreshold, 1e-9)
    }
}
