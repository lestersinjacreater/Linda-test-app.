package com.linda.app.features.learning

import kotlin.math.exp

/**
 * Learn from your taps: a small personal layer on top of Linda's model, trained on this phone only.
 *
 * Linda's model reads a message as numbered "slots" (letter pieces hashed into 32,768 slots, see ModelScorer). This
 * layer keeps one extra weight per slot, learned from the person's own taps: "Mark as safe" pushes the slots of that
 * message towards safe, "Report" towards scam. A new message that shares many slots with one the person tapped gets
 * nudged the same way. Only slot numbers and weights are stored: never words, never messages.
 *
 * It is deliberately weak: its total push is capped ([CAP]), so it can move a verdict by about one step, and the
 * detector ignores it whenever a hard rule applies (see LindaDetector).
 */
data class PersonalModel(val weights: Map<Int, Double> = emptyMap(), val examples: Int = 0) {

    /** How much this layer pushes the message's total (positive = towards scam), capped both ways. */
    fun adjustment(slots: Map<Int, Double>): Double {
        if (weights.isEmpty()) return 0.0
        var sum = 0.0
        for ((slot, x) in slots) sum += (weights[slot] ?: 0.0) * x
        return sum.coerceIn(-CAP, CAP)
    }

    /**
     * One tap. [slots] and [baseLogit] are the message as the main model saw it; [isScam] is what the person said.
     * A few steps of plain logistic regression on the personal weights only (the main model never changes).
     */
    fun learn(slots: Map<Int, Double>, baseLogit: Double, isScam: Boolean): PersonalModel {
        if (slots.isEmpty()) return this
        val w = HashMap(weights)
        val y = if (isScam) 1.0 else 0.0
        repeat(STEPS) {
            var z = baseLogit
            for ((slot, x) in slots) z += (w[slot] ?: 0.0) * x
            val error = sigmoid(z) - y // how wrong Linda still is about this message
            for ((slot, x) in slots) {
                w[slot] = ((w[slot] ?: 0.0) - RATE * error * x).coerceIn(-MAX_WEIGHT, MAX_WEIGHT)
            }
        }
        // Keep the file small: drop weights too small to matter.
        return PersonalModel(w.filterValues { kotlin.math.abs(it) >= 1e-4 }, examples + 1)
    }

    companion object {
        const val CAP = 1.5          // at most this much push on the total: roughly one verdict step
        const val STEPS = 8
        const val RATE = 1.0
        const val MAX_WEIGHT = 3.0

        fun sigmoid(z: Double): Double = if (z >= 0) 1.0 / (1.0 + exp(-z)) else exp(z) / (1.0 + exp(z))
    }
}
