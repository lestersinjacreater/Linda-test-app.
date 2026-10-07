package com.linda.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class ReasonsJsonTest {
    @Test fun reasonsSurviveASaveAndLoad() {
        val reasons = listOf(
            Reason("fake_mpesa", "It says \"M-Pesa\" but isn't.", "Inasema \"M-Pesa\" lakini si M-Pesa."),
            Reason("link", "The message contains a link.", "Ujumbe una kiungo."),
        )
        assertEquals(reasons, ReasonsJson.decode(ReasonsJson.encode(reasons)))
    }

    @Test fun textPicksTheLanguage() {
        val r = Reason("x", "english", "kiswahili")
        assertEquals("english", ReasonsJson.text(r, "en"))
        assertEquals("kiswahili", ReasonsJson.text(r, "sw"))
    }
}
