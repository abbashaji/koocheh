package org.koocheh.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koocheh.AdvertisedIdRotator
import org.koocheh.crypto.HkdfEphemeralIdProvider
import org.koocheh.model.ContextId
import org.koocheh.model.EphemeralId
import org.koocheh.model.FakeClock
import org.koocheh.model.InterestTag

/** Work and Social ids, secrets, stores and link keys share nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextIsolationTest {
    private val work = ContextId.WORK
    private val social = ContextId.SOCIAL
    private fun hex(id: EphemeralId) = id.bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun idsAndSecretsDoNotCrossContexts() {
        val h = VaultHarness().also { it.unlockReal() }
        val ids = HkdfEphemeralIdProvider(h.vault, JdkCryptoBackend())
        assertFalse(h.vault.secret(work).contentEquals(h.vault.secret(social)))
        val times = (0..40).map { 1_700_000_000_000L + it * 7 * 60_000L }
        val workIds = times.map { hex(ids.current(work, it)) }.toSet()
        val socialIds = times.map { hex(ids.current(social, it)) }.toSet()
        assertTrue("ids must rotate", workIds.size > 1 && socialIds.size > 1)
        assertTrue("no id may appear in both contexts", workIds.intersect(socialIds).isEmpty())
    }

    @Test
    fun contactInOneContextLeavesTheOtherEmpty() = runTest {
        val d = Duo(backgroundScope)
        val s = d.connect(work)
        advanceUntilIdle()
        advanceBoth(s, 1)

        assertEquals(1, d.a.store(work).peers.size)
        assertEquals(1, d.a.store(work).incrementCalls.get())
        assertTrue(d.a.store(social).isEmpty())
        assertTrue(d.b.store(social).isEmpty())
        val today = Duo.DAY
        assertTrue(d.a.stack.familiar(social).prompts(social, today).isEmpty())
    }

    @Test
    fun sameOverlapInBothContextsGivesUnlinkableKeysAndSeparateStores() = runTest {
        val d = Duo(backgroundScope)
        val w = d.connect(work)
        val so = d.connect(social)
        advanceUntilIdle()
        advanceBoth(w, 1)
        advanceBoth(so, 1)

        val workKeys = d.a.store(work).encounterDays().keys
        val socialKeys = d.a.store(social).encounterDays().keys
        assertEquals(1, workKeys.size)
        assertEquals(1, socialKeys.size)
        assertTrue(workKeys.intersect(socialKeys).isEmpty())
        assertTrue(d.a.store(work).peers.keys.intersect(d.a.store(social).peers.keys).isEmpty())

        val tags = setOf(InterestTag("coffee"), InterestTag("chess"))
        val kw = d.a.stack.linker.linkKey(work, tags, Duo.DAY)
        val ks = d.a.stack.linker.linkKey(social, tags, Duo.DAY)
        assertFalse(kw.contentEquals(ks))
    }

    @Test
    fun advertisedIdsRotateAtTheNextRotationInstant() = runTest {
        val h = VaultHarness().also { it.unlockReal() }
        val clock = FakeClock(now = 1_700_000_000_000L)
        val ids = HkdfEphemeralIdProvider(h.vault, JdkCryptoBackend())
        val advertised = HashMap<Int, EphemeralId>()
        AdvertisedIdRotator(ids, { slot, id -> advertised[slot] = id }, listOf(work, social), clock, backgroundScope)
            .start()
        runCurrent()

        val first = advertised.toMap()
        assertEquals(setOf(0, 1), first.keys)
        assertNotEquals(first[0], first[1])
        assertEquals(ids.current(work, clock.nowMs()), first[0])
        assertEquals(ids.current(social, clock.nowMs()), first[1])

        val now = clock.nowMs()
        val wait = minOf(ids.nextRotationAtMs(work, now), ids.nextRotationAtMs(social, now)) - now
        clock.advance(wait)
        advanceTimeBy(wait)
        runCurrent()

        assertEquals(ids.current(work, clock.nowMs()), advertised[0])
        assertEquals(ids.current(social, clock.nowMs()), advertised[1])
        assertTrue(advertised[0] != first[0] || advertised[1] != first[1])
    }
}
