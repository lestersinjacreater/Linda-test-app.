package com.linda.app.features.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlocklistParserTest {
    private val h1 = "a3f09c" + "0".repeat(58)
    private val h2 = "b7e112" + "0".repeat(58)

    @Test fun parsesTheGatewaysHashedSync() {
        val json = """{"as_of":"2026-10-20T14:05:00Z",
            "added":[{"hash":"$h1","status":"confirmed","category":"fake_mpesa","label":"Reported for fraud by 14 people","label_sw":"Imeripotiwa"},
                     {"hash":"$h2","status":"confirmed","category":"prize"}],
            "removed":["$h2"]}"""
        val delta = BlocklistParser.parse(json)
        assertEquals("2026-10-20T14:05:00Z", delta.asOf)
        assertEquals(BlockedNumber(h1, "fake_mpesa", "Reported for fraud by 14 people", "Imeripotiwa"), delta.added[0])
        assertEquals(BlockedNumber(h2, "prize"), delta.added[1])
        assertEquals(listOf(h2), delta.removed)
    }

    @Test fun missingCategoryBecomesOther() {
        val delta = BlocklistParser.parse("""{"as_of":"x","added":[{"hash":"$h1","category":""}],"removed":[]}""")
        assertEquals("other", delta.added.single().category)
    }

    @Test fun theLookupPicksOnlyThisNumbersEntryOutOfTheBucket() {
        val json = """{"prefix":"a3f09","entries":[
            {"hash":"a3f09e${"1".repeat(58)}","status":"verified","category":null,"label":"Safaricom Customer Care","label_sw":"Huduma"},
            {"hash":"$h1","status":"suspected","category":"pin_request","label":"Suspected scam, reported by 2 people","label_sw":"x"}]}"""
        assertEquals(LookupEntry("suspected", "pin_request", "Suspected scam, reported by 2 people", "x"), LookupParser.find(json, h1))
        assertNull(LookupParser.find(json, "a3f09f" + "2".repeat(58)))
    }

    @Test fun aNullCategoryStaysNull() {
        val json = """{"prefix":"a3f09","entries":[{"hash":"$h1","status":"verified","category":null,"label":"Bank","label_sw":"Benki"}]}"""
        assertNull(LookupParser.find(json, h1)!!.category)
    }
}
