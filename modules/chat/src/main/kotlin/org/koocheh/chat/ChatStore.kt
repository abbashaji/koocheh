package org.koocheh.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koocheh.storage.ContextStore

/** Seals local records at rest (queued outgoing and received messages). */
interface MessageSealer {
    fun seal(plain: ByteArray): ByteArray
    fun open(sealed: ByteArray): ByteArray
}

/** Narrow port over ContextStore so only the adapter below touches StoredMessage. */
internal interface MessagePort {
    suspend fun append(peerKey: String, expiresAtMs: Long, cipher: ByteArray)
    suspend fun list(peerKey: String): List<ByteArray>
    suspend fun delete(peerKey: String)
    suspend fun purge(nowMs: Long)
}

// Assumes StoredMessage exposes the stored blob as `cipher`.
internal fun ContextStore.asPort(): MessagePort = object : MessagePort {
    override suspend fun append(peerKey: String, expiresAtMs: Long, cipher: ByteArray) =
        putMessage(peerKey, expiresAtMs, cipher)

    override suspend fun list(peerKey: String): List<ByteArray> =
        messages(peerKey).map { it.cipher }

    override suspend fun delete(peerKey: String) = deletePeer(peerKey)
    override suspend fun purge(nowMs: Long) = purgeExpired(nowMs)
}

internal const val KIND_MSG = 0
internal const val KIND_ACK = 1

@Serializable
internal data class ChatRecord(
    val kind: Int,
    val id: String,
    val text: String = "",
    val sentAtMs: Long = 0,
    val expiresAtMs: Long,
    val mine: Boolean = false,
)

/** Append-only record log per peer. Undecodable records are dropped. */
internal class ChatStore(private val port: MessagePort, private val sealer: MessageSealer) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun append(peerKey: String, r: ChatRecord) {
        val sealed = sealer.seal(json.encodeToString(r).toByteArray(Charsets.UTF_8))
        port.append(peerKey, r.expiresAtMs, sealed)
    }

    suspend fun records(peerKey: String): List<ChatRecord> = port.list(peerKey).mapNotNull {
        try {
            json.decodeFromString<ChatRecord>(String(sealer.open(it), Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun delete(peerKey: String) = port.delete(peerKey)
    suspend fun purge(nowMs: Long) = port.purge(nowMs)
}
