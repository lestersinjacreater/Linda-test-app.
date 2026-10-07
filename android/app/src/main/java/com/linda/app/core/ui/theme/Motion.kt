package com.linda.app.core.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Timings from docs/design-system.md section 8. The scan sweep is the only show-off animation. */
object Motion {
    const val STANDARD_MS = 200        // everything ordinary
    const val SWEEP_STEP_MS = 70       // gap between one layer lighting up and the next
    const val SWEEP_SEGMENT_MS = 200   // one layer filling in
    const val SHAKE_MS = 120           // a flagged layer's little shake
    const val CARD_RISE_MS = 250       // the verdict card rising into view
    const val BREATH_MS = 6000         // the home ring's slow breath

    val easeOut = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** When the last layer has finished, counted from the start of the sweep. Must stay under 600ms (section 8). */
    fun sweepTotalMs(layers: Int = 5): Int = (layers - 1) * SWEEP_STEP_MS + SWEEP_SEGMENT_MS
}

/** True when the phone's "remove animations" setting is on. Screens then jump straight to the end state. */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
