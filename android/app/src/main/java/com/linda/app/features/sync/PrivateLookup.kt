package com.linda.app.features.sync

import android.content.Context
import com.linda.app.core.net.Http
import com.linda.app.core.util.PhoneHash
import com.linda.app.core.util.Prefs
import org.json.JSONObject

/** What the gateway says about one number, found privately by its hash prefix (contract 5.10). */
data class LookupEntry(val status: String, val category: String?, val label: String, val labelSw: String)

/** [reached]: the gateway answered. [entry]: what it knows about the number (null = nothing). */
data class LiveAnswer(val reached: Boolean, val entry: LookupEntry?)

object LookupParser {
    /** Picks this number's entry out of the bucket. Every other entry in the bucket is ignored. */
    fun find(json: String, hash: String): LookupEntry? {
        val entries = JSONObject(json).getJSONArray("entries")
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            if (e.getString("hash") == hash) {
                // isNull first: on Android, optString turns a JSON null into the text "null".
                val category = if (e.isNull("category")) null else e.optString("category", "").ifBlank { null }
                return LookupEntry(e.getString("status"), category, e.optString("label", ""), e.optString("label_sw", ""))
            }
        }
        return null
    }
}

/**
 * Asks the gateway about a number WITHOUT telling it which number: only the first 5 characters of the hash
 * are sent, which match thousands of possible numbers. The phone picks its own answer out of the reply.
 * Returns null when there is no server, no network, no answer in time, or nothing known about the number.
 */
object PrivateLookup {
    fun headers(context: Context): Map<String, String> = mapOf("X-Linda-Device" to Prefs.deviceId(context))

    fun check(context: Context, msisdn: String, timeoutMs: Int = 1_500): LookupEntry? = ask(context, msisdn, timeoutMs).entry

    /**
     * The same lookup, also saying whether the gateway answered at all. Ask Linda needs that difference: "Safaricom's
     * list has nothing on this number" is a different answer from "Linda could not reach Safaricom's list".
     */
    fun ask(context: Context, msisdn: String, timeoutMs: Int = 1_500): LiveAnswer {
        val server = Prefs.serverUrl(context)
        if (server.isBlank()) return LiveAnswer(false, null)
        val hash = PhoneHash.of(msisdn)
        val response = Http.get("$server/v1/lookup?prefix=${hash.take(PhoneHash.PREFIX_LENGTH)}", timeoutMs, headers(context))
            ?: return LiveAnswer(false, null)
        if (response.code != 200) return LiveAnswer(false, null)
        return try { LiveAnswer(true, LookupParser.find(response.body, hash)) } catch (e: Exception) { LiveAnswer(false, null) }
    }
}
