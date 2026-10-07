package com.linda.app.features.sync

import org.json.JSONObject

data class BlockedNumber(val msisdn: String, val category: String)

/** What changed on the radar's blocklist since the last sync (contract 5.3). */
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
                BlockedNumber(a.getString("msisdn"), a.optString("category", "other"))
            },
            removed = (0 until removed.length()).map { removed.getString(it) },
        )
    }
}
