package com.linda.app.features.demo

import androidx.annotation.StringRes
import com.linda.app.R

/** One scripted message for demo mode. All numbers are fake placeholders. */
data class DemoSample(@StringRes val title: Int, val sender: String, val body: String)

/**
 * The demo script (root CLAUDE.md section 10). Each one goes through the SAME pipeline as a real SMS
 * (MessageProcessor), with no network. The last two must NOT warn: they prove Linda does not cry wolf.
 */
object DemoSamples {
    const val SCAMMER_NUMBER = "254700000777"

    val all = listOf(
        DemoSample(
            R.string.demo_fake_mpesa, SCAMMER_NUMBER,
            "QK7RT2XY9P Confirmed. You have received Ksh2,500.00 from JOHN KAMAU on 12/10/26 at 4:12 PM. New M-PESA balance is Ksh3,140.00.",
        ),
        DemoSample(R.string.demo_reversal, "254700000778", "Nimekutumia 2,500 kimakosa, tafadhali nirudishie haraka"),
        DemoSample(R.string.demo_prize, "254700000779", "Hongera! Umeshinda Ksh 50,000. Tuma registration fee ya Ksh 500 upate zawadi yako."),
        DemoSample(
            R.string.demo_fuliza, "254700000780",
            "Dear customer, your Fuliza limit has been increased. Click http://safaricom-bonus.xyz to activate",
        ),
        DemoSample(R.string.demo_pin, "254700000781", "Tafadhali tuma PIN yako ya M-Pesa ili tuthibitishe akaunti yako, la sivyo itafungwa leo."),
        DemoSample(
            R.string.demo_real_mpesa, "MPESA",
            "SJ34K9L2QW Confirmed. You have received Ksh2,500.00 from JOHN KAMAU 0700000024 on 12/10/26 at 4:12 PM. New M-PESA balance is Ksh3,140.00. Transaction cost, Ksh0.00.",
        ),
        DemoSample(R.string.demo_chat, "254700000026", "Hey, are we still meeting at 6?"),
    )
}
