package com.linda.app.features.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WarningRouteTest {
    @Test fun tappingTheWarningShowsTheStopScreenFirst() {
        assertEquals("detail/7?takeover=true&report=false", WarningRoute.of(7, null))
        assertEquals("detail/7?takeover=true&report=false", WarningRoute.of(7, AlertNotifier.OPEN_TAKEOVER))
    }

    @Test fun seeWhyGoesStraightToTheExplanation() {
        assertEquals("detail/7?takeover=false&report=false", WarningRoute.of(7, AlertNotifier.OPEN_WHY))
    }

    @Test fun reportOpensTheSheetThatShowsWhatIsSentNeverASilentReport() {
        assertEquals("detail/7?takeover=false&report=true", WarningRoute.of(7, AlertNotifier.OPEN_REPORT))
    }

    private fun strings(folder: String): Map<String, String> {
        val file = listOf("src/main/res/$folder/strings.xml", "app/src/main/res/$folder/strings.xml").map(::File).first { it.exists() }
        return Regex("""<string name="([a-z0-9_]+)"[^>]*>(.*?)</string>""").findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test fun warningTitlesCarryNoEmojiInEitherLanguage() {
        val keys = listOf("alert_scam_title", "alert_caution_title", "call_warning_title", "call_verified_title")
        for (folder in listOf("values", "values-sw")) {
            val s = strings(folder)
            for (k in keys) assertFalse("$folder/$k", s.getValue(k).any { Character.getType(it) == Character.SURROGATE.toInt() || it.code in 0x2600..0x27BF })
        }
    }

    @Test fun newWordingExistsInEnglishAndSwahili() {
        val en = strings("values"); val sw = strings("values-sw")
        for (k in listOf("notif_action_why", "action_undo", "detail_how_decided", "settings_section_protection", "learning_row")) {
            assertTrue(k, en.getValue(k).isNotBlank() && sw.getValue(k).isNotBlank() && en[k] != sw[k])
        }
        for (s in listOf(en, sw)) for (k in listOf("consent_body", "report_dialog_intro")) assertFalse(k, s.getValue(k).contains("rada"))
    }
}
