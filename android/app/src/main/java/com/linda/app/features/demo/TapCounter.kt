package com.linda.app.features.demo

/**
 * The hidden switch for the demo overlay (docs/design-system.md 7.8): tap the logo 7 times. Each tap must come within
 * [maxGapMs] of the one before, so ordinary touching of the logo never switches it on by accident. Pure, so it is unit tested.
 */
class TapCounter(private val required: Int = 7, private val maxGapMs: Long = 1500L) {
    private var count = 0
    private var lastAt = 0L

    /** Call on every tap with the current time. Returns true on the tap that completes the sequence. */
    fun tap(nowMs: Long): Boolean {
        count = if (count > 0 && nowMs - lastAt <= maxGapMs) count + 1 else 1
        lastAt = nowMs
        if (count >= required) {
            count = 0
            return true
        }
        return false
    }
}
