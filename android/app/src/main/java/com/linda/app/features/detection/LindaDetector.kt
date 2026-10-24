package com.linda.app.features.detection

import com.linda.app.features.learning.Money
import com.linda.app.features.learning.MoneyClaim
import com.linda.app.features.learning.MoneyClaims
import com.linda.app.features.learning.MpesaMemory
import com.linda.app.features.learning.PersonalModel
import com.linda.app.features.learning.SenderSignals

/**
 * The on-device detector: model score + context signals -> [Verdict].
 *
 * Context signals (unknown sender, not in contacts, first message) nudge the score a little. They can
 * move a borderline message up to CAUTION; they cannot turn an innocent message into a SCAM, and they
 * never touch a message from a verified sender (M-Pesa, bank, KPLC, KRA), so those are never warned on.
 *
 * Registered senders (business names like BETIKA, short codes like 22444) are not warned on for their
 * wording alone, only when a scam sign is also present ([Senders]). That stops false alarms on real
 * bank alerts, loan reminders and promos, which the model saw too few of in training.
 *
 * M-Pesa memory (features/learning): a message that claims money arrived ("I sent you 2,500 by mistake", or a
 * confirmation) is checked against the person's real M-Pesa messages. "No such money arrived" makes a stranger's
 * text a SCAM, with that fact as the first reason. The memory only ever raises a warning, with one exception: a
 * pasted copy of the person's own genuine M-Pesa message (same transaction code) is confirmed as real.
 */
class LindaDetector(private val scorer: ModelScorer, private val config: FusionConfig = FusionConfig()) : ScamDetector {

    /** All tunable numbers live here, not scattered in code (android/CLAUDE.md F4). */
    data class FusionConfig(
        val unknownSenderBump: Double = 0.10,   // not in contacts AND first message from this sender
        val knownContactDrop: Double = 0.15,    // sender is in the contacts
        val topWords: Int = 3,
    )

    override val verifiedSenders: Set<String> get() = scorer.verifiedSenders
    override val modelVersion: String get() = scorer.version
    override val warnThreshold: Double get() = scorer.warnThreshold
    override val scamThreshold: Double get() = scorer.scamThreshold

    override fun analyse(input: MessageInput): Verdict {
        val detail = scorer.explain(input.body, input.sender)
        val isVerified = detail.meta.getValue("sender_verified") == 1.0
        val fake = detail.meta.getValue("fake_mpesa") == 1.0

        val kind = Senders.kindOf(input.sender, scorer.verifiedSenders)
        val registered = Senders.isRegistered(kind)
        // "A stranger's first message" is a warning sign for a phone number, not for a business name or short code:
        // every business that ever texts you sends a first message, and Scan my inbox sees all of them as first.
        val unknownFirst = input.senderInContacts == false && input.firstMessageFromSender == true && !registered
        // Learn from your taps (features/learning): the person's own "Mark as safe" and "Report" taps, as a small, capped
        // push on the model's total. Never for a verified sender, and a hard sign (link, PIN, fake M-Pesa...) is never
        // talked down by it.
        var personal = if (isVerified) 0.0 else input.personalModel?.adjustment(detail.slots) ?: 0.0
        if (personal < 0 && Senders.hasStrongSign(input.body, detail.meta)) personal = 0.0
        var base = detail.probability // without the personal layer, to know whether it changed the verdict
        var score = if (personal != 0.0) PersonalModel.sigmoid(detail.logit + personal) else base
        if (!isVerified) {
            val bump = (if (unknownFirst) config.unknownSenderBump else 0.0) -
                (if (input.senderInContacts == true && !fake) config.knownContactDrop else 0.0)
            score += bump
            base += bump
        }
        score = score.coerceIn(0.0, 1.0)

        val baseLevel = levelOf(base.coerceIn(0.0, 1.0))
        var level = levelOf(score)
        // One verdict step at most, either way: the person's taps refine Linda, they do not replace it.
        if (level.ordinal - baseLevel.ordinal > 1) level = RiskLevel.CAUTION
        if (baseLevel.ordinal - level.ordinal > 1) level = RiskLevel.CAUTION
        val personalReason = when {
            level.ordinal > baseLevel.ordinal -> ReasonCatalogue.TAPS_SCAM
            level.ordinal < baseLevel.ordinal -> ReasonCatalogue.TAPS_SAFE
            else -> null
        }
        // A registered sender (business name or short code): wording alone does not warn (see Senders for why).
        // A name that copies a brand is impersonation, so it can still warn, but only as CAUTION on wording alone.
        if (registered && level != RiskLevel.SAFE && !Senders.hasStrongSign(input.body, detail.meta)) {
            level = if (kind == SenderKind.LOOKALIKE) RiskLevel.CAUTION else RiskLevel.SAFE
        }

        // M-Pesa memory (features/learning): a message that says money arrived is checked against the person's REAL
        // M-Pesa messages. Banks and businesses (registered senders) are skipped: they really do announce payments.
        // A brand lookalike is not skipped, because pretending to be M-Pesa is exactly the trick.
        val claim = if (input.mpesaMemory != null && !isVerified && (!registered || kind == SenderKind.LOOKALIKE)) MoneyClaims.find(input.body) else null
        val pasted = input.sender == null
        val answer = claim?.let { input.mpesaMemory!!.check(it, input.receivedAt, if (pasted) MpesaMemory.RETENTION_MS else MpesaMemory.WINDOW_MS) }
        var memoryReason: Reason? = null
        if (claim != null) when (answer) {
            MpesaMemory.Answer.NO_REAL_PAYMENT -> {
                // A fact, not a guess: no such money arrived. Pasted text and a contact get a CAUTION (a friend's
                // phone can be stolen, but a friend can also mean a bank transfer); a stranger's text is a SCAM.
                val raiseTo = if (pasted || input.senderInContacts == true) RiskLevel.CAUTION else RiskLevel.SCAM
                level = higher(level, raiseTo)
                score = maxOf(score, if (raiseTo == RiskLevel.SCAM) scorer.scamThreshold else scorer.warnThreshold)
                val amount = Money.format(claim.amountCents)
                memoryReason = if (pasted) ReasonCatalogue.memoryNoPaymentPasted(amount) else ReasonCatalogue.memoryNoPayment(amount)
            }
            MpesaMemory.Answer.REAL_PAYMENT -> if (claim.kind == MoneyClaim.Kind.SENT_BY_MISTAKE && input.senderInContacts != true) {
                // The money is real, but "send it back to me" is still the trick: only Safaricom should reverse it.
                level = higher(level, RiskLevel.CAUTION)
                score = maxOf(score, scorer.warnThreshold)
                memoryReason = ReasonCatalogue.memoryRealPayment(Money.format(claim.amountCents))
            }
            MpesaMemory.Answer.SAME_AS_REAL_MESSAGE -> if (pasted) {
                // The person pasted one of their own genuine M-Pesa messages to ask "is this real?": it is.
                return Verdict(RiskLevel.SAFE, 0f, listOf(ReasonCatalogue.MEMORY_SAME_AS_REAL), scorer.version, "none", "")
            }
            MpesaMemory.Answer.CANNOT_TELL, null -> Unit
        }
        // Know your senders (features/learning): only for phone numbers, never for verified or business senders.
        var senderReason: Reason? = null
        if (kind == SenderKind.PERSONAL) {
            val history = input.senderHistory
            val established = history?.isEstablished(input.receivedAt) == true
            val stranger = input.senderInContacts != true && !established
            // "First" from the person's own messages when known; otherwise Linda's own first-message check.
            val first = history?.let { it.received <= 1 && it.sentByYou == 0 } ?: (input.firstMessageFromSender == true)
            when {
                // Someone the person is really in touch with: wording alone is softened by one step. Never when the
                // memory found a lie, and never when a hard sign is present (a link, a PIN, a fake M-Pesa...).
                established && level != RiskLevel.SAFE && memoryReason == null && !Senders.hasStrongSign(input.body, detail.meta) -> {
                    level = if (level == RiskLevel.SCAM) RiskLevel.CAUTION else RiskLevel.SAFE
                    if (level == RiskLevel.CAUTION) senderReason = ReasonCatalogue.KNOWN_SENDER
                }
                // "Hi, it's me, this is my new number, send me...": the impersonation trick.
                stranger && SenderSignals.claimsNewNumber(input.body) && SenderSignals.asksForMoney(input.body) -> {
                    level = higher(level, RiskLevel.CAUTION)
                    score = maxOf(score, scorer.warnThreshold)
                    senderReason = ReasonCatalogue.NEW_NUMBER_TRICK
                }
                // A stranger's very first message asks for money. A context sign: it can raise to CAUTION, not to SCAM.
                stranger && first && SenderSignals.asksForMoney(input.body) -> {
                    level = higher(level, RiskLevel.CAUTION)
                    score = maxOf(score, scorer.warnThreshold)
                    senderReason = ReasonCatalogue.FIRST_ASKS_MONEY
                }
            }
        }
        score = score.coerceIn(0.0, 1.0)

        if (level == RiskLevel.SAFE) {
            // Harmless messages are the vast majority, so do no extra work for them: no fingerprint (only warnings are reported).
            return Verdict(level, score.toFloat(), emptyList(), scorer.version, "none", "")
        }
        val fingerprint = SimHash.simhash64(Normalizer.normalize(input.body))

        var category = Categories.categorise(input.body, input.sender, scorer.verifiedSenders)
        if (category == "other" && claim?.kind == MoneyClaim.Kind.SENT_BY_MISTAKE) category = "sent_by_mistake"
        // The memory's finding goes first: "no such money arrived" is the most convincing thing Linda can say.
        val reasons = mutableListOf<Reason>()
        memoryReason?.let { reasons += it }
        senderReason?.let { if (it !== ReasonCatalogue.KNOWN_SENDER) reasons += it }
        reasons += ReasonCatalogue.forCategory(category)
        if (detail.meta.getValue("has_link") == 1.0 && category != "phishing_link") reasons += ReasonCatalogue.LINK
        if (detail.meta.getValue("sender_personal_number") == 1.0 && category != "fake_mpesa") reasons += ReasonCatalogue.PERSONAL_NUMBER
        if (unknownFirst && senderReason !== ReasonCatalogue.FIRST_ASKS_MONEY) reasons += ReasonCatalogue.UNKNOWN_SENDER
        topWords(detail)?.let { reasons += it }
        if (senderReason === ReasonCatalogue.KNOWN_SENDER) reasons += ReasonCatalogue.KNOWN_SENDER // last: why only a caution
        personalReason?.let { reasons += it } // the person's own taps changed this verdict: say so
        return Verdict(level, score.toFloat(), reasons, scorer.version, category, fingerprint)
    }

    private fun higher(a: RiskLevel, b: RiskLevel) = if (a.ordinal >= b.ordinal) a else b

    private fun levelOf(score: Double) = when {
        score >= scorer.scamThreshold -> RiskLevel.SCAM
        score >= scorer.warnThreshold -> RiskLevel.CAUTION
        else -> RiskLevel.SAFE
    }

    /** The n-grams that raised the score most, trimmed to whole-looking words, as an explanation. */
    private fun topWords(detail: ModelScorer.Detail): Reason? {
        val words = detail.ngramContributions
            .filter { it.second > 0 }
            .map { it.first.trim() }
            .filter { it.length >= 4 && ' ' !in it }
            .distinct()
            .take(config.topWords)
        return if (words.isEmpty()) null else ReasonCatalogue.wordsReason(words)
    }
}
