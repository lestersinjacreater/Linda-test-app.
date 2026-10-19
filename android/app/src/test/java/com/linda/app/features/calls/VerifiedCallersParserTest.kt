package com.linda.app.features.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VerifiedCallersParserTest {
    @Test fun parsesTheSharedListAndFindsSafaricomCustomerCare() {
        val json = sharedFile().readText()
        val callers = VerifiedCallersParser.parse(json)
        val care = callers.getValue("254722000000")
        assertEquals("Safaricom Customer Care", care.nameEn)
        assertEquals("safaricom", care.kind)
        assertTrue(care.nameSw.isNotBlank())
    }

    @Test fun theCopyBundledInTheAppMatchesTheSharedList() {
        val bundled = File("src/main/assets/verified_callers.json").readText()
        assertEquals(sharedFile().readText(), bundled)
    }

    /** Unit tests run from android/app; the shared folder is two levels up. */
    private fun sharedFile() = File("../../shared/verified_callers.json")
}
