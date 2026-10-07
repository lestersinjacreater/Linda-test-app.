package com.linda.app.features.detection

import org.json.JSONArray
import org.json.JSONObject

/** Saves a verdict's reasons as JSON (for the database) and reads them back. */
object ReasonsJson {
    fun encode(reasons: List<Reason>): String {
        val arr = JSONArray()
        for (r in reasons) arr.put(JSONObject().put("code", r.code).put("en", r.english).put("sw", r.swahili))
        return arr.toString()
    }

    fun decode(json: String): List<Reason> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Reason(o.getString("code"), o.getString("en"), o.getString("sw"))
        }
    }

    /** The reason text in [language] ("en" or "sw"). */
    fun text(reason: Reason, language: String) = if (language == "sw") reason.swahili else reason.english
}
