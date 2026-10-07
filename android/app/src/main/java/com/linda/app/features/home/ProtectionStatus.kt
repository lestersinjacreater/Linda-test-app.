package com.linda.app.features.home

import com.linda.app.features.trace.Layer

/** Something the person can switch on to bring a dimmed layer back. */
enum class Problem { READ_MESSAGES, CONTACTS, NOTIFICATIONS }

/** [problem] is null when the layer is fully working. */
data class LayerHealth(val layer: Layer, val problem: Problem?) {
    val ok: Boolean get() = problem == null
}

/**
 * What the home ring shows (docs/design-system.md 7.2): one arc per layer, dimmed when something that layer
 * needs is switched off. Only things that really stop that layer from working count:
 *  L reads each new text, so it needs the "receive texts" permission;
 *  N looks the sender up in your contacts, so it needs contacts access;
 *  A warns you, so it needs notifications on.
 * I (the on-device model) and D (the decision) live inside the app and are always on.
 */
object ProtectionStatus {
    fun layers(canReceiveSms: Boolean, canReadContacts: Boolean, notificationsOn: Boolean): List<LayerHealth> = listOf(
        LayerHealth(Layer.LANGUAGE, if (canReceiveSms) null else Problem.READ_MESSAGES),
        LayerHealth(Layer.INTELLIGENCE, null),
        LayerHealth(Layer.NETWORK, if (canReadContacts) null else Problem.CONTACTS),
        LayerHealth(Layer.DECISION, null),
        LayerHealth(Layer.ALERT, if (notificationsOn) null else Problem.NOTIFICATIONS),
    )

    /** The most important thing to fix first: without reading texts nothing else matters, and without notifications nobody is told. */
    fun firstProblem(layers: List<LayerHealth>): Problem? =
        listOf(Problem.READ_MESSAGES, Problem.NOTIFICATIONS, Problem.CONTACTS).firstOrNull { p -> layers.any { it.problem == p } }

    fun allOk(layers: List<LayerHealth>): Boolean = layers.all { it.ok }
}
