package com.linda.app.features.learning

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.linda.app.LindaApp
import com.linda.app.core.data.MpesaReceiptEntity

/**
 * The phone side of the M-Pesa memory: where receipts are stored (Room) and when they are read.
 * Everything stays on the phone. Nothing here is ever reported or synced.
 *
 * "Complete since" is the honest part: Linda only says "no such money arrived" for a time window it saw in full,
 * either because it read the inbox back that far, or because it has been listening to every SMS since then.
 */
object LearningStore {
    private const val FILE = "linda_learning"
    private const val ENABLED = "mpesa_memory_enabled"
    private const val LISTENING_SINCE = "mpesa_listening_since"   // when Linda started hearing every new M-Pesa message
    private const val COMPLETE_SINCE = "mpesa_complete_since"     // earliest moment the memory has no gaps after
    private const val BACKFILLED = "mpesa_backfilled"             // the inbox was read once (not repeated after Forget)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun dao(ctx: Context) = (ctx.applicationContext as LindaApp).database.learningDao()

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean(ENABLED, true)

    /** Turning the memory off also forgets it: Linda keeps nothing it is not allowed to use. */
    suspend fun setEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(ENABLED, value).apply()
        if (!value) forget(ctx)
    }

    fun canReadInbox(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** The first time the memory runs, it notes that it is listening from now on. */
    private fun startListening(ctx: Context, now: Long): Long {
        val p = prefs(ctx)
        if (!p.contains(LISTENING_SINCE)) p.edit().putLong(LISTENING_SINCE, now).putLong(COMPLETE_SINCE, now).apply()
        return p.getLong(COMPLETE_SINCE, now)
    }

    /** Called for every incoming text from the M-Pesa sender (and demo ones): keeps the receipt, if it is one. */
    suspend fun learn(ctx: Context, sender: String?, body: String, at: Long) {
        if (!enabled(ctx) || !MpesaReceipts.isMpesaSender(sender)) return
        startListening(ctx, at)
        val r = MpesaReceipts.parse(sender, body, at) ?: return
        dao(ctx).addReceipts(listOf(MpesaReceiptEntity(r.codeHash, r.at, r.amountCents)))
        dao(ctx).forgetReceiptsBefore(at - MpesaMemory.RETENTION_MS)
    }

    /**
     * Reads M-Pesa messages from the inbox for the last [windowMs] (needs the "read messages" permission) and keeps the
     * receipts. Returns false when it could not read. The inbox is the source of truth: reading it closes any gap left
     * while the phone had Linda stopped in the background.
     */
    suspend fun readInbox(ctx: Context, now: Long, windowMs: Long): Boolean {
        if (!enabled(ctx) || !canReadInbox(ctx)) return false
        val since = now - windowMs
        val found = ArrayList<MpesaReceiptEntity>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "UPPER(${Telephony.Sms.ADDRESS}) IN ('MPESA', 'M-PESA') AND ${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    MpesaReceipts.parse(c.getString(0), c.getString(1) ?: "", c.getLong(2))
                        ?.let { found += MpesaReceiptEntity(it.codeHash, it.at, it.amountCents) }
                }
            } ?: return false
        } catch (e: SecurityException) {
            return false
        }
        dao(ctx).addReceipts(found)
        val p = prefs(ctx)
        startListening(ctx, now)
        if (since < p.getLong(COMPLETE_SINCE, now)) p.edit().putLong(COMPLETE_SINCE, since).apply()
        return true
    }

    /** Once, when allowed: fills the memory from the last 30 days of the inbox so it is useful from day one. */
    suspend fun backfillOnce(ctx: Context, now: Long = System.currentTimeMillis()) {
        if (prefs(ctx).getBoolean(BACKFILLED, false)) return
        if (readInbox(ctx, now, MpesaMemory.RETENTION_MS)) prefs(ctx).edit().putBoolean(BACKFILLED, true).apply()
    }

    /** The memory for checking one claim at [at], or null when it is switched off. */
    suspend fun snapshot(ctx: Context, at: Long, windowMs: Long): MpesaMemory? {
        if (!enabled(ctx)) return null
        readInbox(ctx, at, windowMs) // when allowed, re-check the inbox for exactly the window being judged
        val completeSince = startListening(ctx, at)
        val receipts = dao(ctx).receiptsSince(at - MpesaMemory.RETENTION_MS).map { Receipt(it.at, it.amountCents, it.codeHash) }
        return MpesaMemory(receipts, completeSince)
    }

    /** "Forget what Linda learned": deletes every receipt and starts listening afresh from now. */
    suspend fun forget(ctx: Context, now: Long = System.currentTimeMillis()) {
        dao(ctx).forgetAllReceipts()
        // BACKFILLED stays set, so Linda does not quietly read the whole inbox again; it relearns as messages arrive.
        prefs(ctx).edit().putLong(LISTENING_SINCE, now).putLong(COMPLETE_SINCE, now).apply()
    }

    fun observeCount(ctx: Context) = dao(ctx).observeReceiptCount()
}
