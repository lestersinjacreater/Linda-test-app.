package com.linda.app.features.trace

/** Haptics by verdict (docs/design-system.md section 8): Safe none, Caution one short tick, Scam two short pulses. */
object HapticPlan {
    const val GAP_MS = 150L

    fun pulses(level: String): Int = when (level) {
        "SCAM" -> 2
        "CAUTION" -> 1
        else -> 0
    }
}
