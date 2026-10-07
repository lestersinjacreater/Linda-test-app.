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

    /** A context whose resources use the chosen language, so strings.xml / values-sw/strings.xml are picked. */
    fun localized(ctx: Context): Context {
        if (language(ctx) == "system") return ctx
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(Locale(effectiveLanguage(ctx)))
        return ctx.createConfigurationContext(config)
    }
}
