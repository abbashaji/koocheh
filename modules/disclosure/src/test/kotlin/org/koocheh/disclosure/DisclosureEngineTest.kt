package org.koocheh.disclosure

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koocheh.model.DisclosureTier

class DisclosureEngineTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registry = FakePsiRegistry()
    private val age = TestModel.ageRange()
    private val codec = FakeAgeCodec(listOf(age))
    private val tags = (0..3).map { TestModel.tag(it) }

    @AfterTest
    fun tearDown() = scope.cancel()

    private class Peer(val session: DisclosureSession, val store: FakeStore)

    private fun peer(
        channel: PeerChannel,
        name: String,
        mine: Set<org.koocheh.model.InterestTag>,
        threshold: Int = 0,
        clear: ByteArray? = null,
    ): Peer {
        val store = FakeStore()
        val local = LocalDisclosure(mine, threshold, name, "line $name", age, "blur-$name".encodeToByteArray(), clear)
        val engine = DisclosureEngine(FakeCommitments(), FakePsi(registry), store, local, codec, scope)
        return Peer(engine.open(channel, TestModel.context()), store)
    }

    private suspend fun Peer.await(pred: (TierState) -> Boolean): TierState =
        withTimeout(5000) { session.state.first(pred) }

    private suspend fun Peer.reach(tier: DisclosureTier) = await { it.tier == tier && !it.withdrawn }

    private fun pair(thresholdA: Int = 0, thresholdB: Int = 0, clearA: ByteArray? = null, clearB: ByteArray? = null): Pair<Peer, Peer> {
        val (ca, cb) = channelPair()
        val a = peer(ca, "alice", setOf(tags[0], tags[1], tags[2]), thresholdA, clearA)
        val b = peer(cb, "bob", setOf(tags[1], tags[2], tags[3]), thresholdB, clearB)
        return a to b
    }

    private suspend fun advance(a: Peer, b: Peer, target: Int) {
        if (target >= 1) {
            a.session.sayHello()
            b.session.sayHello()
            a.reach(DisclosureTier.T1)
            b.reach(DisclosureTier.T1)
        }
        for (t in 2..target) {
            a.session.confirmNext()
            b.session.confirmNext()
            a.reach(DisclosureTier.valueOf("T$t"))
            b.reach(DisclosureTier.valueOf("T$t"))
        }
    }

    @Test
    fun happyPathAcrossTwoEngines() = runBlocking {
        val clearB = "clear-bob".encodeToByteArray()
        val (a, b) = pair(clearB = clearB)

        a.session.sayHello()
        b.session.sayHello()
        val a1 = a.reach(DisclosureTier.T1)
        val b1 = b.reach(DisclosureTier.T1)
        assertEquals(setOf(tags[1], tags[2]), a1.sharedTags)
        assertEquals(setOf(tags[1], tags[2]), b1.sharedTags)
        assertEquals("bob", a1.peerNickname)
        assertEquals("alice", b1.peerNickname)
        assertNull(a1.peerLine)

        a.session.confirmNext()
        b.session.confirmNext()
        val a2 = a.reach(DisclosureTier.T2)
        val b2 = b.reach(DisclosureTier.T2)
        assertEquals("line bob", a2.peerLine)
        assertEquals("line alice", b2.peerLine)
        assertEquals(age, a2.peerAgeRange)

        a.session.confirmNext()
        b.session.confirmNext()
        val a3 = a.reach(DisclosureTier.T3)
        b.reach(DisclosureTier.T3)
        assertEquals(PeerPhoto("blur-bob".encodeToByteArray(), true), a3.peerPhoto)

        a.session.confirmNext()
        b.session.confirmNext()
        val a4 = a.reach(DisclosureTier.T4)
        val b4 = b.reach(DisclosureTier.T4)
        assertEquals(PeerPhoto(clearB, false), a4.peerPhoto)
        assertEquals(PeerPhoto("blur-alice".encodeToByteArray(), true), b4.peerPhoto)
        assertFalse(a.store.isEmpty())
        assertFalse(b.store.isEmpty())
    }

    @Test
    fun oneSidedConfirmRevealsNothing() = runBlocking {
        val (a, b) = pair()
        a.session.sayHello()
        delay(300)
        val bs = b.session.state.value
        assertEquals(DisclosureTier.T0, bs.tier)
        assertNull(bs.peerNickname)
        assertNull(bs.sharedTags)
        val asv = a.session.state.value
        assertEquals(DisclosureTier.T0, asv.tier)
        assertNull(asv.peerNickname)
        assertNull(asv.sharedTags)
        assertTrue(a.store.isEmpty() && b.store.isEmpty())
    }

    @Test
    fun signalShownOnlyAtOrAboveThreshold() = runBlocking {
        val (a, b) = pair(thresholdA = 5, thresholdB = 1)
        assertEquals(2, b.await { it.sharedCount != null }.sharedCount)
        delay(300)
        assertNull(a.session.state.value.sharedCount)
    }

    @Test
    fun withdrawalAtEveryTierDeletesEverything() = runBlocking {
        for (target in 0..4) {
            val (a, b) = pair()
            advance(a, b, target)
            if (target >= 1) assertFalse(a.store.isEmpty() || b.store.isEmpty())
            a.session.withdraw()
            val bs = b.await { it.withdrawn }
            val asv = a.await { it.withdrawn }
            assertTrue(a.store.isEmpty() && b.store.isEmpty(), "store not empty at tier $target")
            for (s in listOf(asv, bs)) {
                assertNull(s.peerNickname)
                assertNull(s.peerLine)
                assertNull(s.peerPhoto)
                assertNull(s.sharedTags)
            }
        }
    }

    @Test
    fun tamperedCommitmentAborts() = runBlocking {
        val (ca, raw) = channelPair()
        val a = peer(ca, "alice", setOf(tags[0], tags[1]))
        val rawPsi = FakePsi(registry).newSession(setOf(tags[1]), RevealLevel.COUNT_ONLY)
        raw.send(WireCodec.encode(PsiMsg(0, rawPsi.outgoing().b64())))
        val mal = FakeCommitments().commit("mallory".encodeToByteArray())
        raw.send(WireCodec.encode(CommitMsg(1, mal.commitment.b64())))

        a.session.sayHello()
        withTimeout(5000) { while (WireCodec.decode(raw.receive()) !is RevealMsg) Unit }
        raw.send(WireCodec.encode(RevealMsg(1, mal.nonce.b64(), "eve".encodeToByteArray().b64())))

        a.await { it.withdrawn }
        assertTrue(a.store.isEmpty())
        withTimeout(5000) { while (WireCodec.decode(raw.receive()) !is WithdrawMsg) Unit }
    }

    @Test
    fun outOfOrderMessageAborts() = runBlocking {
        val (ca, raw) = channelPair()
        val a = peer(ca, "alice", setOf(tags[0], tags[1]))
        val rawPsi = FakePsi(registry).newSession(setOf(tags[1]), RevealLevel.COUNT_ONLY)
        raw.send(WireCodec.encode(PsiMsg(0, rawPsi.outgoing().b64())))
        raw.send(WireCodec.encode(CommitMsg(3, "AAAA")))
        a.await { it.withdrawn }
        assertTrue(a.store.isEmpty())
    }

    @Test
    fun repeatedMessageAborts() = runBlocking {
        val (ca, raw) = channelPair()
        val a = peer(ca, "alice", setOf(tags[0], tags[1]))
        val rawPsi = FakePsi(registry).newSession(setOf(tags[1]), RevealLevel.COUNT_ONLY)
        val psiMsg = WireCodec.encode(PsiMsg(0, rawPsi.outgoing().b64()))
        raw.send(psiMsg)
        raw.send(psiMsg)
        a.await { it.withdrawn }
    }
}
