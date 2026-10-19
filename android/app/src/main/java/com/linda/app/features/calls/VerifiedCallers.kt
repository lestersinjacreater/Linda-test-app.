package com.linda.app.features.calls

import android.content.Context
import org.json.JSONObject
import java.io.File

/** A genuine number that calls customers, e.g. Safaricom Customer Care on 0722 000 000. */
data class VerifiedCaller(val msisdn: String, val nameEn: String, val nameSw: String, val kind: String) {
    fun name(language: String): String = if (language == "sw") nameSw else nameEn
}

/** Parses shared/verified_callers.json and the gateway's GET /v1/verified-callers (same shape). */
object VerifiedCallersParser {
    fun parse(json: String): Map<String, VerifiedCaller> {
        val callers = JSONObject(json).getJSONArray("callers")
        return (0 until callers.length()).associate {
            val c = callers.getJSONObject(it)
            val caller = VerifiedCaller(
                c.getString("msisdn"), c.getString("name_en"), c.getString("name_sw"), c.optString("kind", "business"),
            )
            caller.msisdn to caller
        }
    }
}

/**
 * The phone's copy of the verified list. Works offline: it starts from the copy bundled in the app
 * (assets/verified_callers.json) and is replaced by the gateway's newer copy after each sync.
 * The list is public, so keeping all of it on the phone leaks nothing (unlike flagged numbers).
 */
object VerifiedCallers {
    private const val ASSET = "verified_callers.json"
    private const val CACHE = "verified_callers.json"

    @Volatile private var cached: Map<String, VerifiedCaller>? = null

    fun find(context: Context, msisdn: String): VerifiedCaller? = all(context)[msisdn]

    fun all(context: Context): Map<String, VerifiedCaller> {
        cached?.let { return it }
        val app = context.applicationContext
        val synced = File(app.filesDir, CACHE)
        val loaded = (if (synced.exists()) parseOrNull(synced.readText()) else null)
            ?: parseOrNull(app.assets.open(ASSET).bufferedReader().use { it.readText() })
            ?: emptyMap()
        cached = loaded
        return loaded
    }

    /** Called by the sync with the gateway's response. A broken response is ignored, never stored. */
    fun store(context: Context, json: String): Boolean {
        val parsed = parseOrNull(json) ?: return false
        File(context.applicationContext.filesDir, CACHE).writeText(json)
        cached = parsed
        return true
    }

    private fun parseOrNull(json: String): Map<String, VerifiedCaller>? =
        try { VerifiedCallersParser.parse(json) } catch (e: Exception) { null }
}
