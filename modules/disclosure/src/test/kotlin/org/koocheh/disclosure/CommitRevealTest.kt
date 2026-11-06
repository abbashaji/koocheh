package org.koocheh.disclosure

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommitRevealTest {
    private val commitments = FakeCommitments()

    private fun round() = CommitRevealRound(1, commitments)

    @Test
    fun honestRoundTrip() {
        val a = round()
        val b = round()
        val ca = a.start("alice".encodeToByteArray())
        val cb = b.start("bob".encodeToByteArray())
        a.onPeerCommit(cb)
        b.onPeerCommit(ca)
        val (na, va) = a.takeReveal()
        val (nb, vb) = b.takeReveal()
        a.onPeerReveal(nb, vb)
        b.onPeerReveal(na, va)
        assertContentEquals("bob".encodeToByteArray(), a.peerValue)
        assertContentEquals("alice".encodeToByteArray(), b.peerValue)
        assertTrue(a.complete && b.complete)
    }

    @Test
    fun tamperedValueIsRejected() {
        val a = round()
        val b = round()
        a.start("alice".encodeToByteArray())
        val cb = b.start("bob".encodeToByteArray())
        a.onPeerCommit(cb)
        val (nb, _) = b.run { onPeerCommit(a.let { cb }); takeReveal() }
        assertFailsWith<TamperedCommitment> { a.onPeerReveal(nb, "eve".encodeToByteArray()) }
    }

    @Test
    fun repeatedCommitIsRejected() {
        val a = round()
        a.onPeerCommit(byteArrayOf(1, 2, 3))
        assertFailsWith<ProtocolViolation> { a.onPeerCommit(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun revealBeforeBothCommittedIsRejected() {
        val a = round()
        assertFailsWith<ProtocolViolation> { a.onPeerReveal(byteArrayOf(1), byteArrayOf(2)) }
        a.onPeerCommit(byteArrayOf(9))
        assertFailsWith<ProtocolViolation> { a.onPeerReveal(byteArrayOf(1), byteArrayOf(2)) }
        assertFailsWith<IllegalStateException> { a.takeReveal() }
    }

    @Test
    fun wireLimitsAndVersion() {
        assertFailsWith<ProtocolViolation> { WireCodec.decode(ByteArray(5000)) }
        assertFailsWith<ProtocolViolation> { WireCodec.requireRevealFits(1, 5000) }
        val v2 = """{"v":2,"msg":{"t":"withdraw","reason":0}}""".encodeToByteArray()
        assertFailsWith<ProtocolViolation> { WireCodec.decode(v2) }
        val ok = WireCodec.encode(CommitMsg(1, "AAAA"))
        assertTrue(WireCodec.decode(ok) is CommitMsg)
    }
}
