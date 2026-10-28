package com.linda.app.features.calls

/** What Linda shows for an incoming call: a warning, a verified badge, or "in your contacts". */
sealed interface CallLabel

/** Why a caller is being flagged. */
sealed class CallWarning : CallLabel {
    /** The gateway confirmed this number as a scammer (offline list or live lookup). */
    data class ReportedNumber(val category: String) : CallWarning()
    /** This number sent a SCAM message to this phone recently. */
    data class RecentScamMessage(val minutesAgo: Int) : CallWarning()
    /** Reported but not proven (suspected, or reported to Safaricom): a softer warning. Live lookup only. */
    data class SuspectedNumber(val category: String?) : CallWarning()
}

/** A genuine number from the verified list, e.g. Safaricom Customer Care. */
data class VerifiedCall(val caller: VerifiedCaller) : CallLabel

/**
 * The number is saved in the person's own contacts and nothing about it looks like a scam. A plain fact from this
 * phone, not a badge: nobody verified it, so it never looks like [VerifiedCall] (docs/design-system.md 7.19).
 */
data object SavedContact : CallLabel

/** The decision, kept free of Android so it can be unit tested. */
object CallRisk {
    const val RECENT_WINDOW_MS = 2 * 60 * 60 * 1000L // F13: a scam message in the last 2 hours

    /**
     * Strongest signal first. Warnings always win over the badge: if anything about this number looks like a
     * scam, Linda never vouches for it. The badge is only for numbers on the verified list with no scam signal.
     * `liveStatus`/`liveCategory` come from the private lookup (5.10) when the phone could reach the gateway.
     * [inContacts] comes last: a saved contact can still be a stolen phone or a reported number, so it only says
     * "in your contacts" when nothing else is known.
     */
    fun decide(
        blockedCategory: String?,
        lastScamMessageAt: Long?,
        now: Long,
        verified: VerifiedCaller? = null,
        liveStatus: String? = null,
        liveCategory: String? = null,
        inContacts: Boolean = false,
    ): CallLabel? {
        if (lastScamMessageAt != null && now - lastScamMessageAt in 0..RECENT_WINDOW_MS) {
            return CallWarning.RecentScamMessage(((now - lastScamMessageAt) / 60_000).toInt())
        }
        blockedCategory?.let { return CallWarning.ReportedNumber(it) }
        if (liveStatus == "confirmed") return CallWarning.ReportedNumber(liveCategory ?: "other")
        if (liveStatus == "suspected" || liveStatus == "reported") return CallWarning.SuspectedNumber(liveCategory)
        verified?.let { return VerifiedCall(it) }
        return if (inContacts) SavedContact else null
    }
}
