package com.linda.app.features.learning

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.linda.app.LindaApp
import com.linda.app.core.data.MpesaReceiptEntity
import com.linda.app.core.data.SeenSenderEntity
import com.linda.app.core.util.PhoneNumbers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

    /**
     * One switch for everything Linda learns on this phone (M-Pesa memory and sender history).
     * Turning it off also forgets: Linda keeps nothing it is not allowed to use.
     */
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

    // ---------- Know your senders ----------

    /**
     * What is known about the phone number [sender] before its new message is counted: Linda's own count (texts and
     * warnings), and, when inbox reading is allowed, the person's own texts to and from it. Null for a business name.
     */
    suspend fun senderHistory(ctx: Context, sender: String?): SenderHistory? {
        if (!enabled(ctx)) return null
        val msisdn = PhoneNumbers.toMsisdn(sender) ?: return null
        val seen = (ctx.applicationContext as LindaApp).database.senderDao().find(sender!!)
        val own = seen?.let { SenderHistory(it.firstSeen, it.count, 0, null, it.warned) }
        return SenderHistory.merge(own, readConversation(ctx, msisdn))
    }

    /** Counts texts to and from this number in the phone's own message store. Only counts and dates are read. */
    private fun readConversation(ctx: Context, msisdn: String): SenderHistory? {
        if (!canReadInbox(ctx)) return null
        // The same number is written in different ways: 0712..., +254712..., 254712...
        val ways = arrayOf("0" + msisdn.substring(3), "+$msisdn", msisdn)
        fun countAndFirst(uri: android.net.Uri): Pair<Int, Long?> = try {
            ctx.contentResolver.query(
                uri, arrayOf(Telephony.Sms.DATE), "${Telephony.Sms.ADDRESS} IN (?, ?, ?)", ways, "${Telephony.Sms.DATE} ASC",
            )?.use { c -> c.count to (if (c.moveToFirst()) c.getLong(0) else null) } ?: (0 to null)
        } catch (e: SecurityException) {
            0 to null
        }
        val (received, firstIn) = countAndFirst(Telephony.Sms.Inbox.CONTENT_URI)
        val (sent, firstOut) = countAndFirst(Telephony.Sms.Sent.CONTENT_URI)
        val first = listOfNotNull(firstIn, firstOut).minOrNull() ?: return null
        return SenderHistory(first, received, sent, firstOut, 0)
    }

    /** After a message is judged: one more text from this sender, and one more warning if Linda warned. */
    suspend fun countMessage(ctx: Context, sender: String, at: Long, warned: Boolean) {
        val senders = (ctx.applicationContext as LindaApp).database.senderDao()
        val w = if (warned) 1 else 0
        if (senders.markSeen(SeenSenderEntity(sender, at, count = 1, warned = w)) == -1L) senders.countAgain(sender, w)
    }

    // ---------- Learn from your taps ----------

    private const val PERSONAL_FILE = "personal_model.json" // slot numbers and weights only, in the app's private files
    @Volatile private var personalCache: PersonalModel? = null
    private val personalCount = MutableStateFlow(-1)

    /** The person's personal layer, or null when learning is switched off. Loaded once, then kept in memory. */
    fun personalModel(ctx: Context): PersonalModel? {
        if (!enabled(ctx)) return null
        personalCache?.let { return it }
        val file = File(ctx.applicationContext.filesDir, PERSONAL_FILE)
        val model = try {
            if (!file.exists()) PersonalModel() else {
                val o = JSONObject(file.readText())
                val slots = o.getJSONArray("slots")
                val weights = o.getJSONArray("weights")
                PersonalModel((0 until slots.length()).associate { slots.getInt(it) to weights.getDouble(it) }, o.getInt("examples"))
            }
        } catch (e: Exception) {
            PersonalModel() // a damaged file is simply started again
        }
        personalCache = model
        personalCount.value = model.examples
        return model
    }

    /**
     * "Mark as safe" ([isScam] = false) or a confirmed "Report" ([isScam] = true) on a message: the personal layer learns
     * from it. Messages from a verified sender are never learned from (they are never warned on anyway).
     */
    suspend fun learnFromTap(ctx: Context, body: String, sender: String?, isScam: Boolean) {
        val current = personalModel(ctx) ?: return
        val scorer = (ctx.applicationContext as LindaApp).scorer
        val detail = scorer.explain(body, sender)
        if (detail.meta["sender_verified"] == 1.0) return
        val updated = current.learn(detail.slots, detail.logit, isScam)
        val o = JSONObject()
        val entries = updated.weights.entries.toList()
        o.put("examples", updated.examples)
        o.put("slots", JSONArray(entries.map { it.key }))
        o.put("weights", JSONArray(entries.map { it.value }))
        File(ctx.applicationContext.filesDir, PERSONAL_FILE).writeText(o.toString())
        personalCache = updated
        personalCount.value = updated.examples
    }

    /** How many taps the personal layer has learned from (for Settings). */
    fun observeTaps(ctx: Context): StateFlow<Int> {
        if (personalCount.value < 0) personalCount.value = personalModel(ctx)?.examples ?: 0
        return personalCount
    }

    /** "Forget what Linda learned": deletes every receipt and starts listening afresh from now. */
    suspend fun forget(ctx: Context, now: Long = System.currentTimeMillis()) {
        File(ctx.applicationContext.filesDir, PERSONAL_FILE).delete()
        personalCache = null
        personalCount.value = 0
        (ctx.applicationContext as LindaApp).database.senderDao().forgetAll()
        dao(ctx).forgetAllReceipts()
        // BACKFILLED stays set, so Linda does not quietly read the whole inbox again; it relearns as messages arrive.
        prefs(ctx).edit().putLong(LISTENING_SINCE, now).putLong(COMPLETE_SINCE, now).apply()
    }

    fun observeCount(ctx: Context) = dao(ctx).observeReceiptCount()
}
