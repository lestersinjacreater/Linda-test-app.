package com.linda.app.core.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Small settings store (SharedPreferences). Only things that are not worth a database table. */
object Prefs {
    private const val FILE = "linda_prefs"
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun onboarded(ctx: Context) = p(ctx).getBoolean("onboarded", false)
    fun setOnboarded(ctx: Context) = p(ctx).edit().putBoolean("onboarded", true).apply()

    /** "system", "en" or "sw". */
    fun language(ctx: Context): String = p(ctx).getString("language", "system") ?: "system"
    fun setLanguage(ctx: Context, value: String) = p(ctx).edit().putString("language", value).apply()

    /** The language warnings are written in: the chosen one, or the phone's if "system" (Swahili or English). */
    fun effectiveLanguage(ctx: Context): String = when (val chosen = language(ctx)) {
        "en", "sw" -> chosen
        else -> if (Locale.getDefault().language == "sw") "sw" else "en"
    }

    /** Reports to the radar are OFF until the user taps Agree (consent screen). */
    fun reportingConsent(ctx: Context) = p(ctx).getBoolean("reporting_consent", false)
    fun setReportingConsent(ctx: Context, value: Boolean) = p(ctx).edit().putBoolean("reporting_consent", value).apply()

    /** Where the radar lives. Empty until set in the hidden developer screen (or a default is shipped). */
    fun serverUrl(ctx: Context): String = p(ctx).getString("server_url", "") ?: ""
    fun setServerUrl(ctx: Context, value: String) = p(ctx).edit().putString("server_url", value.trim().trimEnd('/')).apply()

    /** Family Guardian: OFF until the protected person turns it on. The guardian's number never leaves this phone except as an SMS recipient. */
    fun guardianEnabled(ctx: Context) = p(ctx).getBoolean("guardian_enabled", false)
    fun setGuardianEnabled(ctx: Context, value: Boolean) = p(ctx).edit().putBoolean("guardian_enabled", value).apply()
    fun guardianNumber(ctx: Context): String = p(ctx).getString("guardian_number", "") ?: ""
    fun setGuardianNumber(ctx: Context, value: String) = p(ctx).edit().putString("guardian_number", value).apply()
    /** What the guardian calls this person in the alert, e.g. "Mum". */
    fun protectedName(ctx: Context): String = p(ctx).getString("protected_name", "") ?: ""
    fun setProtectedName(ctx: Context, value: String) = p(ctx).edit().putString("protected_name", value).apply()

    /** Voice warnings: null until the person chooses, so the default can follow Family Guardian (on for protected people). */
    fun voiceChoice(ctx: Context): Boolean? = if (p(ctx).contains("voice_enabled")) p(ctx).getBoolean("voice_enabled", false) else null
    fun setVoiceChoice(ctx: Context, value: Boolean) = p(ctx).edit().putBoolean("voice_enabled", value).apply()

    /** The demo overlay for judges (tap the logo 7 times). Off until switched on. */
    fun demoOverlay(ctx: Context) = p(ctx).getBoolean("demo_overlay", false)
    fun setDemoOverlay(ctx: Context, value: Boolean) = p(ctx).edit().putBoolean("demo_overlay", value).apply()

    /** The hidden developer screen (tap the version 7 times). */
    fun devMode(ctx: Context) = p(ctx).getBoolean("dev_mode", false)
    fun setDevMode(ctx: Context, value: Boolean) = p(ctx).edit().putBoolean("dev_mode", value).apply()

    /** `as_of` from the last successful blocklist sync (sent back as ?since=), and when it happened. */
    fun lastSyncAsOf(ctx: Context): String? = p(ctx).getString("sync_as_of", null)
    fun lastSyncAt(ctx: Context): Long = p(ctx).getLong("sync_at", 0L)
    fun setLastSync(ctx: Context, asOf: String, at: Long) = p(ctx).edit().putString("sync_as_of", asOf).putLong("sync_at", at).apply()
    fun clearLastSync(ctx: Context) = p(ctx).edit().remove("sync_as_of").remove("sync_at").apply()

    /** Random anonymous id, made once per install and hashed. Never derived from the phone number. */
    fun deviceId(ctx: Context): String {
        val existing = p(ctx).getString("device_id", null)
        if (existing != null) return existing
        val fresh = com.linda.app.features.reporting.DeviceIds.fromSeed(java.util.UUID.randomUUID().toString())
        p(ctx).edit().putString("device_id", fresh).apply()
        return fresh
    }

    /** A context in a fixed language ("en" or "sw"), used where both languages are shown at once. */
    fun contextFor(ctx: Context, language: String): Context {
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(Locale(language))
        return ctx.createConfigurationContext(config)
    }

    /** A context whose resources use the chosen language, so strings.xml / values-sw/strings.xml are picked. */
    fun localized(ctx: Context): Context {
        if (language(ctx) == "system") return ctx
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(Locale(effectiveLanguage(ctx)))
        return ctx.createConfigurationContext(config)
    }
}
