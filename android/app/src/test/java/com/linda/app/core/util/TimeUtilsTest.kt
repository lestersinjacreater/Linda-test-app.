package com.linda.app.core.util

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun startOfMonth_isFirstDayAtMidnight() {
        val midMonth = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 17, 15, 42, 10) }

        val result = Calendar.getInstance().apply { timeInMillis = startOfMonthMillis(midMonth.timeInMillis) }

        assertEquals(2026, result.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, result.get(Calendar.MONTH))
        assertEquals(1, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))
        assertEquals(0, result.get(Calendar.SECOND))
        assertEquals(0, result.get(Calendar.MILLISECOND))
    }

    @Test
    fun startOfMonth_isNeverAfterNow() {
        val now = System.currentTimeMillis()
        assertTrue(startOfMonthMillis(now) <= now)
    }

    @Test
    fun startOfMonth_onTheFirstItselfStaysOnTheFirst() {
        val firstAtNoon = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 1, 12, 0, 0) }
        val result = Calendar.getInstance().apply { timeInMillis = startOfMonthMillis(firstAtNoon.timeInMillis) }
        assertEquals(1, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.NOVEMBER, result.get(Calendar.MONTH))
    }
}
