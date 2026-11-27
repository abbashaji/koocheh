package org.koocheh.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.koocheh.model.ContextId

/** Withdrawal at each tier deletes the exchanged data on both sides. */
@OptIn(ExperimentalCoroutinesApi::class)
class WithdrawalTest {

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
