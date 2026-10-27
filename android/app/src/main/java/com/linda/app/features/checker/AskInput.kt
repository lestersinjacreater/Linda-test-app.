package com.linda.app.features.checker

import com.linda.app.core.util.PhoneNumbers

/** What the person typed into Ask Linda: a phone number to look up, or a message to check. */
sealed interface AskInput {
    data class Number(val msisdn: String) : AskInput
    data class Message(val text: String) : AskInput

    companion object {
        /**
         * A Kenyan mobile number on its own, written any common way ("0712 345 678", "+254-712-345678", "(0712) 345678"),
         * is a number. Anything with words in it is a message, even if it contains a number.
         */
        fun parse(raw: String): AskInput {
            val compact = raw.trim().filterNot { it == ' ' || it == '-' || it == '(' || it == ')' || it == '.' }
            val msisdn = PhoneNumbers.toMsisdn(compact)
            return if (msisdn != null) Number(msisdn) else Message(raw.trim())
        }
    }
}
