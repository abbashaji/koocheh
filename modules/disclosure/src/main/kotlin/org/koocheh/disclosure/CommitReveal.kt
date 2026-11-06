package org.koocheh.disclosure

class TamperedCommitment : ProtocolViolation("commitment check failed")

/**
 * One commit-then-reveal step. Each side sends a commitment first; the nonce and value are
 * revealed only once both commitments exist, and the peer's reveal must match its commitment.
 */
class CommitRevealRound(val step: Int, private val commitments: Commitments) {
    private var local: Commit? = null
    private var localValue: ByteArray? = null
    private var peerCommitment: ByteArray? = null
    private var revealed = false

    var peerValue: ByteArray? = null
        private set

    val localStarted: Boolean get() = local != null
    val bothCommitted: Boolean get() = local != null && peerCommitment != null
    val complete: Boolean get() = revealed && peerValue != null

    /** Records our value and returns the commitment to send. */
    fun start(value: ByteArray): ByteArray {
        check(local == null) { "step already confirmed" }
        val c = commitments.commit(value)
        local = c
        localValue = value.copyOf()
        return c.commitment
    }

    fun onPeerCommit(commitment: ByteArray) {
        if (peerCommitment != null) throw ProtocolViolation("repeated commitment")
        if (commitment.isEmpty()) throw ProtocolViolation("empty commitment")
        peerCommitment = commitment
    }

    /** Returns (nonce, value) to reveal. Only legal once both commitments exist, and only once. */
    fun takeReveal(): Pair<ByteArray, ByteArray> {
        check(bothCommitted) { "reveal before both commitments" }
        check(!revealed) { "already revealed" }
        revealed = true
        return local!!.nonce to localValue!!
    }

    fun onPeerReveal(nonce: ByteArray, value: ByteArray) {
        val pc = peerCommitment ?: throw ProtocolViolation("reveal before commitment")
        if (local == null) throw ProtocolViolation("reveal before both committed")
        if (peerValue != null) throw ProtocolViolation("repeated reveal")
        if (!commitments.verify(pc, nonce, value)) throw TamperedCommitment()
        peerValue = value
    }

    fun clear() {
        local = null
        localValue = null
        peerCommitment = null
        peerValue = null
    }
}
