package com.linda.app.features.calls

import android.content.Context
import com.linda.app.LindaApp
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.features.alerts.AlertNotifier

object CallWarnings {
    /**
     * Looks the number up locally (no network) and shows a heads-up notification before the call is
     * answered: a warning if it is flagged, or a verified badge if it is a genuine number.
     * Linda only labels: it never rejects or blocks the call, the user decides.
     * Returns what it found, so demo mode can show it too.
     */
    suspend fun check(context: Context, rawNumber: String?, now: Long = System.currentTimeMillis()): CallLabel? {
        val msisdn = PhoneNumbers.toMsisdn(rawNumber) ?: return null
        val db = (context.applicationContext as LindaApp).database
        val blocked = db.blockedNumberDao().find(msisdn)
        val recent = db.detectionDao().latestScamFrom(msisdn, now - CallRisk.RECENT_WINDOW_MS)
        val verified = VerifiedCallers.find(context, msisdn)
        val label = CallRisk.decide(blocked?.category, recent?.receivedAt, now, verified) ?: return null
        when (label) {
            is CallWarning -> AlertNotifier.showCallWarning(context, msisdn, label)
            is VerifiedCall -> AlertNotifier.showVerifiedCaller(context, msisdn, label.caller)
        }
        return label
    }
}
