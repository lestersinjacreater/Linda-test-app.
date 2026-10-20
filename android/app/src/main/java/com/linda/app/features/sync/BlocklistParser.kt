package com.linda.app.features.sync

import org.json.JSONObject

/** One hashed entry from the gateway (contracts 5.10 and 5.11). Never a phone number. */
data class BlockedNumber(val hash: String, val category: String, val label: String = "", val labelSw: String = "")

/** What changed on the gateway's hashed offline list since the last sync (contract 5.11). */
data class BlocklistDelta(val asOf: String, val added: List<BlockedNumber>, val removed: List<String>)

object BlocklistParser {
    fun parse(json: String): BlocklistDelta {
        val o = JSONObject(json)
        val added = o.getJSONArray("added")
        val removed = o.getJSONArray("removed")
        return BlocklistDelta(
            asOf = o.getString("as_of"),
            added = (0 until added.length()).map {
                val a = added.getJSONObject(it)
                val category = if (a.isNull("category")) "other" else a.optString("category", "other").ifBlank { "other" }
                BlockedNumber(a.getString("hash"), category,
                    a.optString("label", ""), a.optString("label_sw", ""))
            },
            removed = (0 until removed.length()).map { removed.getString(it) },
        )
    }
}
