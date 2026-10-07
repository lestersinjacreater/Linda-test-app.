package com.linda.app.features.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.linda.app.core.util.Prefs
import com.linda.app.features.detection.Reason
import com.linda.app.features.detection.ReasonsJson
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.detection.Verdict
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Reads SCAM warnings aloud with Android's built-in TextToSpeech (F12). No extra library, works offline if the phone's
 * voice data is installed. The rules for WHEN to speak are in [VoicePolicy]; this file is the Android plumbing.
 */
object VoiceWarnings {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main) // TextToSpeech must be created on the main thread
    @Volatile private var lastSpokeAt: Long? = null

    /** The person's own choice if they made one, otherwise on only when Family Guardian is on. */
    fun isEnabled(context: Context): Boolean = VoiceDefault.resolve(Prefs.voiceChoice(context), Prefs.guardianEnabled(context))

    /**
     * Speaks the warning if the rules allow it. Returns the job so the SMS receiver can keep the app alive until the
     * speech ends; returns null when nothing is spoken.
     */
    fun speak(context: Context, verdict: Verdict, source: String): Job? {
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        val now = System.currentTimeMillis()
        val allowed = VoicePolicy.shouldSpeak(
            level = verdict.level,
            enabled = isEnabled(app),
            source = source,
            ringer = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) Ringer.NORMAL else Ringer.QUIET,
            inCall = audio.mode != AudioManager.MODE_NORMAL, // in a call, ringing, or a voice/video call
            lastSpokeAt = lastSpokeAt,
            now = now,
        )
        if (!allowed) return null
        lastSpokeAt = now
        val preferred = Prefs.effectiveLanguage(app)
        val reason = verdict.reasons.firstOrNull()
        return scope.launch {
            say(app, preferred) { lang -> VoiceScript.warning(lang, reason?.let { ReasonsJson.text(it, lang) }) }
        }
    }

    /**
     * The full-screen scam alert reads its own headline and top reason (docs/design-system.md 7.4). Same rules as an
     * arriving text (only if voice is on, phone not on silent or in a call, not twice within 30 s), so it never
     * repeats the warning that was just spoken when the text arrived.
     */
    fun speakTakeover(context: Context, reason: Reason?): Job? {
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        val now = System.currentTimeMillis()
        val allowed = VoicePolicy.shouldSpeak(
            level = RiskLevel.SCAM,
            enabled = isEnabled(app),
            source = "sms",
            ringer = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) Ringer.NORMAL else Ringer.QUIET,
            inCall = audio.mode != AudioManager.MODE_NORMAL,
            lastSpokeAt = lastSpokeAt,
            now = now,
        )
        if (!allowed) return null
        lastSpokeAt = now
        val preferred = Prefs.effectiveLanguage(app)
        return scope.launch { say(app, preferred) { lang -> VoiceScript.takeover(lang, reason?.let { ReasonsJson.text(it, lang) }) } }
    }

    /** The "Test voice" button in Settings. */
    suspend fun speakTest(context: Context): Boolean {
        val app = context.applicationContext
        return say(app, Prefs.effectiveLanguage(app)) { lang -> VoiceScript.test(lang) }
    }

    /** Does this phone have a Kiswahili voice? Many cheap phones do not, and then English is spoken instead. */
    suspend fun swahiliVoiceAvailable(context: Context): Boolean {
        val tts = create(context.applicationContext) ?: return false
        return try { hasSwahili(tts) } finally { tts.shutdown() }
    }

    private fun hasSwahili(tts: TextToSpeech) = tts.isLanguageAvailable(Locale("sw")) >= TextToSpeech.LANG_AVAILABLE

    /** Starts the speech engine and waits until it is ready. Returns null if the phone has none. */
    private suspend fun create(context: Context): TextToSpeech? = suspendCancellableCoroutine { cont ->
        val holder = arrayOfNulls<TextToSpeech>(1)
        holder[0] = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) cont.resume(holder[0]) else { holder[0]?.shutdown(); cont.resume(null) }
        }
        cont.invokeOnCancellation { holder[0]?.shutdown() }
    }

    /** Speaks [script] in the chosen language, waits for it to finish (at most 20 s), then releases the engine. */
    private suspend fun say(context: Context, preferred: String, script: (String) -> String): Boolean {
        val tts = create(context) ?: return false
        return try {
            val language = VoiceLanguage.choose(preferred, hasSwahili(tts))
            if (tts.setLanguage(Locale(language)) < TextToSpeech.LANG_AVAILABLE) return false
            tts.setAudioAttributes(
                AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
            )
            val finished = CompletableDeferred<Unit>()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { finished.complete(Unit) }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { finished.complete(Unit) }
            })
            if (tts.speak(script(language), TextToSpeech.QUEUE_FLUSH, null, "linda-warning") == TextToSpeech.ERROR) return false
            withTimeoutOrNull(20_000) { finished.await() }
            true
        } finally {
            tts.stop()
            tts.shutdown()
        }
    }
}
