package com.linda.app.features.alerts

import com.linda.app.features.detection.RiskLevel

/** Whether the phone is set to make noise. On vibrate or silent the person asked for quiet, so Linda stays quiet too. */
enum class Ringer { NORMAL, QUIET }

/**
 * When to read a warning aloud (F12). Kept free of Android so it can be unit tested. The text notification always
 * appears; the voice is the extra for people who may not read a screen quickly (older people, low vision, busy hands).
 */
object VoicePolicy {
    /** A burst of scam texts should not turn into a wall of talking. */
    const val MIN_GAP_MS = 30_000L

    fun shouldSpeak(
        level: RiskLevel,
        enabled: Boolean,
        source: String,
        ringer: Ringer,
        inCall: Boolean,
        lastSpokeAt: Long?,
        now: Long,
    ): Boolean {
        if (level != RiskLevel.SCAM || !enabled) return false
        if (source != "sms" && source != "demo") return false      // not for pasted text: the person is already looking at the answer
        if (ringer == Ringer.QUIET) return false
        if (inCall) return false                                    // never talk over a phone call or a ringing phone
        if (lastSpokeAt != null && now - lastSpokeAt in 0 until MIN_GAP_MS) return false
        return true
    }
}

/** On by default for people protected by a guardian; anything the person chose themselves always wins. */
object VoiceDefault {
    fun resolve(explicitChoice: Boolean?, guardianEnabled: Boolean): Boolean = explicitChoice ?: guardianEnabled
}

object VoiceLanguage {
    /** Swahili if the person wants Swahili AND the phone has a Swahili voice; otherwise English (always available). */
    fun choose(preferred: String, swahiliVoiceAvailable: Boolean): String =
        if (preferred == "sw" && swahiliVoiceAvailable) "sw" else "en"
}

/** What is said. Short, calm, one clear action. Never the message, a number or an amount. */
object VoiceScript {
    /** [reason] is the first plain-language reason, already in [language]. */
    fun warning(language: String, reason: String?): String {
        val why = reason?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }?.let { "$it. " } ?: ""
        return if (language == "sw") "Onyo la Linda. Ujumbe huu huenda ni ulaghai. ${why}Usitume pesa wala kutoa PIN yako."
        else "Linda warning. This message is probably a scam. ${why}Do not send money and do not share your PIN."
    }

    /** The headline of the full-screen scam alert. Must match strings.xml `takeover_headline` exactly (a test checks): the voice reads what the screen shows. */
    const val TAKEOVER_HEADLINE_EN = "Stop. This looks like a scam."
    const val TAKEOVER_HEADLINE_SW = "Simama. Huu unaonekana kuwa ulaghai."

    /** What the full-screen alert says aloud: its headline and then the top reason, word for word as shown. */
    fun takeover(language: String, reason: String?): String {
        val headline = if (language == "sw") TAKEOVER_HEADLINE_SW else TAKEOVER_HEADLINE_EN
        val why = reason?.trim()?.takeIf { it.isNotEmpty() }
        return if (why == null) headline else "$headline $why"
    }

    fun test(language: String): String =
        if (language == "sw") "Huu ni mtihani wa sauti ya Linda. Ukisikia hili, maonyo yatasomwa kwa sauti."
        else "This is a test of Linda's voice. If you can hear this, warnings will be read aloud."
}
