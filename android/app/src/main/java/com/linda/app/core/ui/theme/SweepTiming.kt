package com.linda.app.core.ui.theme

/**
 * Timing of the "scan sweep" (docs/design-system.md section 8), as plain numbers so a unit test can check it.
 * The five layers light up left to right, 70ms apart, each taking 200ms; a flagged layer then shakes for 120ms.
 */
object SweepTiming {
    const val LAYERS = 5
    const val STEP_MS = 70
    const val SEGMENT_MS = 200
    const val SHAKE_MS = 120
    const val CARD_RISE_MS = 250

    /** When layer [index] (0 = L ... 4 = A) starts filling in. */
    fun startMs(index: Int): Int = index * STEP_MS

    /** When layer [index] has finished filling in. */
    fun endMs(index: Int): Int = startMs(index) + SEGMENT_MS

    /** When the last layer finishes. Must stay under 600ms so it feels instant (section 8). */
    const val TOTAL_MS = (LAYERS - 1) * STEP_MS + SEGMENT_MS

    /** Everything finished, including the last shake: where the animation clock stops. */
    const val ANIMATION_MS = TOTAL_MS + SHAKE_MS

    /** What a layer looks like [elapsedMs] after the sweep began. */
    fun stateAt(index: Int, elapsedMs: Float, flagged: Boolean): SegmentState = when {
        elapsedMs < startMs(index) -> SegmentState.IDLE
        elapsedMs < endMs(index) -> SegmentState.SCANNING
        flagged -> SegmentState.FLAGGED
        else -> SegmentState.PASSED
    }

    /** Sideways wobble in dp of a flagged layer: a short 2dp shake right after it lands, else 0. */
    fun shakeDp(index: Int, elapsedMs: Float): Float {
        val since = elapsedMs - endMs(index)
        if (since < 0f || since >= SHAKE_MS) return 0f
        // two full wobbles that die away
        val fade = 1f - since / SHAKE_MS
        return 2f * fade * kotlin.math.sin(since / SHAKE_MS * 4f * Math.PI.toFloat())
    }
}

/** The four looks of a Layer Trace segment (docs/design-system.md section 6). */
enum class SegmentState { IDLE, SCANNING, PASSED, FLAGGED }
