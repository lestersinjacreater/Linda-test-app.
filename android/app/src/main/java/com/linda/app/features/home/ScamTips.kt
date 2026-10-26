package com.linda.app.features.home

import com.linda.app.R

/** One plain safety tip on Home: a short title and one or two sentences, in English and Kiswahili (strings.xml). */
data class ScamTip(val title: Int, val body: Int)

/** The tips, one shown per day so Home stays fresh without anything to download. */
object ScamTips {
    val all = listOf(
        ScamTip(R.string.tip_jackpot_title, R.string.tip_jackpot_body),
        ScamTip(R.string.tip_mistake_title, R.string.tip_mistake_body),
        ScamTip(R.string.tip_pin_title, R.string.tip_pin_body),
        ScamTip(R.string.tip_confirm_title, R.string.tip_confirm_body),
        ScamTip(R.string.tip_newnumber_title, R.string.tip_newnumber_body),
        ScamTip(R.string.tip_link_title, R.string.tip_link_body),
    )

    /** Today's tip: the same all day, a different one tomorrow. */
    fun forDay(dayNumber: Long): ScamTip = all[Math.floorMod(dayNumber, all.size.toLong()).toInt()]
}
