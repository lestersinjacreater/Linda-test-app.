package com.linda.app.core.util

/**
 * "6 hr ago" for lists and cards: an age is easier to read at a glance than a date. Kept free of Android so it can be
 * unit tested; the words come from strings.xml (English and Kiswahili) in [com.linda.app.core.ui.components.relativeTime].
 */
object RelativeTime {
    enum class Unit { NOW, MINUTES, HOURS, DAYS }

    /** Which unit and how many, for something that happened at [then] seen at [now] (both in milliseconds). */
    fun of(then: Long, now: Long): Pair<Unit, Int> {
        val minutes = ((now - then).coerceAtLeast(0) / 60_000).toInt()
        return when {
            minutes < 1 -> Unit.NOW to 0
            minutes < 60 -> Unit.MINUTES to minutes
            minutes < 24 * 60 -> Unit.HOURS to minutes / 60
            else -> Unit.DAYS to minutes / (24 * 60)
        }
    }
}
