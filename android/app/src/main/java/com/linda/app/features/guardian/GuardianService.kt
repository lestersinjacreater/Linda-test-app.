package com.linda.app.features.guardian

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import com.linda.app.LindaApp
import com.linda.app.core.data.GuardianAlertEntity
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.core.util.Prefs
import com.linda.app.core.util.formatTimeOfDay
import com.linda.app.features.detection.Verdict

/** Sends the alert SMS to the guardian, after [GuardianPolicy] says it is allowed. */
object GuardianService {
    fun hasSmsPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Called for every SCAM verdict from a real or demo SMS. Does nothing unless the person opted in and every rule passes. */
    suspend fun maybeAlert(context: Context, verdict: Verdict, sender: String?, receivedAt: Long): AlertDecision {
        val app = context.applicationContext as LindaApp
        val dao = app.database.guardianAlertDao()
        val key = sender?.takeIf { it.isNotBlank() }?.let { GuardianPolicy.senderKey(it) }
        val decision = GuardianPolicy.decide(
            level = verdict.level,
            enabled = Prefs.guardianEnabled(context),
            guardianNumber = Prefs.guardianNumber(context),
            protectedName = Prefs.protectedName(context),
            hasSmsPermission = hasSmsPermission(context),
            sender = sender,
            verifiedSenders = app.detector.verifiedSenders,
            lastAlertForSenderAt = key?.let { dao.lastSentAt(it) },
            now = System.currentTimeMillis(),
        )
        if (decision != AlertDecision.Send || key == null) return decision

        val text = GuardianMessage.alert(Prefs.effectiveLanguage(context), Prefs.protectedName(context), verdict.category, formatTimeOfDay(receivedAt))
        val ok = send(context, Prefs.guardianNumber(context), text)
        dao.insert(GuardianAlertEntity(senderKey = key, category = verdict.category, sentAt = System.currentTimeMillis(), status = if (ok) "sent" else "failed"))
        return decision
    }

    /** "Send a test alert": proves the setup works without waiting for a scam. Not rate-limited, and not counted against the limit. */
    suspend fun sendTest(context: Context): Boolean {
        if (!hasSmsPermission(context)) return false
        val dao = (context.applicationContext as LindaApp).database.guardianAlertDao()
        val ok = send(context, Prefs.guardianNumber(context), GuardianMessage.test(Prefs.effectiveLanguage(context), Prefs.protectedName(context)))
        dao.insert(GuardianAlertEntity(senderKey = "TEST", category = "test", sentAt = System.currentTimeMillis(), status = if (ok) "sent" else "failed", isTest = true))
        return ok
    }

    @Suppress("DEPRECATION") // SmsManager.getDefault() is the only way before Android 12 (API 31)
    private fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    /** true = handed to the phone's SMS system (the carrier may still fail to deliver it). Never throws. */
    private fun send(context: Context, guardianNumber: String, text: String): Boolean {
        val msisdn = PhoneNumbers.toMsisdn(guardianNumber) ?: return false
        return try {
            val sms = smsManager(context)
            val parts = sms.divideMessage(text)
            if (parts.size > 1) sms.sendMultipartTextMessage("+$msisdn", null, parts, null, null)
            else sms.sendTextMessage("+$msisdn", null, text, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
