package com.linda.app.core.ui.theme

import kotlin.math.PI

/**
 * Apple describes a spring by two designer-friendly numbers: damping ratio (1.0 = no overshoot) and response (the
 * seconds one undamped swing takes). Compose wants damping ratio and stiffness. This turns one into the other so we can
 * use Apple's numbers (see the apple-design skill). Plain numbers, so a unit test can check them.
 */
object SpringMath {
    /** Stiffness for a spring of mass 1 that swings once in [responseSeconds]: (2 * pi / response) squared. */
    fun stiffness(responseSeconds: Double): Float {
        require(responseSeconds > 0.0) { "response must be above zero" }
        val omega = 2.0 * PI / responseSeconds
        return (omega * omega).toFloat()
    }

    /** The everyday spring: no overshoot, calm and non-distracting. Apple uses damping 1.0, response 0.3 to 0.4. */
    const val CALM_DAMPING = 1.0f
    const val CALM_RESPONSE = 0.35

    /** A little bounce, only for motion that began with a flick. Nothing in Linda is flicked yet, so nothing uses it. */
    const val BOUNCY_DAMPING = 0.8f
    const val BOUNCY_RESPONSE = 0.3
}
