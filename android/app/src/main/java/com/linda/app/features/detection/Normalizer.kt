package com.linda.app.features.detection

/**
 * Text normaliser. MUST give exactly the same output as ml/src/features/normalize/normalize.py:
 * the model was trained on the Python output, so any difference silently changes scores.
 * shared/test-vectors.json proves the two agree. Rules are in shared/contracts.md.
 *
 * Scammers disguise words ("K1m@kosa", "M-P3SA", "t u m a"); this undoes the disguises.
 * Only plain ASCII is handled on purpose: anything else (emoji, accents) becomes a space.
 */
object Normalizer {
    const val VERSION = "1"

    /** Characters scammers swap for letters. */
    private val LEET_MAP = mapOf('3' to 'e', '0' to 'o', '1' to 'i', '4' to 'a', '5' to 's', '@' to 'a', '$' to 's')

    // An M-Pesa transaction code: 10 capitals/digits, at least one of each. Its digits are real digits.
    private val TRANSACTION_CODE =
        Regex("(?<![A-Za-z0-9])(?=[A-Z0-9]{10}(?![A-Za-z0-9]))(?=[A-Z0-9]*[A-Z])(?=[A-Z0-9]*[0-9])[A-Z0-9]{10}")
    private val MPESA = Regex("(?<![a-z])m[ ._-]*p[ ._-]*e[ ._-]*s[ ._-]*a(?![a-z])")
    private val SPACED_LETTERS = Regex("(?<![a-z])[a-z](?:[ ._-][a-z]){2,}(?![a-z])")
    private val SPACED_SEPARATOR = Regex("[ ._-]")
    private val AMOUNT_COMMA = Regex("(?<=[0-9]),(?=[0-9])")
    private val NOT_ALNUM = Regex("[^a-z0-9]+")

    fun normalize(text: String): String {
        val protected = BooleanArray(text.length)
        for (m in TRANSACTION_CODE.findAll(text)) {
            for (k in m.range) protected[k] = true
        }
        var s = asciiLower(text) // same length as `text`, so `protected` still lines up
        s = undoLeetspeak(s, protected)
        s = MPESA.replace(s, "mpesa")
        s = SPACED_LETTERS.replace(s) { SPACED_SEPARATOR.replace(it.value, "") }
        s = AMOUNT_COMMA.replace(s, "")
        s = NOT_ALNUM.replace(s, " ")
        return s.trim()
    }

    /** Lowercase A-Z only, so Kotlin and Python agree on every input. */
    private fun asciiLower(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) sb.append(if (c in 'A'..'Z') c + 32 else c)
        return sb.toString()
    }

    private fun isLetter(c: Char) = c in 'a'..'z'

    /**
     * Swap look-alike symbols back to letters, but only inside a word. A "run" is a stretch of
     * consecutive swappable characters. A run is swapped when it has a letter on BOTH sides
     * ("m-p3sa", "c0nf1rmed"), or is only @/$ touching a letter on either side, or is ONE character
     * at the edge of a word. Amounts such as "2,500", "Ksh3,140" and "Ksh500" are left alone.
     */
    internal fun undoLeetspeak(s: String, protected: BooleanArray): String {
        val out = StringBuilder(s.length)
        val n = s.length
        fun swappable(k: Int) = s[k] in LEET_MAP && !protected[k]
        var i = 0
        while (i < n) {
            if (!swappable(i)) {
                out.append(s[i])
                i++
                continue
            }
            var j = i
            while (j < n && swappable(j)) j++
            val run = s.substring(i, j)
            val left = i > 0 && isLetter(s[i - 1])
            val right = j < n && isLetter(s[j])
            val symbolsOnly = run.all { it == '@' || it == '$' }
            val leftEdge = i == 0 || s[i - 1] == ' ' || s[i - 1] == '\t' || s[i - 1] == '\n'
            val rightEdge = j == n || s[j] in " \t\n!?;:" ||
                (s[j] == '.' && !(j + 1 < n && s[j + 1] in '0'..'9'))
            val swap = (left && right) ||
                (symbolsOnly && (left || right)) ||
                (run.length == 1 && ((left && rightEdge) || (right && leftEdge)))
            if (swap) run.forEach { out.append(LEET_MAP.getValue(it)) } else out.append(run)
            i = j
        }
        return out.toString()
    }
}
