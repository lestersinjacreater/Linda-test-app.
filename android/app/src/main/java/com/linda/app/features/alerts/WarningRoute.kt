package com.linda.app.features.alerts

/**
 * Which screen a warning notification opens, as a navigation route (kept free of Android so it can be unit tested).
 * A tap on the warning itself: the full-screen stop first (DetailScreen shows it only for a confident scam).
 * "See why": straight to the explanation. "Report this number": the explanation with the report sheet already up,
 * so the person still sees exactly what will be sent before anything leaves the phone.
 */
object WarningRoute {
    fun of(detectionId: Long, open: String?): String {
        val takeover = open == null || open == AlertNotifier.OPEN_TAKEOVER
        val report = open == AlertNotifier.OPEN_REPORT
        return "detail/$detectionId?takeover=$takeover&report=$report"
    }
}
