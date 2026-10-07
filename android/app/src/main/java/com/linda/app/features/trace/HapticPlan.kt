package com.linda.app.features.trace

import com.linda.app.core.ui.theme.SweepTiming

/** Haptics by verdict (docs/design-system.md section 8): Safe none, Caution one short tick, Scam two short pulses. */
object HapticPlan {
    const val GAP_MS = 150L

    fun pulses(level: String): Int = when (level) {
        "SCAM" -> 2
        "CAUTION" -> 1
        else -> 0
    }

    /**
     * When, counted from the start of the sweep, the buzz should start: the instant the first flagged layer lands, so what
     * the person sees and feels happen together (apple-design: causality and harmony). Null when there is nothing to buzz for.
     */
    fun fireAtMs(results: List<LayerResult>, level: String): Int? {
        if (pulses(level) == 0) return null
        val first = results.indexOfFirst { it.flagged }
        return if (first >= 0) SweepTiming.endMs(first) else SweepTiming.TOTAL_MS
    }
}
