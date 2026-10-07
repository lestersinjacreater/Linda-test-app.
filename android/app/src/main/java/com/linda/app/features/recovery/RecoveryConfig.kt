package com.linda.app.features.recovery

/**
 * Every official number, window and threshold the Recovery guide uses lives HERE and nowhere else.
 *
 * // VERIFY BEFORE DEMO
 * The team MUST confirm each value below against Safaricom's own official pages / the official M-PESA app before the
 * demo. A wrong number in a guide for someone who just lost money is worse than no guide.
 *
 * What was checked, and how (2026-10-07):
 *  - Safaricom's own website could NOT be opened from the build environment (blocked), so NOTHING here is confirmed
 *    on an official Safaricom page yet.
 *  - 456, 333, 100, 200: agree across several independent Kenyan news and explainer pages found by web search.
 *  - DCI hotline: agrees across news reports quoting the DCI.
 *  - 999 / 112: the usual Kenyan emergency numbers, from general knowledge; not confirmed by the search.
 *  - POLICE_REPORT_FROM_KES is a TEAM DECISION, not an official figure.
 */
object RecoveryConfig {
    /** Forward YOUR M-PESA confirmation message (the one for the payment you made) to this number to ask for a reversal. Free. */
    const val REVERSAL_SHORT_CODE = "456" // VERIFY BEFORE DEMO
    /** The automatic reversal request only works inside this window after the payment. */
    const val REVERSAL_WINDOW_HOURS = 24 // VERIFY BEFORE DEMO

    /** Forward scam messages to this number; Safaricom checks and blocks fraud numbers. Free. */
    const val FRAUD_REPORT_SHORT_CODE = "333" // VERIFY BEFORE DEMO

    const val CUSTOMER_CARE_PREPAID = "100" // VERIFY BEFORE DEMO
    const val CUSTOMER_CARE_POSTPAID = "200" // VERIFY BEFORE DEMO

    /** DCI toll-free hotline for reporting fraud (can be anonymous). Dial form and the way it is shown. */
    const val DCI_HOTLINE_DIAL = "0800722203" // VERIFY BEFORE DEMO
    const val DCI_HOTLINE_DISPLAY = "0800 722 203"

    const val POLICE_EMERGENCY = "999" // VERIFY BEFORE DEMO
    const val POLICE_EMERGENCY_ALT = "112" // VERIFY BEFORE DEMO

    /** From this amount the guide treats a police report as important rather than optional. TEAM DECISION. */
    const val POLICE_REPORT_FROM_KES = 10_000 // VERIFY BEFORE DEMO

    /** For docs and tests: every value, how sure we are, and where it came from. */
    data class Entry(val name: String, val value: String, val status: String, val source: String)

    val ENTRIES = listOf(
        Entry("Reversal short code", REVERSAL_SHORT_CODE, "corroborated by news, not on Safaricom's site", "Business Daily, People Daily, Hapakenya explainers"),
        Entry("Reversal window (hours)", REVERSAL_WINDOW_HOURS.toString(), "corroborated by news, not on Safaricom's site", "same"),
        Entry("Fraud report short code", FRAUD_REPORT_SHORT_CODE, "corroborated by news, not on Safaricom's site", "Safaricom fraud-awareness advice as quoted by Kenyan media"),
        Entry("Customer care prepaid", CUSTOMER_CARE_PREPAID, "corroborated by news, not on Safaricom's site", "same"),
        Entry("Customer care postpaid", CUSTOMER_CARE_POSTPAID, "corroborated by news, not on Safaricom's site", "same"),
        Entry("DCI hotline", DCI_HOTLINE_DISPLAY, "corroborated by news quoting the DCI", "Citizen Digital, People Daily"),
        Entry("Police emergency", "$POLICE_EMERGENCY / $POLICE_EMERGENCY_ALT", "general knowledge, not confirmed by search", "none"),
        Entry("Police report from (KES)", POLICE_REPORT_FROM_KES.toString(), "TEAM DECISION, not an official figure", "none"),
    )
}
