package com.linda.app.core.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Kotlin must hash numbers exactly like the gateway (shared/hash-vectors.json, root CLAUDE.md 5.10). */
class PhoneHashTest {
    @Test fun matchesEveryCaseInTheSharedVectors() {
        val cases = JSONObject(File("../../shared/hash-vectors.json").readText()).getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            assertEquals(c.getString("msisdn"), c.getString("sha256"), PhoneHash.of(c.getString("msisdn")))
        }
    }

    @Test fun thePrefixIsTheFirstFiveCharacters() {
        val msisdn = "254722000000"
        assertEquals(PhoneHash.of(msisdn).take(5), PhoneHash.prefixOf(msisdn))
        assertEquals(5, PhoneHash.prefixOf(msisdn).length)
    }
}
