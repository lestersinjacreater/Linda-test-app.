package com.linda.app.core.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Timings from docs/design-system.md section 8. The scan sweep is the only show-off animation. */
object Motion {
    const val STANDARD_MS = 200        // everything ordinary
    // The scan sweep's numbers live in SweepTiming (plain numbers, unit tested).
    const val CARD_RISE_MS = SweepTiming.CARD_RISE_MS  // the verdict card rising into view
    const val BREATH_MS = 6000         // the home ring's slow breath

    val easeOut = CubicBezierEasing(0f, 0f, 0.2f, 1f)
}

/** True when the phone's "remove animations" setting is on. Screens then jump straight to the end state. */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
