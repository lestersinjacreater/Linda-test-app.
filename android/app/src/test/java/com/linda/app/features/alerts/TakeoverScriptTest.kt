package com.linda.app.features.alerts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** The voice must read what the screen shows (docs/design-system.md section 11, rule 6). */
class TakeoverScriptTest {

    private fun stringFromResources(folder: String, name: String): String {
        // Unit tests run from the module folder (android/app); fall back to the repo root layout just in case.
        val file = listOf("src/main/res/$folder/strings.xml", "app/src/main/res/$folder/strings.xml").map(::File).first { it.exists() }
        val match = Regex("<string name=\"$name\">(.*?)</string>").find(file.readText()) ?: error("$name not found in $folder")
        return match.groupValues[1].replace("\\'", "'").replace("\\\"", "\"")
    }

    @Test
    fun spokenHeadline_isTheSameWordsAsTheScreen() {
        assertEquals(stringFromResources("values", "takeover_headline"), VoiceScript.TAKEOVER_HEADLINE_EN)
        assertEquals(stringFromResources("values-sw", "takeover_headline"), VoiceScript.TAKEOVER_HEADLINE_SW)
    }

    @Test
    fun takeoverScript_isHeadlineThenTopReason() {
        assertEquals("Stop. This looks like a scam. It pushes you to open a link.", VoiceScript.takeover("en", "  It pushes you to open a link.  "))
        assertEquals("Simama. Huu unaonekana kuwa ulaghai. Una kiungo.", VoiceScript.takeover("sw", "Una kiungo."))
    }

    @Test
    fun takeoverScript_withoutAReason_isJustTheHeadline() {
        assertEquals(VoiceScript.TAKEOVER_HEADLINE_EN, VoiceScript.takeover("en", null))
        assertEquals(VoiceScript.TAKEOVER_HEADLINE_EN, VoiceScript.takeover("en", "   "))
    }
}
