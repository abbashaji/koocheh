package org.koocheh.disclosure

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koocheh.model.AgeRange
import org.koocheh.model.ContextId
import org.koocheh.model.DisclosureTier
import org.koocheh.model.InterestTag

interface DisclosureSession {
    val state: StateFlow<TierState>
    suspend fun sayHello()
    suspend fun confirmNext()
    suspend fun withdraw()
}

class DisclosureEngine(
    private val commitments: Commitments,
    private val psi: PsiEngine,
    private val store: ContextStore,
    private val local: LocalDisclosure,
    private val ageCodec: AgeRangeCodec,
    private val scope: CoroutineScope,
) {
    fun open(channel: PeerChannel, context: ContextId): DisclosureSession {
        val session = SessionImpl(commitments, psi, store, local, ageCodec, channel, context, scope)
        session.start()
        return session
    }
}

@Serializable
internal data class Bio(val line: String, val age: String)

/**
 * Steps: 0 = PSI count only (T0), 1 = hello (T1), 2 = bio (T2), 3 = blurred photo (T3),
 * 4 = optional clear photo and chat unlock (T4). Every step is commit-then-reveal and needs both sides.
 */
internal class SessionImpl(
    private val commitments: Commitments,
    private val psi: PsiEngine,
    private val store: ContextStore,
    private val local: LocalDisclosure,
    private val ageCodec: AgeRangeCodec,
    private val channel: PeerChannel,
    context: ContextId,
    private val scope: CoroutineScope,
) : DisclosureSession {
    private val peerKey = "$context/${UUID.randomUUID()}"
    private val withdrawal = WithdrawalHandler(store, peerKey)
    private val lock = Mutex()
    private val json = Json
    private val mutableState = MutableStateFlow(TierState.initial())
    override val state: StateFlow<TierState> = mutableState.asStateFlow()
    private val ready = CompletableDeferred<Unit>()
    private var job: Job? = null

    private val rounds = HashMap<Int, CommitRevealRound>()
    private var psi0: PsiSession? = null
    private var psi1: PsiSession? = null
    private var psi1Received = false
    private var completed = -1
    private var wiped = false

    private var sharedCount: Int? = null
    private var sharedTags: Set<InterestTag>? = null
    private var nickname: String? = null
    private var line: String? = null
    private var age: AgeRange? = null
    private var photo: PeerPhoto? = null

    fun start() {
        job = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        try {
            lock.withLock {
                val s = psi.newSession(local.tags, RevealLevel.COUNT_ONLY)
                psi0 = s
                send(PsiMsg(0, s.outgoing().b64()))
            }
            while (true) {
                val raw = channel.receive()
                val stop = lock.withLock {
                    if (!wiped) {
                        try {
                            handle(WireCodec.decode(raw))
                        } catch (v: ProtocolViolation) {
                            wipe(notify = true)
                        }
                    }
                    wiped
                }
                if (stop) break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lock.withLock { if (!wiped) wipe(notify = false) }
        }
    }

    private suspend fun send(msg: WireMessage) = channel.send(WireCodec.encode(msg))

    override suspend fun sayHello() {
        ready.await()
        lock.withLock {
            check(!wiped) { "session withdrawn" }
            check(completed == 0) { "hello not available" }
            startStep(1, local.nickname.encodeToByteArray())
        }
    }

    override suspend fun confirmNext() {
        ready.await()
        lock.withLock {
            check(!wiped) { "session withdrawn" }
            check(completed in 1..3) { "nothing to confirm" }
            val step = completed + 1
            startStep(step, payloadFor(step))
        }
    }

    override suspend fun withdraw() {
        lock.withLock { if (!wiped) wipe(notify = true) }
        job?.cancel()
    }

    private fun payloadFor(step: Int): ByteArray = when (step) {
        2 -> json.encodeToString(Bio.serializer(), Bio(local.line, ageCodec.encode(local.ageRange))).encodeToByteArray()
        3 -> local.blurredPhoto ?: ByteArray(0)
        else -> local.clearPhoto ?: ByteArray(0)
    }

    private suspend fun startStep(step: Int, value: ByteArray) {
        WireCodec.requireRevealFits(step, value.size)
        val round = rounds.getOrPut(step) { CommitRevealRound(step, commitments) }
        check(!round.localStarted) { "step already confirmed" }
        send(CommitMsg(step, round.start(value).b64()))
        if (round.bothCommitted) afterBothCommitted(step, round)
    }

    private suspend fun afterBothCommitted(step: Int, round: CommitRevealRound) {
        val (nonce, value) = round.takeReveal()
        send(RevealMsg(step, nonce.b64(), value.b64()))
        if (step == 1) {
            val s = psi.newSession(local.tags, RevealLevel.TAGS)
            psi1 = s
            send(PsiMsg(1, s.outgoing().b64()))
        }
        tryComplete(step)
    }

    private suspend fun handle(msg: WireMessage) {
        when (msg) {
            is PsiMsg -> handlePsi(msg)
            is CommitMsg -> {
                val round = roundFor(msg.step)
                round.onPeerCommit(msg.commitment.unb64())
                if (round.bothCommitted) afterBothCommitted(msg.step, round)
            }
            is RevealMsg -> {
                val round = rounds[msg.step] ?: throw ProtocolViolation("unexpected reveal")
                if (completed != msg.step - 1) throw ProtocolViolation("out of order")
                round.onPeerReveal(msg.nonce.unb64(), msg.value.unb64())
                tryComplete(msg.step)
            }
            is WithdrawMsg -> wipe(notify = false)
        }
    }

    private fun roundFor(step: Int): CommitRevealRound {
        if (step !in 1..4 || completed != step - 1) throw ProtocolViolation("out of order")
        return rounds.getOrPut(step) { CommitRevealRound(step, commitments) }
    }

    private suspend fun handlePsi(msg: PsiMsg) {
        val data = msg.data.unb64()
        when (msg.round) {
            0 -> {
                val s = psi0 ?: throw ProtocolViolation("unexpected psi")
                if (completed >= 0) throw ProtocolViolation("repeated psi")
                feed(s, data)
                val r = s.result ?: throw ProtocolViolation("psi incomplete")
                completed = 0
                sharedCount = if (r.count >= local.threshold) r.count else null
                publish(DisclosureTier.T0_SIGNAL)
                ready.complete(Unit)
            }
            1 -> {
                val s = psi1 ?: throw ProtocolViolation("unexpected psi")
                if (psi1Received) throw ProtocolViolation("repeated psi")
                feed(s, data)
                psi1Received = true
                completeStep(1)
            }
            else -> throw ProtocolViolation("unknown psi round")
        }
    }

    private fun feed(s: PsiSession, data: ByteArray) {
        try {
            s.incoming(data)
        } catch (e: Exception) {
            throw ProtocolViolation("psi rejected")
        }
    }

    private suspend fun tryComplete(step: Int) = completeStep(step)

    // Also called when PSI round 1 arrives after the peer's reveal.
    private suspend fun completeStep(step: Int) {
        val round = rounds[step] ?: return
        if (!round.complete || completed >= step) return
        if (step == 1 && !psi1Received) return
        val value = round.peerValue ?: return
        val tier = DisclosureTier.entries[step]
        when (step) {
            1 -> {
                val r = psi1?.result ?: throw ProtocolViolation("psi incomplete")
                sharedTags = r.sharedTags ?: throw ProtocolViolation("tags missing")
                sharedCount = r.count
                nickname = value.decodeToString()
            }
            2 -> {
                val bio = try {
                    json.decodeFromString(Bio.serializer(), value.decodeToString())
                } catch (e: SerializationException) {
                    throw ProtocolViolation("bad bio")
                }
                line = bio.line
                age = ageCodec.decode(bio.age) ?: throw ProtocolViolation("bad age range")
            }
            3 -> photo = if (value.isEmpty()) null else PeerPhoto(value, blurred = true)
            4 -> if (value.isNotEmpty()) photo = PeerPhoto(value, blurred = false)
        }
        completed = step
        store.putPeerRecord(peerKey, tier, value)
        publish(tier)
        round.clear()
    }

    private fun publish(tier: DisclosureTier) {
        mutableState.value = TierState(tier, sharedCount, sharedTags, nickname, line, age, photo, false)
    }

    private suspend fun wipe(notify: Boolean) {
        wiped = true
        withdrawal.withdraw(
            notifyPeer = { if (notify) send(WithdrawMsg()) },
            discardMemory = { discard() },
        )
        ready.completeExceptionally(IllegalStateException("session withdrawn"))
        channel.close()
    }

    private fun discard() {
        rounds.values.forEach { it.clear() }
        rounds.clear()
        psi0 = null
        psi1 = null
        sharedCount = null
        sharedTags = null
        nickname = null
        line = null
        age = null
        photo = null
        completed = -1
        mutableState.value = TierState.withdrawn()
    }
}
