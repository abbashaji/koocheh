package org.koocheh.integration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koocheh.model.ContextId
import org.koocheh.model.DisclosureTier
import org.koocheh.security.ActivityFinisher
import org.koocheh.security.AppMode
import org.koocheh.security.DecoyGate
import org.koocheh.security.DiscoveryStopper
import org.koocheh.security.NotificationClearer
import org.koocheh.security.PanicWipe
import org.koocheh.security.RecentsClearer
import org.koocheh.storage.UnlockResult

/** Real PIN, decoy PIN and wipe PIN outcomes, and what is left after a wipe. */
class WipeAndDecoyTest {
    private val work = ContextId.WORK
    private val gate = DecoyGate()

    private fun unlock(h: VaultHarness, pin: String) = h.vault.unlock(pin.toCharArray())

    private fun harnessWithAllPins(): VaultHarness {
        val h = VaultHarness()
        h.unlockReal("1111")
        h.vault.enrollDecoyPin("2222".toCharArray())
        h.vault.enrollWipePin("9999".toCharArray())
        runBlocking { h.stores.memory(work).putPeerRecord("peer", DisclosureTier.T1_HELLO, byteArrayOf(1, 2, 3)) }
        return h
    }

    private fun assertUnreadable(h: VaultHarness) {
        assertNull(h.storage.read())
        assertTrue(h.wrapper.destroyed)
        assertFalse(h.vault.isInitialized())
        assertTrue(h.stores.isEmpty())
        assertThrows(IllegalStateException::class.java) { h.vault.namespace }
        assertThrows(IllegalStateException::class.java) { h.vault.secret(work) }
        assertThrows(IllegalStateException::class.java) { h.stores.memory(work) }
        assertEquals(UnlockResult.WRONG, unlock(h, "1111"))
        assertEquals(UnlockResult.WRONG, unlock(h, "2222"))
    }

    @Test
    fun realPin_opensTheRealNamespace() {
        val h = harnessWithAllPins()
        h.vault.lock()
        assertThrows(IllegalStateException::class.java) { h.vault.secret(work) }
        assertEquals(UnlockResult.REAL, unlock(h, "1111"))
        assertEquals("real", h.vault.namespace)
        assertEquals(AppMode.REAL, gate.route(UnlockResult.REAL))
        assertEquals(1, h.stores.memory(work).peers.size)
    }

    @Test
    fun decoyPin_opensAnEmptyNamespaceWithOtherSecrets() {
        val h = harnessWithAllPins()
        val realSecret = h.vault.secret(work)
        assertEquals(UnlockResult.DECOY, unlock(h, "2222"))
        assertEquals("decoy", h.vault.namespace)
        assertEquals(AppMode.DECOY, gate.route(UnlockResult.DECOY))
        assertFalse(realSecret.contentEquals(h.vault.secret(work)))
        assertTrue(h.stores.memory(work).peers.isEmpty())
        assertEquals(UnlockResult.REAL, unlock(h, "1111"))
        assertEquals(1, h.stores.memory(work).peers.size)
    }

    @Test
    fun wrongPin_staysLockedAndKeepsData() {
        val h = harnessWithAllPins()
        h.vault.lock()
        assertEquals(UnlockResult.WRONG, unlock(h, "0000"))
        assertEquals(AppMode.LOCKED, gate.route(UnlockResult.WRONG))
        assertTrue(h.vault.isInitialized())
        assertEquals(0, h.destroyCalls.get())
    }

    @Test
    fun wipePin_destroysEverythingAndLooksLikeALockedApp() {
        val h = harnessWithAllPins()
        assertEquals(UnlockResult.WIPED, unlock(h, "9999"))
        assertEquals(AppMode.LOCKED, gate.route(UnlockResult.WIPED))
        assertEquals(gate.route(UnlockResult.WRONG), gate.route(UnlockResult.WIPED))
        assertEquals(1, h.destroyCalls.get())
        assertUnreadable(h)
    }

    @Test
    fun panicWipe_runsEveryStepEvenWhenOneFails_andIsRepeatable() {
        val h = harnessWithAllPins()
        val steps = mutableListOf<String>()
        val panic = PanicWipe(
            h.vault,
            DiscoveryStopper {
                steps += "discovery"
                error("radio failure")
            },
            NotificationClearer { steps += "notifications" },
            RecentsClearer { steps += "recents" },
            ActivityFinisher { steps += "activities" },
        )
        runBlocking { panic.execute() }
        assertEquals(listOf("discovery", "notifications", "recents", "activities"), steps)
        assertUnreadable(h)
        runBlocking { panic.execute() }
        assertUnreadable(h)
    }
}
