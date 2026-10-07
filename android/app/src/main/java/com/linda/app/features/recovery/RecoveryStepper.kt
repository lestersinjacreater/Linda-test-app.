package com.linda.app.features.recovery

/** Which step of the checklist the person is on (docs/design-system.md 7.6): the first one not yet ticked. */
object RecoveryStepper {
    fun currentIndex(steps: List<StepId>, done: Set<StepId>): Int? = steps.indexOfFirst { it !in done }.takeIf { it >= 0 }
}
