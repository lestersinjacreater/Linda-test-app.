package com.linda.app.features.learning

import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Locale

/** Small helpers shared by the M-Pesa memory: reading "Ksh2,500.00" and hiding transaction codes. */
internal object Money {
    /** Amounts Linda reasons about. Smaller is noise ("ksh 5"), larger is not an everyday M-Pesa payment. */
    const val MIN_CENTS = 10_00L
    const val MAX_CENTS = 500_000_00L

    /** "2,500.00" -> 250000 cents; null if it is not a usable amount. */
    fun cents(text: String): Long? = try {
        val value = BigDecimal(text.replace(",", "")).movePointRight(2).toLong()
        value.takeIf { it in MIN_CENTS..MAX_CENTS }
    } catch (e: NumberFormatException) {
        null
    }

    /** 250000 -> "Ksh 2,500"; 250050 -> "Ksh 2,500.50" (how people write amounts in Kenya). */
    fun format(cents: Long): String {
        // Locale.US on purpose: "2,500" whatever language the phone is set to (some would write "2.500").
        val shillings = String.format(Locale.US, "%,d", cents / 100)
        val rest = cents % 100
        return if (rest == 0L) "Ksh $shillings" else "Ksh $shillings." + String.format(Locale.US, "%02d", rest)
    }

    /**
     * An M-Pesa transaction code is stored only as a hash: enough to recognise the same code again,
     * nothing a person could read back from the phone's database.
     */
    fun codeHash(code: String): String =
        MessageDigest.getInstance("SHA-256").digest(code.trim().uppercase().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Same pattern as MetadataFeatures: 10 capitals/digits with at least one of each. */
    val TRANSACTION_CODE =
        Regex("(?<![A-Za-z0-9])(?=[A-Z0-9]{10}(?![A-Za-z0-9]))(?=[A-Z0-9]*[A-Z])(?=[A-Z0-9]*[0-9])[A-Z0-9]{10}")

    /** "Ksh2,500.00", "KES 2500", "Kshs. 1,000", "sh 300". Group 1 is the number. */
    val KSH_AMOUNT = Regex(
        "(?<![A-Za-z])(?:kshs|ksh|kes|sh)\\.?\\s*([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE,
    )
}
