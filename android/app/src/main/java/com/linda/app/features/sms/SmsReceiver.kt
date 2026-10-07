package com.linda.app.features.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wakes up for every incoming SMS, even with the app closed and the screen off.
 * Long messages arrive in several parts; the parts from one sender are joined before scoring.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val receivedAt = parts.first().timestampMillis
        val bySender = parts.groupBy { it.originatingAddress ?: "" }

        // goAsync keeps the receiver alive until scoring and saving finish (a few milliseconds).
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val processor = MessageProcessor(context.applicationContext)
                for ((sender, messages) in bySender) {
                    val body = messages.joinToString("") { it.messageBody ?: "" }
                    processor.process(body, sender.ifBlank { null }, receivedAt, source = "sms", notify = true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
