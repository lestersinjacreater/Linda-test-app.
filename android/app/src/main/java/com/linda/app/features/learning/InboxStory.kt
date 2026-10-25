package com.linda.app.features.learning

import com.linda.app.features.detection.Normalizer
import com.linda.app.features.detection.SenderKind
import com.linda.app.features.detection.SimHash

/**
 * What Linda has seen from one registered sender (a business name like SAF_Balance, or a short code), kept on the
 * phone. No message text: only how many texts and since when, the SHAPES of its recent texts (SimHash fingerprints,
 * the same 64-bit number a report sends) and the web addresses it uses.
 * [reported] is true once the person reported one of its texts: such a sender is never treated as routine again.
 */
data class SenderStory(
    val firstSeen: Long,
    val count: Int,
    val templates: List<Long>,
    val domains: Set<String>,
    val reported: Boolean = false,
)

/** The story facts the detector needs for one message: this sender's story, and whether the message carries a
 *  transaction code that is also in one of the person's real M-Pesa messages. */
data class StoryFacts(val sender: SenderStory?, val codeMatch: Boolean)

/**
 * The inbox story (features/learning): relationships between messages, so Linda judges a text the way a person who
 * knows their own inbox would.
 *
 *  1. A business that always texts you the same kind of message is routine. SAF_Balance sends a balance with a link
 *     every day; after two weeks and five texts, one more like it is not a scam, even though it has a link and the
 *     word "Safaricom" (the business rule in Senders warns on those). A NEW web address from that sender, a PIN or a
 *     personal phone number in the text still warns: those are what a hijacked or copied sender would add.
 *  2. Messages from different senders about the same payment: a bank text quoting an M-Pesa transaction code that is
 *     also in the person's real M-Pesa messages is about a real payment.
 *  3. "The way to pay has changed" ("our new Paybill is ...") from a phone number the person is not in touch with
 *     is a known trick to redirect rent, school fees or a supplier payment: a caution.
 *
 * Tested on a donated real inbox, replayed in date order (ml/src/features/evaluate/inbox_replay.py, which mirrors
 * this file: keep the numbers and patterns in step). Tried and dropped: warning on a code that is NOT among the
 * phone's M-Pesa messages; bank texts often arrive seconds before the M-Pesa one, and it never caught a scam.
 */
object InboxStory {
    const val ROUTINE_MIN_MESSAGES = 5
    const val ROUTINE_MIN_DAYS = 14
    // A name that copies a brand (SenderKind.LOOKALIKE) must earn it for longer.
    const val LOOKALIKE_MIN_MESSAGES = 10
    const val LOOKALIKE_MIN_DAYS = 30
    const val TEMPLATE_DISTANCE = 12 // SimHash bits that may differ for "the same kind of message"
    const val MAX_TEMPLATES = 30
    const val CODES_KEPT_MS = MpesaMemory.RETENTION_MS
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    // A web address: dotted words ending in a web ending. Not any "word.word": texts like "Confirmed.You" or
    // "Ksh5.00.Amount" glue sentences together without a space, and those are not links.
    private val HOST = Regex(
        "(?<![a-z0-9@.-])(?:https?://)?((?:[a-z0-9-]+\\.)+(?:com|ke|net|org|info|io|ly|me|app|xyz|link|site|online|top|" +
            "africa|live|shop|club|vip|gl|co|cx|gle|to|us|as|at|in))(?![a-z0-9-])",
        RegexOption.IGNORE_CASE,
    )

    // On Normalizer output (lower case, punctuation turned into spaces).
    private val PAYMENT_CHANGED = Regex(
        "(?<![a-z])(?:new (?:paybill|till|account|buy goods|payment)|our new|has changed|have changed|changed our|" +
            "substituted|now via|now through|should be (?:submitted|paid|made) (?:through|to|via)|no other payment|" +
            "paybill mpya|till mpya|akaunti mpya|namba mpya ya)(?![a-z])",
    )
    private val PAYMENT_PLACE = Regex("(?<![a-z])(?:paybill|till|buy goods|acc(?:ount)?\\.? ?no|account number|a/c)(?![a-z])")
    private val PIN = Regex("(?<![a-z0-9])(?:pin|nambari yako ya siri)(?![a-z0-9])")
    private val PERSONAL_NUMBER_IN_TEXT = Regex("(?:\\+?254|(?<![0-9])0)[17][0-9]{8}(?![0-9])")

    /** The web addresses in a text, lower case, without "www.". */
    fun hosts(body: String): Set<String> =
        HOST.findAll(body).map { it.groupValues[1].lowercase().removePrefix("www.") }.toSet()

    /** Hashes of the M-Pesa-style transaction codes in a text (the same hash as the M-Pesa memory keeps). */
    fun codeHashes(body: String): List<String> = Money.TRANSACTION_CODE.findAll(body).map { Money.codeHash(it.value) }.toList()

    /** The message's shape: the SimHash of its normalised text, as a number. */
    fun shape(body: String): Long = java.lang.Long.parseUnsignedLong(SimHash.simhash64(Normalizer.normalize(body)), 16)

    /** Signs that a sender is not itself: a PIN, a personal phone number to pay or call, or a fake M-Pesa message. */
    fun hardSign(body: String, fakeMpesa: Boolean): Boolean =
        fakeMpesa || PIN.containsMatchIn(Normalizer.normalize(body)) || PERSONAL_NUMBER_IN_TEXT.containsMatchIn(body.replace(" ", ""))

    /** Has this sender texted the person long enough to have a routine? */
    fun established(story: SenderStory?, kind: SenderKind, at: Long): Boolean {
        if (story == null || story.reported) return false
        val lookalike = kind == SenderKind.LOOKALIKE
        val n = if (lookalike) LOOKALIKE_MIN_MESSAGES else ROUTINE_MIN_MESSAGES
        val days = if (lookalike) LOOKALIKE_MIN_DAYS else ROUTINE_MIN_DAYS
        return story.count >= n && at - story.firstSeen >= days * DAY_MS
    }

    /** The same kind of message this sender usually sends: close to one of its recent shapes, or only web
     *  addresses it has used before. */
    fun routine(story: SenderStory?, body: String): Boolean {
        if (story == null) return false
        val s = shape(body)
        if (story.templates.any { java.lang.Long.bitCount(it xor s) <= TEMPLATE_DISTANCE }) return true
        val h = hosts(body)
        return h.isNotEmpty() && story.domains.containsAll(h)
    }

    /** A web address this sender has never used before. */
    fun newDomain(story: SenderStory?, body: String): Boolean = (hosts(body) - (story?.domains ?: emptySet())).isNotEmpty()

    /** Why a registered sender's warning should be lifted, or null to keep it: "code_match" or "routine_sender". */
    fun clears(facts: StoryFacts, kind: SenderKind, body: String, fakeMpesa: Boolean, at: Long): String? {
        if (facts.sender?.reported == true || hardSign(body, fakeMpesa)) return null
        if (facts.codeMatch) return "code_match"
        val s = facts.sender
        if (established(s, kind, at) && routine(s, body) && !newDomain(s, body)) return "routine_sender"
        return null
    }

    /** "Our Paybill has changed, pay to ... from now on." */
    fun paymentChanged(body: String): Boolean {
        val n = Normalizer.normalize(body)
        return PAYMENT_CHANGED.containsMatchIn(n) && PAYMENT_PLACE.containsMatchIn(n)
    }

    /** The story after one more text from this sender. Every text teaches, unless the sender was reported: learning
     *  only from texts Linda did not warn on would never learn a sender it wrongly warned on. */
    fun remember(story: SenderStory?, body: String, at: Long): SenderStory {
        val s = story ?: SenderStory(at, 0, emptyList(), emptySet())
        if (s.reported) return s.copy(count = s.count + 1)
        return SenderStory(
            firstSeen = minOf(s.firstSeen, at),
            count = s.count + 1,
            templates = (s.templates + shape(body)).takeLast(MAX_TEMPLATES),
            domains = s.domains + hosts(body),
        )
    }
}

/**
 * The same story kept in memory, for "Scan my inbox" (which reads old texts oldest first, so each one is judged only
 * on what came before it, like on the day it arrived) and for tests.
 */
class StoryBook(knownCodes: Collection<String> = emptyList()) {
    private val senders = HashMap<String, SenderStory>()
    private val codes = HashSet(knownCodes)

    fun facts(sender: String?, body: String): StoryFacts =
        StoryFacts(sender?.let { senders[it] }, InboxStory.codeHashes(body).any { it in codes })

    /** After a text is judged: M-Pesa codes are kept from the real M-Pesa sender; registered senders get a story. */
    fun remember(sender: String?, kind: SenderKind, body: String, at: Long) {
        if (sender == null) return
        if (MpesaReceipts.isMpesaSender(sender)) codes += InboxStory.codeHashes(body)
        if (kind == SenderKind.BUSINESS || kind == SenderKind.SHORT_CODE || kind == SenderKind.LOOKALIKE) {
            senders[sender] = InboxStory.remember(senders[sender], body, at)
        }
    }
}
