package com.linda.app.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringMathTest {
    @Test
    fun stiffnessFollowsTheResponseTime() {
        // k = (2 pi / response)^2: a 0.3 s spring is about 439, a 0.4 s spring about 247.
        assertEquals(438.65f, SpringMath.stiffness(0.3), 0.1f)
        assertEquals(246.74f, SpringMath.stiffness(0.4), 0.1f)
    }

    @Test
    fun aQuickerResponseMeansAStifferSpring() {
        assertTrue(SpringMath.stiffness(0.25) > SpringMath.stiffness(0.35))
        assertTrue(SpringMath.stiffness(0.35) > SpringMath.stiffness(0.5))
    }

    @Test
    fun theEverydaySpringDoesNotOvershoot_andStaysInAppleRange() {
        assertEquals(1.0f, SpringMath.CALM_DAMPING, 0f)
        assertTrue(SpringMath.CALM_RESPONSE in 0.3..0.4)
        assertTrue(SpringMath.BOUNCY_DAMPING < SpringMath.CALM_DAMPING)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aZeroResponseIsRejected() {
        SpringMath.stiffness(0.0)
    }
}
