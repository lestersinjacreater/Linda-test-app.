package com.linda.app.features.detection

/**
 * "Hi, it's Mum": a text that names someone, from a number that is not saved. If a saved contact has that first name,
 * the text claims to be someone the person knows from a number that is not theirs: the impersonation trick.
 *
 * Pure functions, so they are unit tested without a phone. The phone side (features/sms/ContactsLookup) asks Android's
 * contacts only for the names found here, never for the whole address book, and nothing leaves the phone.
 */
object ContactClaims {
    // On Normalizer output: lower case, apostrophes turned into spaces ("it's" -> "it s", "I'm" -> "i m").
    // Longer phrases first, so "ni mimi brian" gives "brian", not "mimi".
    private val CLAIM = Regex(
        "(?<![a-z0-9])(?:this is|it is|it s|its|i am|i m|im|mimi ni|ni mimi|ni)\\s+([a-z]+)(?![a-z0-9])",
    )

    /** Words that follow "ni" or "it's" in ordinary texts; never a name to look up. */
    private val NOT_NAMES = setOf(
        "the", "not", "from", "here", "there", "now", "just", "only", "sure", "fine", "okay", "late", "home", "your",
        "mimi", "sisi", "wewe", "yeye", "sawa", "kweli", "nini", "nani", "wapi", "lini", "kwa", "hapa", "pale", "leo",
        "kesho", "jana", "bado", "tu", "yako", "wako", "wangu", "yangu", "mpya", "namba", "nambari", "pesa",
    )

    /** At most this many names are looked up per text, so a long text cannot cause many contact queries. */
    const val MAX_NAMES = 3
    private const val MIN_NAME_LETTERS = 3 // "Al" or "Jo" would match far too much

    /** The names this text says it is from, lower case: "Hi, it's Mum. Send me 2000" -> {"mum"}. */
    fun claimedNames(body: String): Set<String> =
        CLAIM.findAll(Normalizer.normalize(body))
            .map { it.groupValues[1] }
            .filter { it.length >= MIN_NAME_LETTERS && it !in NOT_NAMES }
            .take(MAX_NAMES)
            .toSet()

    /** The first word of a saved contact's name, as saved: "Brian (Work)" -> "Brian", "Mum" -> "Mum". Null if none. */
    fun firstName(displayName: String): String? =
        displayName.replace(Regex("\\(.*?\\)"), " ").trim().split(Regex("\\s+")).firstOrNull()
            ?.takeIf { word -> word.length >= MIN_NAME_LETTERS && word.all { it.isLetter() } }

    /** The saved first name (as saved, for the warning) that one of [claimed] matches, or null. */
    fun matchSaved(claimed: Set<String>, savedNames: List<String>): String? =
        savedNames.firstNotNullOfOrNull { name -> firstName(name)?.takeIf { it.lowercase() in claimed } }
}
