package org.koocheh.disclosure

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import org.koocheh.model.AgeRange
import org.koocheh.model.DisclosureTier
import org.koocheh.model.InterestTag

class FakeCommitments : Commitments {
    private val random = SecureRandom()

    private fun digest(nonce: ByteArray, value: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(nonce + value)

    override fun commit(value: ByteArray): Commit {
        val nonce = ByteArray(16).also { random.nextBytes(it) }
        return Commit(digest(nonce, value), nonce)
    }

    override fun verify(commitment: ByteArray, nonce: ByteArray, value: ByteArray): Boolean =
        digest(nonce, value).contentEquals(commitment)
}

/** Shared by both sides of a test so each fake session can look up the peer's tags by id. */
class FakePsiRegistry {
    val sessions = ConcurrentHashMap<String, Set<InterestTag>>()
    val counter = AtomicInteger()
}

class FakePsi(private val registry: FakePsiRegistry) : PsiEngine {
    override fun newSession(localTags: Set<InterestTag>, reveal: RevealLevel): PsiSession {
        val id = "s${registry.counter.incrementAndGet()}"
        registry.sessions[id] = localTags
        return object : PsiSession {
            private var res: PsiResult? = null
            override fun outgoing(): ByteArray = id.encodeToByteArray()
            override fun incoming(msg: ByteArray) {
                val peer = registry.sessions[msg.decodeToString()] ?: error("unknown psi session")
                val shared = localTags.intersect(peer)
                res = PsiResult(shared.size, if (reveal == RevealLevel.TAGS) shared else null)
            }
            override val result: PsiResult? get() = res
        }
    }
}

class FakeStore : ContextStore {
    private val data = ConcurrentHashMap<String, MutableMap<DisclosureTier, ByteArray>>()

    fun isEmpty(): Boolean = data.isEmpty()

    override suspend fun putPeerRecord(peerKey: String, tier: DisclosureTier, payload: ByteArray) {
        data.getOrPut(peerKey) { ConcurrentHashMap() }[tier] = payload
    }

    override suspend fun getPeerRecord(peerKey: String): ByteArray? = data[peerKey]?.values?.lastOrNull()

    override suspend fun deletePeer(peerKey: String) {
        data.remove(peerKey)
    }
}

class FakeAgeCodec(private val known: List<AgeRange>) : AgeRangeCodec {
    override fun encode(range: AgeRange): String = range.toString()
    override fun decode(text: String): AgeRange? = known.firstOrNull { it.toString() == text }
}

class MemoryChannel(private val inbox: Channel<ByteArray>, private val outbox: Channel<ByteArray>) : PeerChannel {
    override suspend fun send(bytes: ByteArray) = outbox.send(bytes)
    override suspend fun receive(): ByteArray = inbox.receive()
    override fun close() {
        inbox.close()
    }
}

fun channelPair(): Pair<MemoryChannel, MemoryChannel> {
    val ab = Channel<ByteArray>(Channel.UNLIMITED)
    val ba = Channel<ByteArray>(Channel.UNLIMITED)
    return MemoryChannel(inbox = ba, outbox = ab) to MemoryChannel(inbox = ab, outbox = ba)
}
