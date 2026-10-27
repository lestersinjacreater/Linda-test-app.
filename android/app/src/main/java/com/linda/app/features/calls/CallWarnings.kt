package com.linda.app.features.calls

import android.content.Context
import com.linda.app.LindaApp
import com.linda.app.core.util.PhoneHash
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.features.alerts.AlertNotifier
import com.linda.app.features.sync.PrivateLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [label]: what to show (null = nothing known). [checkedLive]: the gateway answered. [knownOffline]: found on the phone. */
data class CallCheck(val label: CallLabel?, val checkedLive: Boolean, val knownOffline: Boolean)

object CallWarnings {
    /**
     * Checks the number before the call is answered and shows a heads-up notification: a warning if it is
     * flagged, or a verified badge if it is a genuine number. Linda only labels: it never rejects or blocks
     * the call, the user decides. Returns what it found, so demo mode can show it too.
     *
     * Order: the phone's own offline knowledge first (instant, works with no network); only when that finds
     * nothing does it ask the gateway privately (5.10), with a short timeout so the call is never held up.
     */
    suspend fun check(context: Context, rawNumber: String?, now: Long = System.currentTimeMillis()): CallLabel? {
        val msisdn = PhoneNumbers.toMsisdn(rawNumber) ?: return null
        val label = lookup(context, msisdn, now).label ?: return null
        when (label) {
            is CallWarning -> AlertNotifier.showCallWarning(context, msisdn, label)
            is VerifiedCall -> AlertNotifier.showVerifiedCaller(context, msisdn, label.caller)
        }
        // The big card over the ringing screen (when the person allowed "Display over other apps"); the notification
        // above stays as the fallback, and for phones that did not allow it.
        CallCardOverlay.show(context, msisdn, label)
        return label
    }

    /**
     * What Linda knows about a number, without showing anything: used for calls and by Ask Linda. [CallCheck.checkedLive]
     * says whether Safaricom's list was asked and answered (false: offline, no server, or no need to ask).
     */
    suspend fun lookup(
        context: Context, msisdn: String, now: Long = System.currentTimeMillis(), timeoutMs: Int = 1_500,
    ): CallCheck = withContext(Dispatchers.IO) { // the network lookup must never run on the screen's thread
        val db = (context.applicationContext as LindaApp).database
        val blocked = db.blockedNumberDao().find(PhoneHash.of(msisdn))
        val recent = db.detectionDao().latestScamFrom(msisdn, now - CallRisk.RECENT_WINDOW_MS)
        val verified = VerifiedCallers.find(context, msisdn)
        val live = if (blocked == null && recent == null && verified == null) PrivateLookup.ask(context, msisdn, timeoutMs) else null
        val label = CallRisk.decide(blocked?.category, recent?.receivedAt, now, verified, live?.entry?.status, live?.entry?.category)
        CallCheck(label, checkedLive = live?.reached == true, knownOffline = blocked != null || recent != null || verified != null)
    }
}
