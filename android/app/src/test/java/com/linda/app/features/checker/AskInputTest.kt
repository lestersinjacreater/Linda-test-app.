package com.linda.app.features.checker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AskInputTest {
    @Test fun numbersWrittenAnyCommonWayAreNumbers() {
        listOf("0722 000 000", "+254722000000", "254-722-000000", "(0722) 000000", " 0722000000 ", "0110 000 000")
            .forEach { assertTrue(it, AskInput.parse(it) is AskInput.Number) }
        assertEquals(AskInput.Number("254722000000"), AskInput.parse("0722 000 000"))
    }

    @Test fun anythingWithWordsIsAMessage() {
        val m = AskInput.parse("Sorry, I sent money to 0722000000 by mistake")
        assertTrue(m is AskInput.Message)
        assertTrue(AskInput.parse("MPESA") is AskInput.Message)
        assertTrue(AskInput.parse("12345") is AskInput.Message) // not a Kenyan mobile number
    }
}
