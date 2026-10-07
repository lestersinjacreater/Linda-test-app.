package com.linda.app.features.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryStepperTest {
    private val steps = listOf(StepId.STOP, StepId.EVIDENCE, StepId.REVERSAL)

    @Test
    fun theCurrentStep_isTheFirstOneNotTicked() {
        assertEquals(0, RecoveryStepper.currentIndex(steps, emptySet()))
        assertEquals(1, RecoveryStepper.currentIndex(steps, setOf(StepId.STOP)))
        assertEquals(2, RecoveryStepper.currentIndex(steps, setOf(StepId.STOP, StepId.EVIDENCE)))
    }

    @Test
    fun skippingAStep_keepsThePulseOnTheEarlierOne() {
        assertEquals(1, RecoveryStepper.currentIndex(steps, setOf(StepId.STOP, StepId.REVERSAL)))
    }

    @Test
    fun whenEverythingIsTicked_nothingPulses() {
        assertNull(RecoveryStepper.currentIndex(steps, steps.toSet()))
    }
}
