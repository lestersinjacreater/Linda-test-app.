package com.linda.app.core.util

import java.util.Calendar

/** Milliseconds since the epoch for 00:00:00.000 on the 1st of the month that contains [now]. */
fun startOfMonthMillis(now: Long = System.currentTimeMillis()): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/** For example "12 Oct, 4:12 PM". */
fun formatDateTime(millis: Long): String =
    java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(millis))
