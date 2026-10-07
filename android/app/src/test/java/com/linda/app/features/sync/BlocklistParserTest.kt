package com.linda.app.features.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class BlocklistParserTest {
    @Test fun parsesTheRadarsAnswer() {
        val json = """{"as_of":"2026-10-20T14:05:00Z",
            "added":[{"msisdn":"254700900010","category":"fake_mpesa"},{"msisdn":"254700900011","category":"prize"}],
            "removed":["254700000001"]}"""
        val delta = BlocklistParser.parse(json)
        assertEquals("2026-10-20T14:05:00Z", delta.asOf)
        assertEquals(listOf(BlockedNumber("254700900010", "fake_mpesa"), BlockedNumber("254700900011", "prize")), delta.added)
        assertEquals(listOf("254700000001"), delta.removed)
    }

    @Test fun missingCategoryBecomesOther() {
        val delta = BlocklistParser.parse("""{"as_of":"x","added":[{"msisdn":"254700900010"}],"removed":[]}""")
        assertEquals("other", delta.added.single().category)
    }
}
