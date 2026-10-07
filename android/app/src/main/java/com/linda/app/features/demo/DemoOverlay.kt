package com.linda.app.features.demo

import android.content.Context
import com.linda.app.core.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * The demo overlay's state (docs/design-system.md 7.8). Off by default. While it is off nothing about any message is
 * kept; switching it off throws away what was captured.
 */
object DemoOverlay {
    private val on = MutableStateFlow(false)
    private val last = MutableStateFlow<DemoSnapshot?>(null)
    private val counter = AtomicLong()

    val enabled: StateFlow<Boolean> get() = on
    val latest: StateFlow<DemoSnapshot?> get() = last

    fun init(context: Context) { on.value = Prefs.demoOverlay(context) }
    fun isOn(): Boolean = on.value
    fun nextSequence(): Long = counter.incrementAndGet()
    fun record(snapshot: DemoSnapshot) { if (on.value) last.value = snapshot }

    fun setEnabled(context: Context, value: Boolean) {
        Prefs.setDemoOverlay(context, value)
        on.value = value
        if (!value) last.value = null
    }
}
