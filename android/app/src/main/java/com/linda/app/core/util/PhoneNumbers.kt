package com.linda.app.core.util

/** Phone-number helpers. Inside the system a number is always 2547XXXXXXXX (no plus). */
object PhoneNumbers {
    private val KENYAN_MOBILE = Regex("(?:\\+?254|0)([17][0-9]{8})")

    /** "+254712345678", "0712345678" and "254712345678" all become "254712345678". Anything else (a sender name) returns null. */
    fun toMsisdn(raw: String?): String? {
        val m = KENYAN_MOBILE.matchEntire((raw ?: "").trim().replace(" ", "")) ?: return null
        return "254" + m.groupValues[1]
    }
}
