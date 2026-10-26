package com.linda.app.features.learning

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.linda.app.LindaApp
import com.linda.app.core.data.MpesaCodeEntity
import com.linda.app.core.data.MpesaReceiptEntity
import com.linda.app.core.data.SenderStoryEntity
import com.linda.app.core.data.SeenSenderEntity
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.features.detection.SenderKind
import com.linda.app.features.detection.Senders
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
        keepCodes(ctx, body, at) // the inbox story: any M-Pesa code, so a bank text about the same payment is recognised
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
        val codes = ArrayList<MpesaCodeEntity>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "UPPER(${Telephony.Sms.ADDRESS}) IN ('MPESA', 'M-PESA') AND ${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val body = c.getString(1) ?: ""
                    MpesaReceipts.parse(c.getString(0), body, c.getLong(2))
                        ?.let { found += MpesaReceiptEntity(it.codeHash, it.at, it.amountCents) }
                    InboxStory.codeHashes(body).forEach { codes += MpesaCodeEntity(it, c.getLong(2)) }
                }
            } ?: return false
        } catch (e: SecurityException) {
            return false
        }
        dao(ctx).addReceipts(found)
        dao(ctx).addCodes(codes)
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

    // ---------- The inbox story ----------

    private const val STORY_INBOX_DAYS = 90L // how far back a new business sender's story is read from the inbox
    private const val SELF_MS = 10 * 60 * 1000L

    private fun kind(ctx: Context, sender: String?): SenderKind =
        Senders.kindOf(sender, (ctx.applicationContext as LindaApp).detector.verifiedSenders)

    private suspend fun keepCodes(ctx: Context, body: String, at: Long) {
        val codes = InboxStory.codeHashes(body).map { MpesaCodeEntity(it, at) }
        if (codes.isNotEmpty()) dao(ctx).addCodes(codes)
        dao(ctx).forgetCodesBefore(at - InboxStory.CODES_KEPT_MS)
    }

    private fun SenderStoryEntity.toStory() = SenderStory(
        firstSeen, count,
        templates.split(',').filter { it.isNotEmpty() }.map { java.lang.Long.parseUnsignedLong(it, 16) },
        domains.split(',').filter { it.isNotEmpty() }.toSet(),
        reported,
    )

    private fun SenderStory.toEntity(sender: String) = SenderStoryEntity(
        sender, firstSeen, count,
        templates.joinToString(",") { java.lang.Long.toUnsignedString(it, 16) },
        domains.joinToString(","),
        reported,
    )

    /**
     * What the story says about one message from a registered sender (business name or short code), or null for
     * anyone else or when learning is off. The first time a sender is met, its story is read from the inbox (when
     * allowed), from texts that arrived BEFORE this one, so Linda knows SAF_Balance's daily balance from day one.
     */
    suspend fun storyFacts(ctx: Context, sender: String?, body: String, at: Long): StoryFacts? {
        if (!enabled(ctx) || sender == null || !Senders.isRegistered(kind(ctx, sender))) return null
        val story = dao(ctx).story(sender)?.toStory() ?: storyFromInbox(ctx, sender, body, at)?.also { dao(ctx).saveStory(it.toEntity(sender)) }
        val hashes = InboxStory.codeHashes(body)
        return StoryFacts(story, hashes.isNotEmpty() && dao(ctx).countKnownCodes(hashes) > 0)
    }

    /** Builds a sender's story from its earlier texts in the phone's inbox. Only shapes and web addresses are kept. */
    private fun storyFromInbox(ctx: Context, sender: String, body: String, at: Long): SenderStory? {
        if (!canReadInbox(ctx)) return null
        var story: SenderStory? = null
        try {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?",
                arrayOf(sender, (at - STORY_INBOX_DAYS * 24 * 60 * 60 * 1000L).toString(), at.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val earlier = c.getString(0) ?: ""
                    val date = c.getLong(1)
                    // The message being judged may already be in the inbox, a little "before" itself (the network's
                    // clock and the phone's differ): it must never vouch for itself.
                    val itself = earlier == body && at - date < SELF_MS
                    if (!itself) story = InboxStory.remember(story, earlier, date)
                }
            }
        } catch (e: SecurityException) {
            return null
        }
        return story
    }

    /** After a message from a registered sender is judged: its story learns this text too. */
    suspend fun rememberStory(ctx: Context, sender: String?, body: String, at: Long) {
        if (!enabled(ctx) || sender == null || !Senders.isRegistered(kind(ctx, sender))) return
        val story = InboxStory.remember(dao(ctx).story(sender)?.toStory(), body, at)
        dao(ctx).saveStory(story.toEntity(sender))
    }

    /** The person reported a text from [sender]: its story never clears a warning again. */
    suspend fun storyReported(ctx: Context, sender: String?) {
        if (sender != null) dao(ctx).markStoryReported(sender)
    }

    /** Every M-Pesa code on the phone (hashed), for "Scan my inbox". */
    suspend fun knownCodes(ctx: Context): List<String> = if (enabled(ctx)) dao(ctx).allCodes() else emptyList()

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

    /**
     * The personal layer as it is saved now, or null when nothing was learned yet. "Mark as safe" keeps this so its
     * Undo can put the layer back exactly as it was (a learning step cannot simply be subtracted again).
     */
    fun tapsSnapshot(ctx: Context): String? {
        val file = File(ctx.applicationContext.filesDir, PERSONAL_FILE)
        return if (file.exists()) file.readText() else null
    }

    /** Puts back a [tapsSnapshot]: the saved layer, or no layer at all when the snapshot was null. */
    fun restoreTaps(ctx: Context, snapshot: String?) {
        val file = File(ctx.applicationContext.filesDir, PERSONAL_FILE)
        if (snapshot == null) file.delete() else file.writeText(snapshot)
        personalCache = null // read again from the file on next use
        personalCount.value = personalModel(ctx)?.examples ?: 0
    }

    /** How many taps the personal layer has learned from (for Settings). */
    fun observeTaps(ctx: Context): StateFlow<Int> {
        if (personalCount.value < 0) personalCount.value = personalModel(ctx)?.examples ?: 0
        return personalCount
    }

    /** "Forget everything Linda learned": deletes receipts, sender stories, codes and taps; listens afresh from now. */
    suspend fun forget(ctx: Context, now: Long = System.currentTimeMillis()) {
        File(ctx.applicationContext.filesDir, PERSONAL_FILE).delete()
        personalCache = null
        personalCount.value = 0
        (ctx.applicationContext as LindaApp).database.senderDao().forgetAll()
        // Senders marked "safe" were also learned from taps, so Forget lets Linda check them again.
        (ctx.applicationContext as LindaApp).database.senderDao().forgetAllAllowed()
        dao(ctx).forgetAllReceipts()
        dao(ctx).forgetAllStories()
        dao(ctx).forgetAllCodes()
        // BACKFILLED stays set, so Linda does not quietly read the whole inbox again; it relearns as messages arrive.
        prefs(ctx).edit().putLong(LISTENING_SINCE, now).putLong(COMPLETE_SINCE, now).apply()
    }

    fun observeCount(ctx: Context) = dao(ctx).observeReceiptCount()
}
