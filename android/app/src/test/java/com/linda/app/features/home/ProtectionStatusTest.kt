package com.linda.app.features.home

import com.linda.app.features.trace.Layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionStatusTest {
    @Test
    fun everythingOn_allFiveLayersWork() {
        val layers = ProtectionStatus.layers(canReceiveSms = true, canReadContacts = true, notificationsOn = true)
        assertEquals(5, layers.size)
        assertTrue(ProtectionStatus.allOk(layers))
        assertNull(ProtectionStatus.firstProblem(layers))
    }

    @Test
    fun contactsOff_dimsOnlyTheNetworkLayer() {
        val layers = ProtectionStatus.layers(true, canReadContacts = false, notificationsOn = true)
        assertEquals(listOf(Layer.NETWORK), layers.filter { !it.ok }.map { it.layer })
        assertEquals(Problem.CONTACTS, ProtectionStatus.firstProblem(layers))
    }

    @Test
    fun cannotReceiveTexts_isFixedFirst_thenNotifications_thenContacts() {
        val all = ProtectionStatus.layers(false, false, false)
        assertFalse(ProtectionStatus.allOk(all))
        assertEquals(Problem.READ_MESSAGES, ProtectionStatus.firstProblem(all))
        assertEquals(Problem.NOTIFICATIONS, ProtectionStatus.firstProblem(ProtectionStatus.layers(true, false, false)))
    }

    @Test
    fun theModelAndTheDecision_neverDim() {
        val layers = ProtectionStatus.layers(false, false, false).associateBy { it.layer }
        assertTrue(layers.getValue(Layer.INTELLIGENCE).ok)
        assertTrue(layers.getValue(Layer.DECISION).ok)
    }
}
