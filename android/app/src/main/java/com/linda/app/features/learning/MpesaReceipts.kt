package com.linda.app.features.learning

/**
 * One payment the person really received, read from a genuine M-Pesa message. Only these three facts are kept:
 * never the message, never who sent the money, never the balance.
 */
data class Receipt(val at: Long, val amountCents: Long, val codeHash: String)

/**
 * Reads genuine M-Pesa "you have received" messages (English and Kiswahili). Only messages from the real M-Pesa
 * sender ID count; the same words from anyone else are exactly what a fake confirmation looks like.
 */
object MpesaReceipts {
    private val MPESA_IDS = setOf("MPESA", "M-PESA")

    private val RECEIVED = Regex(
        "(?:you have received|umepokea)\\s*(?:kshs|ksh|kes)\\.?\\s*([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE,
    )

    fun isMpesaSender(sender: String?): Boolean = sender != null && sender.trim().uppercase() in MPESA_IDS

    /** The receipt in a genuine M-Pesa message, or null if the message is not "you have received" money. */
    fun parse(sender: String?, body: String, at: Long): Receipt? {
        if (!isMpesaSender(sender)) return null
        val code = Money.TRANSACTION_CODE.find(body)?.value ?: return null
        val amount = RECEIVED.find(body)?.groupValues?.get(1)?.let(Money::cents) ?: return null
        return Receipt(at, amount, Money.codeHash(code))
    }
}
