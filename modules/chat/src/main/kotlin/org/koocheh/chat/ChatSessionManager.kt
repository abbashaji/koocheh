package org.koocheh.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koocheh.crypto.HandshakeState
import org.koocheh.crypto.SecureSession
import org.koocheh.transport.PeerChannel

/** What the manager needs from ChatService; keeps plaintext inside ChatService. */
internal interface EnvelopeSink {
    suspend fun outgoing(peerKey: String, session: SecureSession): List<MessageEnvelope>
    suspend fun delivered(peerKey: String, envelope: MessageEnvelope)
    suspend fun incoming(peerKey: String, session: SecureSession, envelope: MessageEnvelope): Boolean
}

/**
 * Runs one contact with a matched peer (both sides at T4): handshake into a
 * SecureSession over the PeerChannel, flush queued messages in order, receive
 * until the peer's FIN. Queued messages count as delivered only once the peer's
 * FIN arrives; otherwise they are resent next contact (receiver dedupes by id).
 */
class ChatSessionManager internal constructor(private val sink: EnvelopeSink) {

    constructor(service: ChatService) : this(service as EnvelopeSink)

    /** Returns true if the exchange completed cleanly. */
    suspend fun exchange(peerKey: String, channel: PeerChannel, handshake: HandshakeState): Boolean {
        try {
            val session = establish(channel, handshake) ?: return false
            val sent = mutableListOf<MessageEnvelope>()
            coroutineScope {
                val sender = launch {
                    for (env in sink.outgoing(peerKey, session)) {
                        channel.send(env.encode())
                        sent.add(env)
                    }
                    channel.send(finEnvelope(session).encode())
                }
                var finished = false
                while (!finished) {
                    val env = MessageEnvelope.decode(channel.receive()) ?: continue
                    finished = sink.incoming(peerKey, session, env)
                }
                sender.join()
            }
            sent.forEach { sink.delivered(peerKey, it) }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        } finally {
            channel.close()
        }
    }

    private suspend fun establish(channel: PeerChannel, hs: HandshakeState): SecureSession? {
        var out = hs.next(null)
        for (i in 0 until MAX_ROUNDS) {
            val o = out
            if (o != null) channel.send(o)
            hs.session()?.let { return it }
            out = hs.next(channel.receive())
        }
        return hs.session()
    }

    private fun finEnvelope(session: SecureSession) =
        MessageEnvelope.of(EnvelopeKind.FIN, "fin", 0, 0, session.encrypt(ByteArray(0)))

    private companion object {
        const val MAX_ROUNDS = 8
    }
}
