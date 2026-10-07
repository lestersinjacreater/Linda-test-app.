package com.linda.app.features.calls

/** Why a caller is being flagged. */
sealed class CallWarning {
    /** The radar confirmed this number as a scammer (offline blocklist). */
    data class ReportedNumber(val category: String) : CallWarning()
    /** This number sent a SCAM message to this phone recently. */
    data class RecentScamMessage(val minutesAgo: Int) : CallWarning()
}

/** The decision, kept free of Android so it can be unit tested. */
object CallRisk {
    const val RECENT_WINDOW_MS = 2 * 60 * 60 * 1000L // F13: a scam message in the last 2 hours

    fun decide(blockedCategory: String?, lastScamMessageAt: Long?, now: Long): CallWarning? {
        if (lastScamMessageAt != null && now - lastScamMessageAt in 0..RECENT_WINDOW_MS) {
            return CallWarning.RecentScamMessage(((now - lastScamMessageAt) / 60_000).toInt())
        }
        return blockedCategory?.let { CallWarning.ReportedNumber(it) }
    }
}
