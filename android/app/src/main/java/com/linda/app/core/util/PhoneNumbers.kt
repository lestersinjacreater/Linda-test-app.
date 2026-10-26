package com.linda.app.core.util

/** Phone-number helpers. Inside the system a number is always 2547XXXXXXXX (no plus). */
object PhoneNumbers {
    private val KENYAN_MOBILE = Regex("(?:\\+?254|0)([17][0-9]{8})")

    /** "+254712345678", "0712345678" and "254712345678" all become "254712345678". Anything else (a sender name) returns null. */
    fun toMsisdn(raw: String?): String? {
        val m = KENYAN_MOBILE.matchEntire((raw ?: "").trim().replace(" ", "")) ?: return null
        return "254" + m.groupValues[1]
    }

    /**
     * A sender as people write it: "254700000777" or "+254700000777" becomes "0700 000 777". A sender name ("MPESA")
     * or anything that is not a Kenyan mobile number is returned unchanged.
     */
    fun pretty(raw: String): String {
        val msisdn = toMsisdn(raw) ?: return raw
        val local = "0" + msisdn.drop(3)
        return local.substring(0, 4) + " " + local.substring(4, 7) + " " + local.substring(7)
    }
}
