package org.koocheh.chat

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.koocheh.crypto.SecureSession
import org.koocheh.model.Clock
import org.koocheh.storage.ContextStore

data class ChatMessage(
    val id: String,
    val text: String,
    val sentAtMs: Long,
    val expiresAtMs: Long,
    val mine: Boolean,
)

/**
 * Tier 4 chat. The only place plaintext exists. Never logs message text.
 * Outgoing messages are stored sealed and delivered on next contact.
 */
class ChatService internal constructor(
    private val repo: ChatStore,
    private val now: () -> Long,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) : EnvelopeSink {

    /** Lifetime for new messages; can only be shortened from the 24 h default. */
    @Volatile
    var expiryMs: Long = DEFAULT_EXPIRY_MS
        set(value) {
            field = value.coerceIn(MIN_EXPIRY_MS, DEFAULT_EXPIRY_MS)
        }

    private val changes = MutableStateFlow(0L)
    private val withdrawn = ConcurrentHashMap.newKeySet<String>()

    suspend fun send(peerKey: String, text: String) {
        require(text.isNotEmpty() && text.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) {
            "message empty or too long"
        }
        val t = now()
        repo.append(peerKey, ChatRecord(KIND_MSG, ids(), text, t, t + expiryMs, mine = true))
        changed()
    }

    fun conversation(peerKey: String): Flow<List<ChatMessage>> =
        changes.map { load(peerKey) }

    suspend fun purgeExpired() {
        repo.purge(now())
        changed()
    }

    /** Local withdrawal: delete the conversation and tell the peer at next contact. */
    suspend fun withdraw(peerKey: String) {
        repo.delete(peerKey)
        withdrawn.add(peerKey)
        changed()
    }

    private fun changed() {
        changes.value = changes.value + 1
    }

    private suspend fun load(peerKey: String): List<ChatMessage> {
        val t = now()
        return repo.records(peerKey)
            .filter { it.kind == KIND_MSG && it.expiresAtMs > t }
            .distinctBy { it.id }
            .sortedBy { it.sentAtMs }
            .map { ChatMessage(it.id, it.text, it.sentAtMs, it.expiresAtMs, it.mine) }
    }

    override suspend fun outgoing(peerKey: String, session: SecureSession): List<MessageEnvelope> {
        val t = now()
        if (peerKey in withdrawn) {
            return listOf(
                MessageEnvelope.of(EnvelopeKind.WITHDRAW, ids(), t, t, session.encrypt(ByteArray(0))),
            )
        }
        val recs = repo.records(peerKey)
        val acked = recs.filter { it.kind == KIND_ACK }.map { it.id }.toSet()
        return recs
            .filter { it.kind == KIND_MSG && it.mine && it.id !in acked && it.expiresAtMs > t }
            .sortedBy { it.sentAtMs }
            .map {
                MessageEnvelope.of(
                    EnvelopeKind.MESSAGE, it.id, it.sentAtMs, it.expiresAtMs,
                    session.encrypt(it.text.toByteArray(Charsets.UTF_8)),
                )
            }
    }

    override suspend fun delivered(peerKey: String, envelope: MessageEnvelope) {
        when (envelope.kind) {
            EnvelopeKind.WITHDRAW -> withdrawn.remove(peerKey)
            EnvelopeKind.MESSAGE -> {
                repo.append(
                    peerKey,
                    ChatRecord(KIND_ACK, envelope.id, expiresAtMs = envelope.expiresAtMs, mine = true),
                )
            }
        }
    }

    /** Returns true when the peer signalled it is finished. Undecryptable input is dropped. */
    override suspend fun incoming(
        peerKey: String,
        session: SecureSession,
        envelope: MessageEnvelope,
    ): Boolean {
        val plain = try {
            session.decrypt(envelope.ciphertext())
        } catch (e: Exception) {
            return false
        }
        when (envelope.kind) {
            EnvelopeKind.FIN -> return true
            EnvelopeKind.WITHDRAW -> {
                repo.delete(peerKey)
                changed()
            }
            EnvelopeKind.MESSAGE -> {
                val t = now()
                // A peer can never keep a message longer than the default lifetime.
                val expires = minOf(envelope.expiresAtMs, t + DEFAULT_EXPIRY_MS)
                if (expires <= t) return false
                if (repo.records(peerKey).any { it.id == envelope.id }) return false
                val text = try {
                    String(plain, Charsets.UTF_8)
                } catch (e: Exception) {
                    return false
                }
                repo.append(
                    peerKey,
                    ChatRecord(KIND_MSG, envelope.id, text, minOf(envelope.sentAtMs, t), expires),
                )
                changed()
            }
        }
        return false
    }

    companion object {
        fun create(store: ContextStore, sealer: MessageSealer, clock: Clock): ChatService =
            ChatService(ChatStore(store.asPort(), sealer), { clock.nowMs() })
    }
}
