package com.linda.app.features.calls

import android.content.Context
import com.linda.app.LindaApp
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.features.alerts.AlertNotifier

object CallWarnings {
    /**
     * Looks the number up locally (no network) and shows a heads-up notification if it is flagged.
     * Linda only warns: it never rejects or blocks the call, the user decides.
     * Returns what it found, so demo mode can show it too.
     */
    suspend fun check(context: Context, rawNumber: String?, now: Long = System.currentTimeMillis()): CallWarning? {
        val msisdn = PhoneNumbers.toMsisdn(rawNumber) ?: return null
        val db = (context.applicationContext as LindaApp).database
        val blocked = db.blockedNumberDao().find(msisdn)
        val recent = db.detectionDao().latestScamFrom(msisdn, now - CallRisk.RECENT_WINDOW_MS)
        val warning = CallRisk.decide(blocked?.category, recent?.receivedAt, now) ?: return null
        AlertNotifier.showCallWarning(context, msisdn, warning)
        return warning
    }
}
