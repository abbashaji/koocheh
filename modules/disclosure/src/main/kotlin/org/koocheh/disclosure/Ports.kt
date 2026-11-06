package org.koocheh.disclosure

import org.koocheh.model.AgeRange
import org.koocheh.model.DisclosureTier
import org.koocheh.model.InterestTag

// Local ports for the contracts of C-004..C-007. Adapters to the real modules live elsewhere.

class Commit(val commitment: ByteArray, val nonce: ByteArray)

interface Commitments {
    fun commit(value: ByteArray): Commit
    fun verify(commitment: ByteArray, nonce: ByteArray, value: ByteArray): Boolean
}

enum class RevealLevel { COUNT_ONLY, TAGS }

class PsiResult(val count: Int, val sharedTags: Set<InterestTag>?)

interface PsiSession {
    fun outgoing(): ByteArray
    fun incoming(msg: ByteArray)
    val result: PsiResult?
}

interface PsiEngine {
    fun newSession(localTags: Set<InterestTag>, reveal: RevealLevel): PsiSession
}

interface ContextStore {
    suspend fun putPeerRecord(peerKey: String, tier: DisclosureTier, payload: ByteArray)
    suspend fun getPeerRecord(peerKey: String): ByteArray?
    suspend fun deletePeer(peerKey: String)
}

interface PeerChannel {
    suspend fun send(bytes: ByteArray)
    suspend fun receive(): ByteArray
    fun close()
}

/** Turns an AgeRange into a short string for the wire and back. */
interface AgeRangeCodec {
    fun encode(range: AgeRange): String
    fun decode(text: String): AgeRange?
}

/** What the local user is willing to disclose, tier by tier. Never logged. */
class LocalDisclosure(
    val tags: Set<InterestTag>,
    val threshold: Int,
    val nickname: String,
    val line: String,
    val ageRange: AgeRange,
    val blurredPhoto: ByteArray?,
    val clearPhoto: ByteArray?,
) {
    override fun toString(): String = "LocalDisclosure(redacted)"
}
