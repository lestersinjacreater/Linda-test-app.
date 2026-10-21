package com.linda.app.features.detection

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
        var score = detail.probability
        if (!isVerified) {
            if (unknownFirst) score += config.unknownSenderBump
            if (input.senderInContacts == true && !fake) score -= config.knownContactDrop
        }
        score = score.coerceIn(0.0, 1.0)

        var level = when {
            score >= scorer.scamThreshold -> RiskLevel.SCAM
            score >= scorer.warnThreshold -> RiskLevel.CAUTION
            else -> RiskLevel.SAFE
        }
        // A registered sender (business name or short code): wording alone does not warn (see Senders for why).
        // A name that copies a brand is impersonation, so it can still warn, but only as CAUTION on wording alone.
        if (registered && level != RiskLevel.SAFE && !Senders.hasStrongSign(input.body, detail.meta)) {
            level = if (kind == SenderKind.LOOKALIKE) RiskLevel.CAUTION else RiskLevel.SAFE
        }
        if (level == RiskLevel.SAFE) {
            // Harmless messages are the vast majority, so do no extra work for them: no fingerprint (only warnings are reported).
            return Verdict(level, score.toFloat(), emptyList(), scorer.version, "none", "")
        }
        val fingerprint = SimHash.simhash64(Normalizer.normalize(input.body))

        val category = Categories.categorise(input.body, input.sender, scorer.verifiedSenders)
        val reasons = mutableListOf(ReasonCatalogue.forCategory(category))
        if (detail.meta.getValue("has_link") == 1.0 && category != "phishing_link") reasons += ReasonCatalogue.LINK
        if (detail.meta.getValue("sender_personal_number") == 1.0 && category != "fake_mpesa") reasons += ReasonCatalogue.PERSONAL_NUMBER
        if (unknownFirst) reasons += ReasonCatalogue.UNKNOWN_SENDER
        topWords(detail)?.let { reasons += it }
        return Verdict(level, score.toFloat(), reasons, scorer.version, category, fingerprint)
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
