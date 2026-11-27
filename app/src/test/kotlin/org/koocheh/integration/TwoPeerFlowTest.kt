package org.koocheh.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.koocheh.disclosure.PeerPhoto
import org.koocheh.model.AgeRange
import org.koocheh.model.ContextId
import org.koocheh.model.DisclosureTier
import org.koocheh.model.InterestTag

@OptIn(ExperimentalCoroutinesApi::class)
class TwoPeerFlowTest {

    private fun assertBoth(s: Sessions, tier: DisclosureTier) {
        assertEquals(tier, s.a.state.value.tier)
        assertEquals(tier, s.b.state.value.tier)
    }

    private fun assertEncounters(d: Duo, a: Int, b: Int) {
        assertEquals(a, d.a.store().incrementCalls.get())
        assertEquals(b, d.b.store().incrementCalls.get())
    }

    @Test
    fun tiersZeroToFour_nothingIsRevealedOneSidedly() = runTest {
        val d = Duo(backgroundScope)
        val s = d.connect()
        advanceUntilIdle()

        // Tier 0: a count only, no names, nothing stored, no encounter.
        assertBoth(s, DisclosureTier.T0_SIGNAL)
        for (st in listOf(s.a.state.value, s.b.state.value)) {
            assertEquals(2, st.sharedCount)
            assertNull(st.sharedTags)
            assertNull(st.peerNickname)
        }
        assertTrue(d.a.store().isEmpty() && d.b.store().isEmpty())

        // Tier 1: one side saying hello reveals nothing to either side.
        s.a.sayHello()
        advanceUntilIdle()
        assertBoth(s, DisclosureTier.T0_SIGNAL)
        assertNull(s.a.state.value.peerNickname)
        assertNull(s.b.state.value.peerNickname)
        assertNull(s.b.state.value.sharedTags)
        assertTrue(d.a.store().isEmpty() && d.b.store().isEmpty())
        s.b.sayHello()
        advanceUntilIdle()
        assertBoth(s, DisclosureTier.T1_HELLO)
        assertEquals("Bo", s.a.state.value.peerNickname)
        assertEquals("Ava", s.b.state.value.peerNickname)
        assertEquals(setOf(InterestTag("coffee"), InterestTag("chess")), s.a.state.value.sharedTags)
        assertEquals(1, d.a.store().peers.size)
        assertEncounters(d, 1, 1)

        // Tier 2: bio, one-sided confirmation changes nothing.
        s.a.confirmNext()
        advanceUntilIdle()
        assertBoth(s, DisclosureTier.T1_HELLO)
        assertNull(s.a.state.value.peerLine)
        assertNull(s.b.state.value.peerLine)
        assertEquals(DisclosureTier.T1_HELLO, d.b.store().peers.values.single().tier)
        s.b.confirmNext()
        advanceUntilIdle()
        assertBoth(s, DisclosureTier.T2_BIO)
        assertEquals("Bo's line", s.a.state.value.peerLine)
        assertEquals(AgeRange.R25_34, s.b.state.value.peerAgeRange)

        // Tier 3: blurred photo.
        s.a.confirmNext()
        advanceUntilIdle()
        assertBoth(s, DisclosureTier.T2_BIO)
        assertNull(s.a.state.value.peerPhoto)
        assertNull(s.b.state.value.peerPhoto)
        s.b.confirmNext()
        advanceUntilIdle()
        assertBoth(s, DisclosureTier.T3_PHOTO)
        assertEquals(PeerPhoto("blur-Bo".encodeToByteArray(), true), s.a.state.value.peerPhoto)

        // Tier 4: clear photo and chat.
        s.a.confirmNext()
        advanceUntilIdle()
        assertBoth(s, DisclosureTier.T3_PHOTO)
        assertEquals(PeerPhoto("blur-Ava".encodeToByteArray(), true), s.b.state.value.peerPhoto)
        s.b.confirmNext()
        advanceUntilIdle()
        assertBoth(s, DisclosureTier.T4_CHAT)
        assertEquals(PeerPhoto("clear-Bo".encodeToByteArray(), false), s.a.state.value.peerPhoto)
        assertEquals(DisclosureTier.T4_CHAT, d.a.store().peers.values.single().tier)

        // One encounter per session, however far it went.
        assertEncounters(d, 1, 1)
        assertEquals(setOf(Duo.DAY), d.a.store().encounterDays().values.single())

        // Chat text exists only in memory and sealed in the store.
        val key = d.a.store().peers.keys.single()
        val chat = d.a.stack.chat(ContextId.WORK)
        chat.send(key, "salam")
        assertEquals(listOf("salam"), chat.conversation(key).first().map { it.text })
        val sealed = d.a.store().messages(key).single().cipher
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("salam"))
    }

    @Test
    fun familiarRecordsOnlyAfterBothPeersSaidHello() = runTest {
        val d = Duo(backgroundScope)
        val s = d.connect()
        advanceUntilIdle()
        assertEncounters(d, 0, 0)
        s.a.sayHello()
        advanceUntilIdle()
        assertEncounters(d, 0, 0)
        s.b.sayHello()
        advanceUntilIdle()
        assertEncounters(d, 1, 1)
    }

    @Test
    fun withdrawalAtTierZero_recordsNoEncounter() = runTest {
        val d = Duo(backgroundScope)
        val s = d.connect()
        advanceUntilIdle()
        s.a.withdraw()
        advanceUntilIdle()
        assertTrue(s.a.state.value.withdrawn && s.b.state.value.withdrawn)
        assertEncounters(d, 0, 0)
    }

    @Test
    fun peerWithdrawingAtTierZeroAfterOurHello_recordsNoEncounter() = runTest {
        val d = Duo(backgroundScope)
        val s = d.connect()
        advanceUntilIdle()
        s.a.sayHello()
        advanceUntilIdle()
        s.b.withdraw()
        advanceUntilIdle()
        assertTrue(s.a.state.value.withdrawn)
        assertEncounters(d, 0, 0)
    }

    // Withdrawal at each tier deletes the exchanged data on both sides.
    private fun withdrawalAt(tier: Int) = runTest {
        val d = Duo(backgroundScope)
        val s = d.connect()
        advanceUntilIdle()
        advanceBoth(s, tier)
        var key: String? = null
        if (tier == 4) {
            key = d.a.store().peers.keys.single()
            d.a.stack.chat(ContextId.WORK).send(key, "bye")
            assertEquals(1, d.a.store().messages(key).size)
        }

        s.a.withdraw()
        advanceUntilIdle()

        assertTrue(s.a.state.value.withdrawn)
        assertTrue(s.b.state.value.withdrawn)
        assertTrue(d.a.store().peers.isEmpty())
        assertTrue(d.b.store().peers.isEmpty())
        if (key != null) assertTrue(d.a.store().messages(key).isEmpty())
        for (st in listOf(s.a.state.value, s.b.state.value)) {
            assertNull(st.sharedTags)
            assertNull(st.peerNickname)
            assertNull(st.peerLine)
            assertNull(st.peerPhoto)
        }
        try {
            s.b.confirmNext()
            fail("a withdrawn session must not continue")
        } catch (expected: IllegalStateException) {
            // expected
        }
    }

    @Test fun withdrawalAtTier0() = withdrawalAt(0)
    @Test fun withdrawalAtTier1() = withdrawalAt(1)
    @Test fun withdrawalAtTier2() = withdrawalAt(2)
    @Test fun withdrawalAtTier3() = withdrawalAt(3)
    @Test fun withdrawalAtTier4() = withdrawalAt(4)
}
