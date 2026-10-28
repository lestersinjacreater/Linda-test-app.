package com.linda.app.features.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding "it's Mum" in a text, and matching it to a saved name (the pure part; the phone asks its contacts). */
class ContactClaimsTest {
    @Test fun englishClaimsAreFound() {
        assertEquals(setOf("mum"), ContactClaims.claimedNames("Hi, it's Mum. Please send me 2000 for fare"))
        assertEquals(setOf("brian"), ContactClaims.claimedNames("Hello this is Brian, my phone is off"))
        assertEquals(setOf("brian"), ContactClaims.claimedNames("I'm Brian from work"))
    }

    @Test fun swahiliClaimsAreFound() {
        assertEquals(setOf("wanjiru"), ContactClaims.claimedNames("Habari, ni Wanjiru. Nitumie pesa haraka"))
        assertEquals(setOf("wanjiru"), ContactClaims.claimedNames("Mimi ni Wanjiru, simu yangu imeharibika"))
        assertEquals(setOf("wanjiru"), ContactClaims.claimedNames("Ni mimi Wanjiru, nitumie 500"))
    }

    @Test fun ordinaryWordsAfterNiAreNotNames() {
        assertTrue(ContactClaims.claimedNames("Ni sawa, tutaonana kesho").isEmpty())
        assertTrue(ContactClaims.claimedNames("Hii ni namba yangu mpya").isEmpty())
        assertTrue(ContactClaims.claimedNames("It is fine, I'm here now").isEmpty())
    }

    @Test fun shortWordsAreNeverLookedUp() {
        assertTrue(ContactClaims.claimedNames("Hi, it's me, this is my new number").isEmpty())
    }

    @Test fun aLongTextLooksUpAtMostThreeNames() {
        val text = "this is Anna. it is Brian. i am Carol. ni David. mimi ni Esther"
        assertEquals(ContactClaims.MAX_NAMES, ContactClaims.claimedNames(text).size)
    }

    @Test fun theSavedFirstNameIsKeptAsSaved() {
        assertEquals("Brian", ContactClaims.firstName("Brian (Work)"))
        assertEquals("Mum", ContactClaims.firstName("Mum"))
        assertNull(ContactClaims.firstName("Jo"))
        assertNull(ContactClaims.firstName("0722 555 101"))
    }

    @Test fun aClaimMatchesOnlyTheSameFirstName() {
        assertEquals("Mum", ContactClaims.matchSaved(setOf("mum"), listOf("Mumbi Kariuki", "Mum")))
        assertNull(ContactClaims.matchSaved(setOf("mum"), listOf("Mumbi Kariuki")))
        assertNull(ContactClaims.matchSaved(emptySet(), listOf("Mum")))
    }
}
